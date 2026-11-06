package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

/** Draft edits and the applied photo keep separate immutable settings snapshots. */
final class PhotoOptions {
    final int lockStyle,homeStyle,aodMode,tint;
    final String lockPhoto,homePhoto,framePhoto;
    final boolean followLock;
    final float homeX,homeY,homeZoom;
    final float x,y,zoom,frameX,frameY,frameSize,frameAngle,aodOffsetX,aodOffsetY;
    PhotoOptions(int lockStyle,float x,float y,float zoom,float frameX,float frameY,float frameSize,float frameAngle) {
        this(lockStyle,x,y,zoom,frameX,frameY,frameSize,frameAngle,0,0);
    }
    PhotoOptions(int lockStyle,float x,float y,float zoom,float frameX,float frameY,float frameSize,float frameAngle,float aodOffsetX,float aodOffsetY) {
        this(lockStyle,x,y,zoom,frameX,frameY,frameSize,frameAngle,aodOffsetX,aodOffsetY,0);
    }
    PhotoOptions(int lockStyle,float x,float y,float zoom,float frameX,float frameY,float frameSize,float frameAngle,float aodOffsetX,float aodOffsetY,int homeStyle) {
        this(lockStyle,x,y,zoom,frameX,frameY,frameSize,frameAngle,aodOffsetX,aodOffsetY,homeStyle,
            "","","",true,.5f,.5f,1,0,0xff264552);
    }
    PhotoOptions(int lockStyle,float x,float y,float zoom,float frameX,float frameY,float frameSize,float frameAngle,
            float aodOffsetX,float aodOffsetY,int homeStyle,String lockPhoto,String homePhoto,String framePhoto,
            boolean followLock,float homeX,float homeY,float homeZoom,int aodMode,int tint) {
        this.lockStyle=lockStyle>=0&&lockStyle<=5?lockStyle:0;
        this.homeStyle=homeStyle>=0&&homeStyle<=3?homeStyle:0;
        this.x=finite(x,0,1,.5f);this.y=finite(y,0,1,.5f);this.zoom=finite(zoom,1,4,1);
        this.frameX=finite(frameX,0,1,.5f);this.frameY=finite(frameY,0,1,.5f);
        this.frameSize=finite(frameSize,.25f,1,1);this.frameAngle=finite(frameAngle,-45,45,0);
        this.aodOffsetX=finite(aodOffsetX,-.2f,.2f,0);this.aodOffsetY=finite(aodOffsetY,-.02f,.4f,0);
        this.lockPhoto=mediaName(lockPhoto);this.homePhoto=mediaName(homePhoto);this.framePhoto=mediaName(framePhoto);
        this.followLock=followLock;this.homeX=finite(homeX,0,1,.5f);this.homeY=finite(homeY,0,1,.5f);this.homeZoom=finite(homeZoom,1,4,1);
        this.aodMode=aodMode>=0&&aodMode<=2?aodMode:0;this.tint=tint|0xff000000;
    }
    static String mediaName(String name){return name!=null&&name.matches("photo-[a-f0-9]{32}\\.png")?name:"";}
    int aod(int variant){return aodMode==1?101:aodMode==2?-1:variant-601;}
    static PhotoOptions defaults(){return new PhotoOptions(0,.5f,.5f,1,.5f,.5f,1,0);}
    private static float finite(float value,float min,float max,float fallback){return Float.isNaN(value)||Float.isInfinite(value)?fallback:Math.max(min,Math.min(max,value));}
    static PhotoOptions load(Context c,boolean preview) {
        SharedPreferences p=c.getSharedPreferences(preview?"alive_photo_draft":"alive_photo_applied",0);
        if(preview&&!p.contains("lock"))return load(c,false);
        return new PhotoOptions(p.getInt("lock",0),p.getFloat("x",.5f),p.getFloat("y",.5f),p.getFloat("zoom",1),
            p.getFloat("frame_x",.5f),p.getFloat("frame_y",.5f),p.getFloat("frame_size",1),p.getFloat("frame_angle",0),
            p.getFloat("aod_offset_x",0),p.getFloat("aod_offset_y",0),p.getInt("home",0),
            p.getString("lock_photo",""),p.getString("home_photo",""),p.getString("frame_photo",""),p.getBoolean("follow_lock",true),
            p.getFloat("home_x",.5f),p.getFloat("home_y",.5f),p.getFloat("home_zoom",1),p.getInt("aod_mode",0),p.getInt("tint",0xff264552));
    }
    void save(Context c,boolean preview) {
        if(!write(c.getSharedPreferences(preview?"alive_photo_draft":"alive_photo_applied",0).edit()).commit())
            throw new IllegalStateException("Cannot save photo settings");
    }
    private SharedPreferences.Editor write(SharedPreferences.Editor editor) {
        return editor.putInt("lock",lockStyle)
            .putInt("home",homeStyle)
            .putFloat("x",x).putFloat("y",y).putFloat("zoom",zoom).putFloat("frame_x",frameX).putFloat("frame_y",frameY)
            .putFloat("frame_size",frameSize).putFloat("frame_angle",frameAngle)
            .putFloat("aod_offset_x",aodOffsetX).putFloat("aod_offset_y",aodOffsetY)
            .putString("lock_photo",lockPhoto).putString("home_photo",homePhoto).putString("frame_photo",framePhoto)
            .putBoolean("follow_lock",followLock).putFloat("home_x",homeX).putFloat("home_y",homeY).putFloat("home_zoom",homeZoom)
            .putInt("aod_mode",aodMode).putInt("tint",tint);
    }
    PhotoOptions edit(int key,float value) {
        return new PhotoOptions(key==0?(int)value:lockStyle,key==1?value:x,key==2?value:y,key==3?value:zoom,
            key==4?value:frameX,key==5?value:frameY,key==6?1/value:frameSize,key==7?value:frameAngle,
            key==8?value:aodOffsetX,key==9?value:aodOffsetY,key==10?(int)value:homeStyle,lockPhoto,homePhoto,framePhoto,
            key==14?value!=0:followLock,key==11?value:homeX,key==12?value:homeY,key==13?value:homeZoom,key==15?(int)value:aodMode,tint);
    }
    PhotoOptions media(int slot,String name) {
        if(slot<0||slot>2||mediaName(name).isEmpty())throw new IllegalArgumentException("Invalid photo selection");
        return new PhotoOptions(lockStyle,slot==0?.5f:x,slot==0?.5f:y,slot==0?1:zoom,
            slot==2?.5f:frameX,slot==2?.5f:frameY,slot==2?1:frameSize,slot==2?0:frameAngle,aodOffsetX,aodOffsetY,homeStyle,
            slot==0?name:lockPhoto,slot==1?name:homePhoto,slot==2?name:framePhoto,slot==1?false:followLock,
            slot==1?.5f:homeX,slot==1?.5f:homeY,slot==1?1:homeZoom,aodMode,tint);
    }
    PhotoOptions color(int value) {
        return new PhotoOptions(lockStyle,x,y,zoom,frameX,frameY,frameSize,frameAngle,aodOffsetX,aodOffsetY,homeStyle,
            lockPhoto,homePhoto,framePhoto,followLock,homeX,homeY,homeZoom,aodMode,value);
    }
    PhotoOptions editableCrop(int slot) {
        if(slot!=1||!followLock)return this;
        // A first home gesture/slider edit starts from the image actually on screen.
        return new PhotoOptions(lockStyle,x,y,zoom,frameX,frameY,frameSize,frameAngle,aodOffsetX,aodOffsetY,homeStyle,
            lockPhoto,lockPhoto,framePhoto,false,x,y,zoom,aodMode,tint);
    }
    Bundle bundle() {
        Bundle b=new Bundle();b.putFloatArray("values",new float[]{lockStyle,x,y,zoom,frameX,frameY,frameSize,frameAngle,
            aodOffsetX,aodOffsetY,homeStyle,homeX,homeY,homeZoom,aodMode});
        b.putStringArray("media",new String[]{lockPhoto,homePhoto,framePhoto});b.putBoolean("follow",followLock);b.putInt("tint",tint);return b;
    }
    static PhotoOptions fromBundle(Bundle b) {
        float[] v=b.getFloatArray("values");String[] m=b.getStringArray("media");
        if(v==null||v.length!=15||m==null||m.length!=3)return defaults();
        return new PhotoOptions((int)v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9],(int)v[10],
            m[0],m[1],m[2],b.getBoolean("follow",true),v[11],v[12],v[13],(int)v[14],b.getInt("tint",0xff264552));
    }
}
