package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.os.Looper;
import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class WakeGlideTest {
    public static final class Info {public float brightnessMaximum=1;}
    public static final class Limits {private float mThermalMaxBrightness=Float.NaN,mBatteryThermalLimitBrightness=1,mLowBatteryLimitBrightness=1;}
    public static final class Controller {
        private int mDisplayId=0;private final Limits mDisplayPowerControllerImpl=new Limits();final Info info=new Info();
        public Info getBrightnessInfo(){return info;}
    }
    public static final class Display {private int mDisplayId=0,mScreenState=2;}
    public static final class Photonic {
        private final Display this$0=new Display();private final WakeGlide glide;
        boolean ambient=true,eligible=true;int submissions;float backlight,sdr;
        Photonic(WakeGlide glide){this.glide=glide;}
        public boolean setState(int state,float b,float s) throws Exception {
            Object[] args=glide.arguments(this,method(),new Object[]{state,b,s},ambient,eligible);
            if(args!=null){submissions++;backlight=(Float)args[1];sdr=(Float)args[2];}
            return true;
        }
        static Method method()throws Exception{return Photonic.class.getMethod("setState",int.class,float.class,float.class);}
    }
    @Test public void wakeSurvivesTrailingAodThenSettlesWithoutMoreNativeRequests()throws Exception{
        WakeGlide glide=new WakeGlide();glide.caps(new Controller());Photonic p=new Photonic(glide);
        try{
            p.setState(2,.1f,.08f);glide.wake(true);
            p.setState(2,.1f,.08f); // PowerManager has not updated isInteractive yet.
            p.ambient=false;p.setState(2,.8f,.7f);assertEquals(.1f,p.backlight,0);assertEquals(.08f,p.sdr,0);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));p.setState(2,.04f,.03f);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2600));
            assertEquals(.04f,p.backlight,0);assertEquals(.03f,p.sdr,0);
            int count=p.submissions;shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));assertEquals(count,p.submissions);
        }finally{glide.cancel();}
    }
    @Test public void zeroOffAndCancellationCannotReplayAStaleWake()throws Exception{
        for(int reason=0;reason<3;reason++){
            WakeGlide glide=new WakeGlide();glide.caps(new Controller());Photonic p=new Photonic(glide);
            p.setState(2,.1f,.1f);glide.wake(true);p.ambient=false;p.setState(2,.5f,.5f);
            if(reason==0){p.eligible=false;p.setState(2,.5f,.5f);}
            else if(reason==1){p.this$0.mScreenState=1;p.setState(1,-1,-1);}
            else glide.cancel();
            int count=p.submissions;shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertEquals(count,p.submissions);
        }
    }
    @Test public void externalDisplayAndUnmanagedWallpaperUseNativeBacklight()throws Exception{
        WakeGlide glide=new WakeGlide();glide.caps(new Controller());Photonic p=new Photonic(glide);
        p.this$0.mDisplayId=1;p.setState(2,.1f,.1f);glide.wake(true);p.ambient=false;p.setState(2,.8f,.7f);
        assertEquals(.8f,p.backlight,0);assertEquals(.7f,p.sdr,0);
        p.this$0.mDisplayId=0;p.ambient=true;p.eligible=false;p.setState(2,.1f,.1f);
        glide.wake(true);p.ambient=false;p.setState(2,.8f,.7f);assertEquals(.8f,p.backlight,0);glide.cancel();
    }
    @Test public void publicNormalizedCapsAndLowBatteryLimitTakePrecedence()throws Exception{
        Controller c=new Controller();c.info.brightnessMaximum=.6f;c.mDisplayPowerControllerImpl.mThermalMaxBrightness=.4f;
        c.mDisplayPowerControllerImpl.mLowBatteryLimitBrightness=.2f;assertEquals(.2f,WakeGlide.brightnessCap(c),0);
        WakeGlide glide=new WakeGlide();glide.caps(c);Photonic p=new Photonic(glide);
        p.setState(2,.3f,.3f);glide.wake(true);p.ambient=false;p.setState(2,.5f,.5f);
        assertEquals(.2f,p.backlight,0);assertEquals(.2f,p.sdr,0);glide.cancel();
    }
}
