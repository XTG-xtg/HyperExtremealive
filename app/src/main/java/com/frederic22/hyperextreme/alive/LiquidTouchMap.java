package com.frederic22.hyperextreme.alive;

import android.os.Bundle;

/** Small, immutable screen-space coverage snapshot; no photograph crosses the IPC boundary. */
final class LiquidTouchMap {
    final int width,height,screenWidth,screenHeight;
    final String scene;
    private final byte[] bits;
    LiquidTouchMap(int width,int height,int screenWidth,int screenHeight,String scene,byte[] bits){
        this.width=width;this.height=height;this.screenWidth=screenWidth;this.screenHeight=screenHeight;
        this.scene=scene;this.bits=bits.clone();
    }
    boolean hit(float x,float y){
        if(!Float.isFinite(x)||!Float.isFinite(y)||x<0||y<0||x>=1||y>=1)return false;
        int index=(int)(y*height)*width+(int)(x*width);
        return (bits[index/8]&(1<<(index%8)))!=0;
    }
    Bundle bundle(){
        Bundle b=new Bundle();b.putInt("w",width);b.putInt("h",height);b.putInt("sw",screenWidth);b.putInt("sh",screenHeight);
        b.putString("scene",scene);b.putByteArray("bits",bits.clone());return b;
    }
    static LiquidTouchMap from(Bundle b){
        if(b==null)return null;
        int w=b.getInt("w"),h=b.getInt("h"),sw=b.getInt("sw"),sh=b.getInt("sh");
        byte[] bits=b.getByteArray("bits");String scene=b.getString("scene","");
        if(w<1||h<1||w>512||h>512||sw<1||sh<1||bits==null||bits.length!=(w*h+7)/8||scene.length()>8192)return null;
        return new LiquidTouchMap(w,h,sw,sh,scene,bits);
    }
}
