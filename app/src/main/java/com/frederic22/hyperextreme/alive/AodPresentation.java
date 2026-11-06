package com.frederic22.hyperextreme.alive;

import android.content.res.AssetManager;
import android.opengl.GLES30;
import java.io.IOException;

/** Wallpaper-only luminance compensation. Never changes panel brightness or AOD policy. */
final class AodPresentation implements AutoCloseable {
    private AssetGl shader;
    private final AssetManager assets;
    private final int width,height;
    private final int[] texture=new int[1],framebuffer=new int[1],vao=new int[1];
    AodPresentation(AssetManager assets,int width,int height)throws IOException{
        this.width=width;this.height=height;this.assets=assets;
    }
    private void prepare(){
        if(shader!=null)return;
        try {
        shader=new AssetGl(assets,"shader/alive/presentation.vert","shader/alive/presentation.frag");
        } catch(IOException error){throw new IllegalStateException("Presentation shader failed",error);}
        GLES30.glGenTextures(1,texture,0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture[0]);
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D,1,GLES30.GL_RGBA8,width,height);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
        GLES30.glGenFramebuffers(1,framebuffer,0);GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,framebuffer[0]);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,GLES30.GL_COLOR_ATTACHMENT0,GLES30.GL_TEXTURE_2D,texture[0],0);
        int status=GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);GLES30.glGenVertexArrays(1,vao,0);
        if(status!=GLES30.GL_FRAMEBUFFER_COMPLETE){close();throw new IllegalStateException("AOD framebuffer incomplete");}
    }
    void render(float gain){
        render(gain,0,0);
    }
    void render(float gain,float x,float y){
        if(Math.abs(gain-1)<.001f&&Math.abs(x)<.00001f&&Math.abs(y)<.00001f)return;
        prepare();
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST);GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glDisable(GLES30.GL_STENCIL_TEST);GLES30.glColorMask(true,true,true,true);
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER,0);
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER,framebuffer[0]);
        // Resolve the multisampled window; glCopyTexImage is not valid on all MSAA surfaces.
        GLES30.glBlitFramebuffer(0,0,width,height,0,0,width,height,GLES30.GL_COLOR_BUFFER_BIT,GLES30.GL_NEAREST);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);GLES30.glViewport(0,0,width,height);
        shader.use();shader.f("exposure",gain);GLES30.glUniform2f(shader.at("offset"),x,-y);GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture[0]);GLES30.glUniform1i(shader.at("image"),0);
        GLES30.glBindVertexArray(vao[0]);GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);GLES30.glBindVertexArray(0);
    }
    public void close(){if(shader!=null){shader.close();shader=null;}GLES30.glDeleteTextures(1,texture,0);GLES30.glDeleteFramebuffers(1,framebuffer,0);GLES30.glDeleteVertexArrays(1,vao,0);}
}
