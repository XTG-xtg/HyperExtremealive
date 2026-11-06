package com.frederic22.hyperextreme.alive;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.SystemClock;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Bounded import inspection; some ROM retrievers return null for otherwise decodable clips. */
final class VivoVideoMetadata implements AutoCloseable {
    final long duration;
    final int rate,frames;
    final Bitmap poster;
    private VivoVideoMetadata(long duration,int rate,int frames,Bitmap poster){this.duration=duration;this.rate=rate;this.frames=frames;this.poster=poster;}
    static VivoVideoMetadata read(File file)throws IOException {
        MediaExtractor extractor=new MediaExtractor();Bitmap poster=null;
        try(FileInputStream input=new FileInputStream(file)){
            extractor.setDataSource(input.getFD(),0,file.length());
            MediaFormat format=null;
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat candidate=extractor.getTrackFormat(i);
                String mime=candidate.getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("video/")){format=candidate;extractor.selectTrack(i);break;}
            }
            if(format==null)throw new IOException("没有可解码的视频轨道");
            int w=format.getInteger(MediaFormat.KEY_WIDTH),h=format.getInteger(MediaFormat.KEY_HEIGHT);
            if(w<1||h<1||w>8192||h>8192||(long)w*h>33_554_432L)throw new IOException("视频分辨率过大");
            long durationUs=format.containsKey(MediaFormat.KEY_DURATION)?format.getLong(MediaFormat.KEY_DURATION):0;
            if(durationUs<1000)throw new IOException("无法读取视频时长");
            long duration=durationUs/1000;
            int rate=30;
            if(format.containsKey(MediaFormat.KEY_FRAME_RATE)){
                try{rate=Math.round(format.getNumber(MediaFormat.KEY_FRAME_RATE).floatValue());}catch(RuntimeException ignored){}
            }
            rate=Math.max(1,Math.min(240,rate));
            int frames=(int)Math.min(Integer.MAX_VALUE,Math.max(1,Math.round(durationUs*(rate/1_000_000.0))));
            try(MediaMetadataRetriever retriever=new MediaMetadataRetriever()){
                retriever.setDataSource(input.getFD(),0,file.length());
                float scale=Math.min(1,1280f/Math.max(w,h));
                poster=retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));
            }catch(RuntimeException ignored){/* Decode the selected track directly below. */}
            if(poster==null)poster=decodePoster(extractor,format);
            VivoVideoMetadata result=new VivoVideoMetadata(duration,rate,frames,poster);poster=null;return result;
        }catch(RuntimeException e){throw new IOException("视频读取失败，请选择系统支持的视频",e);}
        finally{extractor.release();if(poster!=null)poster.recycle();}
    }
    private static Bitmap decodePoster(MediaExtractor extractor,MediaFormat format)throws IOException {
        int transfer=format.getInteger(MediaFormat.KEY_COLOR_TRANSFER,MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        if(transfer==MediaFormat.COLOR_TRANSFER_ST2084||transfer==MediaFormat.COLOR_TRANSFER_HLG)
            throw new IOException("系统无法生成 HDR 视频封面，请先转换为 SDR 视频");
        MediaCodec codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        boolean started=false;
        try{
            int rotation=format.getInteger(MediaFormat.KEY_ROTATION,0);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            // Byte-buffer output has no automatic display rotation.
            format.setInteger(MediaFormat.KEY_ROTATION,0);
            codec.configure(format,null,null,0);codec.start();started=true;
            extractor.seekTo(0,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean ended=false;
            long deadline=SystemClock.elapsedRealtime()+5000;
            while(SystemClock.elapsedRealtime()<deadline){
                if(Thread.currentThread().isInterrupted())throw new IOException("视频导入已取消");
                if(!ended){int index=codec.dequeueInputBuffer(10000);
                    if(index>=0){ByteBuffer buffer=codec.getInputBuffer(index);int size=extractor.readSampleData(buffer,0);
                        ended=size<0;codec.queueInputBuffer(index,0,Math.max(0,size),ended?0:extractor.getSampleTime(),ended?MediaCodec.BUFFER_FLAG_END_OF_STREAM:0);
                        if(!ended)extractor.advance();
                    }
                }
                int index=codec.dequeueOutputBuffer(info,10000);
                if(index<0)continue;
                try(Image image=codec.getOutputImage(index)){
                    if(image!=null){Bitmap bitmap=yuvPoster(image,codec.getOutputFormat());
                        if(rotation%360!=0){Matrix matrix=new Matrix();matrix.postRotate(rotation);
                            Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
                            if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;
                        }
                        return bitmap;
                    }
                }finally{codec.releaseOutputBuffer(index,false);}
                if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)break;
            }
            throw new IOException("无法解码视频封面");
        }finally{try{if(started)codec.stop();}finally{codec.release();}}
    }
    private static Bitmap yuvPoster(Image image,MediaFormat format)throws IOException {
        if(image.getFormat()!=ImageFormat.YUV_420_888||image.getPlanes().length!=3)throw new IOException("不支持的视频像素格式");
        Rect crop=image.getCropRect();float scale=Math.min(1,1280f/Math.max(crop.width(),crop.height()));
        int w=Math.max(1,Math.round(crop.width()*scale)),h=Math.max(1,Math.round(crop.height()*scale));
        int[] pixels=new int[w*h];Image.Plane[] p=image.getPlanes();ByteBuffer[] bytes={p[0].getBuffer(),p[1].getBuffer(),p[2].getBuffer()};
        boolean full=format.getInteger(MediaFormat.KEY_COLOR_RANGE,MediaFormat.COLOR_RANGE_LIMITED)==MediaFormat.COLOR_RANGE_FULL;
        boolean bt709=format.getInteger(MediaFormat.KEY_COLOR_STANDARD,MediaFormat.COLOR_STANDARD_BT601_NTSC)==MediaFormat.COLOR_STANDARD_BT709;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int sx=crop.left+Math.min(crop.width()-1,(int)((x+.5f)*crop.width()/w));
            int sy=crop.top+Math.min(crop.height()-1,(int)((y+.5f)*crop.height()/h));
            float l=sample(bytes[0],p[0],sx,sy),u=sample(bytes[1],p[1],sx/2,sy/2)-128,v=sample(bytes[2],p[2],sx/2,sy/2)-128;
            if(!full){l=(l-16)*255f/219;u*=255f/224;v*=255f/224;}
            int r=channel(l+(bt709?1.5748f:1.402f)*v),g=channel(l-(bt709?.187324f:.344136f)*u-(bt709?.468124f:.714136f)*v),b=channel(l+(bt709?1.8556f:1.772f)*u);
            pixels[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
    private static int sample(ByteBuffer buffer,Image.Plane plane,int x,int y){return buffer.get(buffer.position()+y*plane.getRowStride()+x*plane.getPixelStride())&255;}
    private static int channel(float v){return Math.max(0,Math.min(255,Math.round(v)));}
    @Override public void close(){poster.recycle();}
}
