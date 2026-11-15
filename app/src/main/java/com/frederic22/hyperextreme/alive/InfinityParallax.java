package com.frederic22.hyperextreme.alive;

/** Clip-space transforms keep parallax consistent across aspect ratios and theme families. */
final class InfinityParallax {
    private InfinityParallax(){}
    static void background(float[] matrix,InfinityMotion motion){
        float x=motion.desktopX(),y=motion.desktopY();
        float angle=(float)Math.toRadians(x*1.5f),scale=1+.09f*Math.max(Math.abs(x),Math.abs(y));
        float cosine=(float)Math.cos(angle)*scale,sine=(float)Math.sin(angle)*scale;
        float dx=x*.055f,dy=y*.035f+x*.02f;
        // Overscan covers the shifted/rotated full-screen quad, including gradient-only homes.
        for(int column=0;column<16;column+=4){
            float a=matrix[column],b=matrix[column+1],w=matrix[column+3];
            matrix[column]=cosine*a-sine*b+dx*w;
            matrix[column+1]=sine*a+cosine*b+dy*w;
        }
    }
    static void foreground(float[] matrix,InfinityMotion motion){
        for(int column=0;column<16;column+=4){
            matrix[column]+=motion.desktopX()*.1f*matrix[column+3];
            matrix[column+1]+=motion.desktopY()*.06f*matrix[column+3];
        }
    }
}
