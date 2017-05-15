/**
 *   ownCloud Android client application
 *
 *   @author Tobias Kaminsky
 *   @author David A. Velasco
 *   Copyright (C) 2015 ownCloud Inc.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.appspot.apprtc.util;

import android.accounts.Account;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ImageView;

import org.appspot.apprtc.R;

import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Locale;


/**
 * Manager for concurrent access to thumbnails cache.
 */
public class ThumbnailsCacheManager {
    
    private static final String TAG = ThumbnailsCacheManager.class.getSimpleName();
    
    private static final String CACHE_FOLDER = "thumbnailCache";

    private static final Object mThumbnailsDiskCacheLock = new Object();
    private static DiskLruImageCache mThumbnailCache = null;
    private static boolean mThumbnailCacheStarting = true;
    
    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 10; // 10MB
    private static final CompressFormat mCompressFormat = CompressFormat.JPEG;
    private static final int mCompressQuality = 70;
    private static Context mAppContext;

    static public void ThumbnailsCacheManagerInit(Context context) {
        synchronized (mThumbnailsDiskCacheLock) {
            mAppContext = context;

            if (mThumbnailCacheStarting) {
                // initialise thumbnails cache on background thread
                new InitDiskCacheTask().execute();
            }
        }
    }

    private static class InitDiskCacheTask extends AsyncTask<File, Void, Void> {

        @Override
        protected Void doInBackground(File... params) {
            synchronized (mThumbnailsDiskCacheLock) {
                mThumbnailCacheStarting = true;

                if (mThumbnailCache == null) {
                    try {
                        // Check if media is mounted or storage is built-in, if so,
                        // try and use external cache dir; otherwise use internal cache dir
                        final String cachePath =
                                mAppContext.getExternalCacheDir().getPath() +
                                        File.separator + CACHE_FOLDER;
                        final File diskCacheDir = new File(cachePath);
                        mThumbnailCache = new DiskLruImageCache(
                                diskCacheDir,
                                DISK_CACHE_SIZE,
                                mCompressFormat,
                                mCompressQuality
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                        mThumbnailCache = null;
                    }
                }
                mThumbnailCacheStarting = false; // Finished initialization
                mThumbnailsDiskCacheLock.notifyAll(); // Wake any waiting threads
            }
            return null;
        }
    }

    /**
     * Add thumbnail to cache
     * @param imageKey: thumb key
     * @param bitmap:   image for extracting thumbnail
     * @param path:     image path
     * @param px:       thumbnail dp
     * @return Bitmap
     */
    private static Bitmap addThumbnailToCache(String imageKey, Bitmap bitmap, String path, int px){

        Bitmap thumbnail = ThumbnailUtils.extractThumbnail(bitmap, px, px);

        // Add thumbnail to cache
        addBitmapToCache(imageKey, thumbnail);

        return thumbnail;
    }
    
    public static void addBitmapToCache(String key, Bitmap bitmap) {
        synchronized (mThumbnailsDiskCacheLock) {
            if (mThumbnailCache != null) {
                mThumbnailCache.put(key, bitmap);
            }
        }
    }

    public static Bitmap getBitmapFromDiskCache(String key) {
        synchronized (mThumbnailsDiskCacheLock) {
            // Wait while disk cache is started from background thread
            while (mThumbnailCacheStarting) {
                try {
                    mThumbnailsDiskCacheLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (mThumbnailCache != null) {
                return mThumbnailCache.getBitmap(key);
            }
        }
        return null;
    }

    public static void LoadImage(final String url, ImageView image, final String displayname, final boolean rounded, boolean fromCache) {
        final WeakReference<ImageView> imageView = new WeakReference<ImageView>(image);
        final Handler uiHandler = new Handler();
        final int FG_COLOR = 0xFFFAFAFA;
        final String name = displayname;

        if (fromCache) {
            Bitmap bitmap = ThumbnailsCacheManager.getBitmapFromDiskCache(url);

            if (bitmap != null) {
                if (rounded) {
                    RoundedBitmapDrawable roundedBitmap = RoundedBitmapDrawableFactory.create(imageView.get().getResources(), bitmap);
                    roundedBitmap.setCircular(true);
                    imageView.get().setImageDrawable(roundedBitmap);
                } else {
                    imageView.get().setImageBitmap(bitmap);
                }
                imageView.get().setContentDescription(displayname);
                return;
            }
        }

        AsyncHttpURLConnection httpConnection =
                new AsyncHttpURLConnection("GET", url, "", new AsyncHttpURLConnection.AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                        Log.d("LoadImage", errorMessage);
                    }

                    @Override
                    public void onHttpComplete(String response) {
                        int size = 96;
                        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        final String trimmedName = name == null ? "" : name.trim();
                        drawTile(canvas, trimmedName, 0, 0, size, size);
                        ThumbnailsCacheManager.addBitmapToCache(url, bitmap);

                        onHttpComplete(bitmap);
                    }

                    @Override
                    public void onHttpComplete(final Bitmap response) {
                        if (imageView != null && imageView.get() != null) {
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (rounded) {
                                        RoundedBitmapDrawable roundedBitmap = RoundedBitmapDrawableFactory.create(imageView.get().getResources(), response);
                                        roundedBitmap.setCircular(true);
                                        imageView.get().setImageDrawable(roundedBitmap);
                                    }
                                    else {
                                        imageView.get().setImageBitmap(response);
                                    }
                                    imageView.get().setContentDescription(displayname);

                                }

                            });

                        }
                    }

