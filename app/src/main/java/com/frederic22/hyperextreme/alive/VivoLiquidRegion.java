package com.frederic22.hyperextreme.alive;

import android.graphics.Bitmap;
import android.graphics.Color;

/** One selection for the GPU, preview hit testing and the SystemUI gesture map. */
final class VivoLiquidRegion implements AutoCloseable {
    private static final float[] SAMPLES={.15f,.5f,.85f};
    final Bitmap mask;
    final PhotoViewport crop=new PhotoViewport();
    private final int photoWidth,photoHeight,width,height;
    VivoLiquidRegion(VivoOptions options,Bitmap photo,Bitmap subject,Bitmap paint,int width,int height){
        photoWidth=photo.getWidth();photoHeight=photo.getHeight();this.width=width;this.height=height;
        Bitmap source=options.area==1?paint:subject;
        if(options.area==4||source==null){
            mask=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
            // A missing selection must never silently turn into a full-image effect.
            mask.eraseColor(options.area==4?Color.WHITE:Color.BLACK);
        }else{
            mask=source.copy(Bitmap.Config.ARGB_8888,true);
            if(options.area==3){
                int[] pixels=new int[mask.getWidth()*mask.getHeight()];mask.getPixels(pixels,0,mask.getWidth(),0,0,mask.getWidth(),mask.getHeight());
                for(int i=0;i<pixels.length;i++){int value=255-Color.red(pixels[i]);pixels[i]=Color.rgb(value,value,value);}
                mask.setPixels(pixels,0,mask.getWidth(),0,0,mask.getWidth(),mask.getHeight());
            }
        }
        crop(options.viewport());
    }
    void crop(PhotoViewport viewport){crop.dimensions(photoWidth,photoHeight,width,height);crop.restore(viewport.centerX(),viewport.centerY(),viewport.zoom());}
    boolean hit(float x,float y,float scale){
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(scale)||scale<=0||x<0||x>1||y<0||y>1)return false;
        float sx=.5f+(x-.5f)/scale,sy=.4f+(y-.4f)/scale;
        float u=crop.left()+sx*crop.width(),v=crop.top()+sy*crop.height();
        if(u<0||v<0||u>=1||v>=1)return false;
        return Color.red(mask.getPixel(Math.min(mask.getWidth()-1,(int)(u*mask.getWidth())),Math.min(mask.getHeight()-1,(int)(v*mask.getHeight()))))>=128;
    }
    LiquidTouchMap touchMap(VivoOptions options){
        int w=width>=height?256:Math.max(1,Math.round(256f*width/height));
        int h=height>=width?256:Math.max(1,Math.round(256f*height/width));byte[] bits=new byte[(w*h+7)/8];
        float scale=options.wallpaperZoom?1.05f:1;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            // Conservative cells: do not steal double-tap-to-sleep across a selection edge.
            boolean selected=true;
            for(float dy:SAMPLES)for(float dx:SAMPLES)selected&=hit((x+dx)/w,(y+dy)/h,scale);
            if(selected){int i=y*w+x;bits[i/8]|=(byte)(1<<(i%8));}
        }
        return new LiquidTouchMap(w,h,width,height,options.json(),bits);
    }
    public void close(){mask.recycle();}
}
