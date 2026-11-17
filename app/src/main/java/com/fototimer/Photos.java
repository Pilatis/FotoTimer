package com.fototimer;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lê as fotos do FotoTimer (Pictures/FotoTimer) e gera miniaturas. */
final class Photos {
    private Photos() {}

    static final class Photo {
        final Uri uri;
        final long takenAt;
        final String folder; // "" = pasta principal
        Photo(Uri u, long t, String f) { uri = u; takenAt = t; folder = f; }
    }

    static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    /** folder: null = todas as pastas, "" = só a principal, "Nome" = só essa pasta. */
    static List<Photo> list(Context c, int limit, String folder) {
        List<Photo> out = new ArrayList<>();
        String[] cols = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.RELATIVE_PATH };
        String sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
        String[] args = { "Pictures/FotoTimer%" };
        String order = MediaStore.Images.Media.DATE_ADDED + " DESC, " + MediaStore.Images.Media._ID + " DESC";
        try (Cursor cur = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols, sel, args, order)) {
            if (cur == null) return out;
            while (cur.moveToNext() && (limit <= 0 || out.size() < limit)) {
                long id = cur.getLong(0);
                long t = cur.isNull(1) ? cur.getLong(2) * 1000L : cur.getLong(1);
                String f = Folders.fromPath(cur.getString(3));
                if (folder != null && !folder.equals(f)) continue;
                out.add(new Photo(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), t, f));
            }
        } catch (Exception ignored) {}
        return out;
    }

    static Bitmap cached(Uri u, int px) { return CACHE.get(u + "@" + px); }

    static Bitmap thumb(Context c, Uri u, int px) {
        String key = u + "@" + px;
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        try {
            b = c.getContentResolver().loadThumbnail(u, new Size(px, px), null);
            if (b != null) CACHE.put(key, b);
        } catch (Exception ignored) {}
        return b;
    }

    static String time(long t) {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(t));
    }

    /** "Hoje", "Ontem" ou "12/11/2026". */
    static String day(long t) {
        Calendar now = Calendar.getInstance(), d = Calendar.getInstance();
        d.setTimeInMillis(t);
        if (same(now, d)) return "Hoje";
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (same(now, d)) return "Ontem";
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(t));
    }

    static String full(long t) { return day(t) + " às " + time(t); }

    private static boolean same(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
