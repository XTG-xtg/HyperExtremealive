package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;

/** Own the launcher flag only while an applied Infinity home wallpaper opts in. */
final class LauncherWallpaperScroll {
    private static final String KEY="pref_key_wallpaper_screen_scrolled_span";
    private static final String BACKUP="hyperextreme_alive_scroll_previous";
    private Context selected;
    private ContentObserver observer;
    private int user=-1;
    private Integer previous;
    private long writeAt;

    static boolean wants(Bundle config,boolean ownsHome){
        int variant=config.getInt("variant",0);
        return ownsHome&&config.getBoolean("configured",false)&&config.getBoolean("launcher_scroll",true)
            &&variant>=801&&variant<=820;
    }
    void refresh(Context context,Handler worker,Bundle config,Runnable changed){
        try {
            int current=(Integer)Class.forName("android.app.ActivityManager").getMethod("getCurrentUser").invoke(null);
            Context target=SystemAodDimming.selectedContext(context);
            refreshForUser(current,target,worker,config,SystemAodDimming.ownsHome(target),changed);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){release();}
    }
    void refreshForUser(int current,Context target,Handler worker,Bundle config,boolean ownsHome,Runnable changed){
        try{
            if(user!=current||observer==null){
                release();
                if(selected!=null&&observer!=null)selected.getContentResolver().unregisterContentObserver(observer);
                observer=null;user=-1;
                previous=null;
                selected=target;
                ContentObserver next=new ContentObserver(worker){@Override public void onChange(boolean selfChange){changed.run();}};
                selected.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY),false,next);
                observer=next;user=current;
                int saved=Settings.Secure.getInt(selected.getContentResolver(),BACKUP,Integer.MIN_VALUE);
                if(saved!=Integer.MIN_VALUE)previous=saved;
            }
            if(!wants(config,ownsHome)){release();return;}
            int value=Settings.Secure.getInt(selected.getContentResolver(),KEY,0);
            if(previous==null){
                if(!Settings.Secure.putInt(selected.getContentResolver(),BACKUP,value))return;
                previous=value;
            }
            if(value==1)return;
            long now=SystemClock.uptimeMillis(),delay=2000-(now-writeAt);
            if(delay>0){worker.postDelayed(changed,delay);return;}
            if(Settings.Secure.putInt(selected.getContentResolver(),KEY,1))writeAt=now;
        }catch(RuntimeException|LinkageError unavailable){release();}
    }
    private void release(){
        if(previous==null||selected==null)return;
        try{
            int current=Settings.Secure.getInt(selected.getContentResolver(),KEY,0);
            if(current==1&&previous!=1&&!Settings.Secure.putInt(selected.getContentResolver(),KEY,previous))return;
            if(!Settings.Secure.putString(selected.getContentResolver(),BACKUP,null))return;
        }catch(RuntimeException unavailable){return;}
        previous=null;
    }
}
