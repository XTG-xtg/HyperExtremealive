package com.frederic22.hyperextreme.alive.core;

/** Center-crop UVs must stay inside the image; values above one smear clamped edges. */
public final class PhotoGeometry {
    private PhotoGeometry() { }

    public static float cropX(float viewportAspect, float photoAspect) {
        return Math.min(1f, viewportAspect / photoAspect);
    }

    public static float cropY(float viewportAspect, float photoAspect) {
        return Math.min(1f, photoAspect / viewportAspect);
    }
    public static float[] gesture(float x,float y,float zoom,float photoAspect,float viewportAspect,
            float panX,float panY,float focusX,float focusY,float factor) {
        if(!Float.isFinite(photoAspect)||photoAspect<=0||!Float.isFinite(viewportAspect)||viewportAspect<=0
                ||!Float.isFinite(factor)||factor<=0||!Float.isFinite(panX)||!Float.isFinite(panY)||!Float.isFinite(focusX)||!Float.isFinite(focusY))
            return new float[]{x,y,zoom};
        float next=Math.max(1,Math.min(4,zoom*factor));
        float oldX=cropX(viewportAspect,photoAspect)/zoom,oldY=cropY(viewportAspect,photoAspect)/zoom;
        float newX=cropX(viewportAspect,photoAspect)/next,newY=cropY(viewportAspect,photoAspect)/next;
        x=bound(x,oldX)-panX*oldX+(focusX-.5f)*(oldX-newX);
        y=bound(y,oldY)-panY*oldY+(focusY-.5f)*(oldY-newY);
        return new float[]{bound(x,newX),bound(y,newY),next};
    }
    private static float bound(float center,float span){return Math.max(span/2,Math.min(1-span/2,center));}
}
