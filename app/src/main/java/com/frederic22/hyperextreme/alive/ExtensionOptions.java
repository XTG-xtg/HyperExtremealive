package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

/** Independent opt-in settings; opening the editor never changes an applied wallpaper. */
final class ExtensionOptions {
    static final String PREFS="alive_extensions";
    final boolean clock;
    final String style;
    final int light, seconds, color;
    final float scale;
    ExtensionOptions(boolean clock,String style,int light,int seconds,int color,float scale){
        this.clock=clock;this.style=validStyle(style)?style:NativeHyperOsStyles.ALL[0].id;
        this.light=light>=0&&light<=2?light:0;this.seconds=seconds==5||seconds==15?seconds:10;
        this.color=color|0xff000000;this.scale=Float.isFinite(scale)?Math.max(.65f,Math.min(1.35f,scale)):1;
    }
    static boolean validStyle(String style){
        if(style==null)return false;
        return NativeHyperOsStyles.find(style)!=null||NativeVivoClockStyles.contains(style)||style.matches("flyme-[0-5]");
    }
    static ExtensionOptions defaults(){return new ExtensionOptions(false,null,0,10,0xffeeeeee,1);}
    static ExtensionOptions load(Context c){
        SharedPreferences p=c.getSharedPreferences(PREFS,0);
        try{return new ExtensionOptions(p.getBoolean("clock",false),p.getString("style",null),p.getInt("light",0),p.getInt("seconds",10),p.getInt("color",0xffeeeeee),p.getFloat("scale",1));}
        catch(ClassCastException corrupt){return defaults();}
    }
    void write(Bundle b){
        b.putBoolean("ext_clock",clock);b.putString("ext_style",style);b.putInt("ext_light",light);b.putInt("ext_seconds",seconds);
        b.putInt("ext_color",color);b.putFloat("ext_scale",scale);
    }
    static ExtensionOptions from(Bundle b){return new ExtensionOptions(b.getBoolean("ext_clock",false),b.getString("ext_style"),b.getInt("ext_light",0),b.getInt("ext_seconds",10),b.getInt("ext_color",0xffeeeeee),b.getFloat("ext_scale",1));}
    void save(Context c){
        if(!c.getSharedPreferences(PREFS,0).edit().putBoolean("clock",clock).putString("style",style).putInt("light",light)
                .putInt("seconds",seconds).putInt("color",color).putFloat("scale",scale).commit())
            throw new IllegalStateException("Cannot save extension settings");
        c.getContentResolver().notifyChange(Protocol.CONFIG,null);
    }
    String clockKey(){return style+":"+color+":"+scale;}
}
