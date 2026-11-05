package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.os.Process;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;

final class RootBridge {
    private static final String[] SU={"su","/system/bin/su","/system/xbin/su","/sbin/su","/debug_ramdisk/su","/product/bin/su"};
    static final class Result {final int code;final String text;Result(int c,String t){code=c;text=t;}boolean ok(){return code==0;}}
    static String quote(String value){return "'"+value.replace("'","'\"'\"'")+"'";}
    static Result run(String command) throws Exception {
        IOException unavailable=null;
        for(String su:SU){
            java.lang.Process p;
            try{p=new ProcessBuilder(su,"-M","-c",command).redirectErrorStream(true).start();}catch(IOException e){unavailable=e;continue;}
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            Thread reader=new Thread(()->{try(InputStream in=p.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))>=0){synchronized(output){if(output.size()<262144)output.write(b,0,n);}}}catch(IOException ignored){}},"RootOutput");
            reader.setDaemon(true);reader.start();
            if(!p.waitFor(20,TimeUnit.SECONDS)){p.destroyForcibly();reader.join(1000);return new Result(124,"Root command timed out; check authorization");}
            reader.join(1000);synchronized(output){return new Result(p.exitValue(),output.toString("UTF-8"));}
        }
        throw new IOException("No su executable",unavailable);
    }
    static synchronized Result wallpaper(Context c,boolean apply) throws Exception {
        return wallpaper(c,apply?"apply":"query");
    }
    static void record(Context c,String operation,Result result) {
        String text="operation="+operation+" time="+System.currentTimeMillis()+" exit="+result.code+"\n"+result.text;
        c.getSharedPreferences("alive_diagnostics",0).edit().putString("last_result",text)
            .putString("last_"+operation,text).apply();
        android.util.Log.i(Protocol.TAG,text);
    }
    static String lastResult(Context c) {
        return c.getSharedPreferences("alive_diagnostics",0).getString("last_result","");
    }
    static synchronized Result wallpaper(Context c,String operation) throws Exception {
        if(!operation.equals("apply")&&!operation.equals("query")&&!operation.equals("check"))throw new IllegalArgumentException("Invalid operation");
        boolean apply=operation.equals("apply");
        File local=new File(c.getCacheDir(),"alive_root.dex");
        try(InputStream in=c.getAssets().open("alive_root.dex");OutputStream out=new FileOutputStream(local)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(local)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)hash.update(b,0,n);}
        StringBuilder hex=new StringBuilder();for(byte b:hash.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        String folder="/data/local/tmp/"+Protocol.PACKAGE+"-helper",remote=folder+"/"+hex+".dex";
        String inner="CLASSPATH="+quote(remote)+" app_process /system/bin "+Protocol.ROOT_MAIN+" "+operation+" "+(Process.myUid()/100000);
        String cmd="mkdir -p "+quote(folder)+" && chmod 755 "+quote(folder)+" && cp "+quote(local.getAbsolutePath())+" "+quote(remote)+" && chmod 644 "+quote(remote)+" && su -M 1000 -c "+quote(inner);
        Result result=run(cmd);
        if(apply&&result.ok()&&!result.text.contains("PAIR_VERIFIED"))result=new Result(3,"Incomplete verification: "+result.text);
        if(result.code==10)result=new Result(10,"系统框架适配未就绪：请为 HyperExtreme 勾选“系统框架（system）”并重启。android 不是此 LSPosed 的系统框架作用域。本次预检没有修改壁纸。\n"+result.text);
        record(c,operation,result);
        if(apply&&result.ok()){Config.prefs(c).edit().putBoolean("enabled",true).apply();Config.changed(c);}
        return result;
    }
}
