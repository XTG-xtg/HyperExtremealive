package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Data-only, digest-pinned resource packs. No downloaded code or AssetManager reflection. */
final class DynamicResources {
    static final String MISSING = "请先在 Alive 的动态壁纸页导入资源包";
    private static final Map<AssetManager, Store> STORES = new WeakHashMap<>();
    private static final Object IMPORT_LOCK = new Object();
    interface Progress { void update(int percent); }
    private record Entry(long bytes, String sha256) {}
    private static final class Store {
        final byte[] manifest;
        final String digest;
        final File root, installed;
        final Map<String, Entry> files = new LinkedHashMap<>();
        final long total;
        Store(Context context) throws IOException {
            try (InputStream input=context.getAssets().open("dynamic-wallpapers.json")) {
                manifest=limited(input,1024*1024);
            }
            digest=hex(hash().digest(manifest));
            root=new File(context.getFilesDir(),"alive/dynamic-packs");
            installed=new File(root,digest);
            long size=0;
            try {
                JSONObject json=new JSONObject(new String(manifest,StandardCharsets.UTF_8));
                if(json.getInt("format")!=1||!"alive-dynamic-v1".equals(json.getString("id"))) throw new IOException("资源索引版本错误");
                JSONObject entries=json.getJSONObject("files");
                if(entries.length()==0||entries.length()>4096) throw new IOException("资源索引数量错误");
                Iterator<String> keys=entries.keys();
                while(keys.hasNext()) {
                    String name=keys.next();safeName(name);
                    JSONObject row=entries.getJSONObject(name);
                    long bytes=row.getLong("bytes");String sha=row.getString("sha256");
                    if(bytes<=0||bytes>256L*1024*1024||!sha.matches("[a-f0-9]{64}")) throw new IOException("资源索引错误");
                    files.put(name,new Entry(bytes,sha));size+=bytes;
                }
            } catch(org.json.JSONException error) { throw new IOException("资源索引无法读取",error); }
            if(size>1024L*1024*1024)throw new IOException("资源索引过大");
            total=size;
        }
        boolean ready() {
            File marker=new File(installed,"complete");
            if(!marker.isFile()||marker.length()!=64)return false;
            try { if(!digest.equals(new String(java.nio.file.Files.readAllBytes(marker.toPath()),StandardCharsets.US_ASCII)))return false; }
            catch(IOException e){return false;}
            for(Map.Entry<String,Entry> e:files.entrySet()) {
                File file=new File(installed,e.getKey());
                if(!file.isFile()||file.length()!=e.getValue().bytes)return false;
            }
            return true;
        }
    }
    static synchronized void bind(Context context) throws IOException { store(context); }
    private static synchronized Store store(Context context) throws IOException {
        AssetManager assets=context.getAssets();Store store=STORES.get(assets);
        if(store==null){store=new Store(context);STORES.put(assets,store);}
        return store;
    }
    static boolean needsPack(int variant){return variant>0&&variant<600;}
    static boolean installed(Context context) {
        try { return store(context).ready(); } catch(IOException ignored) { return false; }
    }
    static void require(Context context,int variant) throws IOException {
        bind(context);
        if(needsPack(variant)&&!installed(context))throw new IOException(MISSING);
    }
    private static File external(AssetManager assets,String path)throws IOException {
        Store source;
        synchronized(DynamicResources.class){source=STORES.get(assets);}
        if(source==null||!source.files.containsKey(path))return null;
        File file=new File(source.installed,path);
        if(!new File(source.installed,"complete").isFile()||!file.isFile()||file.length()!=source.files.get(path).bytes)
            throw new FileNotFoundException(MISSING);
        return file;
    }
    static InputStream open(AssetManager assets,String path)throws IOException {
        File file=external(assets,path);return file==null?assets.open(path):new FileInputStream(file);
    }
    static AssetFileDescriptor openFd(AssetManager assets,String path)throws IOException {
        File file=external(assets,path);
        return file==null?assets.openFd(path):new AssetFileDescriptor(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY),0,file.length());
    }
    /** Caller owns the input. The old valid generation remains available throughout validation. */
    static void importPack(Context context,InputStream input,Progress progress)throws IOException {
        synchronized(IMPORT_LOCK){importChecked(context,input,progress);}
    }
    private static void importChecked(Context context,InputStream input,Progress progress)throws IOException {
        Store store=store(context);
        if(!store.root.isDirectory()&&!store.root.mkdirs())throw new IOException("无法创建资源目录");
        if(new StatFs(store.root.getPath()).getAvailableBytes()<store.total+16L*1024*1024)throw new IOException("存储空间不足，请至少预留 "+(store.total/1024/1024+16)+" MB");
        File stage=new File(store.root,"stage-"+UUID.randomUUID());
        if(!stage.mkdir())throw new IOException("无法创建导入目录");
        boolean published=false;Set<String> seen=new HashSet<>();long written=0;int lastProgress=-1;
        try {
            InputStream bounded=new FilterInputStream(input){
                long bytes;
                private void check(int n)throws IOException{if(n>0&&(bytes+=n)>store.total+16L*1024*1024)throw new IOException("资源包超过大小限制");}
                @Override public int read()throws IOException{int n=super.read();check(n<0?0:1);return n;}
                @Override public int read(byte[] b,int o,int n)throws IOException{int count=in.read(b,o,n);check(count);return count;}
                @Override public void close(){} // The SAF caller owns its stream.
            };
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(bounded,65536))) {
                byte[] buffer=new byte[65536];ZipEntry item;
                while((item=zip.getNextEntry())!=null) {
                    progress.update(lastProgress<0?0:lastProgress);
                    String name=item.getName();safeName(name);
                    if(item.isDirectory()||!seen.add(name)||seen.size()>store.files.size()+1)throw new IOException("资源包存在重复或多余文件");
                    if(name.equals("manifest.json")) {
                        if(!Arrays.equals(limited(zip,1024*1024),store.manifest))throw new IOException("资源包版本不匹配，请下载当前版本的配套资源包");
                    } else {
                        if(!name.startsWith("assets/"))throw new IOException("不是 Alive 动态壁纸资源包");
                        String path=name.substring(7);Entry expected=store.files.get(path);
                        if(expected==null)throw new IOException("资源包包含不支持的文件");
                        if(item.getSize()>expected.bytes)throw new IOException("资源文件大小错误");
                        File destination=new File(stage,path);checkedChild(stage,destination);
                        if(!destination.getParentFile().isDirectory()&&!destination.getParentFile().mkdirs())throw new IOException("无法创建资源子目录");
                        MessageDigest digest=hash();long count=0;
                        try(FileOutputStream output=new FileOutputStream(destination)) {
                            for(int n;(n=zip.read(buffer))!=-1;) {
                                count+=n;if(count>expected.bytes)throw new IOException("资源文件超出大小限制");
                                output.write(buffer,0,n);digest.update(buffer,0,n);written+=n;
                                int percent=(int)(written*99/store.total);
                                if(percent!=lastProgress){lastProgress=percent;progress.update(percent);}
                            }
                            output.getFD().sync();
                        }
                        if(count!=expected.bytes||!hex(digest.digest()).equals(expected.sha256))throw new IOException("资源校验失败，请重新下载完整资源包");
                    }
                    zip.closeEntry();
                }
            }
            if(!seen.contains("manifest.json")||seen.size()!=store.files.size()+1)throw new IOException("资源包不完整，请重新下载");
            progress.update(99);
            try(FileOutputStream marker=new FileOutputStream(new File(stage,"complete"))) {
                marker.write(store.digest.getBytes(StandardCharsets.US_ASCII));marker.getFD().sync();
            }
            if(store.ready()) { progress.update(100);return; }
            // Only a previously incomplete generation can be replaced here.
            if(store.installed.exists())removeTree(store.root,store.installed);
            if(!stage.renameTo(store.installed))throw new IOException("资源包启用失败，请重试");
            published=true;progress.update(100);
        } finally { if(!published)removeTree(store.root,stage); }
    }
    private static void safeName(String name)throws IOException {
        if(name.isEmpty()||name.length()>240||name.startsWith("/")||name.contains("\\")||name.contains(":")||name.indexOf('\0')>=0)
            throw new IOException("资源包路径不合法");
        for(String part:name.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IOException("资源包路径不合法");
    }
    private static void checkedChild(File root,File file)throws IOException {
        String base=root.getCanonicalPath()+File.separator;
        if(!file.getCanonicalPath().startsWith(base))throw new IOException("资源路径越界");
    }
    private static void removeTree(File root,File file)throws IOException {
        checkedChild(root,file);
        if(!file.exists())return;
        if(file.isDirectory()) {
            File[] children=file.listFiles();if(children==null)throw new IOException("无法清理导入目录");
            for(File child:children)removeTree(root,child);
        }
        if(!file.delete())throw new IOException("无法清理导入文件");
    }
    private static byte[] limited(InputStream input,int limit)throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];
        for(int n;(n=input.read(buffer))!=-1;){if(output.size()+n>limit)throw new IOException("资源清单过大");output.write(buffer,0,n);}
        return output.toByteArray();
    }
    private static MessageDigest hash(){try{return MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new AssertionError(e);}}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder(bytes.length*2);for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    private DynamicResources(){}
}
