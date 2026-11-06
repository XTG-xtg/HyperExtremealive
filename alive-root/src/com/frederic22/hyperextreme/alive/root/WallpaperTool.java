package com.frederic22.hyperextreme.alive.root;

import com.frederic22.hyperextreme.alive.core.ModuleIdentity;

import android.content.ComponentName;
import android.content.pm.ServiceInfo;
import android.os.Process;
import android.os.SystemClock;
import java.lang.reflect.*;

/** Privileged, allowlisted CLI. Runs as system UID, never as an unrestricted command server. */
public final class WallpaperTool {
    private static final String PKG=ModuleIdentity.PACKAGE;
    private static Object service() throws Exception {
        Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"wallpaper");
        return Class.forName("android.app.IWallpaperManager$Stub").getMethod("asInterface",Class.forName("android.os.IBinder")).invoke(null,binder);
    }
    private static String query(Object manager,int which,int user) throws Exception {
        Method m=manager.getClass().getMethod("getWallpaperInfoWithFlags",int.class,int.class);
        m.setAccessible(true);
        Object info=m.invoke(manager,which,user);
        if(info==null)return "STATIC";
        ComponentName component=(ComponentName)info.getClass().getMethod("getComponent").invoke(info);
        return component.flattenToString();
    }
    private static boolean preflight(int user) throws Exception {
        Object pm=Class.forName("android.app.AppGlobals").getMethod("getPackageManager").invoke(null);
        Method permission=pm.getClass().getMethod("checkPermission",String.class,String.class,int.class);
        permission.setAccessible(true);
        int ambient=(Integer)permission.invoke(pm,"android.permission.AMBIENT_WALLPAPER",PKG,user);
        System.out.println("PREFLIGHT uid="+Process.myUid()+" user="+user+" AMBIENT_WALLPAPER="+ambient);
        Method serviceInfo=pm.getClass().getMethod("getServiceInfo",ComponentName.class,long.class,int.class);
        serviceInfo.setAccessible(true);
        for(String name:new String[]{"HomeWallpaperService","LockWallpaperService"}) {
            ComponentName component=new ComponentName(PKG,ModuleIdentity.CODE_PACKAGE+".alive."+name);
            ServiceInfo info=(ServiceInfo)serviceInfo.invoke(pm,component,0L,user);
            if(info==null||!info.enabled||!info.exported||!info.applicationInfo.enabled||
                    !"android.permission.BIND_WALLPAPER".equals(info.permission)) {
                throw new IllegalStateException("Invalid wallpaper service: "+component);
            }
            System.out.println("SERVICE_OK "+component.flattenToString());
        }
        if(ambient!=0)System.out.println("FRAMEWORK_REQUIRED: enable HyperExtreme Alive scope System Framework (system), then reboot. No wallpaper was changed by preflight.");
        return ambient==0;
    }
    private static void set(Object manager,String service,int which,int user) throws Exception {
        ComponentName component=new ComponentName(PKG,ModuleIdentity.CODE_PACKAGE+".alive."+service);
        for(Method m:manager.getClass().getMethods()) {
            if(!m.getName().equals("setWallpaperComponentChecked"))continue;
            Class<?>[] p=m.getParameterTypes();
            if(p.length!=4||p[1]!=String.class||p[2]!=int.class||p[3]!=int.class)continue;
            Object description;
            if(p[0]==ComponentName.class)description=component;
            else if(p[0].getName().equals("android.app.wallpaper.WallpaperDescription")) {
                Class<?> builderClass=Class.forName(p[0].getName()+"$Builder");
                Object builder=builderClass.getConstructor().newInstance();
                builderClass.getMethod("setComponent",ComponentName.class).invoke(builder,component);
                description=builderClass.getMethod("build").invoke(builder);
            } else continue;
            m.setAccessible(true);
            Object result=m.invoke(manager,description,"android",which,user);
            if(Boolean.FALSE.equals(result))throw new IllegalStateException("System rejected which="+which);
            String actual=query(manager,which,user);
            long deadline=SystemClock.uptimeMillis()+2500;
            while(!component.flattenToString().equals(actual)&&SystemClock.uptimeMillis()<deadline) {
                Thread.sleep(100);actual=query(manager,which,user);
            }
            if(!component.flattenToString().equals(actual))throw new IllegalStateException("Verification mismatch: "+actual);
            System.out.println("VERIFIED which="+which+" component="+actual);return;
        }
        throw new NoSuchMethodException("Supported four-argument setWallpaperComponentChecked");
    }
    public static void main(String[] args) {
        try {
            if(Process.myUid()!=1000)throw new SecurityException("System UID required; actual="+Process.myUid());
            if(args.length!=2)throw new IllegalArgumentException("Usage: query|check|apply USER_ID");
            int user=Integer.parseInt(args[1]);if(user<0||user>10000)throw new IllegalArgumentException("Invalid user");
            Object manager=service();
            if("query".equals(args[0])) {
                for(int which=1;which<=2;which++)System.out.println("STATE which="+which+" component="+query(manager,which,user));
                preflight(user);
            } else if("check".equals(args[0])) {
                if(!preflight(user))System.exit(10);
            } else if("apply".equals(args[0])) {
                if(!preflight(user)){System.exit(10);return;}
                // These are two verified transactions, not an atomic pair. Partial failure is explicit.
                set(manager,"HomeWallpaperService",1,user);set(manager,"LockWallpaperService",2,user);
                for(int which=1;which<=2;which++) {
                    String name=which==1?"HomeWallpaperService":"LockWallpaperService";
                    String expected=new ComponentName(PKG,ModuleIdentity.CODE_PACKAGE+".alive."+name).flattenToString();
                    if(!expected.equals(query(manager,which,user)))throw new IllegalStateException("Final pair mismatch which="+which);
                }
                System.out.println("PAIR_VERIFIED");
            } else throw new IllegalArgumentException("Unknown operation");
        } catch(Throwable e) {
            Throwable cause=e instanceof InvocationTargetException?((InvocationTargetException)e).getTargetException():e;
            System.err.println("ERROR "+cause);System.exit(2);
        }
    }
}
