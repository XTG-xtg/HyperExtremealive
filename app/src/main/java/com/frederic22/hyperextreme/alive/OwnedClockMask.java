package com.frederic22.hyperextreme.alive;

import android.view.View;

/** Hide only the replaced clock, retaining the latest native alpha for rollback. */
final class OwnedClockMask {
    private static int activeCount;
    static boolean any(){return activeCount>0;}
    private View target;
    private float desired;
    private boolean writing;
    float nativeAlpha(){return desired;}
    boolean bound(View view){return target==view;}
    void bind(View next){
        if(next==target)return;
        clear();target=next;desired=next.getAlpha();activeCount++;write(0);
    }
    boolean intercept(View view,float alpha){
        if(writing||view!=target)return false;
        desired=alpha;return true;
    }
    void clear(){
        if(target==null)return;
        try{if(target.getAlpha()==0)write(desired);}
        finally{target=null;activeCount--;}
    }
    private void write(float value){writing=true;try{target.setAlpha(value);}finally{writing=false;}}
}
