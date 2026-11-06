package com.frederic22.hyperextreme.alive.core;

/** Display-space clock bounds already include the ROM's burn-in translation. */
public final class AodPlacement {
    public final int width,height,left,top,right,bottom;
    public AodPlacement(int width,int height,int left,int top,int right,int bottom) {
        if(width<1||height<1||width>16384||height>16384||left<0||top<0||right>width||bottom>height||right<=left||bottom<=top)
            throw new IllegalArgumentException("Invalid clock bounds");
        this.width=width;this.height=height;this.left=left;this.top=top;this.right=right;this.bottom=bottom;
    }
    public boolean fits(int w,int h){return w>0&&h>0&&Math.abs((double)w*height/((double)h*width)-1)<.02;}
    /** Clock hosts can include empty layout space; they are not glyph bounds. */
    public boolean isClockContent(){return (bottom-top)<height*.35f && (right-left)<width*.95f;}
    public float x(){return Math.max(-.25f,Math.min(.25f,(left+right)*.5f/width-.5f));}
    // Leave enough room below the clock for the largest AOD orb, without moving off screen.
    public float y(){return .5f-Math.min(.78f,bottom/(float)height+.15f*width/height);}
    public static float driftX(long epochMs){return triangle(epochMs/60000,17)*.006f;}
    public static float driftY(long epochMs){return triangle(epochMs/60000+7,23)*.004f;}
    private static float triangle(long minute,int period){float t=Math.floorMod(minute,period*2)/(float)period;return t<=1?2*t-1:3-2*t;}
}
