package com.fototimer;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.MediaStore;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pastas dentro de Pictures/FotoTimer. A pasta principal é "" (vazio).
 * O Android só "enxerga" pastas que têm fotos, então as vazias ficam lembradas nas preferências.
 */
final class Folders {
    private Folders() {}

    static final String ROOT = "Pictures/FotoTimer";
    static final String ROOT_LABEL = "FotoTimer (principal)";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("folders", Context.MODE_PRIVATE);
    }

    /** Caminho usado ao salvar: "Pictures/FotoTimer" ou "Pictures/FotoTimer/Nome". */
    static String path(String folder) {
        return folder == null || folder.isEmpty() ? ROOT : ROOT + "/" + folder;
    }

    /** Nome da pasta a partir do caminho do MediaStore ("Pictures/FotoTimer/Nome/" -> "Nome"). */
    static String fromPath(String relativePath) {
        if (relativePath == null) return "";
        String p = relativePath;
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (p.length() <= ROOT.length() || !p.startsWith(ROOT + "/")) return "";
        String rest = p.substring(ROOT.length() + 1);
        int slash = rest.indexOf('/');
        return slash >= 0 ? rest.substring(0, slash) : rest;
    }

    static String label(String folder) {
        return folder == null || folder.isEmpty() ? ROOT_LABEL : folder;
    }

    /** Tira caracteres que não podem estar em nome de pasta. Devolve "" se não sobrar nada. */
    static String clean(String name) {
        if (name == null) return "";
        String n = name.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", " ").replaceAll("\\s+", " ").trim();
        while (n.startsWith(".")) n = n.substring(1).trim();
        if (n.length() > 40) n = n.substring(0, 40).trim();
        return n;
    }

    /** Todas as pastas (sem a principal), em ordem alfabética. */
    static List<String> list(Context c) {
        Set<String> all = new HashSet<>(sp(c).getStringSet("names", new HashSet<>()));
        for (Photos.Photo p : Photos.list(c, 0, null)) if (!p.folder.isEmpty()) all.add(p.folder);
        List<String> out = new ArrayList<>(all);
        final Collator col = Collator.getInstance();
        out.sort(col::compare);
        return out;
    }

    static boolean exists(Context c, String name) {
        for (String f : list(c)) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    static void create(Context c, String name) {
        Set<String> names = new HashSet<>(sp(c).getStringSet("names", new HashSet<>()));
        names.add(name);
        sp(c).edit().putStringSet("names", names).apply();
    }

    private static void forget(Context c, String name) {
        Set<String> names = new HashSet<>(sp(c).getStringSet("names", new HashSet<>()));
        names.remove(name);
        sp(c).edit().putStringSet("names", names).apply();
    }

    /** Pasta onde as próximas fotos serão salvas ("" = principal). */
    static String destination(Context c) {
        String d = sp(c).getString("dest", "");
        if (!d.isEmpty() && !exists(c, d)) return "";
        return d;
    }

    static void setDestination(Context c, String folder) {
        sp(c).edit().putString("dest", folder == null ? "" : folder).apply();
    }

    static int count(Context c, String folder) {
        return Photos.list(c, 0, folder).size();
    }

    /** Move uma foto para outra pasta. Devolve false se o Android não deixar. */
    static boolean move(Context c, Uri photo, String toFolder) {
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.RELATIVE_PATH, path(toFolder));
            return c.getContentResolver().update(photo, v, null, null) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Renomeia: move todas as fotos para o novo nome. Devolve quantas NÃO puderam ser movidas. */
    static int rename(Context c, String from, String to) {
        int failed = 0;
        for (Photos.Photo p : Photos.list(c, 0, from)) if (!move(c, p.uri, to)) failed++;
        create(c, to);
        if (failed == 0) forget(c, from);
        if (from.equals(sp(c).getString("dest", ""))) setDestination(c, to);
        return failed;
    }

    /**
     * Apaga a pasta. deletePhotos = true apaga as fotos; false move para a pasta principal.
     * Devolve quantas fotos não puderam ser apagadas/movidas.
     */
    static int delete(Context c, String name, boolean deletePhotos) {
        int failed = 0;
        for (Photos.Photo p : Photos.list(c, 0, name)) {
            boolean ok;
            if (deletePhotos) {
                try { ok = c.getContentResolver().delete(p.uri, null, null) > 0; }
                catch (Exception e) { ok = false; }
            } else {
                ok = move(c, p.uri, "");
            }
            if (!ok) failed++;
        }
        if (failed == 0) forget(c, name);
        if (name.equals(sp(c).getString("dest", ""))) setDestination(c, "");
        return failed;
    }
}
