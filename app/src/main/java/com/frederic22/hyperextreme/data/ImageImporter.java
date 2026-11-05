package com.frederic22.hyperextreme.data;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Orients via ImageDecoder, bounds decode memory and publishes an exact-size transparent PNG atomically. */
public final class ImageImporter {
    private ImageImporter() { }

    public static String importPng(File source, File target, int width, int height) throws IOException {
        boolean adapt = width > 0 && height > 0;
        if ((width > 0) != (height > 0)) throw new IOException("壁纸尺寸无效");
        if (adapt && (width > 8192 || height > 8192 || (long) width * height > 16_777_216L))
            throw new IOException("壁纸尺寸过大，暂不自动适配；未覆盖原有图片");
        Bitmap decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source), (decoder, info, unused) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB));
            int w = info.getSize().getWidth(), h = info.getSize().getHeight();
            double scale = Math.min(1, 4096.0 / Math.max(w, h));
            if (adapt) scale = Math.min(scale, Math.max((double) width / w, (double) height / h));
            decoder.setTargetSize(Math.max(1, (int) Math.ceil(w * scale)), Math.max(1, (int) Math.ceil(h * scale)));
        });
        Bitmap output = decoded;
        try {
            if (adapt && (decoded.getWidth() != width || decoded.getHeight() != height)) {
                output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                double[] crop = ImageFitGeometry.crop(decoded.getWidth(), decoded.getHeight(), width, height);
                Matrix matrix = new Matrix();
                matrix.setRectToRect(new RectF((float) crop[0], (float) crop[1], (float) crop[2], (float) crop[3]),
                    new RectF(0, 0, width, height), Matrix.ScaleToFit.FILL);
                new Canvas(output).drawBitmap(decoded, matrix, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            }
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建图片目录");
            AtomicFile atomic = new AtomicFile(target);
            FileOutputStream stream = null;
            try {
                stream = atomic.startWrite();
                if (!output.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("PNG 编码失败");
                atomic.finishWrite(stream);
            } catch (IOException | RuntimeException error) {
                atomic.failWrite(stream);
                throw error;
            }
            return output.getWidth() + "×" + output.getHeight();
        } finally {
            if (output != decoded) output.recycle();
            decoded.recycle();
        }
    }
}
