package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.os.Process;
import android.provider.Settings;
import java.io.IOException;
import java.util.Objects;

/** Controls the real per-user system switch. No wallpaper preset stores an AOD preference. */
final class SystemAodControl {
    static final String ENABLED = "doze_always_on", USER_SET = "aod_mode_user_set";
    static final String FORCE = "hyperextreme_force_aod", FULL = "full_screen_aod_on";
    interface Store {
        String read(String key) throws Exception;
        void write(String key, String value) throws Exception;
    }
    static final class State {
        final boolean enabled;
        final String error;
        final boolean forced;
        State(boolean enabled, boolean forced, String error) { this.enabled=enabled; this.forced=forced; this.error=error; }
    }
    private static Store store(Context context) {
        return new Store() {
            public String read(String key) { return Settings.Secure.getString(context.getContentResolver(), key); }
            public void write(String key, String value) throws Exception {
                int user=Process.myUid()/100000;
                String command="settings --user "+user+(value==null ? " delete secure " : " put secure ")+key;
                if(value!=null) command+=" "+RootBridge.quote(value);
                RootBridge.Result result=RootBridge.run(command);
                if(!result.ok()) throw new IOException("系统息屏设置写入失败，请检查 Root 授权："+result.text.trim());
            }
        };
    }
    static State read(Context context) {
        try { Store store=store(context); return new State("1".equals(store.read(ENABLED)), "1".equals(store.read(FORCE)), ""); }
        catch(Exception e) { return new State(false,false,"系统息屏状态读取失败："+e.getMessage()); }
    }
    static synchronized RootBridge.Result set(Context context, boolean enabled) {
        RootBridge.Result result;
        try {
            if(enabled && Settings.Secure.getInt(context.getContentResolver(),"accessibility_display_inversion_enabled",0)==1)
                throw new IOException("请先在系统无障碍设置中关闭颜色反转，再开启息屏显示");
            set(store(context),enabled);
            result=new RootBridge.Result(0,enabled ? "系统息屏显示已开启" : "系统息屏显示已关闭");
        } catch(Exception e) { result=new RootBridge.Result(1,e.getMessage()); }
        RootBridge.record(context,"aod",result);
        return result;
    }
    static void set(Store store, boolean enabled) throws Exception {
        String value=enabled ? "1" : "0";
        update(store,new String[]{USER_SET,ENABLED},new String[]{value,value});
    }
    static synchronized RootBridge.Result setForced(Context context,boolean forced) {
        RootBridge.Result result;
        try {
            if(forced && Settings.Secure.getInt(context.getContentResolver(),"accessibility_display_inversion_enabled",0)==1)
                throw new IOException("请先关闭无障碍颜色反转，再强制开启息屏显示");
            setForced(store(context),forced);
            result=new RootBridge.Result(0,forced ? "强制息屏支持已开启，重启手机后完整生效" : "强制息屏支持已关闭，重启手机恢复设备默认判断");
        } catch(Exception e) { result=new RootBridge.Result(1,e.getMessage()); }
        RootBridge.record(context,"aod_force",result);
        return result;
    }
    static void setForced(Store store,boolean forced) throws Exception {
        if(forced) update(store,new String[]{FORCE,USER_SET,FULL,ENABLED},new String[]{"1","1","1","1"});
        else update(store,new String[]{FORCE},new String[]{"0"});
    }
    private static void update(Store store,String[] keys,String[] values) throws Exception {
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++) old[i]=store.read(keys[i]);
        int attempted=0;
        try {
            for(int i=0;i<keys.length;i++) { attempted=i+1; store.write(keys[i],values[i]); }
            for(int i=0;i<keys.length;i++) if(!values[i].equals(store.read(keys[i])))
                throw new IOException("系统未确认息屏开关已更新："+keys[i]);
        } catch(Exception failure) {
            if(attempted>0) {
                // Restore independently: failure restoring one key must not skip the other.
                StringBuilder errors=new StringBuilder();
                for(int i=attempted-1;i>=0;i--) restore(store,keys[i],old[i],errors);
                if(errors.length()>0) throw new IOException(failure.getMessage()+"；恢复原状态失败："+errors,failure);
            }
            throw failure;
        }
    }
    private static void restore(Store store,String key,String old,StringBuilder errors) {
        try {
            store.write(key,old);
            if(!Objects.equals(old,store.read(key))) throw new IOException("回读不一致");
        } catch(Exception e) { errors.append(key).append(" ").append(e.getMessage()).append("；"); }
    }
    private SystemAodControl() {}
}
