package com.frederic22.hyperextreme.alive;

import android.content.Context;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Digest-pinned private runtimes. Never install APKs or start their Application/services. */
final class NativeClockRuntime {
    private static final Set<String> VERIFIED = new HashSet<>();
    private static ClassLoader flyme;
    private static String flymePath;
    static File unpack(Context c,String name)throws Exception {
        if(!"hyperos".equals(name))throw new IllegalArgumentException("Clock family");
        return pinned(c,"native-clock/runtime/hyperos/runtime.apk","native-clock/runtime/hyperos/runtime.sha256");
    }
    static File bridge(Context c)throws Exception {return pinned(c,"native-clock/bridge.dex","native-clock/bridge.sha256");}
    static File vivoLayouts(Context c)throws Exception {return pinned(c,"native-clock/vivo/layouts.apk","native-clock/vivo/layouts.sha256");}
    static File globalFont(Context c)throws Exception {
        return store(c,"native-clock/runtime/hyperos/fonts/MiSansVF.ttf","0ddef90648998900175cfdca9a6f087a2544c182f130b0ad4f7e94a03a115e79");
    }
    static synchronized ClassLoader flymeLoader(Context c)throws Exception {
        File code=pinned(c,"native-clock/runtime/flyme/code.dex","native-clock/runtime/flyme/code.sha256");
        if(flyme==null||!code.getPath().equals(flymePath)){
            flyme=new DexClassLoader(code.getPath(),c.getCodeCacheDir().getPath(),null,Context.class.getClassLoader());flymePath=code.getPath();
        }
        return flyme;
    }
    static File pinned(Context c,String asset,String digestAsset)throws Exception {
        try(InputStream in=c.getAssets().open(digestAsset)) {
            byte[] data=in.readNBytes(129);
            if(data.length>128)throw new IOException("Oversized digest");
            return store(c,asset,new String(data,java.nio.charset.StandardCharsets.US_ASCII).trim());
        }
    }
    static synchronized File store(Context c,String asset,String digest)throws Exception {
        if(!digest.matches("[a-f0-9]{64}"))throw new IOException("Invalid digest");
        File folder=new File(c.getCodeCacheDir(),"alive-clock-v1");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Clock cache unavailable");
        File target=new File(folder,digest+asset.substring(asset.lastIndexOf('.')));
        String stamp=target.getPath()+":"+target.length()+":"+target.lastModified();
        if(target.isFile()&&VERIFIED.contains(stamp))return target;
        if(target.isFile())try(InputStream in=new FileInputStream(target)) {
            if(digest.equals(hash(in))&&!target.canWrite()){VERIFIED.add(stamp);return target;}
        }
        File temp=File.createTempFile("clock-",".tmp",folder);
        try {
            try(InputStream in=c.getAssets().open(asset);FileOutputStream out=new FileOutputStream(temp)) {
                if(!temp.setReadOnly())throw new IOException("Cannot protect runtime");
                byte[] chunk=new byte[65536];for(int n;(n=in.read(chunk))!=-1;)out.write(chunk,0,n);out.getFD().sync();
            }
            try(InputStream in=new FileInputStream(temp)){if(!digest.equals(hash(in)))throw new IOException("Clock runtime checksum mismatch");}
            Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            VERIFIED.add(target.getPath()+":"+target.length()+":"+target.lastModified());return target;
        }finally{Files.deleteIfExists(temp.toPath());}
    }
    private static String hash(InputStream in)throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)hash.update(b,0,n);
        StringBuilder out=new StringBuilder();for(byte v:hash.digest())out.append(String.format(Locale.ROOT,"%02x",v&255));return out.toString();
    }
}
