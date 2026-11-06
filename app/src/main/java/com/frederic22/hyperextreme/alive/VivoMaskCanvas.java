// Adapted from OpenAlive VivoMaskEditor; MIT, see docs/alive/OPENALIVE_LICENSE.
package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.ArrayDeque;

/** Owns its source/mask bitmaps. Undo history is bounded to six full strokes. */
final class VivoMaskCanvas extends View implements AutoCloseable {
    final Bitmap photo,mask;
    final RectF rect=new RectF(),box=new RectF(0,0,1,1);
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),overlay=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final ArrayDeque<int[]> history=new ArrayDeque<>();
    boolean erase,boxMode;
    float brush=.03f;
    private float lastX,lastY;
    private boolean closed;
    VivoMaskCanvas(Context context,Bitmap source,Bitmap initial){
        super(context);setContentDescription("照片蒙版编辑画布");
        float scale=Math.min(1f,1280f/Math.max(source.getWidth(),source.getHeight()));
        int w=Math.max(1,Math.round(source.getWidth()*scale)),h=Math.max(1,Math.round(source.getHeight()*scale));
        photo=Bitmap.createScaledBitmap(source,w,h,true);if(photo!=source)source.recycle();
        mask=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);mask.eraseColor(Color.BLACK);
        if(initial!=null){new Canvas(mask).drawBitmap(initial,null,new Rect(0,0,w,h),paint);initial.recycle();}
        overlay.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{0,0,0,0,40,0,0,0,0,180,0,0,0,0,255,.42f,0,0,0,0})));
    }
    @Override protected void onDraw(Canvas c){
        if(closed)return;
        float scale=Math.min((float)getWidth()/photo.getWidth(),(float)getHeight()/photo.getHeight());
        float w=photo.getWidth()*scale,h=photo.getHeight()*scale;rect.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
        c.drawBitmap(photo,null,rect,paint);c.drawBitmap(mask,null,rect,overlay);
        if(boxMode){
            paint.setColor(0xff58c8ff);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*getResources().getDisplayMetrics().density);
            c.drawRect(rect.left+box.left*rect.width(),rect.top+box.top*rect.height(),rect.left+box.right*rect.width(),rect.top+box.bottom*rect.height(),paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
    private void snapshot(){
        int[] pixels=new int[mask.getWidth()*mask.getHeight()];mask.getPixels(pixels,0,mask.getWidth(),0,0,mask.getWidth(),mask.getHeight());
        if(history.size()==6)history.removeFirst();history.addLast(pixels);
    }
    void undo(){if(history.isEmpty()||closed)return;mask.setPixels(history.removeLast(),0,mask.getWidth(),0,0,mask.getWidth(),mask.getHeight());invalidate();}
    void clear(){if(closed)return;snapshot();mask.eraseColor(Color.BLACK);invalidate();}
    void replace(Bitmap bitmap){snapshot();new Canvas(mask).drawBitmap(bitmap,null,new Rect(0,0,mask.getWidth(),mask.getHeight()),null);boxMode=false;invalidate();}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!isEnabled()||closed||rect.width()==0)return true;
        float x=Math.max(0,Math.min(1,(e.getX()-rect.left)/rect.width())),y=Math.max(0,Math.min(1,(e.getY()-rect.top)/rect.height()));
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
            if(!rect.contains(e.getX(),e.getY()))return false;
            getParent().requestDisallowInterceptTouchEvent(true);lastX=x;lastY=y;if(!boxMode)snapshot();
        }
        if(boxMode){
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE||e.getActionMasked()==MotionEvent.ACTION_UP)
                box.set(Math.min(lastX,x),Math.min(lastY,y),Math.max(lastX,x),Math.max(lastY,y));
        }else if(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_MOVE||e.getActionMasked()==MotionEvent.ACTION_UP){
            paint.setColor(erase?Color.BLACK:Color.WHITE);paint.setStrokeWidth(brush*Math.max(mask.getWidth(),mask.getHeight()));paint.setStrokeCap(Paint.Cap.ROUND);
            Canvas c=new Canvas(mask);c.drawLine(lastX*mask.getWidth(),lastY*mask.getHeight(),x*mask.getWidth(),y*mask.getHeight(),paint);
            c.drawCircle(x*mask.getWidth(),y*mask.getHeight(),paint.getStrokeWidth()/2,paint);lastX=x;lastY=y;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();
        invalidate();return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void close(){if(closed)return;closed=true;photo.recycle();mask.recycle();history.clear();}
}
