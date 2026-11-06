package com.frederic22.hyperextreme.alive;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Immutable private images: publishing changes references, never a texture's backing file. */
final class PhotoMedia {
    static final String DEFAULT_ASSET="photo/default.jpg";
    private static File defaultFile(Context c){return new File(c.getFilesDir(),"alive/builtin-photo-v1.jpg");}
    private PhotoMedia() { }
    static File resolve(Context c,PhotoOptions options,boolean preview,int slot) {
        String name=slot==2?options.framePhoto:slot==1&&!options.followLock?options.homePhoto:options.lockPhoto;
        if(!name.isEmpty())return new File(c.getFilesDir(),"alive/media/"+name);
        if(slot!=0)return resolve(c,options,preview,0);
        File draft=new File(c.getFilesDir(),"alive/photo-draft.png");
        if(preview&&draft.isFile())return draft;
        File applied=new File(c.getFilesDir(),"alive/photo.png");
        return applied.isFile()?applied:defaultFile(c);
    }
    static boolean available(Context c,PhotoOptions options,boolean preview,int slot){
        File file=resolve(c,options,preview,slot);
        return file.isFile()||file.equals(defaultFile(c));
    }
    /** Called by background render/import jobs, never by a Compose key or availability check. */
    static synchronized File prepare(Context c,PhotoOptions options,boolean preview,int slot)throws IOException {
        File file=resolve(c,options,preview,slot);
        if(!file.equals(defaultFile(c))||file.isFile())return file;
        if(!file.getParentFile().isDirectory()&&!file.getParentFile().mkdirs())throw new IOException("Cannot create default photo directory");
        File temp=new File(file.getPath()+".next");
        try {
            try(java.io.InputStream in=c.getAssets().open(DEFAULT_ASSET);FileOutputStream out=new FileOutputStream(temp)){
                byte[] buffer=new byte[32768];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);
                out.getFD().sync();
            }
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE);
        }finally{Files.deleteIfExists(temp.toPath());}
        return file;
    }
    static String importImage(Context c,Uri uri)throws IOException {
        File directory=new File(c.getFilesDir(),"alive/media");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create photo directory");
        Bitmap bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(),uri),(decoder,info,source)->{
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            float ratio=Math.min(1,2560f/Math.max(info.getSize().getWidth(),info.getSize().getHeight()));
            decoder.setTargetSize(Math.max(1,(int)(info.getSize().getWidth()*ratio)),Math.max(1,(int)(info.getSize().getHeight()*ratio)));
        });
        String name="photo-"+UUID.randomUUID().toString().replace("-","")+".png";
        File target=new File(directory,name),temp=new File(directory,name+".next");
        try {
            try(FileOutputStream out=new FileOutputStream(temp)){
                if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Photo encoding failed");
                out.getFD().sync();
            }
            Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);
            return name;
        }finally{bitmap.recycle();Files.deleteIfExists(temp.toPath());}
    }
    static void validate(Context c,PhotoOptions options,boolean preview,int variant)throws IOException {
        int slots=variant==602?3:2;
        for(int slot=0;slot<slots;slot++){
            File file=prepare(c,options,preview,slot);
            if(!file.isFile()||file.length()==0)throw new IOException("Photo is missing for slot "+slot+"; choose it again");
        }
    }
    static PhotoOptions freezeLegacy(Context c,PhotoOptions options,boolean preview)throws IOException {
        if(!options.lockPhoto.isEmpty())return options;
        File directory=new File(c.getFilesDir(),"alive/media");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create photo directory");
        String name="photo-"+UUID.randomUUID().toString().replace("-","")+".png";
        File destination=new File(directory,name);
        try(java.io.FileInputStream in=new java.io.FileInputStream(prepare(c,options,preview,0));FileOutputStream out=new FileOutputStream(destination)){
            byte[] buffer=new byte[32768];int size;while((size=in.read(buffer))!=-1)out.write(buffer,0,size);out.getFD().sync();
        }
        android.os.Bundle b=options.bundle();b.putStringArray("media",new String[]{name,options.homePhoto,options.framePhoto});
        return PhotoOptions.fromBundle(b);
    }
}
