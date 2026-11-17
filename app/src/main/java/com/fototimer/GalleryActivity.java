package com.fototimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Galeria interna: miniaturas das fotos do FotoTimer com data e hora. */
public class GalleryActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Photos.Photo> photos = new ArrayList<>();
    private Adapter adapter;
    private TextView subtitle;
    private View empty;
    private GridView grid;
    private int thumbPx;
    private LinearLayout chips;
    private String filter = null; // null = todas, "" = principal, "Nome" = pasta
    private List<String> folderNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);

        // barra superior
        LinearLayout h = Ui.row(this);
        h.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, "Voltar", v -> finish());
        ((LinearLayout.LayoutParams) back.getLayoutParams()).leftMargin = 0;
        h.addView(back);
        LinearLayout titles = Ui.col(this);
        titles.setPadding(Ui.dp(this, 14), 0, 0, 0);
        titles.addView(Ui.text(this, "Fotos", 22, Ui.TEXT, true));
        subtitle = Ui.text(this, "", 12, Ui.TEXT2, false);
        subtitle.setPadding(0, Ui.dp(this, 4), 0, 0);
        titles.addView(subtitle);
        h.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        h.addView(Ui.iconButton(this, R.drawable.ic_folder, "Gerenciar pastas", v ->
                startActivity(new Intent(this, FoldersActivity.class))));
        h.addView(Ui.iconButton(this, R.drawable.ic_open, "Abrir na galeria do celular", v -> openExternal()));
        root.addView(h);

        // filtro por pasta
        String start = getIntent().getStringExtra("folder");
        if (start != null) filter = start;
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chips = Ui.row(this);
        chips.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 12));
        hs.addView(chips);
        root.addView(hs);

        FrameLayout body = new FrameLayout(this);
        grid = new GridView(this);
        grid.setNumColumns(3);
        int sp = Ui.dp(this, 4);
        grid.setHorizontalSpacing(sp);
        grid.setVerticalSpacing(sp);
        grid.setPadding(sp, 0, sp, sp);
        grid.setClipToPadding(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> view(photos.get(pos).uri));
        grid.setOnItemLongClickListener((p, v, pos, id) -> { options(photos.get(pos)); return true; });
        body.addView(grid, new FrameLayout.LayoutParams(-1, -1));

        empty = emptyState();
        body.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        Ui.edgeToEdge(this, root);
        thumbPx = getResources().getDisplayMetrics().widthPixels / 3;
    }

    private View emptyState() {
        LinearLayout e = Ui.col(this);
        e.setGravity(Gravity.CENTER);
        int p = Ui.dp(this, 40);
        e.setPadding(p, 0, p, p * 2);
        ImageView i = Ui.icon(this, R.drawable.ic_gallery, Ui.TEXT3, 56);
        e.addView(i);
        TextView t = Ui.text(this, "Nenhuma foto ainda", 18, Ui.TEXT, true);
        t.setPadding(0, Ui.dp(this, 18), 0, 0);
        e.addView(t);
        TextView d = Ui.text(this, "As fotos tiradas pelo FotoTimer aparecem aqui, com a data e a hora de cada uma.", 14, Ui.TEXT2, false);
        d.setGravity(Gravity.CENTER);
        d.setLineSpacing(0, 1.2f);
        d.setPadding(0, Ui.dp(this, 8), 0, 0);
        e.addView(d);
        e.setVisibility(View.GONE);
        return e;
    }

    @Override protected void onResume() { super.onResume(); load(); }

    private void load() {
        Photos.IO.execute(() -> {
            final List<String> names = Folders.list(this);
            if (filter != null && !filter.isEmpty() && !names.contains(filter)) filter = null; // pasta apagada
            final String f = filter;
            List<Photos.Photo> l = Photos.list(this, 0, f);
            ui.post(() -> {
                folderNames = names;
                buildChips(names);
                photos.clear();
                photos.addAll(l);
                adapter.notifyDataSetChanged();
                int n = photos.size();
                String where = f == null ? "todas as pastas" : Folders.label(f);
                subtitle.setText(n == 0 ? where : n + (n == 1 ? " foto" : " fotos") + " · " + where);
                empty.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void buildChips(List<String> names) {
        chips.removeAllViews();
        chips.addView(chip("Todas", null));
        chips.addView(chip("Principal", ""));
        for (String n : names) chips.addView(chip(n, n));
    }

    private View chip(String label, final String value) {
        boolean on = value == null ? filter == null : value.equals(filter);
        TextView t = Ui.text(this, label, 13, on ? Ui.ACCENT : Ui.TEXT2, true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        int ph = Ui.dp(this, 14);
        t.setPadding(ph, 0, ph, 0);
        t.setBackground(Ui.touch(on
                ? Ui.roundStroke(Ui.ACCENT_SOFT, Ui.ACCENT, Ui.dp(this, 17), Ui.dp(this, 1))
                : Ui.roundStroke(0, Ui.STROKE, Ui.dp(this, 17), Ui.dp(this, 1)), Ui.dp(this, 17)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(this, 34));
        lp.leftMargin = Ui.dp(this, 4);
        lp.rightMargin = Ui.dp(this, 4);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> { filter = value; load(); });
        return t;
    }

    private void moveDialog(final Photos.Photo p) {
        final List<String> targets = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        if (!p.folder.isEmpty()) { targets.add(""); labels.add(Folders.ROOT_LABEL); }
        for (String n : folderNames) if (!n.equals(p.folder)) { targets.add(n); labels.add(n); }
        labels.add("Nova pasta…");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Mover para")
                .setItems(labels.toArray(new String[0]), (d, w) -> {
                    if (w == targets.size()) {
                        FoldersActivity.askName(this, "Nova pasta", "", name -> {
                            Folders.create(this, name);
                            doMove(p, name);
                        });
                    } else {
                        doMove(p, targets.get(w));
                    }
                })
                .show();
    }

    private void doMove(Photos.Photo p, String to) {
        if (Folders.move(this, p.uri, to)) {
            Toast.makeText(this, "Movida para " + Folders.label(to), Toast.LENGTH_SHORT).show();
            load();
        } else {
            Toast.makeText(this, "Não foi possível mover esta foto", Toast.LENGTH_LONG).show();
        }
    }

    private void view(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(u, "image/jpeg")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Exception e) {
            Toast.makeText(this, "Nenhum app para abrir imagens", Toast.LENGTH_SHORT).show();
        }
    }

    private void openExternal() {
        if (photos.isEmpty()) {
            Toast.makeText(this, "Ainda não há fotos", Toast.LENGTH_SHORT).show();
            return;
        }
        view(photos.get(0).uri);
    }

    private void options(Photos.Photo p) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(Photos.full(p.takenAt))
                .setItems(new String[]{"Abrir", "Compartilhar", "Mover para outra pasta", "Apagar"}, (d, which) -> {
                    if (which == 0) view(p.uri);
                    else if (which == 1) share(p.uri);
                    else if (which == 2) moveDialog(p);
                    else confirmDelete(p);
                })
                .show();
    }

    private void share(Uri u) {
        Intent s = new Intent(Intent.ACTION_SEND).setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(s, "Compartilhar foto"));
    }

    private void confirmDelete(Photos.Photo p) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Apagar esta foto?")
                .setMessage("Não dá para desfazer.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Apagar", (d, w) -> {
                    try {
                        getContentResolver().delete(p.uri, null, null);
                        load();
                    } catch (Exception e) {
                        Toast.makeText(this, "Não foi possível apagar (apague pela Galeria do celular)", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    // ---------------- grade ----------------

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return photos.size(); }
        @Override public Object getItem(int i) { return photos.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            Holder h;
            if (convert == null) {
                SquareFrame f = new SquareFrame(GalleryActivity.this);
                f.setBackground(Ui.round(Ui.CARD, Ui.dp(GalleryActivity.this, 6)));
                f.setClipToOutline(true);
                h = new Holder();
                h.img = new ImageView(GalleryActivity.this);
                h.img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                f.addView(h.img, new FrameLayout.LayoutParams(-1, -1));

                LinearLayout cap = Ui.col(GalleryActivity.this);
                int p = Ui.dp(GalleryActivity.this, 7);
                cap.setPadding(p, Ui.dp(GalleryActivity.this, 18), p, p);
                cap.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                        new int[]{0xCC000000, 0x00000000}));
                h.time = Ui.text(GalleryActivity.this, "", 13, 0xFFFFFFFF, true);
                h.date = Ui.text(GalleryActivity.this, "", 10, 0xCCFFFFFF, false);
                h.date.setPadding(0, Ui.dp(GalleryActivity.this, 3), 0, 0);
                cap.addView(h.time);
                cap.addView(h.date);
                f.addView(cap, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
                f.setTag(h);
                convert = f;
            } else {
                h = (Holder) convert.getTag();
            }
            Photos.Photo p = photos.get(pos);
            h.uri = p.uri;
            h.time.setText(Photos.time(p.takenAt));
            h.date.setText(Photos.day(p.takenAt));
            Bitmap c = Photos.cached(p.uri, thumbPx);
            h.img.setImageBitmap(c);
            if (c == null) {
                final Holder hh = h;
                final Uri u = p.uri;
                Photos.IO.execute(() -> {
                    Bitmap b = Photos.thumb(GalleryActivity.this, u, thumbPx);
                    ui.post(() -> { if (u.equals(hh.uri)) hh.img.setImageBitmap(b); });
                });
            }
            return convert;
        }
    }

    private static class Holder {
        ImageView img;
        TextView time, date;
        Uri uri;
    }
}
