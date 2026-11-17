package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBinder;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class AodPanelBridgeTest {
    private final Context context=RuntimeEnvironment.getApplication();
    private ConfigProvider provider(){
        ConfigProvider p=new ConfigProvider();p.attachInfo(context,null);
        ShadowContentResolver.registerProviderInternal(Protocol.CONFIG.getAuthority(),p);return p;
    }
    private void bridge(ConfigProvider provider,boolean managed){
        Bundle extras=new Bundle();extras.putBoolean("managed",managed);provider.call("panelBridge",null,extras);
    }
    @Test public void ordinaryAppCannotDeclareSystemPanelManagement(){
        ConfigProvider provider=provider();ShadowBinder.setCallingUid(Process.myUid());
        assertThrows(SecurityException.class,()->bridge(provider,true));
    }
    @Test public void serviceWatchesProviderManagementAndFallsBackAfterRevocation(){
        ConfigProvider provider=provider();ShadowBinder.setCallingUid(1000);
        AodBrightness.panelManaged(context,false);AodBrightness.save(context,5);
        AodBrightness.Watch watch=new AodBrightness.Watch(context,new Handler(Looper.getMainLooper()),()->{});
        try{
            assertFalse(watch.managed);assertEquals(5,watch.value,0);
            bridge(provider,true);shadowOf(Looper.getMainLooper()).idle();assertTrue(watch.managed);
            assertFalse(com.frederic22.hyperextreme.alive.Config.prefs(context).contains(AodBrightness.PANEL_MANAGED));
            bridge(provider,false);shadowOf(Looper.getMainLooper()).idle();assertFalse(watch.managed);assertEquals(5,watch.value,0);
        }finally{watch.close();AodBrightness.panelManaged(context,false);}
    }
}
