package com.frederic22.hyperextreme.alive;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.WindowManager;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Owns only the current Alive panel and its layout flags; native AOD eligibility stays authoritative. */
final class AodWallpaperGuard {
    private final Map<View, Panel> panels = new WeakHashMap<>();

    static boolean contracts(ClassLoader loader) {
        if(!baseContracts(loader))return false;
        try {
            Class<?> host=Class.forName("com.miui.aod.DozeHost",false,loader);
            Class<?> root=Class.forName("com.miui.aod.AODView",false,loader);
            if(!View.class.isAssignableFrom(host.getDeclaredMethod("showSecurityIdentityViewAt",android.view.ViewGroup.class).getReturnType())
                    ||host.getDeclaredField("lp").getType()!=WindowManager.LayoutParams.class)return false;
            for(String[] check:new String[][]{{"com.miui.aod.widget.AODSettings","isSupportAodWallpaper"},
                    {"com.miui.aod.Utils","isAodEnable"},{"com.miui.aod.Utils","isUsingSuperWallpaperStyle"}})
                if(Class.forName(check[0],false,loader).getDeclaredMethod(check[1],android.content.Context.class).getReturnType()!=boolean.class)return false;
            if(Class.forName("com.miui.aod.util.WallPaperUtils",false,loader).getDeclaredMethod("isWallpaperSupportsAmbientMode").getReturnType()!=boolean.class)return false;
            return host.getDeclaredMethod("disableShowWallpaper").getReturnType()==void.class
                &&root.getDeclaredMethod("makeNormalPanel").getReturnType()==void.class
                &&host.getDeclaredField("mNeedShowWallpaper").getType()==boolean.class
                &&host.getDeclaredField("mFingerprintPressed").getType()==boolean.class
                &&host.getDeclaredField("mKeyguardOccluded").getType()==boolean.class
                &&View.class.isAssignableFrom(host.getDeclaredField("mAODView").getType());
        }catch(ReflectiveOperationException|RuntimeException|LinkageError absent){return false;}
    }

    // These contracts belong to wallpaper visibility, independent of retired overlays.
    private static boolean baseContracts(ClassLoader loader) {
        try {
            Class<?> host=Class.forName("com.miui.aod.DozeHost",false,loader);
            for(String name:new String[]{"isDozing","isAodVisible","isFingerprintPressed"})
                if(host.getDeclaredMethod(name).getReturnType()!=boolean.class)return false;
            for(String name:new String[]{"fireFingerprintPressed","onFingerprintPressed"})host.getDeclaredMethod(name,boolean.class,boolean.class);
            for(String name:new String[]{"onStopDoze","screenTurnOn","removeAODView"})host.getDeclaredMethod(name);
            host.getDeclaredField("mKeyguardOccluded");
            Class<?> view=Class.forName("com.miui.aod.AODView",false,loader);
            view.getDeclaredField("mHost");view.getDeclaredMethod("onAttachedToWindow");view.getDeclaredMethod("onDetachedFromWindow");
            return true;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError absent){return false;}
    }

    static final class Panel {
        final WeakReference<View> root;
        final WeakReference<Object> host;
        boolean templateDisabled, ownsPermission, normalCall;
        Drawable background, transparent;
        int backgroundColor;
        Predicate<View> nativeEligibility;
        WindowManager.LayoutParams layout;
        int addedFlags, stockFormat;
        boolean changedLayout;
        Panel(View root, Object host) { this.root=new WeakReference<>(root);this.host=new WeakReference<>(host); }
    }

