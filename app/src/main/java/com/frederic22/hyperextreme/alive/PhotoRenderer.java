package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** OpenAlive photo frame graph hosted in the same EGL lifecycle as the other scenes. */
final class PhotoRenderer implements WallpaperRenderer {
    private long handle;
    private final int[] textures=new int[3];
    private SceneMotion scene;
    private TextureMotion textureMotion;
    private FrameMotion frameMotion;
    private MaskFrames masks;
    private final FrameSample sample=new FrameSample();
    private final PhotoMotion motion=new PhotoMotion();
    private float aspect;
    private boolean submitted;
    private int mode;
    private int width,height,aod,photoWidth,photoHeight,lockStyle=2;

    PhotoRenderer(Context context,int width,int height,int initial,int variant,boolean preview,Runnable invalidate) throws IOException {
        this(context.getAssets(), photo(context,preview),width,height,initial,variant,invalidate);
        try{configure(PhotoOptions.load(context,preview));}catch(RuntimeException|LinkageError failure){close();throw failure;}
    }

    // Device tests use a synthetic image through this constructor, never a user's photo.
    PhotoRenderer(AssetManager assets,File photo,int width,int height,int initial,int variant,Runnable invalidate) throws IOException {
        if(variant!=601&&variant!=602)throw new IllegalArgumentException("Photo variant "+variant);
        mode=initial;
        aod=variant==601?0:1;this.width=width;this.height=height;
        try {
            Bitmap bitmap=BitmapFactory.decodeFile(photo.getAbsolutePath());
            if(bitmap==null)throw new IOException("Photo is missing or cannot be decoded");
            photoWidth=bitmap.getWidth();photoHeight=bitmap.getHeight();
            aspect=(float)photoWidth/photoHeight;
            try { textures[0]=upload(bitmap); } finally { bitmap.recycle(); }
            try(InputStream in=assets.open("shader/photo/aod/lensPhoto/lens_decorator.png")) {
                bitmap=BitmapFactory.decodeStream(in);
                if(bitmap==null)throw new IOException("Invalid lens decorator");
                try { textures[1]=upload(bitmap); } finally { bitmap.recycle(); }
            }
            // Curved-glass lock texture and original home texture are separate composition passes.
            handle=NativeScene.create(width,height,initial,aod,2,6);
            if(handle==0)throw new IOException("Photo frame graph creation failed");
            NativeScene.tint(handle,0xff264552);
            scene=new SceneMotion(width,height,initial,aod==1?-1:0,2,6,false);
            textureMotion=new TextureMotion(width,height,initial,2,6);
            if(aod==1) {
                PhotoStyle style=PhotoStyle.get(1);
                frameMotion=new FrameMotion(width,height,initial,true,style);
                // Both paper apertures sample the photo, including the independent rear paper transform.
                NativeScene.texture(handle,5,textures[0],photoWidth,photoHeight);
                NativeScene.frameCrop(handle,.5f,.5f,1,0);
                int[] mask=new int[1];GLES30.glGenTextures(1,mask,0);textures[2]=mask[0];
                configure(textures[2]);
                GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGB,1,1,0,GLES30.GL_RGB,GLES30.GL_UNSIGNED_BYTE,ByteBuffer.allocateDirect(3));
                NativeScene.texture(handle,0,textures[2],style.size,style.size);
                masks=new MaskFrames(assets,new Handler(Looper.myLooper()),invalidate,style);
            }
        } catch(IOException|RuntimeException|LinkageError failure) {
            close();throw failure;
        }
    }

    private static File photo(Context context,boolean preview) {
        File draft=AliveSettings.draft(context);
        return preview&&draft.isFile()?draft:new File(context.getFilesDir(),"alive/photo.png");
    }

    private static void configure(int texture) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
    }

    private static int upload(Bitmap bitmap) {
        int[] id=new int[1];GLES30.glGenTextures(1,id,0);configure(id[0]);
        if(bitmap.hasAlpha()&&bitmap.isPremultiplied()) {
            int w=bitmap.getWidth(),h=bitmap.getHeight();int[] pixels=new int[w*h];
            bitmap.getPixels(pixels,0,w,0,0,w,h);
            ByteBuffer rgba=ByteBuffer.allocateDirect(pixels.length*4);
            for(int pixel:pixels)rgba.put((byte)(pixel>>16)).put((byte)(pixel>>8)).put((byte)pixel).put((byte)(pixel>>24));
            rgba.flip();GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGBA,w,h,0,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,rgba);
        } else GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,bitmap,0);
        return id[0];
    }

    public WallpaperMotion motion(){return motion;}
    boolean animating(){return scene.transitionActive()||textureMotion.active()||frameMotion!=null&&frameMotion.active();}
    void configure(PhotoOptions options) {
        if(lockStyle!=options.lockStyle) {
            long next=NativeScene.create(width,height,mode,aod,options.lockStyle,6);
            if(next==0)throw new IllegalStateException("Photo effect change failed");
            NativeScene.destroy(handle);handle=next;lockStyle=options.lockStyle;
            scene.close();textureMotion.close();
            scene=new SceneMotion(width,height,mode,aod==1?-1:0,lockStyle,6,false);
            textureMotion=new TextureMotion(width,height,mode,lockStyle,6);
            if(frameMotion!=null) {
                frameMotion.close();frameMotion=new FrameMotion(width,height,mode,true);
                NativeScene.texture(handle,5,textures[0],photoWidth,photoHeight);
                NativeScene.texture(handle,0,textures[2],720,720);
            }
            NativeScene.tint(handle,0xff264552);
        }
        NativeScene.photoCrop(handle,options.x,options.y,options.zoom);
        NativeScene.frameCrop(handle,options.frameX,options.frameY,options.frameSize,options.frameAngle);
        scene.followClock(options.aodOffsetX,.335f-options.aodOffsetY);
        if(frameMotion!=null)frameMotion.position(options.aodOffsetX,.315f-options.aodOffsetY);
        sample.clear();
    }
    public boolean frameReady(){return submitted;}
    public void render() {
        submitted=false;
        if(frameMotion!=null) {
            if(!sample.pending)sample.captureTextures(textureMotion);
            sample.capture(frameMotion,scene,SystemClock.uptimeMillis());
            if(!masks.upload(sample.mask,textures[2])) {
                if(masks.failed())throw new IllegalStateException("Photo mask decode failed");
                return;
            }
            if(!NativeScene.frame(handle,sample.effect))throw new IllegalStateException("Invalid photo frame packet");
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDisable(GLES30.GL_BLEND);GLES30.glDisable(GLES30.GL_CULL_FACE);
        if(!NativeScene.effects(handle,frameMotion==null?textureMotion.packet():sample.textures)
                ||!NativeScene.render(handle,textures[0],textures[1],aspect,frameMotion==null?scene.packet():sample.base))
            throw new IllegalStateException("Invalid photo scene packet");
        submitted=true;sample.clear();
    }

    public void close() {
        if(masks!=null){masks.close();masks=null;}
        if(frameMotion!=null){frameMotion.close();frameMotion=null;}
        if(textureMotion!=null){textureMotion.close();textureMotion=null;}
        if(scene!=null){scene.close();scene=null;}
        if(handle!=0){NativeScene.destroy(handle);handle=0;}
        GLES30.glDeleteTextures(textures.length,textures,0);java.util.Arrays.fill(textures,0);sample.clear();
    }

    private final class PhotoMotion implements WallpaperMotion {
        public void change(int next,boolean animate) {
            if(next!=mode)sample.clear();
            textureMotion.change(next,animate);
            if(frameMotion!=null){frameMotion.change(next,scene);if(!animate)frameMotion.finish();}
            scene.change(next);if(!animate)scene.finish();mode=next;
        }
        public void advance(long time,float speed) {
            textureMotion.advance(time);
            if(frameMotion!=null)frameMotion.advance(time);
            scene.advance(time,speed);
        }
        // Launcher offsets are not lens/clock coordinates. Cropping belongs to the photo editor.
        public void follow(float x,float y){}
        public void pause(){if(scene!=null)scene.pause(true);}
        public boolean active(){return scene.active()||textureMotion.active()||frameMotion!=null&&frameMotion.active();}
    }
}
