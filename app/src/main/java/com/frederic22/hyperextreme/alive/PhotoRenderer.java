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
    private final int[] textures=new int[8],textureWidth=new int[8],textureHeight=new int[8];
    private final AssetManager assets;
    private final int variant;
    private final Runnable invalidate;
    private SceneMotion scene;
    private TextureMotion textureMotion;
    private FrameMotion frameMotion;
    private MaskFrames masks;
    private final FrameSample sample=new FrameSample();
    private final PhotoMotion motion=new PhotoMotion();
    private float aspect;
    private boolean submitted;
    private boolean catchUp;
    private boolean continuous=true;
    private int mode;
    private int width,height,aod=-999,photoWidth,photoHeight,lockStyle=-1,homeStyle=-1;
    private PhotoOptions options=PhotoOptions.defaults();
    private float anchorX,anchorY=.165f;
    private boolean systemAnchor;

    PhotoRenderer(Context context,int width,int height,int initial,int variant,boolean preview,Runnable invalidate) throws IOException {
        this(context,PhotoOptions.load(context,preview),width,height,initial,variant,preview,invalidate);
    }
    PhotoRenderer(Context c,PhotoOptions options,int w,int h,int initial,int variant,boolean preview,Runnable invalidate)throws IOException {
        this(c.getAssets(),PhotoMedia.resolve(c,options,preview,0),PhotoMedia.resolve(c,options,preview,1),
            PhotoMedia.resolve(c,options,preview,2),w,h,initial,variant,options,invalidate);
    }

    // Device tests use a synthetic image through this constructor, never a user's photo.
    PhotoRenderer(AssetManager assets,File photo,int width,int height,int initial,int variant,Runnable invalidate) throws IOException {
        this(assets,photo,photo,photo,width,height,initial,variant,new PhotoOptions(2,.5f,.5f,1,.5f,.5f,1,0),invalidate);
    }
    PhotoRenderer(AssetManager assets,File photo,File home,File frame,int width,int height,int initial,int variant,PhotoOptions options,Runnable invalidate)throws IOException {
        if(variant<601||variant>606)throw new IllegalArgumentException("Photo variant "+variant);
        this.assets=assets;this.variant=variant;this.invalidate=invalidate;mode=initial;
        this.width=width;this.height=height;
        try {
            loadImage(0,photo);photoWidth=textureWidth[0];photoHeight=textureHeight[0];
            aspect=(float)photoWidth/photoHeight;
            loadAsset(1,"shader/photo/aod/lensPhoto/lens_decorator.png",false);
            if(!home.equals(photo))loadImage(6,home);
            if(variant==602&&!frame.equals(photo))loadImage(7,frame);
            configure(options);
        } catch(IOException|RuntimeException|LinkageError failure) {
            close();throw failure;
        }
    }

    private void loadImage(int index,File file)throws IOException {
        Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath());
        if(bitmap==null)throw new IOException("Photo is missing or cannot be decoded");
        try{textures[index]=upload(bitmap);textureWidth[index]=bitmap.getWidth();textureHeight[index]=bitmap.getHeight();}
        finally{bitmap.recycle();}
    }
    private void loadAsset(int index,String path,boolean repeat)throws IOException {
        if(textures[index]!=0)return;
        try(InputStream in=assets.open(path)){
            Bitmap bitmap=BitmapFactory.decodeStream(in);
            if(bitmap==null)throw new IOException("Invalid texture: "+path);
            try{textures[index]=upload(bitmap);textureWidth[index]=bitmap.getWidth();textureHeight[index]=bitmap.getHeight();}
            finally{bitmap.recycle();}
            if(repeat){
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_REPEAT);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_REPEAT);
            }
        }
    }
    private void bind(int slot,int texture){NativeScene.texture(handle,slot,textures[texture],textureWidth[texture],textureHeight[texture]);}
    private void masks(int next){
        if(masks!=null){masks.close();masks=null;}
        if(frameMotion!=null){frameMotion.close();frameMotion=null;}
        if(next<1||next>5)return;
        PhotoStyle style=PhotoStyle.get(next);
        frameMotion=new FrameMotion(width,height,mode,next==1,style);
        if(textures[2]==0){
            int[] id=new int[1];GLES30.glGenTextures(1,id,0);textures[2]=id[0];configure(textures[2]);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGB,1,1,0,GLES30.GL_RGB,GLES30.GL_UNSIGNED_BYTE,ByteBuffer.allocateDirect(3));
        }
        textureWidth[2]=textureHeight[2]=style.size;
        masks=new MaskFrames(assets,new Handler(Looper.myLooper()),invalidate,style);
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
    public void continuous(boolean value){continuous=value;}
    boolean animating(){return scene.transitionActive()||textureMotion.active()||frameMotion!=null&&frameMotion.active();}
    void configure(PhotoOptions options) {
        this.options=options;
        int nextAod=options.aod(variant);
        if(lockStyle!=options.lockStyle||homeStyle!=options.homeStyle||aod!=nextAod) {
            try {
                if(options.lockStyle==3||options.homeStyle==3){
                    loadAsset(3,"shader/photo/lock/groundGlass/gray.png",false);
                    loadAsset(4,"shader/photo/lock/groundGlass/mask.png",true);
                }
                if(nextAod==101)loadAsset(5,"shader/photo/aod/fullAod/gray.png",false);
            }catch(IOException e){throw new IllegalStateException("Photo auxiliary textures failed",e);}
            long next=NativeScene.create(width,height,mode,nextAod,options.lockStyle,6+options.homeStyle);
            if(next==0)throw new IllegalStateException("Photo effect change failed");
            NativeScene.destroy(handle);handle=next;lockStyle=options.lockStyle;homeStyle=options.homeStyle;
            if(scene!=null)scene.close();if(textureMotion!=null)textureMotion.close();
            scene=new SceneMotion(width,height,mode,nextAod>=1&&nextAod<=5?-1:nextAod,lockStyle,6+homeStyle,false);
            textureMotion=new TextureMotion(width,height,mode,lockStyle,6+homeStyle);
            if(aod!=nextAod){masks(nextAod);aod=nextAod;}
            else if(frameMotion!=null){frameMotion.close();frameMotion=new FrameMotion(width,height,mode,aod==1,PhotoStyle.get(aod));}
            if(aod>=1&&aod<=5)bind(0,2);
            if(aod==1)bind(5,textures[7]!=0?7:0);
            if(lockStyle==3||homeStyle==3){bind(1,3);bind(2,4);}
            if(aod==101)bind(3,5);
        }
        // The home composition owns its crop even when it references the same image.
        bind(4,!options.followLock&&textures[6]!=0?6:0);
        NativeScene.tint(handle,options.tint);
        NativeScene.photoCrop(handle,options.x,options.y,options.zoom);
        NativeScene.homeCrop(handle,options.followLock?options.x:options.homeX,options.followLock?options.y:options.homeY,options.followLock?options.zoom:options.homeZoom);
        NativeScene.frameCrop(handle,options.frameX,options.frameY,options.frameSize,options.frameAngle);
        anchor(anchorX,anchorY,systemAnchor);
        sample.clear();
    }
    void anchor(float x,float y,boolean system){
        anchorX=x;anchorY=y;systemAnchor=system;
        scene.followClock(x+options.aodOffsetX,(system?y:.335f+y-.165f)-options.aodOffsetY);
        if(frameMotion!=null)frameMotion.position(x+options.aodOffsetX,(system?y:.315f+y-.165f)-options.aodOffsetY);
        sample.clear();
    }
    public boolean frameReady(){return submitted;}
    boolean needsRedraw(){return catchUp;}
    public void render() {
        submitted=false;
        boolean delayedSample=sample.pending;
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
        // CPU animation may have finished while this older sample waited for its mask.
        // Present the latest snapshot once more before allowing the worker to go idle.
        catchUp=delayedSample;submitted=true;sample.clear();
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
            // A settings repaint must not rotate an otherwise frozen AOD photo by the idle time.
            float rotationSpeed=continuous||animating()?speed:0;
            textureMotion.advance(time);
            if(frameMotion!=null)frameMotion.advance(time);
            scene.advance(time,rotationSpeed);
        }
        // Launcher offsets are not lens/clock coordinates. Cropping belongs to the photo editor.
        public void follow(float x,float y){}
        public void pause(){if(scene!=null)scene.pause(true);}
        public boolean active(){return scene.active()||textureMotion.active()||frameMotion!=null&&frameMotion.active();}
    }
}
