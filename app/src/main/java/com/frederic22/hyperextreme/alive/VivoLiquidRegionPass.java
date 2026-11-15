package com.frederic22.hyperextreme.alive;

import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.GLUtils;

/** Restores original photo pixels outside the selection, including native lattice spillover. */
final class VivoLiquidRegionPass implements AutoCloseable {
    private int program,vao;
    private final int[] textures=new int[2],state=new int[12],viewport=new int[4];
    private final boolean[] colorMask=new boolean[4],enabled=new boolean[5];
    private static final int[] CAPS={GLES30.GL_BLEND,GLES30.GL_DEPTH_TEST,GLES30.GL_STENCIL_TEST,GLES30.GL_SCISSOR_TEST,GLES30.GL_CULL_FACE};
    private static final int[] STATES={GLES30.GL_CURRENT_PROGRAM,GLES30.GL_VERTEX_ARRAY_BINDING,GLES30.GL_DRAW_FRAMEBUFFER_BINDING,
        GLES30.GL_BLEND_SRC_RGB,GLES30.GL_BLEND_DST_RGB,GLES30.GL_BLEND_SRC_ALPHA,GLES30.GL_BLEND_DST_ALPHA,
        GLES30.GL_BLEND_EQUATION_RGB,GLES30.GL_BLEND_EQUATION_ALPHA,GLES30.GL_ACTIVE_TEXTURE};
    VivoLiquidRegionPass(Bitmap photo,Bitmap mask,boolean displayP3){
        int[] previous={0};GLES30.glGetIntegerv(GLES30.GL_TEXTURE_BINDING_2D,previous,0);
        Bitmap upload=VivoColorSpace.convert(photo,displayP3);
        try{
            GLES30.glGenTextures(2,textures,0);
            for(int i=0;i<2;i++){
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,textures[i]);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,i==0?upload:mask,0);
            }
        }catch(RuntimeException e){close();throw e;}
        finally{if(upload!=photo)upload.recycle();GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,previous[0]);}
    }
    private static int shader(int type,String text){
        int s=GLES30.glCreateShader(type);GLES30.glShaderSource(s,text);GLES30.glCompileShader(s);int[] ok={0};
        GLES30.glGetShaderiv(s,GLES30.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0){String message=GLES30.glGetShaderInfoLog(s);GLES30.glDeleteShader(s);throw new IllegalStateException(message);}return s;
    }
    private void create(){
        int v=shader(GLES30.GL_VERTEX_SHADER,"#version 300 es\nout vec2 uv;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));uv=vec2(p.x,1.0-p.y);gl_Position=vec4(p*2.0-1.0,0.0,1.0);}"),f=0;
        try{
            f=shader(GLES30.GL_FRAGMENT_SHADER,"#version 300 es\nprecision highp float;in vec2 uv;uniform sampler2D photo;uniform sampler2D mask;uniform vec4 crop;uniform float scale;out vec4 color;void main(){vec2 p=vec2(.5,.4)+(uv-vec2(.5,.4))/scale;vec2 t=crop.xy+p*crop.zw;float coverage=texture(mask,t).r;color=vec4(texture(photo,t).rgb,1.0-coverage);}");
            program=GLES30.glCreateProgram();GLES30.glAttachShader(program,v);GLES30.glAttachShader(program,f);GLES30.glLinkProgram(program);
            int[] ok={0};GLES30.glGetProgramiv(program,GLES30.GL_LINK_STATUS,ok,0);
            if(ok[0]==0)throw new IllegalStateException(GLES30.glGetProgramInfoLog(program));
            GLES30.glGenVertexArrays(1,ok,0);vao=ok[0];
        }catch(RuntimeException e){close();throw e;}
        finally{GLES30.glDeleteShader(v);if(f!=0)GLES30.glDeleteShader(f);}
    }
    void draw(PhotoViewport crop,float scale,int width,int height){
        if(program==0)create();
        for(int i=0;i<STATES.length;i++)GLES30.glGetIntegerv(STATES[i],state,i);
        for(int i=0;i<2;i++){GLES30.glActiveTexture(GLES30.GL_TEXTURE0+i);GLES30.glGetIntegerv(GLES30.GL_TEXTURE_BINDING_2D,state,10+i);}
        GLES30.glGetIntegerv(GLES30.GL_VIEWPORT,viewport,0);GLES30.glGetBooleanv(GLES30.GL_COLOR_WRITEMASK,colorMask,0);
        for(int i=0;i<CAPS.length;i++){enabled[i]=GLES30.glIsEnabled(CAPS[i]);GLES30.glDisable(CAPS[i]);}
        try{
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER,0);GLES30.glViewport(0,0,width,height);GLES30.glColorMask(true,true,true,true);
            GLES30.glEnable(GLES30.GL_BLEND);GLES30.glBlendEquationSeparate(GLES30.GL_FUNC_ADD,GLES30.GL_FUNC_ADD);
            GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA,GLES30.GL_ZERO,GLES30.GL_ONE);
            GLES30.glUseProgram(program);GLES30.glBindVertexArray(vao);
            for(int i=0;i<2;i++){GLES30.glActiveTexture(GLES30.GL_TEXTURE0+i);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,textures[i]);}
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"photo"),0);GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"mask"),1);
            GLES30.glUniform4f(GLES30.glGetUniformLocation(program,"crop"),crop.left(),crop.top(),crop.width(),crop.height());
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program,"scale"),scale);GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);
        }finally{
            for(int i=0;i<2;i++){GLES30.glActiveTexture(GLES30.GL_TEXTURE0+i);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,state[10+i]);}
            GLES30.glActiveTexture(state[9]);GLES30.glUseProgram(state[0]);GLES30.glBindVertexArray(state[1]);GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER,state[2]);
            GLES30.glBlendFuncSeparate(state[3],state[4],state[5],state[6]);GLES30.glBlendEquationSeparate(state[7],state[8]);
            GLES30.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);GLES30.glColorMask(colorMask[0],colorMask[1],colorMask[2],colorMask[3]);
            for(int i=0;i<CAPS.length;i++)if(enabled[i])GLES30.glEnable(CAPS[i]);else GLES30.glDisable(CAPS[i]);
        }
    }
    public void close(){if(program!=0)GLES30.glDeleteProgram(program);if(vao!=0)GLES30.glDeleteVertexArrays(1,new int[]{vao},0);GLES30.glDeleteTextures(2,textures,0);textures[0]=textures[1]=program=vao=0;}
}