                    private boolean drawTile(Canvas canvas, String letter, int tileColor,
                                             int left, int top, int right, int bottom) {
                        letter = letter.toUpperCase(Locale.getDefault());
                        Paint tilePaint = new Paint(), textPaint = new Paint();
                        tilePaint.setColor(tileColor);
                        textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
                        textPaint.setColor(FG_COLOR);
                        textPaint.setTypeface(Typeface.create("sans-serif-light",
                                Typeface.NORMAL));
                        textPaint.setTextSize((float) ((right - left) * 0.8));
                        Rect rect = new Rect();

                        canvas.drawRect(new Rect(left, top, right, bottom), tilePaint);
                        textPaint.getTextBounds(letter, 0, 1, rect);
                        float width = textPaint.measureText(letter);
                        canvas.drawText(letter, (right + left) / 2 - width / 2, (top + bottom)
                                / 2 + rect.height() / 2, textPaint);
                        return true;
                    }

                    private boolean drawTile(Canvas canvas, String name, int left, int top, int right, int bottom) {
                        if (name != null) {
                            final String letter = getFirstLetter(name);
                            final int color = ThumbnailsCacheManager.getColorForName(name);
                            drawTile(canvas, letter, color, left, top, right, bottom);
                            return true;
                        }
                        return false;
                    }


                });
        httpConnection.setBitmap();
        httpConnection.send();
    }

    public interface LoadImageCallback {
        void onImageLoaded(Bitmap bitmap);
    }

    public static void LoadImage(final String url, LoadImageCallback callback, final Resources resources, final String displayname, final boolean rounded, boolean fromCache) {
        final LoadImageCallback mCallback = callback;
        final Handler uiHandler = new Handler();
        final int FG_COLOR = 0xFFFAFAFA;
        final String name = displayname;

        if (fromCache) {
            Bitmap bitmap = ThumbnailsCacheManager.getBitmapFromDiskCache(url);

            if (bitmap != null) {
                if (rounded) {
                    RoundedBitmapDrawable roundedBitmap = RoundedBitmapDrawableFactory.create(resources, bitmap);
                    roundedBitmap.setCircular(true);
                    mCallback.onImageLoaded(roundedBitmap.getBitmap());
                } else {
                    mCallback.onImageLoaded(bitmap);
                }
                return;
            }
        }

        AsyncHttpURLConnection httpConnection =
                new AsyncHttpURLConnection("GET", url, "", new AsyncHttpURLConnection.AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                        Log.d("LoadImage", errorMessage);
                    }

                    @Override
                    public void onHttpComplete(String response) {
                        int size = 96;
                        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        final String trimmedName = name == null ? "" : name.trim();
                        drawTile(canvas, trimmedName, 0, 0, size, size);
                        ThumbnailsCacheManager.addBitmapToCache(url, bitmap);
                        onHttpComplete(bitmap);
                    }

                    @Override
                    public void onHttpComplete(final Bitmap response) {
                        if (mCallback != null) {
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (rounded) {
                                        RoundedBitmapDrawable roundedBitmap = RoundedBitmapDrawableFactory.create(resources, response);
                                        roundedBitmap.setCircular(true);
                                        mCallback.onImageLoaded(roundedBitmap.getBitmap());
                                    }
                                    else {
                                        mCallback.onImageLoaded(response);
                                    }

                                }

                            });

                        }
                    }

                    private boolean drawTile(Canvas canvas, String letter, int tileColor,
                                             int left, int top, int right, int bottom) {
                        letter = letter.toUpperCase(Locale.getDefault());
                        Paint tilePaint = new Paint(), textPaint = new Paint();
                        tilePaint.setColor(tileColor);
                        textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
                        textPaint.setColor(FG_COLOR);
                        textPaint.setTypeface(Typeface.create("sans-serif-light",
                                Typeface.NORMAL));
                        textPaint.setTextSize((float) ((right - left) * 0.8));
                        Rect rect = new Rect();

                        canvas.drawRect(new Rect(left, top, right, bottom), tilePaint);
                        textPaint.getTextBounds(letter, 0, 1, rect);
                        float width = textPaint.measureText(letter);
                        canvas.drawText(letter, (right + left) / 2 - width / 2, (top + bottom)
                                / 2 + rect.height() / 2, textPaint);
                        return true;
                    }

                    private boolean drawTile(Canvas canvas, String name, int left, int top, int right, int bottom) {
                        if (name != null) {
                            final String letter = getFirstLetter(name);
                            final int color = ThumbnailsCacheManager.getColorForName(name);
                            drawTile(canvas, letter, color, left, top, right, bottom);
                            return true;
                        }
                        return false;
                    }


                });
        httpConnection.setBitmap();
        httpConnection.send();
    }

    public static void LoadMenuImage(final String url, MenuItem menuItem, String displayname, final boolean rounded, Resources resources, boolean fromCache) {
        final Resources mResources = resources;
        final WeakReference<MenuItem> menuItemImage = new WeakReference<MenuItem>(menuItem);
        final Handler uiHandler = new Handler();
        final int FG_COLOR = 0xFFFAFAFA;
        final String name = displayname;

        if (fromCache) {
            Bitmap bitmap = ThumbnailsCacheManager.getBitmapFromDiskCache(url);

            if (bitmap != null) {
                if (rounded) {
                    RoundedBitmapDrawable roundedBitmap = RoundedBitmapDrawableFactory.create(mResources, bitmap);
                    roundedBitmap.setCircular(true);
                    menuItemImage.get().setIcon(roundedBitmap);
                } else {
                    menuItemImage.get().setIcon(new BitmapDrawable(bitmap));
                }
                return;
            }
        }

        AsyncHttpURLConnection httpConnection =
                new AsyncHttpURLConnection("GET", url, "", new AsyncHttpURLConnection.AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                        Log.d("LoadImage", errorMessage);
                    }

                    @Override
                    public void onHttpComplete(String response) {
                        int size = 96;
                        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        final String trimmedName = name == null ? "" : name.trim();
                        drawTile(canvas, trimmedName, 0, 0, size, size);
                        ThumbnailsCacheManager.addBitmapToCache(url, bitmap);
                        onHttpComplete(bitmap);
                    }

                    @Override
                    public void onHttpComplete(final Bitmap response) {
                        if (menuItemImage != null && menuItemImage.get() != null) {
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (rounded) {
                                        RoundedBitmapDrawable roundedBitmap = RoundedBitmapDrawableFactory.create(mResources, response);
                                        roundedBitmap.setCircular(true);
                                        menuItemImage.get().setIcon(roundedBitmap);
                                    }
                                    else {
                                        menuItemImage.get().setIcon(new BitmapDrawable(response));
                                    }
                                }

                            });

                        }
                    }

                    private boolean drawTile(Canvas canvas, String letter, int tileColor,
                                             int left, int top, int right, int bottom) {
                        letter = letter.toUpperCase(Locale.getDefault());
                        Paint tilePaint = new Paint(), textPaint = new Paint();
                        tilePaint.setColor(tileColor);
                        textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
                        textPaint.setColor(FG_COLOR);
                        textPaint.setTypeface(Typeface.create("sans-serif-light",
                                Typeface.NORMAL));
                        textPaint.setTextSize((float) ((right - left) * 0.8));
                        Rect rect = new Rect();

                        canvas.drawRect(new Rect(left, top, right, bottom), tilePaint);
                        textPaint.getTextBounds(letter, 0, 1, rect);
                        float width = textPaint.measureText(letter);
                        canvas.drawText(letter, (right + left) / 2 - width / 2, (top + bottom)
                                / 2 + rect.height() / 2, textPaint);
                        return true;
                    }

                    private boolean drawTile(Canvas canvas, String name, int left, int top, int right, int bottom) {
                        if (name != null) {
                            final String letter = getFirstLetter(name);
                            final int color = ThumbnailsCacheManager.getColorForName(name);
                            drawTile(canvas, letter, color, left, top, right, bottom);
                            return true;
                        }
                        return false;
                    }


                });
        httpConnection.setBitmap();
        httpConnection.send();
    }

    public static int getColorForName(String name) {
        if (name == null || name.isEmpty()) {
            return 0xFF202020;
        }
        int colors[] = {0xFFe91e63, 0xFF9c27b0, 0xFF673ab7, 0xFF3f51b5,
                0xFF5677fc, 0xFF03a9f4, 0xFF00bcd4, 0xFF009688, 0xFFff5722,
                0xFF795548, 0xFF607d8b};
        return colors[(int) ((name.hashCode() & 0xffffffffl) % colors.length)];
    }

    public static String getFirstLetter(String name) {
        for(Character c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                return c.toString();
            }
        }
        return "X";
    }
}
