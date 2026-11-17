package com.fototimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Organizar pastas: criar, renomear, apagar e escolher onde as próximas fotos são salvas. */
public class FoldersActivity extends Activity {

    interface NameCallback { void on(String name); }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout h = Ui.row(this);
        h.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, "Voltar", v -> finish());
        ((LinearLayout.LayoutParams) back.getLayoutParams()).leftMargin = 0;
        h.addView(back);
        LinearLayout titles = Ui.col(this);
        titles.setPadding(Ui.dp(this, 14), 0, 0, 0);
        titles.addView(Ui.text(this, "Pastas", 22, Ui.TEXT, true));
        TextView sub = Ui.text(this, "Dentro de Pictures/FotoTimer", 12, Ui.TEXT2, false);
        sub.setPadding(0, Ui.dp(this, 4), 0, 0);
        titles.addView(sub);
        h.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(h);

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout c = Ui.col(this);
        int g = Ui.dp(this, 16);
        c.setPadding(g, Ui.dp(this, 4), g, Ui.dp(this, 16));
        list = Ui.col(this);
        c.addView(list);
        TextView hint = Ui.text(this, "Toque numa pasta para ver as fotos, salvar as próximas fotos nela, renomear ou apagar.", 12, Ui.TEXT3, false);
        hint.setLineSpacing(0, 1.25f);
        hint.setPadding(Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4), 0);
        c.addView(hint);
        scroll.addView(c);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = Ui.col(this);
        bar.setPadding(g, Ui.dp(this, 10), g, Ui.dp(this, 12));
        LinearLayout add = Ui.row(this);
        add.setGravity(Gravity.CENTER);
        add.setBackground(Ui.touch(Ui.round(Ui.ACCENT, Ui.dp(this, 29)), Ui.dp(this, 29)));
        add.addView(Ui.icon(this, R.drawable.ic_add, 0xFFFFFFFF, 22));
        TextView al = Ui.text(this, "Nova pasta", 17, 0xFFFFFFFF, true);
        al.setPadding(Ui.dp(this, 10), 0, 0, 0);
        add.addView(al);
        add.setOnClickListener(v -> askName(this, "Nova pasta", "", name -> {
            if (Folders.exists(this, name)) { toast("Já existe uma pasta com esse nome"); return; }
            Folders.create(this, name);
            reload();
        }));
        bar.addView(add, new LinearLayout.LayoutParams(-1, Ui.dp(this, 58)));
        root.addView(bar);

        setContentView(root);
        Ui.edgeToEdge(this, root);
    }

    @Override protected void onResume() { super.onResume(); reload(); }

    private void reload() {
        Photos.IO.execute(() -> {
            final List<String> names = Folders.list(this);
            final List<Integer> counts = new ArrayList<>();
            final int rootCount = Folders.count(this, "");
            for (String n : names) counts.add(Folders.count(this, n));
            final String dest = Folders.destination(this);
            ui.post(() -> {
                list.removeAllViews();
                list.addView(row("", rootCount, dest.isEmpty(), true));
                for (int i = 0; i < names.size(); i++)
                    list.addView(row(names.get(i), counts.get(i), names.get(i).equals(dest), false));
            });
        });
    }

    private View row(final String folder, final int count, boolean isDest, boolean isRoot) {
        LinearLayout r = Ui.row(this);
        int p = Ui.dp(this, 14);
        r.setPadding(p, p, p, p);
        r.setBackground(Ui.touch(Ui.round(Ui.CARD, Ui.dp(this, 18)), Ui.dp(this, 18)));
        r.setLayoutParams(Ui.lpTop(this, -1, -2, 10));

        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_folder);
        ic.setImageTintList(android.content.res.ColorStateList.valueOf(isDest ? Ui.ACCENT : Ui.TEXT2));
        int ip = Ui.dp(this, 11);
        ic.setPadding(ip, ip, ip, ip);
        ic.setBackground(Ui.round(isDest ? Ui.ACCENT_SOFT : Ui.CARD2, Ui.dp(this, 14)));
        r.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));

        LinearLayout t = Ui.col(this);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        TextView name = Ui.text(this, Folders.label(folder), 16, Ui.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(name);
        TextView s = Ui.text(this, count + (count == 1 ? " foto" : " fotos"), 12, Ui.TEXT2, false);
        s.setPadding(0, Ui.dp(this, 5), 0, 0);
        t.addView(s);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        if (isDest) {
            TextView chip = Ui.text(this, "Salvando aqui", 11, Ui.ACCENT, true);
            chip.setPadding(Ui.dp(this, 9), Ui.dp(this, 5), Ui.dp(this, 9), Ui.dp(this, 5));
            chip.setBackground(Ui.round(Ui.ACCENT_SOFT, Ui.dp(this, 10)));
            r.addView(chip);
        } else {
            r.addView(Ui.icon(this, R.drawable.ic_chevron, Ui.TEXT3, 20));
        }
        r.setOnClickListener(v -> options(folder, count, isDest, isRoot));
        return r;
    }

    private void options(final String folder, final int count, boolean isDest, boolean isRoot) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        labels.add("Ver fotos");
        actions.add(() -> startActivity(new Intent(this, GalleryActivity.class).putExtra("folder", folder)));
        if (!isDest) {
            labels.add("Salvar as próximas fotos aqui");
            actions.add(() -> { Folders.setDestination(this, folder); reload(); });
        }
        if (!isRoot) {
            labels.add("Renomear");
            actions.add(() -> askName(this, "Renomear pasta", folder, name -> {
                if (name.equals(folder)) return;
                if (Folders.exists(this, name)) { toast("Já existe uma pasta com esse nome"); return; }
                Photos.IO.execute(() -> {
                    int failed = Folders.rename(this, folder, name);
                    ui.post(() -> {
                        if (failed > 0) toast(failed + " fotos não puderam ser movidas e ficaram na pasta antiga");
                        reload();
                    });
                });
            }));
            labels.add("Apagar pasta");
            actions.add(() -> confirmDelete(folder, count));
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(Folders.label(folder))
                .setItems(labels.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .show();
    }

    private void confirmDelete(final String folder, int count) {
        if (CaptureService.running && folder.equals(Folders.destination(this))) {
            toast("Há uma sessão usando esta pasta. Pare a sessão antes de apagar.");
            return;
        }
        if (count == 0) {
            Folders.delete(this, folder, false);
            reload();
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Apagar \"" + folder + "\"?")
                .setMessage("A pasta tem " + count + (count == 1 ? " foto." : " fotos.") + " O que fazer com elas?")
                .setNeutralButton("Cancelar", null)
                .setNegativeButton("Mover para a principal", (d, w) -> runDelete(folder, false))
                .setPositiveButton("Apagar as fotos", (d, w) ->
                        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle("Apagar " + count + (count == 1 ? " foto?" : " fotos?"))
                                .setMessage("Não dá para desfazer.")
                                .setNegativeButton("Cancelar", null)
                                .setPositiveButton("Apagar", (d2, w2) -> runDelete(folder, true))
                                .show())
                .show();
    }

    private void runDelete(final String folder, final boolean deletePhotos) {
        Photos.IO.execute(() -> {
            int failed = Folders.delete(this, folder, deletePhotos);
            ui.post(() -> {
                if (failed > 0) toast(failed + " fotos não puderam ser " + (deletePhotos ? "apagadas" : "movidas") + ". A pasta foi mantida.");
                reload();
            });
        });
    }

    /** Pede um nome de pasta (usado aqui e na tela principal). */
    static void askName(Context c, String title, String current, NameCallback cb) {
        EditText e = new EditText(c);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setText(current);
        e.setSelectAllOnFocus(true);
        e.setHint("Ex.: Obra, Jardim, Viagem");
        e.setSingleLine(true);
        FrameLayout wrap = new FrameLayout(c);
        int p = Ui.dp(c, 22);
        wrap.setPadding(p, Ui.dp(c, 8), p, 0);
        wrap.addView(e);
        AlertDialog d = new AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(wrap)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (dd, w) -> {
                    String name = Folders.clean(e.getText().toString());
                    if (name.isEmpty()) Toast.makeText(c, "Digite um nome para a pasta", Toast.LENGTH_SHORT).show();
                    else cb.on(name);
                })
                .create();
        d.setOnShowListener(x -> {
            e.requestFocus();
            if (d.getWindow() != null) d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        });
        d.show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
