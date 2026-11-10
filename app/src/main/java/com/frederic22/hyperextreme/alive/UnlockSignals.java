package com.frederic22.hyperextreme.alive;

import java.lang.reflect.Method;
import java.util.List;

/** Observe confirmed keyguard exit, never authentication attempts or wallpaper visibility. */
final class UnlockSignals {
    static boolean goingAway(Method method, List<?> args) {
        Class<?>[] parameters=method.getParameterTypes();
        return method.getName().equals("notifyKeyguardGoingAway")&&method.getReturnType()==void.class
            &&parameters.length==1&&parameters[0]==boolean.class&&args.size()==1&&Boolean.TRUE.equals(args.get(0));
    }
    static boolean exitStart(Method method) {
        Class<?>[] parameters=method.getParameterTypes();
        return method.getName().equals("startKeyguardExitAnimation")&&method.getReturnType()==void.class
            &&(parameters.length==2&&parameters[0]==long.class&&parameters[1]==long.class
                ||parameters.length==1&&parameters[0].getName().equals("com.android.wm.shell.shared.compat.SurfaceTransition$Params"));
    }
    static boolean cancelled(Method method, List<?> args) {
        Class<?>[] parameters=method.getParameterTypes();
        // A generic exit cancellation can also complete an unlock. Only this true
        // branch explicitly calls showKeyguard on the verified SystemUI contract.
        return method.getName().equals("finishSurfaceBehindRemoteAnimation")&&method.getReturnType()==void.class
            &&parameters.length==1&&parameters[0]==boolean.class&&args.size()==1&&Boolean.TRUE.equals(args.get(0));
    }
}
