package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import java.lang.reflect.Proxy;
import java.util.Collections;

/** Assets from the candidate APK without installing it; preferences are read-only defaults. */
final class ProbeContext {
    static Context vivo(Context base,String path)throws Exception{
        if(!path.matches("/data/local/tmp/hyperextreme-vivo-[a-z-]+"))throw new IllegalArgumentException("Isolated Vivo directory required");
        Context assets=wrap(base.createPackageContext(Protocol.PACKAGE,Context.CONTEXT_IGNORE_SECURITY));
        return new ContextWrapper(assets){
            private java.io.File dir(String name){java.io.File f=new java.io.File(path,name);f.mkdirs();return f;}
            public Context getApplicationContext(){return this;}
            public java.io.File getFilesDir(){return dir("files");}
            public java.io.File getCacheDir(){return dir("cache");}
            public java.io.File getCodeCacheDir(){return dir("code-cache");}
            public java.io.File getDir(String name,int mode){if(!name.matches("[A-Za-z0-9._-]+"))throw new IllegalArgumentException();return dir("dir-"+name);}
            public java.io.FileOutputStream openFileOutput(String name,int mode)throws java.io.FileNotFoundException{
                if(!name.matches("[A-Za-z0-9._-]+"))throw new IllegalArgumentException();return new java.io.FileOutputStream(new java.io.File(getFilesDir(),name));
            }
            public boolean deleteFile(String name){if(!name.matches("[A-Za-z0-9._-]+"))throw new IllegalArgumentException();return new java.io.File(getFilesDir(),name).delete();}
            public android.content.pm.ApplicationInfo getApplicationInfo(){android.content.pm.ApplicationInfo info=new android.content.pm.ApplicationInfo(super.getApplicationInfo());info.dataDir=path;return info;}
        };
    }
    static Context wrap(Context base)throws Exception{
        String path=System.getProperty("alive.test.apk","");
        if(!path.startsWith("/data/local/tmp/hyperextreme-"))throw new IllegalArgumentException("Explicit isolated test APK required");
        AssetManager assets=AssetManager.class.getConstructor().newInstance();
        int cookie=(Integer)AssetManager.class.getMethod("addAssetPath",String.class).invoke(assets,path);
        if(cookie==0)throw new IllegalArgumentException("Test APK assets unavailable: "+path);
        SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
            new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
                if(method.getName().equals("getAll"))return Collections.emptyMap();
                if(method.getName().equals("contains"))return false;
                if(method.getName().startsWith("get")&&args!=null&&args.length==2)return args[1];
                throw new AssertionError("Probe cannot write preferences: "+method.getName());
            });
        return new ContextWrapper(base){
            public Context getApplicationContext(){return this;}
            public AssetManager getAssets(){return assets;}
            public SharedPreferences getSharedPreferences(String name,int mode){return prefs;}
        };
    }
}
