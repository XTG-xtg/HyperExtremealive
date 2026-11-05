package com.frederic22.hyperextreme.alive;

import android.graphics.*;
import com.frederic22.hyperextreme.data.ImageImporter;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;

/** ImageDecoder / PNG integration tests on synthetic pixels, never user pictures. */
public final class ImageImportProbe {
    private static int assertions;
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        File directory = new File("/data/local/tmp/hyperextreme-layout-review/image-tests"); directory.mkdirs();
        File source = new File(directory, "source.png"), target = new File(directory, "fitted.png");
        Bitmap input = Bitmap.createBitmap(1440, 3136, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(input); Paint paint = new Paint();
        paint.setColor(0xff2c9750); canvas.drawRect(720-100, 1568-300, 720+100, 1568+300, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        paint.setColor(0x803065bc); canvas.drawRect(620, 1768, 820, 1868, paint);
        try (FileOutputStream out = new FileOutputStream(source)) { input.compress(Bitmap.CompressFormat.PNG, 100, out); }
        input.recycle();
        check(ImageImporter.importPng(source, target, 631, 1471).equals("631×1471"), "Exact user-reported target size");
        Bitmap result = BitmapFactory.decodeFile(target.getPath());
        check(result.getWidth() == 631 && result.getHeight() == 1471, "Decoded exact dimensions");
        check(Color.alpha(result.getPixel(0, 0)) == 0, "Transparent corners preserved");
        check(Color.green(result.getPixel(315, 735)) > 130, "Centered subject remains centered");
        int partial = Color.alpha(result.getPixel(315, 860));
        check(partial > 110 && partial < 145, "Partial alpha preserved"); result.recycle();
        byte[] previous = Files.readAllBytes(target.toPath());
        File invalid = new File(directory, "invalid.bin"); Files.write(invalid.toPath(), new byte[]{1, 2, 3});
        try { ImageImporter.importPng(invalid, target, 631, 1471); throw new AssertionError("Invalid image accepted"); }
        catch (IOException expected) { check(Arrays.equals(previous, Files.readAllBytes(target.toPath())), "Decode failure preserves previous selection"); }
        try { ImageImporter.importPng(source, target, 50_000, 50_000); throw new AssertionError("Huge target accepted"); }
        catch (IOException expected) { check(Arrays.equals(previous, Files.readAllBytes(target.toPath())), "Oversize target preserves selection"); }
        File rotated = new File(directory, "exif.jpg");
        input = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888); canvas = new Canvas(input);
        canvas.drawColor(Color.BLUE); paint.setColor(Color.RED); canvas.drawRect(0, 0, 160, 640, paint);
        ByteArrayOutputStream jpg = new ByteArrayOutputStream(); input.compress(Bitmap.CompressFormat.JPEG, 100, jpg); input.recycle();
        byte[] raw = jpg.toByteArray();
        // JPEG APP1 / TIFF orientation=6 (90 degrees clockwise).
        byte[] exif = {0x45,0x78,0x69,0x66,0,0, 0x49,0x49,0x2a,0,8,0,0,0, 1,0,0x12,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0};
        try (FileOutputStream out = new FileOutputStream(rotated)) {
            out.write(raw, 0, 2); out.write(new byte[]{(byte)0xff,(byte)0xe1,0,(byte)(exif.length+2)});
            out.write(exif); out.write(raw, 2, raw.length-2);
        }
        ImageImporter.importPng(rotated, target, 0, 0); result = BitmapFactory.decodeFile(target.getPath());
        check(result.getWidth() == 640 && result.getHeight() == 320, "EXIF applied before size adaptation");
        check(Color.red(result.getPixel(320, 40)) > 200, "EXIF orientation is clockwise"); result.recycle();
        ImageImporter.importPng(rotated, target, 320, 640); result = BitmapFactory.decodeFile(target.getPath());
        check(result.getWidth() == 320 && result.getHeight() == 640, "Swapped source dimensions are not falsely considered matching");
        result.recycle();
        System.out.println("IMAGE_IMPORT_OK assertions=" + assertions + " exact=631x1471 alpha=true exif=true atomic_failure=true");
    }
}
