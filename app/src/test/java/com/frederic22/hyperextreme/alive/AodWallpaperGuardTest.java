package com.frederic22.hyperextreme.alive;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.widget.FrameLayout;
import android.view.WindowManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class AodWallpaperGuardTest {
    public static class Host {
        public boolean mNeedShowWallpaper=true,mKeyguardOccluded,mFingerprintPressed;
        public FrameLayout mAODView;
        public WindowManager.LayoutParams lp;
        boolean dozing=true,visible=true;
        public boolean isDozing(){return dozing;}
        public boolean isAodVisible(){return visible;}
    }
    public static class Root extends FrameLayout {
        public final Host mHost;
        Root(Context c,Host host){super(c);mHost=host;}
    }
    private ActivityController<Activity> activity;
    private Host host;
    private Root root;
    private AodWallpaperGuard guard;
    @Before public void create() {
        activity=Robolectric.buildActivity(Activity.class).setup();
        host=new Host();root=new Root(activity.get(),host);host.mAODView=root;
        activity.get().setContentView(root);guard=new AodWallpaperGuard();
        assertTrue(root.isAttachedToWindow());
    }
    @After public void close(){activity.pause().stop().destroy();}
    private void inflate(boolean enabled) {
        AodWallpaperGuard.Panel panel=guard.begin(root);
        if(!guard.normalDisable(panel,host,enabled))host.mNeedShowWallpaper=false;
        root.setBackgroundColor(Color.BLACK);
        guard.finish(panel,enabled);
    }
    private int color(){return ((ColorDrawable)root.getBackground()).getColor();}

    @Test public void lateConfigRepairsFirstPanelWithoutWaitingForAnotherScreenOff() {
        inflate(false);assertFalse(host.mNeedShowWallpaper);assertEquals(Color.BLACK,color());
        guard.refresh(true);assertTrue(host.mNeedShowWallpaper);assertEquals(Color.TRANSPARENT,color());
    }
    @Test public void warmConfigPreservesExistingPermissionAndDisableRestoresStock() {
        inflate(true);assertTrue(host.mNeedShowWallpaper);assertEquals(Color.TRANSPARENT,color());
        guard.refresh(false);assertFalse(host.mNeedShowWallpaper);assertEquals(Color.BLACK,color());
        guard.refresh(true);assertTrue(host.mNeedShowWallpaper);assertEquals(Color.TRANSPARENT,color());
    }
    @Test public void aRepeatedNormalPanelBeforeConfigDoesNotLoseSystemPermissionEvidence() {
        inflate(false);inflate(false);assertFalse(host.mNeedShowWallpaper);
        guard.refresh(true);assertTrue(host.mNeedShowWallpaper);assertEquals(Color.TRANSPARENT,color());
    }
    @Test public void stockBackgroundMutationCanStillBeReconciledAfterInflation() {
        inflate(false);root.setBackgroundColor(Color.BLACK);
        guard.refresh(true);assertTrue(host.mNeedShowWallpaper);assertEquals(Color.TRANSPARENT,color());
    }
    @Test public void neverGrantsWallpaperWhenTheSystemDidNotPermitIt() {
        host.mNeedShowWallpaper=false;inflate(false);guard.refresh(true);
        assertFalse(host.mNeedShowWallpaper);assertEquals(Color.BLACK,color());
    }
    @Test public void occlusionAndFingerprintBlockLateRecovery() {
        inflate(false);host.mFingerprintPressed=true;guard.refresh(true);assertFalse(host.mNeedShowWallpaper);
        host.mFingerprintPressed=false;host.mKeyguardOccluded=true;guard.refresh(true);assertFalse(host.mNeedShowWallpaper);
        host.mKeyguardOccluded=false;guard.refresh(true);assertTrue(host.mNeedShowWallpaper);
    }
    @Test public void stoppedOrScheduledOffAodCannotBeStarted() {
        inflate(false);host.dozing=false;guard.refresh(true);assertFalse(host.mNeedShowWallpaper);
        host.dozing=true;host.visible=false;guard.refresh(true);assertFalse(host.mNeedShowWallpaper);
    }
    @Test public void anUnrelatedSystemDisableRevokesTemplateEvidence() {
        inflate(false);guard.revoke(host);guard.refresh(true);
        assertFalse(host.mNeedShowWallpaper);assertEquals(Color.BLACK,color());
    }
    @Test public void replacementPanelCannotInheritAnOldPermission() {
        inflate(false);host.mAODView=new FrameLayout(activity.get());guard.refresh(true);
        assertFalse(host.mNeedShowWallpaper);assertEquals(Color.BLACK,color());
    }
    @Test public void detachedPanelCannotBeResurrected() {
        inflate(false);guard.detach(root);guard.refresh(true);assertFalse(host.mNeedShowWallpaper);
    }
    @Test public void newerSecurityBackgroundIsNeverRemoved() {
        inflate(false);ColorDrawable security=new ColorDrawable(Color.RED);root.setBackground(security);
        guard.refresh(true);assertFalse(host.mNeedShowWallpaper);assertSame(security,root.getBackground());
    }
    @Test public void mutatedSecurityBackgroundIsNeverRemoved() {
        inflate(false);root.setBackgroundColor(Color.RED);guard.refresh(true);
        assertFalse(host.mNeedShowWallpaper);assertEquals(Color.RED,color());
    }
    private void nativeWindow(boolean allowed) {
        host.mNeedShowWallpaper=false;root.setBackgroundColor(Color.BLACK);
        host.lp=new WindowManager.LayoutParams();host.lp.format=PixelFormat.OPAQUE;
        host.lp.flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        root.setLayoutParams(host.lp);
        guard.created(root,host,v->allowed);
    }
    @Test public void lateClassificationRepairsOnlyTheCurrentNativeWindow() {
        nativeWindow(true);guard.refresh(true);
        assertTrue(host.mNeedShowWallpaper);assertEquals(Color.TRANSPARENT,color());
        assertTrue((host.lp.flags&WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)!=0);
        assertTrue((host.lp.flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);
        assertEquals(PixelFormat.TRANSPARENT,host.lp.format);
        guard.refresh(false);assertFalse(host.mNeedShowWallpaper);assertEquals(Color.BLACK,color());
        assertEquals(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,host.lp.flags);
        assertEquals(PixelFormat.OPAQUE,host.lp.format);
    }
    @Test public void nativeHardwareAndSettingsGatesRemainAuthoritative() {
        nativeWindow(false);guard.refresh(true);
        assertFalse(host.mNeedShowWallpaper);assertEquals(Color.BLACK,color());
        assertEquals(PixelFormat.OPAQUE,host.lp.format);
    }
    @Test public void nativeEligibilityIsRecheckedAfterConfigArrives() {
        nativeWindow(true);guard.created(root,host,v->false);guard.refresh(true);
        assertFalse(host.mNeedShowWallpaper);
    }
    @Test public void aReplacedWindowCannotReceiveOldLayoutFlags() {
        nativeWindow(true);root.setLayoutParams(new WindowManager.LayoutParams());guard.refresh(true);
        assertFalse(host.mNeedShowWallpaper);assertEquals(PixelFormat.OPAQUE,host.lp.format);
    }
    @Test public void lateClassificationCannotStartScheduledOffAodOrHideFingerprint() {
        nativeWindow(true);host.visible=false;guard.refresh(true);assertFalse(host.mNeedShowWallpaper);
        host.visible=true;host.mFingerprintPressed=true;guard.refresh(true);assertFalse(host.mNeedShowWallpaper);
    }
    @Test public void classificationOverrideCannotEscapeTheStockCreationCall() {
        assertFalse(AodWallpaperEligibility.classify(false,true));
        assertFalse(AodWallpaperEligibility.classify(true,false));
        assertTrue(AodWallpaperEligibility.classify(true,true));
        assertFalse(AodWallpaperEligibility.allowed(activity.get(),getClass().getClassLoader()));
    }
    @Test public void unsupportedRomFailsClosed() {assertFalse(AodWallpaperGuard.contracts(getClass().getClassLoader()));}
}
