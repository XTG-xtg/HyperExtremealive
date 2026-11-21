package com.frederic22.hyperextreme.alive;

import org.junit.Test;
import static org.junit.Assert.*;

public class MiuiAodPanelBrightnessTest {
    @Test public void brightnessCopyPreservesNativeCapsReasonAndFlags() throws Exception {
        NativeState nativeState=new NativeState(.013f,.05f,2,false);
        NativeState copy=(NativeState)MiuiAodPanelBrightness.copyBrightness(nativeState,.1f);
        assertNotSame(nativeState,copy);
        assertEquals(.013f,nativeState.brightness,0);
        assertEquals(.1f,copy.brightness,0);
        assertEquals(.05f,copy.thermalCap,0);
        assertEquals(2,copy.reason);
        assertFalse(copy.writeUserSetting);
    }
    @Test public void unknownRomContractKeepsNativeResult(){
        MiuiAodPanelBrightness adapter=new MiuiAodPanelBrightness();
        Object original=new Object();
        assertSame(original,adapter.update(new Object(),new Object(),original,5,message->{}));
    }
    @Test public void automaticOffClearsHoldWithoutAUserWake() throws Exception {
        MiuiAodPanelBrightness adapter=new MiuiAodPanelBrightness();
        java.lang.reflect.Field field=MiuiAodPanelBrightness.class.getDeclaredField("sessions");field.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Map<Object,AodPanelHold> sessions=(java.util.Map<Object,AodPanelHold>)field.get(adapter);
        AodPanelHold hold=new AodPanelHold();hold.brightness(.013f,.1f,5,true);sessions.put(this,hold);
        adapter.powerRequest(new Display(0),new Request(1,4));assertEquals(1,sessions.size());
        adapter.powerRequest(new Display(1),new Request(1,1));assertEquals(1,sessions.size());
        adapter.powerRequest(new Display(0),new Request(1,1));assertTrue(sessions.isEmpty());
    }
    public static final class Display {private final int mDisplayId;Display(int id){mDisplayId=id;}}
    public static final class Request {public int policy,dozeScreenState;Request(int p,int s){policy=p;dozeScreenState=s;}}
    public static final class NativeState {
        final float brightness,thermalCap;final int reason;final boolean writeUserSetting;
        NativeState(float b,float cap,int reason,boolean write){brightness=b;thermalCap=cap;this.reason=reason;writeUserSetting=write;}
        public static final class Builder {
            NativeState original;float brightness;
            public static Builder from(NativeState state){Builder b=new Builder();b.original=state;b.brightness=state.brightness;return b;}
            public Builder setBrightness(float value){brightness=value;return this;}
            public NativeState build(){return new NativeState(brightness,original.thermalCap,original.reason,original.writeUserSetting);}
        }
    }
}
