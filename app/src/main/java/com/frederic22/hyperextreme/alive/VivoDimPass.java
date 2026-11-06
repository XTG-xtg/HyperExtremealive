// Derived from OpenAlive; MIT; see docs/alive/OPENALIVE_LICENSE and IMPORTED-VIVO.json.
package com.frederic22.hyperextreme.alive;

import android.opengl.GLES30;

/** Final wallpaper-only RGB multiplication. Restores the original Vivo renderer's GL state. */
final class VivoDimPass implements AutoCloseable {
    private int program,vao,level;
    private final int[] state=new int[13],viewport=new int[4];
    private final boolean[] mask=new boolean[4];
    private static final int[] CAPS={GLES30.GL_BLEND,GLES30.GL_DEPTH_TEST,GLES30.GL_STENCIL_TEST,GLES30.GL_SCISSOR_TEST,GLES30.GL_CULL_FACE};
    private final boolean[] enabled=new boolean[CAPS.length];
    private static final int[] STATES={GLES30.GL_CURRENT_PROGRAM,GLES30.GL_VERTEX_ARRAY_BINDING,GLES30.GL_DRAW_FRAMEBUFFER_BINDING,
        GLES30.GL_BLEND_SRC_RGB,GLES30.GL_BLEND_DST_RGB,GLES30.GL_BLEND_SRC_ALPHA,GLES30.GL_BLEND_DST_ALPHA,
        GLES30.GL_BLEND_EQUATION_RGB,GLES30.GL_BLEND_EQUATION_ALPHA};
    private static int shader(int type,String source){
        int s=GLES30.glCreateShader(type);GLES30.glShaderSource(s,source);GLES30.glCompileShader(s);
        int[] ok={0};GLES30.glGetShaderiv(s,GLES30.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0){String error=GLES30.glGetShaderInfoLog(s);GLES30.glDeleteShader(s);throw new IllegalStateException(error);}return s;
    }
    private void create(){
        int v=shader(GLES30.GL_VERTEX_SHADER,"#version 300 es\nvoid main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.0-1.0,0.0,1.0);}"),f=0;
        try{
            f=shader(GLES30.GL_FRAGMENT_SHADER,"#version 300 es\nprecision mediump float;uniform float level;out vec4 color;void main(){color=vec4(0.0,0.0,0.0,1.0-level);}");
            program=GLES30.glCreateProgram();GLES30.glAttachShader(program,v);GLES30.glAttachShader(program,f);GLES30.glLinkProgram(program);
            int[] ok={0};GLES30.glGetProgramiv(program,GLES30.GL_LINK_STATUS,ok,0);
            if(ok[0]==0)throw new IllegalStateException(GLES30.glGetProgramInfoLog(program));
            level=GLES30.glGetUniformLocation(program,"level");GLES30.glGenVertexArrays(1,ok,0);vao=ok[0];
        }catch(RuntimeException e){close();throw e;}
        finally{GLES30.glDeleteShader(v);if(f!=0)GLES30.glDeleteShader(f);}
    }
    void draw(float brightness,int width,int height){
        if(brightness>=1)return;
        if(program==0)create();
        for(int i=0;i<STATES.length;i++)GLES30.glGetIntegerv(STATES[i],state,i);
        GLES30.glGetIntegerv(GLES30.GL_VIEWPORT,viewport,0);GLES30.glGetBooleanv(GLES30.GL_COLOR_WRITEMASK,mask,0);
        for(int i=0;i<CAPS.length;i++){enabled[i]=GLES30.glIsEnabled(CAPS[i]);GLES30.glDisable(CAPS[i]);}
        try{
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER,0);GLES30.glViewport(0,0,width,height);
            GLES30.glColorMask(true,true,true,true);GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendEquationSeparate(GLES30.GL_FUNC_ADD,GLES30.GL_FUNC_ADD);
            GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA,GLES30.GL_ZERO,GLES30.GL_ONE);
            GLES30.glUseProgram(program);GLES30.glUniform1f(level,brightness);GLES30.glBindVertexArray(vao);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);
        }finally{
            GLES30.glUseProgram(state[0]);GLES30.glBindVertexArray(state[1]);GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER,state[2]);
            GLES30.glBlendFuncSeparate(state[3],state[4],state[5],state[6]);GLES30.glBlendEquationSeparate(state[7],state[8]);
            GLES30.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);GLES30.glColorMask(mask[0],mask[1],mask[2],mask[3]);
            for(int i=0;i<CAPS.length;i++)if(enabled[i])GLES30.glEnable(CAPS[i]);else GLES30.glDisable(CAPS[i]);
        }
    }
    @Override public void close(){if(program!=0)GLES30.glDeleteProgram(program);if(vao!=0)GLES30.glDeleteVertexArrays(1,new int[]{vao},0);program=vao=0;}
}
