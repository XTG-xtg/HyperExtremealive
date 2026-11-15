package com.frederic22.hyperextreme.alive;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.io.IOException;

/** Convert RGBA cutouts or grayscale masks into the opaque luminance texture Vivo expects. */
final class VivoMaskGeometry {
    private VivoMaskGeometry() { }

    static Bitmap normalize(Bitmap source, int width, int height) throws IOException {
        float sourceAspect=(float)source.getWidth()/source.getHeight(),targetAspect=(float)width/height;
        if(Math.abs(sourceAspect/targetAspect-1f)>.02f)
            throw new IOException("蒙版与壁纸比例不一致，请导入与原图对齐的完整蒙版");
        return normalizeTexture(source,width,height);
    }
    // Factory masks are square UV textures, sampled over the entire portrait photo.
    // Imported masks still go through normalize() and its aspect-ratio validation.
    static Bitmap normalizeTexture(Bitmap source, int width, int height) {
        int[] pixels=new int[source.getWidth()*source.getHeight()];
        source.getPixels(pixels,0,source.getWidth(),0,0,source.getWidth(),source.getHeight());
        boolean alpha=false;
        for(int pixel:pixels)if(Color.alpha(pixel)<255){alpha=true;break;}
        for(int i=0;i<pixels.length;i++){
            int p=pixels[i];
            int value=alpha?Color.alpha(p):(Color.red(p)*299+Color.green(p)*587+Color.blue(p)*114)/1000;
            pixels[i]=Color.rgb(value,value,value);
        }
        Bitmap normalized=Bitmap.createBitmap(pixels,source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);
        if(normalized.getWidth()==width&&normalized.getHeight()==height)return normalized;
        try{return Bitmap.createScaledBitmap(normalized,width,height,true);}
        finally{normalized.recycle();}
    }
}