    Panel begin(View root) {
        Panel previous=panels.remove(root);
        Object host=field(root,"mHost");
        if(previous!=null&&previous.host.get()==host){previous.normalCall=false;return previous;}
        return new Panel(root,host);
    }
    boolean normalDisable(Panel panel, Object host, boolean enabled) {
        if(panel==null||panel.host.get()!=host)return false;
        panel.normalCall=true;
        if(!Boolean.TRUE.equals(field(host,"mNeedShowWallpaper")))return false;
        panel.templateDisabled=true;
        panel.ownsPermission=enabled&&safe(host);
        return panel.ownsPermission;
    }
    void finish(Panel panel, boolean enabled) {
        View root=panel.root.get();
        if(root==null||!panel.normalCall||!panel.templateDisabled)return;
        panel.background=root.getBackground();
        panel.backgroundColor=color(panel.background);
        panels.put(root,panel);
        // The stock host may not have assigned mAODView until inflation returns.
        if(panel.ownsPermission&&enabled&&safe(panel.host.get()))transparent(panel,root);
    }
    void created(View root,Object host,Predicate<View> nativeEligibility) {
        if(field(root,"mHost")!=host||!(root.getLayoutParams() instanceof WindowManager.LayoutParams)
                ||root.getLayoutParams()!=field(host,"lp"))return;
        Panel panel=panels.get(root);
        if(panel==null){panel=new Panel(root,host);panel.background=root.getBackground();panel.backgroundColor=color(panel.background);}
        panel.nativeEligibility=nativeEligibility;
        panel.layout=(WindowManager.LayoutParams)root.getLayoutParams();
        panels.put(root,panel);
    }
    void refresh(boolean enabled) {
        for(Panel panel:new ArrayList<>(panels.values())) {
            View root=panel.root.get();Object host=panel.host.get();
            if(root==null||host==null)continue;
            if(!enabled) {
                if(panel.ownsPermission&&field(host,"mAODView")==root)writePermission(host,false);
                panel.ownsPermission=false;
                restoreBackground(panel,root);
                restoreLayout(panel,root);
                continue;
            }
            if(!root.isAttachedToWindow()||field(host,"mAODView")!=root||!safe(host)
                    ||!Boolean.TRUE.equals(call(host,"isDozing"))
                    ||!Boolean.TRUE.equals(call(host,"isAodVisible")))continue;
            if(!ownedBackground(panel,root))continue;
            if(panel.nativeEligibility!=null&&!panel.nativeEligibility.test(root))continue;
            if(panel.layout!=null&&(root.getLayoutParams()!=panel.layout||field(host,"lp")!=panel.layout))continue;
            if(!panel.ownsPermission) {
                if((!panel.templateDisabled&&panel.nativeEligibility==null)||!writePermission(host,true))continue;
                panel.ownsPermission=true;
                android.util.Log.i(Protocol.TAG,"AOD wallpaper restored after config became ready");
            }
            if(panel.layout!=null&&!panel.changedLayout){
                panel.stockFormat=panel.layout.format;
                panel.addedFlags=(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)&~panel.layout.flags;
                panel.layout.flags|=panel.addedFlags;panel.layout.format=PixelFormat.TRANSPARENT;
                panel.changedLayout=true;root.setLayoutParams(panel.layout);
            }
            // Do not overwrite a newer security/ROM background, even if flags lag behind it.
            if(root.getBackground()==panel.background||root.getBackground()==panel.transparent)transparent(panel,root);
        }
    }
    void revoke(Object host) {
        for(Panel panel:new ArrayList<>(panels.values()))if(panel.host.get()==host) {
            View root=panel.root.get();
            if(root!=null){restoreBackground(panel,root);restoreLayout(panel,root);panels.remove(root);}
        }
    }
    void detach(View root) { panels.remove(root); }
    private static void transparent(Panel panel,View root) {
        panel.transparent=new ColorDrawable(Color.TRANSPARENT);
        root.setBackground(panel.transparent);
    }
    private static void restoreBackground(Panel panel,View root) {
        if(panel.transparent!=null&&root.getBackground()==panel.transparent&&color(panel.transparent)==Color.TRANSPARENT)root.setBackground(panel.background);
    }
    private static boolean ownedBackground(Panel panel,View root){
        Drawable current=root.getBackground();
        return current==panel.background&&color(current)==panel.backgroundColor
            ||current==panel.transparent&&color(current)==Color.TRANSPARENT;
    }
    private static int color(Drawable value){return value instanceof ColorDrawable?((ColorDrawable)value).getColor():Integer.MIN_VALUE;}
    private static void restoreLayout(Panel panel,View root){
        if(!panel.changedLayout||root.getLayoutParams()!=panel.layout)return;
        panel.layout.flags&=~panel.addedFlags;
        if(panel.layout.format==PixelFormat.TRANSPARENT)panel.layout.format=panel.stockFormat;
        panel.changedLayout=false;root.setLayoutParams(panel.layout);
    }
    private static boolean safe(Object host) {
        return Boolean.FALSE.equals(field(host,"mKeyguardOccluded"))&&Boolean.FALSE.equals(field(host,"mFingerprintPressed"));
    }
    private static Object field(Object host,String name) {
        if(host==null)return null;
        for(Class<?> type=host.getClass();type!=null;type=type.getSuperclass())try {
            Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(host);
        }catch(ReflectiveOperationException|RuntimeException ignored){}
        return null;
    }
    private static Object call(Object host,String name) {
        if(host==null)return null;
        try{return host.getClass().getMethod(name).invoke(host);}
        catch(ReflectiveOperationException|RuntimeException error){return null;}
    }
    private static boolean writePermission(Object host,boolean value) {
        if(host==null)return false;
        for(Class<?> c=host.getClass();c!=null;c=c.getSuperclass())try {
            Field f=c.getDeclaredField("mNeedShowWallpaper");f.setAccessible(true);
            if(f.getType()!=boolean.class)return false;
            f.setBoolean(host,value);return true;
        }catch(NoSuchFieldException absent) {
            // Continue through the ROM's base classes.
        }catch(ReflectiveOperationException|RuntimeException denied){return false;}
        return false;
    }
}
