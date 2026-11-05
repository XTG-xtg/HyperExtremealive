// Derived from OpenAlive 2d54c72a573b2f66e20b558283bef58cdb0ada60; MIT; see docs/alive/OPENALIVE_LICENSE and docs/alive/PROVENANCE.json.
package com.frederic22.hyperextreme.alive;

import android.content.res.AssetManager;
import android.os.*;
import android.util.Log;
import android.opengl.GLES30;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Bounded directional read-ahead. GL only uploads ready masks, never decodes on its thread. */
final class MaskFrames implements AutoCloseable {
    static final int SIZE=720,BYTES=SIZE*SIZE*3,START=61,END=62;
    private final AssetManager assets;
    private final HandlerThread thread=new HandlerThread("PhotoMask");
    private final Handler worker,owner;
    private final Runnable ready;
    private final LinkedHashMap<Integer,ByteBuffer> cache=new LinkedHashMap<>(8,.75f,true);
    private final MaskDecoder decoder=new MaskDecoder();
    private final String[] paths;
    private final int size,bytes,start,end,capacity;
    private volatile boolean closed;
    private int desired,uploaded=-1,failures,decodedFrames,presentedFrames,direction=1,allocatedSide,uploadedTexture;
    private boolean queued;
    private final Runnable decode=this::decode;
    MaskFrames(AssetManager assets,Handler owner,Runnable ready){this(assets,owner,ready,PhotoStyle.get(1));}
    MaskFrames(AssetManager assets,Handler owner,Runnable ready,PhotoStyle style){
        this.assets=assets;this.owner=owner;this.ready=ready;size=style.size;bytes=size*size*3;start=style.frames;end=start+1;desired=start;
        // Two endpoints plus a short look-ahead window, at most 12 MiB per 720px engine.
        capacity=Math.max(4,Math.min(8,8*BYTES/bytes));paths=new String[end+1];
        for(int i=0;i<paths.length;i++)paths[i]="masks/"+style.folder+"/"+(i==start?"start":i==end?"end":String.format(Locale.ROOT,"%03d",i))+".rgbz";
        thread.start();worker=new Handler(thread.getLooper());
        queued=true;worker.post(decode);
    }
    static int index(long micros){return micros<=0?START:micros>=483667?END:Math.min(60,Math.round(micros*120f/1000000f));}
    synchronized boolean upload(int index,int texture){
        if(closed)return false;
        if(index<0||index>end)throw new IllegalArgumentException("Mask index "+index);
        if(index!=desired){direction=index==start?-1:index==end?1:desired==start?1:desired==end?-1:index>desired?1:-1;desired=index;}
        if(texture!=uploadedTexture){uploadedTexture=texture;uploaded=-1;allocatedSide=0;}
        if(!queued&&failures<3){queued=true;worker.post(decode);}
        if(uploaded==index)return true;
        ByteBuffer value=cache.get(index);
        if(value==null)return false;
        // The cache lock protects a reusable buffer until GL has consumed it.
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);value.position(0);
        int side=index==end?720:size;
        if(allocatedSide!=side){GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGB,side,side,0,GLES30.GL_RGB,GLES30.GL_UNSIGNED_BYTE,value);allocatedSide=side;}
        else GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D,0,0,0,side,side,GLES30.GL_RGB,GLES30.GL_UNSIGNED_BYTE,value);
        if(Diagnostics.TRACE){if(index<start)presentedFrames++;if(index==end)Log.i("AliveClean","Frame mask endpoint: decoded="+decodedFrames+" presented="+presentedFrames);}
        uploaded=index;return true;
    }
    synchronized boolean failed(){return failures>=3;}
    private LinkedHashSet<Integer> wanted(){
        LinkedHashSet<Integer> result=new LinkedHashSet<>();result.add(desired);result.add(start);result.add(end);
        int step=desired==start?1:desired==end?-1:direction;
        int base=desired==start?0:desired==end?start-1:desired+step;
        for(int i=0;i<capacity&&result.size()<capacity;i++){int n=base+i*step;if(n>=0&&n<start)result.add(n);}
        return result;
    }
    private void decode(){
        int target=-1;ByteBuffer pixels;
        synchronized(this){if(closed){queued=false;return;}
            Set<Integer> wanted=wanted();for(int n:wanted)if(!cache.containsKey(n)){target=n;break;}
            if(target<0){queued=false;return;}
            if(cache.size()>=capacity){Integer victim=null;for(int n:cache.keySet())if(!wanted.contains(n)){victim=n;break;}
                if(victim==null){queued=false;return;}pixels=cache.remove(victim);pixels.clear();}
            else pixels=ByteBuffer.allocateDirect(bytes);
        }
        try{
            int side=target==end?720:size;
            try(InputStream in=assets.open(paths[target])){decoder.decode(in,pixels,side*side*3);}
            synchronized(this){if(!closed){decodedFrames++;cache.put(target,pixels);if(target==desired)owner.post(ready);}}
        }catch(IOException|RuntimeException e){synchronized(this){failures++;if(!closed)owner.post(ready);}Log.e("AliveClean","Mask decode failed",e);}
        synchronized(this){queued=false;if(!closed&&failures<3){queued=true;worker.post(decode);}}
    }
    @Override public synchronized void close(){if(closed)return;closed=true;worker.removeCallbacksAndMessages(null);cache.clear();worker.post(decoder::close);thread.quitSafely();}
}
