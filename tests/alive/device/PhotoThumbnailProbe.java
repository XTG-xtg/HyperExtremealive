package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import java.io.*;
import java.util.HashSet;
import java.util.zip.CRC32;

/** Exercises the same six-thumbnail batch used by the library, without reading user photos. */
public final class PhotoThumbnailProbe {
    public static void main(String[] args)throws Exception{
        Looper.prepareMainLooper();Handler main=new Handler(Looper.getMainLooper());
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context base=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        Context c=ProbeContext.vivo(base,"/data/local/tmp/hyperextreme-vivo-thumbnail-test");
        new PhotoStyleThumbnails(c,PhotoOptions.defaults(),(images,error)->{
            try{
                if(!error.isEmpty()||images.length!=6)throw new AssertionError(error);
                HashSet<Long> hashes=new HashSet<>();
                File out=new File("/data/local/tmp/hyperextreme-photo-test");
                for(int n=0;n<images.length;n++){
                    Bitmap b=images[n];int[] p=new int[b.getWidth()*b.getHeight()];b.getPixels(p,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());
                    CRC32 crc=new CRC32();int lit=0;
                    for(int color:p){crc.update(color>>16);crc.update(color>>8);crc.update(color);if((color&0xffffff)!=0)lit++;}
                    if(lit<100||b.getWidth()>288||b.getHeight()>640)throw new AssertionError("Blank/unbounded thumbnail "+n);
                    if(!hashes.add(crc.getValue()))throw new AssertionError("Reused shape "+n);
                    try(FileOutputStream stream=new FileOutputStream(new File(out,"tile-"+(601+n)+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,stream);}
                    System.out.println("PHOTO_TILE "+(601+n)+" size="+b.getWidth()+"x"+b.getHeight()+" crc="+crc.getValue());b.recycle();
                }
                System.out.println("PHOTO_THUMBNAILS_PASS six_distinct_real_aod_shapes");
                main.post(()->patterned(c));
            }catch(Throwable e){e.printStackTrace();System.exit(1);}
        });
        main.postDelayed(()->{System.err.println("Thumbnail timeout");System.exit(2);},45000);Looper.loop();
    }
    private static void patterned(Context context){try{
        Bitmap source=Bitmap.createBitmap(320,640,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(source);canvas.drawColor(0xff4870c0);Paint paint=new Paint();
        for(int y=0;y<640;y+=40)for(int x=0;x<320;x+=40){
            paint.setColor(((x/40+y/40)%2==0)?0xfff0b050:0xff50c0d0);canvas.drawRect(x,y,x+40,y+40,paint);
        }
        paint.setColor(Color.WHITE);paint.setStrokeWidth(8);canvas.drawLine(30,0,290,640,paint);
        paint.setColor(0xffff5080);canvas.drawCircle(110,280,65,paint);
        String name="photo-00000000000000000000000000000011.png";
        File file=new File(context.getFilesDir(),"alive/media/"+name);file.getParentFile().mkdirs();
        save(source,file);
        PhotoOptions options=PhotoOptions.defaults().media(0,name);
        if(PhotoStyleThumbnails.key(context,options).equals(PhotoStyleThumbnails.key(context,options.edit(3,2))))
            throw new AssertionError("Crop must invalidate thumbnail cache");
        new PhotoStyleThumbnails(context,options,(images,error)->{try{
            if(!error.isEmpty()||images.length!=6)throw new AssertionError(error);
            Bitmap lens=images[0];int w=lens.getWidth(),h=lens.getHeight();
            Bitmap flat=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(flat);c.drawColor(Color.BLACK);
            Path circle=new Path();circle.addOval(new RectF(10,10,w-10,h-10),Path.Direction.CW);c.clipPath(circle);
            c.drawBitmap(source,new Rect(0,160,320,480),new RectF(10,10,w-10,h-10),new Paint(Paint.FILTER_BITMAP_FLAG));
            long difference=0;int samples=0;
            for(int y=10;y<h-10;y++)for(int x=10;x<w-10;x++){
                int a=lens.getPixel(x,y),b=flat.getPixel(x,y);
                if((b&0xffffff)==0)continue;
                for(int shift:new int[]{0,8,16})difference+=Math.abs(((a>>shift)&255)-((b>>shift)&255));
                samples+=3;
            }
            double mean=(double)difference/samples;if(mean<20)throw new AssertionError("Lens resembles a flat circle: "+mean);
            File out=new File("/data/local/tmp/hyperextreme-photo-test");
            save(source,new File(out,"tile-pattern-source.png"));save(flat,new File(out,"tile-flat-circle-reference.png"));
            for(int i=0;i<6;i++){save(images[i],new File(out,"tile-pattern-"+(601+i)+".png"));images[i].recycle();}
            source.recycle();flat.recycle();
            System.out.println("PHOTO_LENS_NOT_FLAT_PASS mean_rgb_difference="+mean+" immutable_source=true crop_key=true");System.exit(0);
        }catch(Throwable e){e.printStackTrace();System.exit(1);}});
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
    private static void save(Bitmap image,File file)throws IOException{
        try(FileOutputStream stream=new FileOutputStream(file)){if(!image.compress(Bitmap.CompressFormat.PNG,100,stream))throw new IOException("PNG encode failed");}
    }
}
