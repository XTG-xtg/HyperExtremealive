package com.frederic22.hyperextreme.alive;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Surface;
import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Silent timestamp-paced clips. Hidden slots release their codec, not just their GL loop. */
final class VivoClipDecoder implements AutoCloseable {
    private final File file;
    private final Surface surface;
    private final Runnable invalidate;
    private final HandlerThread thread=new HandlerThread("AliveVivoVideo");
    private final Handler handler;
    private MediaExtractor extractor;
    private MediaCodec codec;
    private final MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    private int output=-1;
    private boolean inputEnded;
    private long positionUs,baseUs,baseNanos,durationUs;
    private volatile boolean visible,closed;
    private volatile RuntimeException failure;
    private final Runnable pump=this::pump;
    VivoClipDecoder(File file,Surface surface,Runnable invalidate){
        this.file=file;this.surface=surface;this.invalidate=invalidate;thread.start();handler=new Handler(thread.getLooper());
    }
    void visible(boolean value){
        if(closed||visible==value)return;visible=value;
        handler.post(()->{if(closed)return;if(!visible)stop();else if(codec==null)try{start();pump();}catch(Exception e){fail(e);}});
    }
    void check(){if(failure!=null)throw failure;}
    private void start()throws Exception {
        extractor=new MediaExtractor();
        try(FileInputStream input=new FileInputStream(file)){extractor.setDataSource(input.getFD(),0,file.length());}
        MediaFormat format=null;
        for(int i=0;i<extractor.getTrackCount();i++){
            MediaFormat next=extractor.getTrackFormat(i);String mime=next.getString(MediaFormat.KEY_MIME);
            if(mime!=null&&mime.startsWith("video/")){format=next;extractor.selectTrack(i);break;}
        }
        if(format==null)throw new IllegalStateException("Video track missing");
        durationUs=format.getLong(MediaFormat.KEY_DURATION);positionUs=Math.max(0,positionUs%Math.max(1,durationUs));
        extractor.seekTo(positionUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        codec.configure(format,surface,null,0);codec.start();baseUs=positionUs;baseNanos=System.nanoTime();
    }
    private void pump(){
        if(closed||!visible||codec==null)return;
        try{
            for(int count=0;count<4&&!inputEnded;count++){
                int index=codec.dequeueInputBuffer(0);if(index<0)break;
                java.nio.ByteBuffer buffer=codec.getInputBuffer(index);buffer.clear();int size=extractor.readSampleData(buffer,0);
                inputEnded=size<0;codec.queueInputBuffer(index,0,Math.max(0,size),inputEnded?0:extractor.getSampleTime(),inputEnded?MediaCodec.BUFFER_FLAG_END_OF_STREAM:0);
                if(!inputEnded)extractor.advance();
            }
            long delay=5;
            for(int count=0;count<4;count++){
                if(output<0)output=codec.dequeueOutputBuffer(info,0);
                if(output<0)break;
                if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0){
                    codec.releaseOutputBuffer(output,false);output=-1;codec.flush();extractor.seekTo(0,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    inputEnded=false;positionUs=baseUs=0;baseNanos=System.nanoTime();break;
                }
                if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0||info.presentationTimeUs<baseUs){codec.releaseOutputBuffer(output,false);output=-1;continue;}
                long due=baseNanos+(info.presentationTimeUs-baseUs)*1000,remaining=due-System.nanoTime();
                if(remaining>2_000_000L){delay=Math.min(25,Math.max(1,remaining/1_000_000));break;}
                // Surface timestamps remain media timestamps; GL consumes only the latest frame.
                codec.releaseOutputBuffer(output,visible&&!closed);positionUs=info.presentationTimeUs;output=-1;
            }
            if(visible&&!closed)handler.postDelayed(pump,delay);
        }catch(Exception e){fail(e);}
    }
    private void fail(Exception e){failure=new IllegalStateException("Vivo video decoder failed",e);stop();invalidate.run();}
    private void stop(){
        handler.removeCallbacks(pump);output=-1;inputEnded=false;
        if(codec!=null){try{codec.stop();}catch(RuntimeException ignored){}finally{codec.release();codec=null;}}
        if(extractor!=null){extractor.release();extractor=null;}
    }
    @Override public void close(){
        if(closed)return;closed=true;visible=false;
        CountDownLatch done=new CountDownLatch(1);
        handler.post(()->{try{stop();}finally{thread.quitSafely();done.countDown();}});
        try{if(!done.await(3,TimeUnit.SECONDS))android.util.Log.w(Protocol.TAG,"Vivo decoder cleanup still pending");}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
