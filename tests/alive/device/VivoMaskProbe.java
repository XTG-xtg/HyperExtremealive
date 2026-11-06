package com.frederic22.hyperextreme.alive;

import android.content.*;
import android.graphics.*;
import java.io.*;

/** Runs the candidate's Kotlin/ONNX segmenter on a bundled factory image, not a user's photo. */
public final class VivoMaskProbe {
    public static void main(String[] args)throws Exception {
        android.os.Looper.prepareMainLooper();
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context base=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context assets=ProbeContext.wrap(base.createPackageContext(Protocol.PACKAGE,Context.CONTEXT_IGNORE_SECURITY));
        File out=new File("/data/local/tmp/hyperextreme-vivo-test");out.mkdirs();
        Context c=new ContextWrapper(assets){public File getCodeCacheDir(){File dir=new File(out,"code-cache");dir.mkdirs();return dir;}};
        Bitmap source;
        try(InputStream in=c.getAssets().open("vivo/preview/liquid.jpg")){source=BitmapFactory.decodeStream(in);}
        Bitmap image=Bitmap.createScaledBitmap(source,360,800,true);if(image!=source)source.recycle();
        int checks=0;
        Class<?> type=Class.forName("com.frederic22.hyperextreme.alive.VivoSubjectMask");
        for(int step=0;step<2;step++){
            Object job=type.getConstructor().newInstance();RectF box=step==0?new RectF(0,0,1,1):new RectF(.2f,.2f,.8f,.8f);
            Bitmap mask=(Bitmap)type.getMethod("generate",Context.class,Bitmap.class,RectF.class).invoke(job,c,image,box);
            if(mask.getWidth()!=image.getWidth()||mask.getHeight()!=image.getHeight())throw new AssertionError("Mask dimensions");checks++;
            int minimum=255,maximum=0,foreground=0;
            for(int y=0;y<mask.getHeight();y++)for(int x=0;x<mask.getWidth();x++){
                int v=Color.red(mask.getPixel(x,y));minimum=Math.min(minimum,v);maximum=Math.max(maximum,v);if(v>128)foreground++;
                if(step==1&&(x<72||x>=288||y<160||y>=640)){if(v!=0)throw new AssertionError("Mask escaped selected box");checks++;}
            }
            if(minimum>5||maximum<245||foreground<50||foreground>mask.getWidth()*mask.getHeight()*.95)throw new AssertionError("Mask range/coverage "+minimum+"/"+maximum+"/"+foreground);checks++;
            try(FileOutputStream stream=new FileOutputStream(new File(out,"subject-"+step+".png"))){mask.compress(Bitmap.CompressFormat.PNG,100,stream);}mask.recycle();
        }
        image.recycle();System.out.println("VIVO_MASK_PASS checks="+checks+" full_and_box=ok offline_onnx=ok");System.exit(0);
    }
}
