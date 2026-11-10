// Adapted from OpenAlive (MIT); see docs/alive/IMPORTED-EXTENSIONS.json.
package com.frederic22.hyperextreme.alive;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.res.*;
import android.util.AttributeSet;
import android.view.*;
import android.widget.TextView;
import dalvik.system.DexClassLoader;
import java.io.File;

/** Original binary layouts and isolated constraint solver; no Vivo services start. */
final class OfficialVivoClockUi extends ContextWrapper {
    static final String PACKAGE="com.vivo.systemuiplugin";
    private static String sharedPath;
    private static ClassLoader shared;
    private final Resources resources;
    private final Resources.Theme theme;
    private final ClassLoader loader;
    OfficialVivoClockUi(Context host)throws Exception {
        super(host);
        File apk=NativeClockRuntime.vivoLayouts(host);String path=apk.getPath();
        synchronized(OfficialVivoClockUi.class){
            if(!path.equals(sharedPath)){shared=new DexClassLoader(path,host.getCodeCacheDir().getPath(),null,Context.class.getClassLoader());sharedPath=path;}
            loader=shared;
        }
        ApplicationInfo info=new ApplicationInfo();info.packageName=PACKAGE;info.uid=android.os.Process.myUid();
        info.sourceDir=path;info.publicSourceDir=path;info.targetSdkVersion=35;
        resources=host.getPackageManager().getResourcesForApplication(info);
        theme=resources.newTheme();theme.applyStyle(android.R.style.Theme_Material_NoActionBar,true);
    }
    View inflate(int group,LayoutInflater.Factory2 factory){
        LayoutInflater inflater=LayoutInflater.from(getBaseContext()).cloneInContext(this);inflater.setFactory2(factory);
        int layout=resources.getIdentifier("view_time_s"+(group==4?2:group),"layout",PACKAGE);
        if(layout==0)throw new Resources.NotFoundException("Original Vivo clock layout "+group);
        return inflater.inflate(layout,null,false);
    }
    View create(String name,AttributeSet attrs)throws Exception {
        if(name.endsWith("ConstraintLayout"))return (View)loader.loadClass("androidx.constraintlayout.widget.ConstraintLayout").getConstructor(Context.class,AttributeSet.class).newInstance(this,attrs);
        if(name.startsWith("com.vivo.")&&(name.endsWith("TextView")||name.endsWith("InfoWidgetContainer")))return new TextView(this,attrs);
        return null;
    }
    @Override public Resources getResources(){return resources;}
    @Override public AssetManager getAssets(){return resources.getAssets();}
    @Override public Resources.Theme getTheme(){return theme;}
    @Override public ClassLoader getClassLoader(){return loader;}
}
