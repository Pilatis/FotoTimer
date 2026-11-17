package com.fototimer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Janela de dicas com atalhos para as configurações de bateria. */
final class TipsDialog {
    private TipsDialog() {}

    private static final String[][] TIPS = {
            {"Libere a bateria", "Deixe o FotoTimer \"Sem restrições\" de bateria. Sem isso, o Android pode pausar as fotos quando a tela apaga."},
            {"Tela apagada é normal", "Depois de iniciar, pode bloquear o celular ou sair do app. Para parar, use o botão Parar no app ou na notificação."},
            {"Sessões longas: na tomada", "Cada foto liga a câmera por ~2 s. Para centenas de fotos, deixe o celular carregando."},
            {"Celular firme e em pé", "Use um suporte ou tripé. As fotos saem na vertical (retrato)."},
            {"Espaço no celular", "Cada foto ocupa cerca de 3 a 5 MB. 100 fotos ≈ 400 MB."},
            {"Xiaomi, Samsung, Motorola…", "Algumas marcas têm economia extra: procure \"Início automático\", \"Apps em suspensão\" ou \"Economia de bateria\" e libere o FotoTimer."},
    };

    static boolean batteryOk(Activity a) {
        PowerManager pm = (PowerManager) a.getSystemService(Activity.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(a.getPackageName());
    }

    @SuppressLint("BatteryLife")
    static void requestBattery(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + a.getPackageName())));
        } catch (Exception e) {
            openAppSettings(a);
        }
    }

    static void openAppSettings(Activity a) {
        a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + a.getPackageName())));
    }

    static void show(Activity a) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = Ui.col(a);
        card.setBackground(Ui.round(Ui.CARD, Ui.dp(a, 24)));
        int p = Ui.dp(a, 22);
        card.setPadding(p, p, p, Ui.dp(a, 16));

        LinearLayout head = Ui.row(a);
        head.addView(Ui.icon(a, R.drawable.ic_help, Ui.ACCENT, 26));
        TextView title = Ui.text(a, "Dicas", 22, Ui.TEXT, true);
        title.setPadding(Ui.dp(a, 12), 0, 0, 0);
        head.addView(title);
        card.addView(head);

        // status da bateria
        boolean ok = batteryOk(a);
        LinearLayout status = Ui.row(a);
        int sp = Ui.dp(a, 12);
        status.setPadding(sp, sp, sp, sp);
        status.setBackground(Ui.round(ok ? 0x223DDC97 : Ui.AMBER_SOFT, Ui.dp(a, 14)));
        status.addView(Ui.icon(a, ok ? R.drawable.ic_check : R.drawable.ic_battery, ok ? Ui.GREEN : Ui.AMBER, 20));
        TextView st = Ui.text(a, ok ? "Bateria liberada: pronto para tela apagada"
                : "Bateria ainda otimizada: as fotos podem pausar", 13, Ui.TEXT, true);
        st.setPadding(Ui.dp(a, 10), 0, 0, 0);
        status.addView(st, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(status, Ui.lpTop(a, -1, -2, 16));

        ScrollView scroll = new ScrollView(a);
        LinearLayout list = Ui.col(a);
        for (int i = 0; i < TIPS.length; i++) list.addView(tip(a, i + 1, TIPS[i][0], TIPS[i][1]));
        scroll.addView(list);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
        slp.topMargin = Ui.dp(a, 6);
        card.addView(scroll, slp);

        // ações
        if (!ok) card.addView(button(a, "Liberar bateria agora", true, () -> { d.dismiss(); requestBattery(a); }), Ui.lpTop(a, -1, Ui.dp(a, 50), 14));
        card.addView(button(a, "Configurações do app", false, () -> { d.dismiss(); openAppSettings(a); }), Ui.lpTop(a, -1, Ui.dp(a, 50), 10));
        card.addView(button(a, "Fechar", false, d::dismiss), Ui.lpTop(a, -1, Ui.dp(a, 46), 4));

        d.setContentView(card);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(0));
        int maxH = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.85f);
        w.setLayout(a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 32), maxH);
        w.setGravity(Gravity.CENTER);
        d.show();
    }

    private static LinearLayout tip(Activity a, int n, String title, String body) {
        LinearLayout r = Ui.row(a);
        r.setGravity(Gravity.TOP);
        r.setPadding(0, Ui.dp(a, 14), 0, 0);
        TextView num = Ui.text(a, String.valueOf(n), 13, Ui.ACCENT, true);
        num.setGravity(Gravity.CENTER);
        num.setBackground(Ui.oval(Ui.ACCENT_SOFT));
        r.addView(num, new LinearLayout.LayoutParams(Ui.dp(a, 28), Ui.dp(a, 28)));
        LinearLayout t = Ui.col(a);
        t.setPadding(Ui.dp(a, 12), Ui.dp(a, 4), 0, 0);
        t.addView(Ui.text(a, title, 15, Ui.TEXT, true));
        TextView b = Ui.text(a, body, 13, Ui.TEXT2, false);
        b.setLineSpacing(0, 1.2f);
        b.setPadding(0, Ui.dp(a, 5), 0, 0);
        t.addView(b);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return r;
    }

    private static TextView button(Activity a, String label, boolean primary, Runnable r) {
        TextView b = Ui.text(a, label, 15, primary ? 0xFFFFFFFF : Ui.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.touch(primary ? Ui.round(Ui.ACCENT, Ui.dp(a, 25)) : Ui.round(Ui.CARD2, Ui.dp(a, 25)), Ui.dp(a, 25)));
        if (label.equals("Fechar")) b.setBackground(Ui.touch(Ui.round(0, Ui.dp(a, 25)), Ui.dp(a, 25)));
        b.setOnClickListener(v -> r.run());
        return b;
    }
}
