package releaseprobe;

import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.opengl.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;

/** Uses ONLY classes from the optimized APK. No app startup, service binding or preference writes. */
public final class ReleaseRuntimeProbe {
    private static final File OUT = new File("/data/local/tmp/hyperextreme-release-test");
    private static final int W = 288, H = 640;
    private static final int[] VARIANTS = {1,101,201,301,501,601,602,603,604,605,606,701,702,703};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private JSONObject names;
    private Context context;
    private AssetManager assets;
    private Object renderer;
    private Method render, ready;
    private int test, frames;
    private long started;
    private EGLDisplay display;
    private EGLContext egl;
    private EGLSurface surface;
    private File photo;

    private Class<?> type(String name) throws Exception { return Class.forName(names.getString(name)); }
    private Method method(String owner, String name, Class<?>... parameters) throws Exception {
        Method m = type(owner).getDeclaredMethod(names.getString(owner + "." + name), parameters);
        m.setAccessible(true); return m;
    }
    private Object construct(String owner, Class<?>[] parameters, Object... args) throws Exception {
        Constructor<?> c = type(owner).getDeclaredConstructor(parameters);
        c.setAccessible(true); return c.newInstance(args);
    }
    private void start() throws Exception {
        names = new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(OUT,"names.json").toPath()), StandardCharsets.UTF_8));
        Class<?> at = Class.forName("android.app.ActivityThread");
        Context base = (Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        assets = AssetManager.class.getConstructor().newInstance();
        if ((Integer)AssetManager.class.getMethod("addAssetPath",String.class).invoke(assets,new File(OUT,"candidate.apk").getPath()) == 0)
            throw new AssertionError("Release assets unavailable");
        SharedPreferences prefs = (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
            new Class<?>[]{SharedPreferences.class}, (p,m,a) -> {
                if (m.getName().equals("getAll")) return Collections.emptyMap();
                if (m.getName().equals("contains")) return false;
                if (m.getName().startsWith("get") && a != null && a.length == 2) return a[1];
                throw new AssertionError("Preference access forbidden: " + m.getName());
            });
        context = new ContextWrapper(base) {
            private File dir(String name) { File f = new File(OUT,name); f.mkdirs(); return f; }
            public Context getApplicationContext() { return this; }
            public AssetManager getAssets() { return assets; }
            public Resources getResources() { return new Resources(assets,base.getResources().getDisplayMetrics(),base.getResources().getConfiguration()); }
            public File getFilesDir() { return dir("files"); }
            public File getCacheDir() { return dir("cache"); }
            public File getCodeCacheDir() { return dir("code-cache"); }
            public SharedPreferences getSharedPreferences(String n,int mode) { return prefs; }
        };
        photo = new File(OUT,"fixture.png");
        Bitmap bitmap = Bitmap.createBitmap(320,640,Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap); Paint p = new Paint(); p.setColor(0xffda7651); c.drawPaint(p);
        p.setColor(0xff409cc9); c.drawCircle(160,320,140,p);
        try (FileOutputStream f = new FileOutputStream(photo)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,f); }
        bitmap.recycle();
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (!EGL14.eglInitialize(display,new int[2],0,new int[2],0)) throw new AssertionError("EGL initialize");
        EGLConfig[] configs = new EGLConfig[1]; int[] count = new int[1];
        int[] attributes = {EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
            EGL14.EGL_DEPTH_SIZE,24,EGL14.EGL_NONE};
        if (!EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0) || count[0] == 0) throw new AssertionError("EGL config");
        egl = EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        surface = EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,W,EGL14.EGL_HEIGHT,H,EGL14.EGL_NONE},0);
        if (!EGL14.eglMakeCurrent(display,surface,surface,egl)) throw new AssertionError("EGL current");
        render = method("WallpaperRenderer","render"); ready = method("WallpaperRenderer","frameReady");
        next();
    }
    private void next() throws Exception {
        if (renderer != null) { ((AutoCloseable)renderer).close(); renderer = null; }
        if (test == VARIANTS.length * 3) {
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(display,surface); EGL14.eglDestroyContext(display,egl); EGL14.eglTerminate(display);
            System.out.println("RELEASE_RUNTIME_PASS cases=" + test + " frames=" + frames + " source=optimized-apk");
            System.exit(0); return;
        }
        int variant = VARIANTS[test / 3], mode = test % 3;
        Runnable invalidate = () -> {};
        if (variant >= 601 && variant <= 606) {
            Object options = method("PhotoOptions","defaults").invoke(null);
            renderer = construct("PhotoRenderer",new Class<?>[]{AssetManager.class,File.class,File.class,File.class,
                int.class,int.class,int.class,int.class,type("PhotoOptions"),Runnable.class},
                assets,photo,photo,photo,W,H,mode,variant,options,invalidate);
        } else if (variant >= 701) {
            Object options = method("VivoOptions","load",Context.class,int.class,boolean.class).invoke(null,context,variant,true);
            // R8 removes the unused preview argument; dark remains in the residual signature.
            renderer = construct("VivoRenderer",new Class<?>[]{Context.class,type("VivoOptions"),int.class,int.class,int.class,boolean.class,Runnable.class},
                context,options,W,H,mode,false,invalidate);
        } else {
            renderer = method("WallpaperRenderer","create",AssetManager.class,int.class,boolean.class,int.class,int.class,int.class,boolean.class,Runnable.class)
                .invoke(null,assets,variant,false,W,H,mode,false,invalidate);
        }
        started = SystemClock.uptimeMillis(); handler.post(this::draw);
    }
    private void draw() {
        try {
            render.invoke(renderer);
            if (!(Boolean)ready.invoke(renderer)) {
                if (SystemClock.uptimeMillis()-started > 15000) throw new AssertionError("Renderer timeout");
                handler.postDelayed(this::draw,16); return;
            }
            ByteBuffer pixels = ByteBuffer.allocateDirect(W*H*4);
            GLES30.glReadPixels(0,0,W,H,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixels);
            if (GLES30.glGetError() != 0) throw new AssertionError("GL error");
            int lit = 0;
            for (int i=0; i<W*H; i++) if ((pixels.get(i*4)&255)+(pixels.get(i*4+1)&255)+(pixels.get(i*4+2)&255)>24) lit++;
            if (lit < 32) throw new AssertionError("Empty optimized renderer frame");
            System.out.println("RELEASE_FRAME variant="+VARIANTS[test/3]+" mode="+(test%3)+" lit="+lit);
            frames++; test++; next();
        } catch (Throwable failure) { fail(failure); }
    }
    private static void fail(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    public static void main(String[] args) {
        try { Looper.prepareMainLooper(); new ReleaseRuntimeProbe().start(); Looper.loop(); }
        catch (Throwable failure) { fail(failure); }
    }
}
