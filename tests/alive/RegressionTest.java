import com.frederic22.hyperextreme.alive.core.AlivePermissionPolicy;
import com.frederic22.hyperextreme.alive.core.PhotoGeometry;

public final class RegressionTest {
    private static int checks;
    private static void check(boolean value) {
        if (!value) throw new AssertionError("Regression " + checks);
        checks++;
    }
    public static void main(String[] args) {
        String pkg = "com.frederic22.hyperextreme";
        String ambient = "android.permission.AMBIENT_WALLPAPER";
        check(AlivePermissionPolicy.grants(ambient, pkg));
        for (String other : new String[]{null, "", "android", "com.qihang.aod", pkg + ".other"}) {
            check(!AlivePermissionPolicy.grants(ambient, other));
        }
        for (String permission : new String[]{null, "", "android.permission.SET_WALLPAPER_COMPONENT",
                "android.permission.READ_CONTACTS", "android.permission.BIND_WALLPAPER"}) {
            check(!AlivePermissionPolicy.grants(permission, pkg));
        }
        for (float viewport : new float[]{.449f, 1f, 2.2f}) {
            for (float photo : new float[]{.3f, .449f, 1f, 1.778f, 4f}) {
                float x = PhotoGeometry.cropX(viewport, photo), y = PhotoGeometry.cropY(viewport, photo);
                check(x > 0 && x <= 1 && y > 0 && y <= 1);
                check(x == 1 || y == 1);
                // The sampled physical image has the same aspect as the viewport.
                check(Math.abs(photo * x / y - viewport) < .00001f);
            }
        }
        System.out.println("Permission/crop regression assertions: " + checks);
    }
}
