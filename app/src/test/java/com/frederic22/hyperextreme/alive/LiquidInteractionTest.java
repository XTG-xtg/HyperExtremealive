package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LiquidInteractionTest {
    private Bitmap bitmap(int w,int h,int color){Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);b.eraseColor(color);return b;}
    @Test public void manualMaskNeverFallsBackToSubject(){
        Bitmap photo=bitmap(100,200,Color.WHITE),subject=bitmap(100,200,Color.WHITE);VivoOptions o=new VivoOptions(1);o.area=1;
        try(VivoLiquidRegion r=new VivoLiquidRegion(o,photo,subject,null,100,200)){assertFalse(r.hit(.5f,.5f,1));}
        finally{photo.recycle();subject.recycle();}
    }
    @Test public void manualAndSubjectAreIndependentAndBackgroundInverts(){
        Bitmap photo=bitmap(100,200,Color.WHITE),subject=bitmap(100,200,Color.BLACK),paint=bitmap(100,200,Color.WHITE);
        for(int y=0;y<200;y++)for(int x=0;x<50;x++)subject.setPixel(x,y,Color.WHITE);
        try{
            for(int area=1;area<=4;area++){
                VivoOptions o=new VivoOptions(1);o.area=area;
                try(VivoLiquidRegion r=new VivoLiquidRegion(o,photo,subject,paint,100,200)){
                    assertEquals(area!=3,r.hit(.25f,.5f,1));assertEquals(area!=2,r.hit(.75f,.5f,1));
                }
            }
        }finally{photo.recycle();subject.recycle();paint.recycle();}
    }
    @Test public void cropAndLockZoomUseSameCoordinateSystemAsPublishedMap(){
        Bitmap photo=bitmap(400,200,Color.WHITE),mask=bitmap(400,200,Color.BLACK);
        for(int y=40;y<160;y++)for(int x=270;x<310;x++)mask.setPixel(x,y,Color.WHITE);
        VivoOptions o=new VivoOptions(1);o.area=1;o.x=.75f;o.zoom=2;
        try(VivoLiquidRegion r=new VivoLiquidRegion(o,photo,null,mask,100,200)){
            LiquidTouchMap map=LiquidTouchMap.from(r.touchMap(o).bundle());assertNotNull(map);
            assertTrue(map.hit(.25f,.5f));assertFalse(map.hit(.99f,.5f));
            for(float y=.05f;y<1;y+=.08f)for(float x=.05f;x<1;x+=.08f)if(map.hit(x,y))assertTrue(r.hit(x,y,1.05f));
            assertFalse(map.hit(Float.NaN,.5f));assertFalse(map.hit(1,.5f));assertFalse(map.hit(-1,.5f));
        }finally{photo.recycle();mask.recycle();}
    }
    @Test public void malformedCoverageIsRejected(){Bundle b=new Bundle();b.putInt("w",512);b.putInt("h",512);b.putInt("sw",1200);b.putInt("sh",2670);b.putByteArray("bits",new byte[1]);assertNull(LiquidTouchMap.from(b));}
    private boolean event(LiquidTapGesture g,int action,int count,float x,long at,boolean eligible,boolean hit,String scene){return g.update(action,count,x,100,at,eligible,hit,scene,15,500);}
    @Test public void selectedTapsExcludeSleepAndOutsideTapsDoNot(){
        LiquidTapGesture g=new LiquidTapGesture();assertFalse(event(g,0,1,100,100,true,true,"a"));assertTrue(g.suppressDoubleTap());
        assertTrue(event(g,1,1,101,150,true,true,"a"));
        assertFalse(event(g,0,1,100,200,true,false,"a"));assertFalse(g.suppressDoubleTap());assertFalse(event(g,1,1,100,250,true,false,"a"));
    }
    @Test public void dragsLongPressesMultitouchStaleScenesAndAodNeverBecomeTaps(){
        for(int mode=0;mode<5;mode++){
            LiquidTapGesture g=new LiquidTapGesture();event(g,0,1,100,100,true,true,"a");
            assertFalse(event(g,1,mode==2?2:1,mode==0?140:100,mode==1?800:150,mode!=4,true,mode==3?"b":"a"));
        }
    }
    @Test public void cancelAndOutsideDownCannotStartLiquid(){
        LiquidTapGesture g=new LiquidTapGesture();event(g,0,1,100,100,true,true,"a");event(g,3,1,100,120,true,true,"a");assertFalse(event(g,1,1,100,150,true,true,"a"));
        event(g,0,1,100,200,true,false,"a");assertFalse(event(g,1,1,100,240,true,true,"a"));
    }
    @Test public void oldPreferencesDefaultToTouchAndAutoplayRoundTrips(){
        VivoOptions old=VivoOptions.parse("{\"family\":1}");assertFalse(old.liquidAutoMotion);old.liquidAutoMotion=true;assertTrue(VivoOptions.parse(old.json()).liquidAutoMotion);
    }
}
