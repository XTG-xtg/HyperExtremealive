package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.SharedPreferences;

/** Draft edits and the applied photo keep separate immutable settings snapshots. */
final class PhotoOptions {
    final int lockStyle;
    final float x,y,zoom,frameX,frameY,frameSize,frameAngle,aodOffsetX,aodOffsetY;
    PhotoOptions(int lockStyle,float x,float y,float zoom,float frameX,float frameY,float frameSize,float frameAngle) {
        this(lockStyle,x,y,zoom,frameX,frameY,frameSize,frameAngle,0,0);
    }
    PhotoOptions(int lockStyle,float x,float y,float zoom,float frameX,float frameY,float frameSize,float frameAngle,float aodOffsetX,float aodOffsetY) {
        this.lockStyle=lockStyle>=0&&lockStyle<=2?lockStyle:0;
        this.x=finite(x,0,1,.5f);this.y=finite(y,0,1,.5f);this.zoom=finite(zoom,1,4,1);
        this.frameX=finite(frameX,0,1,.5f);this.frameY=finite(frameY,0,1,.5f);
        this.frameSize=finite(frameSize,.25f,1,1);this.frameAngle=finite(frameAngle,-45,45,0);
        this.aodOffsetX=finite(aodOffsetX,-.2f,.2f,0);this.aodOffsetY=finite(aodOffsetY,-.02f,.4f,0);
    }
    static PhotoOptions defaults(){return new PhotoOptions(0,.5f,.5f,1,.5f,.5f,1,0);}
    private static float finite(float value,float min,float max,float fallback){return Float.isNaN(value)||Float.isInfinite(value)?fallback:Math.max(min,Math.min(max,value));}
    static PhotoOptions load(Context c,boolean preview) {
        SharedPreferences p=c.getSharedPreferences(preview?"alive_photo_draft":"alive_photo_applied",0);
        if(preview&&!p.contains("lock"))return load(c,false);
        return new PhotoOptions(p.getInt("lock",0),p.getFloat("x",.5f),p.getFloat("y",.5f),p.getFloat("zoom",1),
            p.getFloat("frame_x",.5f),p.getFloat("frame_y",.5f),p.getFloat("frame_size",1),p.getFloat("frame_angle",0),
            p.getFloat("aod_offset_x",0),p.getFloat("aod_offset_y",0));
    }
    void save(Context c,boolean preview) {
        c.getSharedPreferences(preview?"alive_photo_draft":"alive_photo_applied",0).edit().putInt("lock",lockStyle)
            .putFloat("x",x).putFloat("y",y).putFloat("zoom",zoom).putFloat("frame_x",frameX).putFloat("frame_y",frameY)
            .putFloat("frame_size",frameSize).putFloat("frame_angle",frameAngle)
            .putFloat("aod_offset_x",aodOffsetX).putFloat("aod_offset_y",aodOffsetY).apply();
    }
}
