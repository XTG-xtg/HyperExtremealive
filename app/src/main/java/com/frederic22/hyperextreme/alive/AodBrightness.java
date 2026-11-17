package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;

/** Shared 0..5 wallpaper exposure; zero preserves the original AOD brightness. */
public final class AodBrightness {
    public static final String KEY="aod_brightness";
    public static final float DEFAULT=0f,MIN=0f,MAX=5f,STEP=.25f;
    public static final String PANEL_MANAGED="aod_panel_managed";
    private static volatile boolean panelManaged;
    private AodBrightness(){}
    public static float normalize(float value){return Float.isFinite(value)?Math.max(MIN,Math.min(MAX,value)):DEFAULT;}
    public static float load(Context context){return normalize(Config.prefs(context).getFloat(KEY,DEFAULT));}
    public static void save(Context context,float value){
        Config.prefs(context).edit().putFloat(KEY,normalize(value)).apply();
        context.getContentResolver().notifyChange(Protocol.CONFIG,null);
        EngineRegistry.refreshOptions(context);
    }
    public static float gain(float value,float weight){
        float t=Float.isFinite(weight)?Math.max(0,Math.min(1,weight)):0;
        float level=normalize(value);
        return 1+((level==0?1:level)-1)*t;
    }
    /** Wake restores the panel and removes the OEM mask before the scene finishes.
     * Extra exposure must leave on its first awake frame, not trail that restoration.
     * Sub-unity dimming can still fade smoothly without producing a bright overshoot. */
    public static float frameGain(float value,float weight,boolean ambient){
        float exposure=gain(value,weight);
        return ambient?exposure:Math.min(1,exposure);
    }
    public static boolean panelManaged(){return panelManaged;}
    static void panelManaged(Context context,boolean managed){
        if(panelManaged==managed)return;
        panelManaged=managed;
        EngineRegistry.refreshOptions(context);
        context.getContentResolver().notifyChange(Protocol.CONFIG,null);
    }
    public static float renderGain(float value,float weight,boolean ambient,boolean preview,boolean managed){
        return !preview&&managed?1:frameGain(value,weight,ambient);
    }
    public static float dimWeight(float dim,float aodDim){
        return Math.max(0,Math.min(1,(1-dim)/Math.max(.001f,1-aodDim)));
    }
    /** Explicit brightness uses unmasked wallpaper pixels; zero keeps the OEM appearance. */
    public static float wallpaperBlack(float black,float value){
        // Fully black is the custom-AOD/hide target, never a brightness adjustment.
        if(!Float.isFinite(black)||black<=0||black>=1||normalize(value)==0)return black;
        return 0;
    }
    /** Keep the entry mask until wake/hide. OEM ambient-light updates must not
     * change an explicitly selected AOD brightness during the same session. */
    public static final class Hold {
        private float entry=Float.NaN;
        public void clear(){entry=Float.NaN;}
        public float black(float requested,float brightness){
            if(normalize(brightness)==0||!Float.isFinite(requested)||requested<=0||requested>=1){
                clear();return requested;
            }
            if(!Float.isFinite(entry))entry=requested;
            return entry;
        }
    }
    /** Hook layers draw their own mask; retain its raw progress for exposure/reversals. */
    public static float wallpaperDim(float dim,float aodDim,float value){
        if(!Float.isFinite(aodDim)||aodDim<=0)return dim;
        return 1-wallpaperBlack(1-dim,value);
    }
    // Same curve as shader/alive/presentation.frag; preserve true black and highlight detail.
    public static final String GLSL="vec3 aodExpose(vec3 rgb, float exposure) {\n"
        +"    rgb = clamp(rgb, 0.0, 1.0);\n"
        +"    return exposure <= 1.0 ? rgb * exposure : 1.0 - pow(1.0 - rgb, vec3(exposure));\n}\n";
    public static final String AGSL="float3 aodExpose(float3 rgb, float exposure) {\n"
        +"    rgb = clamp(rgb, 0.0, 1.0);\n"
        +"    return exposure <= 1.0 ? rgb * exposure : 1.0 - pow(1.0 - rgb, float3(exposure));\n}\n";

    /** Event-driven config bridge for both in-app services and SystemUI renderers. */
    public static final class Watch extends ContentObserver implements AutoCloseable {
        private final Context context;
        private final Runnable invalidate;
        private boolean registered;
        public volatile float value=DEFAULT;
        public volatile boolean managed;
        public Watch(Context context,Handler handler,Runnable invalidate){
            super(handler);this.context=context;this.invalidate=invalidate;
            refresh();
            try{context.getContentResolver().registerContentObserver(Protocol.CONFIG,true,this);registered=true;}
            catch(RuntimeException error){android.util.Log.w(Protocol.TAG,"Brightness observer unavailable",error);}
        }
        public void refresh(){
            try {
                // Spatial/raster services share the package but run in :wallpaper.
                // A process-local flag would apply shader gain on top of panel gain.
                Bundle result=context.getContentResolver().call(Protocol.CONFIG,"getConfig",null,null);
                if(result!=null){value=normalize(result.getFloat(KEY,DEFAULT));managed=result.getBoolean(PANEL_MANAGED,false);}
                else {managed=false;if(Protocol.PACKAGE.equals(context.getPackageName()))value=load(context);}
            }catch(RuntimeException error){managed=false;android.util.Log.w(Protocol.TAG,"Brightness refresh unavailable",error);}
        }
        @Override public void onChange(boolean selfChange){
            refresh();
            android.hardware.display.DisplayManager displays=context.getSystemService(android.hardware.display.DisplayManager.class);
            android.view.Display display=displays==null?null:displays.getDisplay(android.view.Display.DEFAULT_DISPLAY);
            if(display==null||display.getState()!=android.view.Display.STATE_OFF)invalidate.run();
        }
        @Override public void close(){if(registered){context.getContentResolver().unregisterContentObserver(this);registered=false;}}
    }
}
