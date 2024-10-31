package io.github.deltacv.visionloop.io;

import android.graphics.Bitmap;
import io.github.deltacv.steve.util.EvictingBlockingQueue;

import java.util.concurrent.ArrayBlockingQueue;

@SuppressWarnings("unused")
public class BitmapRecycler {

    private final EvictingBlockingQueue<Bitmap> bitmaps;

    private final int width;
    private final int height;
    private final int size;

    public BitmapRecycler(int width, int height, int size) {
        bitmaps = new EvictingBlockingQueue<>(new ArrayBlockingQueue<>(size));
        this.width = width;
        this.height = height;
        this.size = size;

        for (int i = 0; i < size; i++) {
            bitmaps.add(Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565));
        }
    }

    public Bitmap takeBitmap() {
        return bitmaps.poll();
    }

    public void returnBitmap(Bitmap bitmap) {
        bitmaps.add(bitmap);

        if(bitmap.getWidth() != width || bitmap.getHeight() != height || bitmap.getConfig() != Bitmap.Config.RGB_565 || bitmaps.remainingCapacity() == size) {
            throw new IllegalArgumentException("Invalid bitmap returned to the recycler");
        }
    }

}
