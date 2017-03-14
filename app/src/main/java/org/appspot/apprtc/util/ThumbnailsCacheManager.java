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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ImageView;

import org.appspot.apprtc.R;

import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;


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

            // initialise thumbnails cache on background thread
            new InitDiskCacheTask().execute();
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

    public static void LoadImage(String url, ImageView image) {
        final WeakReference<ImageView> imageView = new WeakReference<ImageView>(image);
        final Handler uiHandler = new Handler();

        AsyncHttpURLConnection httpConnection =
                new AsyncHttpURLConnection("GET", url, "", new AsyncHttpURLConnection.AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                        Log.d("LoadImage", errorMessage);
                    }

                    @Override
                    public void onHttpComplete(String response) {

                    }

                    @Override
                    public void onHttpComplete(final Bitmap response) {
                        if (imageView != null && imageView.get() != null) {
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    imageView.get().setImageBitmap(response);
                                }

                            });

                        }
                    }
                });
        httpConnection.setBitmap();
        httpConnection.send();
    }
}
