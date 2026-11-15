package com.frederic22.hyperextreme.alive;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.UserHandle;
import com.frederic22.hyperextreme.alive.core.ModuleIdentity;
import java.lang.reflect.Method;
import java.util.List;
import java.util.WeakHashMap;

/** The ROM's wallpaper mask runs after our shader, so exposure alone cannot lift its ceiling. */
final class SystemAodDimming {
    private SystemAodDimming(){}
    private static final WeakHashMap<Object,AodBrightness.Hold> HOLDS=new WeakHashMap<>();

    static synchronized Object[] heldArguments(Object panel,List<?> original,float brightness,boolean owned){
        if(panel==null)return null;
        if(!owned||AodBrightness.normalize(brightness)==0||!candidate(original,AodBrightness.MAX)){
            HOLDS.remove(panel);return null;
        }
        AodBrightness.Hold hold=HOLDS.computeIfAbsent(panel,key->new AodBrightness.Hold());
        float requested=(Float)original.get(1);
        float black=AodBrightness.wallpaperBlack(hold.black(requested,brightness),brightness);
        if(black==requested)return null;
        Object[] args=original.toArray();args[1]=black;return args;
    }

    static boolean contract(Method method){
        Class<?>[] p=method.getParameterTypes();
        return method.getName().equals("doWallpaperBlackAnim")&&method.getReturnType()==void.class
            &&p.length==5&&p[0]==boolean.class&&p[1]==float.class&&p[2]==int.class
            &&p[3]==boolean.class&&!p[4].isPrimitive();
    }
    static boolean candidate(List<?> args,float brightness){
        return args.size()==5&&Boolean.FALSE.equals(args.get(0))&&args.get(1) instanceof Float
            &&args.get(2) instanceof Integer&&(Integer)args.get(2)==1
            &&args.get(3) instanceof Boolean
            &&AodBrightness.wallpaperBlack((Float)args.get(1),brightness)!=(Float)args.get(1)
            &&Float.isFinite((Float)args.get(1));
    }
    static boolean owns(ComponentName component){
        if(component==null||!ModuleIdentity.PACKAGE.equals(component.getPackageName()))return false;
        String service=component.getClassName();
        return ModuleIdentity.HOME_SERVICE.equals(service)||ModuleIdentity.LOCK_SERVICE.equals(service)
            ||ModuleIdentity.SPATIAL_SERVICE.equals(service)||ModuleIdentity.RASTER_SERVICE.equals(service);
    }
    static ComponentName lockBinding(ComponentName home,ComponentName lock,int lockWallpaperId){
        // Null WallpaperInfo also means a separate STATIC lock wallpaper; only -1 is shared.
        return lock!=null?lock:lockWallpaperId==-1?home:null;
    }
    static boolean ownsFullAod(Object panel){
        if(panel==null||Build.VERSION.SDK_INT<34)return false;
        try {
            Class<?> type=panel.getClass();
            Object manager=type.getField("miuiFullAodManager").get(panel);
            if(manager==null||!Boolean.TRUE.equals(manager.getClass().getMethod("fullAodEnable").invoke(manager)))return false;
            Context context=(Context)type.getField("context").get(panel);
            Object users=type.getField("selectedUserInteractor").get(panel);
            int user=(Integer)users.getClass().getMethod("getSelectedUserId").invoke(users);
            return ownsLock(context,user);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){
            // Unknown contracts/bindings preserve the system animation and its target.
            return false;
        }
    }
    static boolean ownsSelectedLock(Context context){
        if(context==null||Build.VERSION.SDK_INT<34)return false;
        try{
            int user=(Integer)Class.forName("android.app.ActivityManager").getMethod("getCurrentUser").invoke(null);
            return ownsLock(context,user);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return false;}
    }
    private static boolean ownsLock(Context context,int user) throws ReflectiveOperationException {
        UserHandle handle=(UserHandle)UserHandle.class.getMethod("of",int.class).invoke(null,user);
        Context selected=(Context)Context.class.getMethod("createContextAsUser",UserHandle.class,int.class).invoke(context,handle,0);
        WallpaperManager wallpapers=WallpaperManager.getInstance(selected);
        WallpaperInfo lock=wallpapers.getWallpaperInfo(WallpaperManager.FLAG_LOCK);
        if(lock!=null)return owns(lock.getComponent());
        int lockId=wallpapers.getWallpaperId(WallpaperManager.FLAG_LOCK);
        if(lockId!=-1)return false;
        WallpaperInfo home=wallpapers.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM);
        return owns(lockBinding(home==null?null:home.getComponent(),null,lockId));
    }
    static Object[] arguments(List<?> original,float brightness){
        Object[] args=original.toArray();
        args[1]=AodBrightness.wallpaperBlack((Float)args[1],brightness);
        return args;
    }
}
