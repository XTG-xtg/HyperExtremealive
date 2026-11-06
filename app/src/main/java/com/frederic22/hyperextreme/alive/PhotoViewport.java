// Derived from OpenAlive; MIT; see docs/alive/OPENALIVE_LICENSE and IMPORTED-VIVO.json.
package com.frederic22.hyperextreme.alive;

/** Normalized, centre-cropped photo viewport. No edit preserves centre-cover. */
final class PhotoViewport {
    private float imageAspect=1,viewAspect=1,zoom=1,x=.5f,y=.5f;
    private boolean measured;
    void dimensions(int imageWidth,int imageHeight,int viewWidth,int viewHeight){
        if(imageWidth<1||imageHeight<1||viewWidth<1||viewHeight<1)throw new IllegalArgumentException("Empty viewport");
        imageAspect=(float)imageWidth/imageHeight;viewAspect=(float)viewWidth/viewHeight;measured=true;clamp();
    }
    float width(){return Math.min(1,viewAspect/imageAspect)/zoom;}
    float height(){return Math.min(1,imageAspect/viewAspect)/zoom;}
    float left(){return x-width()/2;}
    float top(){return y-height()/2;}
    float zoom(){return zoom;}
    float centerX(){return x;}
    float centerY(){return y;}
    void restore(float cx,float cy,float scale){
        x=finite(cx,.5f);y=finite(cy,.5f);zoom=Math.max(1,Math.min(8,finite(scale,1)));clamp();
    }
    void reset(){restore(.5f,.5f,1);}
    void drag(float dx,float dy){
        x-=finite(dx,0)*width();y-=finite(dy,0)*height();clamp();
    }
    void scale(float factor,float focusX,float focusY){
        if(!Float.isFinite(factor)||factor<=0||!Float.isFinite(focusX)||!Float.isFinite(focusY))return;
        float oldW=width(),oldH=height();
        zoom=Math.max(1,Math.min(8,zoom*factor));
        // Keep the image point under the fingers fixed while changing scale.
        x+=(focusX-.5f)*(oldW-width());y+=(focusY-.5f)*(oldH-height());clamp();
    }
    private void clamp(){if(!measured)return;float w=width()/2,h=height()/2;x=Math.max(w,Math.min(1-w,x));y=Math.max(h,Math.min(1-h,y));}
    private static float finite(float value,float fallback){return Float.isFinite(value)?value:fallback;}
}
