package com.fototimer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int MAX_COUNT = 99999;
    private static final int MAX_INTERVAL = 24 * 3600;
    private static final long MB_PER_PHOTO = 4;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    // estado
    private RingView ring;
    private TextView bigNumber, bigSub, stateChip;
    private TextView stat1Label, stat1, stat2Label, stat2, stat3Label, stat3;
    private TextView summary;

    // configurações
    private LinearLayout settingsCard, countBlock;
    private Segmented mode, cameraSeg, whenSeg;
    private LinearLayout schedBlock;
    private TextView schedValue, folderValue;
    private long schedAt = 0;
    private Stepper countStep, intervalStep;
    private ChipRow countChips, intervalChips;
    private Switch stampSwitch;

    // bateria / última foto / botão
    private LinearLayout batteryBanner, lastCard;
    private ImageView lastThumb;
    private TextView lastTime, lastCount;
    private LinearLayout mainButton;
    private ImageView mainIcon;
    private TextView mainLabel;

    private Uri shownLast;
    private boolean lastRunning;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            render();
            ui.postDelayed(this, 250);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);

        root.addView(header());

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = Ui.col(this);
        int g = Ui.dp(this, 16);
        content.setPadding(g, Ui.dp(this, 4), g, Ui.dp(this, 16));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        content.addView(batteryBanner());
        content.addView(statusCard());
        content.addView(lastPhotoCard());
        content.addView(sectionTitle("Configurações"));
        content.addView(settingsCard());

        root.addView(bottomBar());

        setContentView(root);
        Ui.edgeToEdge(this, root);
        loadPrefs();
    }

    // ------------------------------------------------------------ construção da tela

    private View header() {
        LinearLayout h = Ui.row(this);
        int p = Ui.dp(this, 20);
        h.setPadding(p, Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 10));
        LinearLayout titles = Ui.col(this);
        titles.addView(Ui.text(this, "FotoTimer", 26, Ui.TEXT, true));
        TextView sub = Ui.text(this, "Fotos automáticas em intervalo", 13, Ui.TEXT2, false);
        sub.setPadding(0, Ui.dp(this, 4), 0, 0);
        titles.addView(sub);
        h.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        h.addView(Ui.iconButton(this, R.drawable.ic_help, "Dicas", v -> TipsDialog.show(this)));
        h.addView(Ui.iconButton(this, R.drawable.ic_gallery, "Ver fotos", v -> openGallery()));
        return h;
    }

    private View batteryBanner() {
        batteryBanner = Ui.row(this);
        int p = Ui.dp(this, 14);
        batteryBanner.setPadding(p, p, p, p);
        batteryBanner.setBackground(Ui.roundStroke(Ui.AMBER_SOFT, 0x55FFB547, Ui.dp(this, 16), Ui.dp(this, 1)));
        batteryBanner.addView(Ui.icon(this, R.drawable.ic_battery, Ui.AMBER, 22));
        LinearLayout t = Ui.col(this);
        t.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        t.addView(Ui.text(this, "Otimização de bateria ativa", 14, Ui.TEXT, true));
        TextView d = Ui.text(this, "O Android pode pausar as fotos com a tela apagada.", 12, Ui.TEXT2, false);
        d.setPadding(0, Ui.dp(this, 4), 0, 0);
        d.setLineSpacing(0, 1.15f);
        t.addView(d);
        batteryBanner.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView fix = Ui.text(this, "Corrigir", 13, 0xFF1A1300, true);
        fix.setGravity(Gravity.CENTER);
        fix.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        fix.setBackground(Ui.touch(Ui.round(Ui.AMBER, Ui.dp(this, 16)), Ui.dp(this, 16)));
        fix.setOnClickListener(v -> TipsDialog.requestBattery(this));
        batteryBanner.addView(fix, new LinearLayout.LayoutParams(-2, Ui.dp(this, 34)));
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.bottomMargin = Ui.dp(this, 12);
        batteryBanner.setLayoutParams(lp);
        return batteryBanner;
    }

    private View statusCard() {
        LinearLayout card = Ui.card(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout top = Ui.row(this);
        top.addView(Ui.text(this, "Status", 13, Ui.TEXT2, true));
        top.addView(Ui.spacer(this));
        stateChip = Ui.text(this, "Pronto", 12, Ui.TEXT, true);
        stateChip.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        top.addView(stateChip);
        card.addView(top, Ui.lp(-1, -2));

        FrameLayout ringFrame = new FrameLayout(this);
        ring = new RingView(this);
        ringFrame.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout center = Ui.col(this);
        center.setGravity(Gravity.CENTER);
        bigNumber = Ui.text(this, "0", 52, Ui.TEXT, true);
        bigNumber.setGravity(Gravity.CENTER);
        bigSub = Ui.text(this, "", 14, Ui.TEXT2, false);
        bigSub.setGravity(Gravity.CENTER);
        bigSub.setPadding(0, Ui.dp(this, 6), 0, 0);
        center.addView(bigNumber);
        center.addView(bigSub);
        ringFrame.addView(center, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        int s = Ui.dp(this, 210);
        card.addView(ringFrame, Ui.lpTop(this, s, s, 14));

        LinearLayout stats = Ui.row(this);
        stats.setBaselineAligned(false);
        TextView[] a = stat(stats), bb = stat(stats), c = stat(stats);
        stat1Label = a[0]; stat1 = a[1]; stat2Label = bb[0]; stat2 = bb[1]; stat3Label = c[0]; stat3 = c[1];
        card.addView(stats, Ui.lpTop(this, -1, -2, 18));

        summary = Ui.text(this, "", 12, Ui.TEXT3, false);
        summary.setGravity(Gravity.CENTER);
        card.addView(summary, Ui.lpTop(this, -1, -2, 14));
        return card;
    }

    private TextView[] stat(LinearLayout parent) {
        LinearLayout box = Ui.col(this);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 10);
        box.setPadding(Ui.dp(this, 4), p, Ui.dp(this, 4), p);
        box.setBackground(Ui.round(Ui.CARD2, Ui.dp(this, 14)));
        TextView label = Ui.text(this, "", 11, Ui.TEXT2, false);
        TextView value = Ui.text(this, "—", 16, Ui.TEXT, true);
        value.setPadding(0, Ui.dp(this, 6), 0, 0);
        box.addView(label);
        box.addView(value);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        if (parent.getChildCount() > 0) lp.leftMargin = Ui.dp(this, 8);
        parent.addView(box, lp);
        return new TextView[]{label, value};
    }

    private View lastPhotoCard() {
        lastCard = Ui.row(this);
        int p = Ui.dp(this, 12);
        lastCard.setPadding(p, p, Ui.dp(this, 16), p);
        lastCard.setBackground(Ui.touch(Ui.round(Ui.CARD, Ui.dp(this, 20)), Ui.dp(this, 20)));
        lastCard.setOnClickListener(v -> openGallery());

        lastThumb = new ImageView(this);
        lastThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        lastThumb.setBackground(Ui.round(Ui.CARD2, Ui.dp(this, 12)));
        final int r = Ui.dp(this, 12);
        lastThumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        lastThumb.setClipToOutline(true);
        lastCard.addView(lastThumb, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));

        LinearLayout t = Ui.col(this);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        t.addView(Ui.text(this, "Última foto", 12, Ui.TEXT2, false));
        lastTime = Ui.text(this, "", 15, Ui.TEXT, true);
        lastTime.setPadding(0, Ui.dp(this, 5), 0, 0);
        t.addView(lastTime);
        lastCard.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        lastCount = Ui.text(this, "Ver fotos", 13, Ui.ACCENT, true);
        lastCard.addView(lastCount);
        lastCard.addView(Ui.icon(this, R.drawable.ic_chevron, Ui.ACCENT, 20));

        lastCard.setLayoutParams(Ui.lpTop(this, -1, -2, 12));
        lastCard.setVisibility(View.GONE);
        return lastCard;
    }

    private TextView sectionTitle(String s) {
        TextView t = Ui.text(this, s, 13, Ui.TEXT2, true);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 22), 0, Ui.dp(this, 10));
        return t;
    }

    private View settingsCard() {
        settingsCard = Ui.card(this);

        // quando começar
        settingsCard.addView(label("Início", null));
        whenSeg = new Segmented(this, "Agora", "Agendar");
        whenSeg.setListener(i -> {
            if (i == 1 && schedAt < System.currentTimeMillis() + 60_000) pickSchedule();
            saveAndRefresh();
        });
        settingsCard.addView(whenSeg, Ui.lpTop(this, -1, -2, 10));
        schedBlock = Ui.row(this);
        int sp = Ui.dp(this, 14);
        schedBlock.setPadding(sp, sp, sp, sp);
        schedBlock.setBackground(Ui.touch(Ui.round(Ui.CARD2, Ui.dp(this, 14)), Ui.dp(this, 14)));
        LinearLayout st = Ui.col(this);
        st.addView(Ui.text(this, "Começar em", 12, Ui.TEXT2, false));
        schedValue = Ui.text(this, "", 16, Ui.TEXT, true);
        schedValue.setPadding(0, Ui.dp(this, 5), 0, 0);
        st.addView(schedValue);
        schedBlock.addView(st, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView change = Ui.text(this, "Alterar", 13, Ui.ACCENT, true);
        schedBlock.addView(change);
        schedBlock.setOnClickListener(v -> { if (v.isEnabled()) pickSchedule(); });
        settingsCard.addView(schedBlock, Ui.lpTop(this, -1, -2, 10));

        // pasta de destino
        settingsCard.addView(Ui.divider(this));
        LinearLayout folderRow = Ui.row(this);
        folderRow.addView(label("Salvar em", "Pasta dentro de Pictures/FotoTimer"), new LinearLayout.LayoutParams(0, -2, 1f));
        folderValue = Ui.text(this, "", 14, Ui.ACCENT, true);
        folderValue.setSingleLine(true);
        folderValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        folderValue.setMaxWidth(Ui.dp(this, 150));
        folderValue.setPadding(Ui.dp(this, 10), 0, 0, 0);
        folderRow.addView(folderValue);
        folderRow.addView(Ui.icon(this, R.drawable.ic_chevron, Ui.ACCENT, 20));
        folderRow.setOnClickListener(v -> { if (v.isEnabled()) pickFolder(); });
        settingsCard.addView(folderRow);

        // modo
        settingsCard.addView(Ui.divider(this));
        settingsCard.addView(label("Modo", null));
        mode = new Segmented(this, "Quantidade fixa", "Sem limite");
        mode.setListener(i -> { saveAndRefresh(); });
        settingsCard.addView(mode, Ui.lpTop(this, -1, -2, 10));

        // quantidade
        countBlock = Ui.col(this);
        countBlock.addView(Ui.divider(this));
        countStep = new Stepper(this, 1, MAX_COUNT, v -> String.valueOf(v),
                (v, d) -> v + d * (v < 20 ? 1 : v < 100 ? 5 : v < 1000 ? 10 : 100));
        countStep.setListener(v -> saveAndRefresh());
        countStep.setOnValueClick(v -> askNumber("Quantidade de fotos", "Ex.: 100", countStep.get(), 1, MAX_COUNT,
                n -> { countStep.set(n); saveAndRefresh(); }));
        countBlock.addView(labeledRow("Quantidade de fotos", "Quantas fotos tirar", countStep));
        countChips = new ChipRow(this, new int[]{10, 50, 100, 200, 500, 1000},
                new String[]{"10", "50", "100", "200", "500", "1000"}, v -> { countStep.set(v); saveAndRefresh(); });
        countBlock.addView(countChips, Ui.lpTop(this, -1, -2, 12));
        settingsCard.addView(countBlock);

        // intervalo
        settingsCard.addView(Ui.divider(this));
        intervalStep = new Stepper(this, 3, MAX_INTERVAL, Ui::interval, MainActivity::intervalStep);
        intervalStep.setListener(v -> saveAndRefresh());
        intervalStep.setOnValueClick(v -> askNumber("Intervalo em segundos", "Mínimo 3", intervalStep.get(), 3, MAX_INTERVAL,
                n -> { intervalStep.set(n); saveAndRefresh(); }));
        settingsCard.addView(labeledRow("Intervalo", "Tempo entre cada foto", intervalStep));
        intervalChips = new ChipRow(this, new int[]{5, 10, 30, 60, 300, 600, 1800},
                new String[]{"5 s", "10 s", "30 s", "1 min", "5 min", "10 min", "30 min"},
                v -> { intervalStep.set(v); saveAndRefresh(); });
        settingsCard.addView(intervalChips, Ui.lpTop(this, -1, -2, 12));

        // câmera
        settingsCard.addView(Ui.divider(this));
        settingsCard.addView(label("Câmera", null));
        cameraSeg = new Segmented(this, "Traseira", "Frontal");
        cameraSeg.setListener(i -> saveAndRefresh());
        settingsCard.addView(cameraSeg, Ui.lpTop(this, -1, -2, 10));

        // carimbo de data/hora
        settingsCard.addView(Ui.divider(this));
        stampSwitch = new Switch(this);
        stampSwitch.setThumbTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0xFFFFFFFF, 0xFFB8BFCC}));
        stampSwitch.setTrackTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.ACCENT, Ui.STROKE}));
        stampSwitch.setOnCheckedChangeListener((v, c) -> saveAndRefresh());
        View stampRow = labeledRow("Data e hora na foto", "Grava no canto da imagem, ex.: 12/11/2026  14:32:05", stampSwitch);
        stampRow.setOnClickListener(v -> { if (stampSwitch.isEnabled()) stampSwitch.toggle(); });
        settingsCard.addView(stampRow);
        return settingsCard;
    }

    private static int intervalStep(int v, int d) {
        if (d > 0) {
            if (v < 10) return v + 1;
            if (v < 60) return v + 5;
            if (v < 600) return v + 30;
            if (v < 3600) return v + 300;
            return v + 1800;
        } else {
            if (v <= 10) return v - 1;
            if (v <= 60) return v - 5;
            if (v <= 600) return v - 30;
            if (v <= 3600) return v - 300;
            return v - 1800;
        }
    }

    private View label(String title, String sub) {
        LinearLayout l = Ui.col(this);
        l.addView(Ui.text(this, title, 15, Ui.TEXT, true));
        if (sub != null) {
            TextView s = Ui.text(this, sub, 12, Ui.TEXT2, false);
            s.setPadding(0, Ui.dp(this, 4), 0, 0);
            s.setLineSpacing(0, 1.15f);
            l.addView(s);
        }
        return l;
    }

    private View labeledRow(String title, String sub, View control) {
        LinearLayout r = Ui.row(this);
        r.addView(label(title, sub), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(control);
        return r;
    }

    private View bottomBar() {
        LinearLayout bar = Ui.col(this);
        int g = Ui.dp(this, 16);
        bar.setPadding(g, Ui.dp(this, 10), g, Ui.dp(this, 12));
        bar.setBackgroundColor(Ui.BG);

        mainButton = Ui.row(this);
        mainButton.setGravity(Gravity.CENTER);
        mainIcon = Ui.icon(this, R.drawable.ic_play, 0xFFFFFFFF, 24);
        mainLabel = Ui.text(this, "Iniciar", 17, 0xFFFFFFFF, true);
        mainLabel.setPadding(Ui.dp(this, 10), 0, 0, 0);
        mainButton.addView(mainIcon);
        mainButton.addView(mainLabel);
        mainButton.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            if (CaptureService.running) confirmStop(); else start();
        });
        bar.addView(mainButton, new LinearLayout.LayoutParams(-1, Ui.dp(this, 58)));
        return bar;
    }

    // ------------------------------------------------------------ estado / atualização

    private void loadPrefs() {
        schedAt = prefs.getLong("schedAt", 0);
        boolean sched = prefs.getBoolean("scheduled", false) && schedAt > System.currentTimeMillis();
        whenSeg.select(sched ? 1 : 0);
        mode.select(prefs.getBoolean("unlimited", false) ? 1 : 0);
        countStep.set(prefs.getInt("count", 100));
        intervalStep.set(prefs.getInt("interval", 30));
        cameraSeg.select(prefs.getBoolean("front", false) ? 1 : 0);
        stampSwitch.setChecked(prefs.getBoolean("stamp", true));
        saveAndRefresh();
    }

    private void saveAndRefresh() {
        if (prefs == null || stampSwitch == null) return;
        prefs.edit()
                .putBoolean("unlimited", mode.selected() == 1)
                .putInt("count", countStep.get())
                .putInt("interval", intervalStep.get())
                .putBoolean("front", cameraSeg.selected() == 1)
                .putBoolean("stamp", stampSwitch.isChecked())
                .putBoolean("scheduled", whenSeg.selected() == 1)
                .putLong("schedAt", schedAt)
                .apply();
        schedBlock.setVisibility(whenSeg.selected() == 1 ? View.VISIBLE : View.GONE);
        schedValue.setText(schedAt > 0 ? whenText(schedAt) : "Escolher data e hora");
        folderValue.setText(Folders.label(Folders.destination(this)));
        countBlock.setVisibility(mode.selected() == 1 ? View.GONE : View.VISIBLE);
        countChips.highlight(countStep.get());
        intervalChips.highlight(intervalStep.get());
        render();
    }

    private void render() {
        if (ring == null) return;
        boolean running = CaptureService.running;
        if (running != lastRunning) { lastRunning = running; applyRunningState(running); }
        boolean waiting = running && CaptureService.waiting;
        boolean wantSchedule = whenSeg.selected() == 1;
        mainLabel.setText(waiting ? "Cancelar agendamento" : running ? "Parar" : wantSchedule ? "Agendar" : "Iniciar");

        if (waiting) {
            int total = CaptureService.total, iv = CaptureService.intervalSec;
            long left = Math.max(0, CaptureService.scheduledWall - System.currentTimeMillis());
            ring.set(0f, Ui.ACCENT);
            chip("Agendado", Ui.ACCENT);
            bigNumber.setText(Ui.duration((left + 999) / 1000));
            bigSub.setText("para começar");
            stat1Label.setText("Começa");
            stat1.setText(whenShort(CaptureService.scheduledWall));
            stat2Label.setText("Fotos");
            stat2.setText(total > 0 ? String.valueOf(total) : "sem limite");
            stat3Label.setText("Intervalo");
            stat3.setText(Ui.interval(iv));
            summary.setText("Pode apagar a tela ou sair do app. Não desligue o celular nem feche o FotoTimer nos apps recentes.");
            summary.setTextColor(Ui.TEXT3);
        } else if (running) {
            int total = CaptureService.total, taken = CaptureService.taken, iv = CaptureService.intervalSec;
            long now = SystemClock.elapsedRealtime();
            long left = CaptureService.nextShotAt - now;
            boolean shooting = CaptureService.shooting || left <= 0;

            bigNumber.setText(String.valueOf(taken));
            bigSub.setText(total > 0 ? "de " + total + " fotos" : (taken == 1 ? "foto · sem limite" : "fotos · sem limite"));
            ring.set(shooting ? 1f : 1f - (float) left / (iv * 1000f), shooting ? Ui.GREEN : Ui.ACCENT);

            boolean blocked = CaptureService.consecutiveFails >= 3;
            chip(blocked ? "Falhando" : shooting ? "Fotografando" : "Em andamento", blocked ? Ui.RED : Ui.GREEN);

            stat1Label.setText("Próxima foto");
            stat1.setText(shooting ? "agora" : Ui.duration((left + 999) / 1000));
            stat2Label.setText("Decorrido");
            stat2.setText(Ui.duration((now - CaptureService.startedAt) / 1000));
            stat3Label.setText(total > 0 ? "Termina às" : "Intervalo");
            if (total > 0) {
                long endIn = CaptureService.startedAt + (long) (total - 1) * iv * 1000L - now;
                stat3.setText(clock(System.currentTimeMillis() + Math.max(0, endIn)));
            } else {
                stat3.setText(Ui.interval(iv));
            }
            String fails = CaptureService.failed > 0 ? CaptureService.failed + " falharam" : "";
            if (blocked) fails = "A câmera não está respondendo. Veja as Dicas para liberar o app em segundo plano.";
            summary.setText(fails.isEmpty() ? "Pode apagar a tela ou sair do app — a captura continua." : fails);
            summary.setTextColor(blocked ? Ui.RED : Ui.TEXT3);
        } else {
            boolean unlimited = mode.selected() == 1;
            int count = countStep.get(), iv = intervalStep.get();
            ring.set(0f, Ui.ACCENT);
            chip("Pronto", Ui.TEXT2);
            bigNumber.setText(unlimited ? "∞" : String.valueOf(count));
            bigSub.setText((unlimited ? "fotos" : (count == 1 ? "foto" : "fotos")) + " · a cada " + Ui.interval(iv));

            if (unlimited) {
                stat1Label.setText("Fotos por hora");
                stat1.setText(String.valueOf(3600 / iv));
                stat2Label.setText("Espaço / hora");
                stat2.setText("~" + Ui.size(3600L / iv * MB_PER_PHOTO));
                stat3Label.setText("Termina");
                stat3.setText("ao parar");
            } else {
                long secs = (long) (count - 1) * iv;
                stat1Label.setText("Duração");
                stat1.setText(Ui.durationWords(secs));
                stat2Label.setText("Espaço");
                stat2.setText("~" + Ui.size(count * MB_PER_PHOTO));
                stat3Label.setText("Termina às");
                stat3.setText(clock(System.currentTimeMillis() + secs * 1000L));
            }
            if (wantSchedule && schedAt > 0) {
                chip("Vai agendar", Ui.TEXT2);
                stat3Label.setText("Começa");
                stat3.setText(whenShort(schedAt));
            }
            String last = CaptureService.lastSummary;
            summary.setText(last != null ? "Última sessão: " + last
                    : wantSchedule ? "Toque em Agendar. O app espera a hora marcada, mesmo com a tela apagada."
                    : "Toque em Iniciar. A tela pode ser apagada depois.");
            summary.setTextColor(Ui.TEXT3);
        }

        Uri u = CaptureService.lastUri;
        if (u != null && !u.equals(shownLast)) showLast(u, CaptureService.lastShotWall);
    }

    private void applyRunningState(boolean running) {
        mainButton.setBackground(Ui.touch(Ui.round(running ? Ui.RED : Ui.ACCENT, Ui.dp(this, 29)), Ui.dp(this, 29)));
        mainIcon.setImageResource(running ? R.drawable.ic_stop : R.drawable.ic_play);
        setEnabledDeep(settingsCard, !running);
        settingsCard.animate().alpha(running ? 0.45f : 1f).setDuration(200).start();
        if (!running) refreshLastFromStore();
    }

    private void setEnabledDeep(View v, boolean e) {
        v.setEnabled(e);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) setEnabledDeep(g.getChildAt(i), e);
        }
    }

    private void chip(String text, int color) {
        stateChip.setText(text);
        stateChip.setTextColor(color);
        stateChip.setBackground(Ui.round((color & 0x00FFFFFF) | 0x26000000, Ui.dp(this, 12)));
    }

    private static String clock(long wall) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(wall));
    }

    private void refreshLastFromStore() {
        Photos.IO.execute(() -> {
            List<Photos.Photo> l = Photos.list(this, 1, null);
            ui.post(() -> {
                if (l.isEmpty()) { lastCard.setVisibility(View.GONE); shownLast = null; }
                else showLast(l.get(0).uri, l.get(0).takenAt);
            });
        });
    }

    private void showLast(Uri u, long when) {
        shownLast = u;
        lastCard.setVisibility(View.VISIBLE);
        lastTime.setText(Photos.full(when));
        int px = Ui.dp(this, 56);
        Bitmap c = Photos.cached(u, px);
        if (c != null) { lastThumb.setImageBitmap(c); return; }
        Photos.IO.execute(() -> {
            Bitmap b = Photos.thumb(this, u, px);
            ui.post(() -> { if (u.equals(shownLast) && b != null) lastThumb.setImageBitmap(b); });
        });
    }

    private void updateBatteryBanner() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean ok = pm.isIgnoringBatteryOptimizations(getPackageName());
        batteryBanner.setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------ ações

    private void start() {
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), 1);
            return;
        }
        boolean scheduled = whenSeg.selected() == 1;
        if (scheduled && schedAt < System.currentTimeMillis() + 10_000) {
            toast("Escolha um horário no futuro");
            pickSchedule();
            return;
        }
        Intent i = new Intent(this, CaptureService.class)
                .setAction(CaptureService.ACTION_START)
                .putExtra("count", mode.selected() == 1 ? 0 : countStep.get())
                .putExtra("interval", intervalStep.get())
                .putExtra("front", cameraSeg.selected() == 1)
                .putExtra("stamp", stampSwitch.isChecked())
                .putExtra("folder", Folders.destination(this))
                .putExtra("startAt", scheduled ? schedAt : 0L);
        startForegroundService(i);
        if (scheduled) toast("Agendado para " + whenText(schedAt));
        // mostra o estado novo imediatamente
        ui.postDelayed(this::render, 150);
    }

    private void confirmStop() {
        boolean waiting = CaptureService.waiting;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(waiting ? "Cancelar o agendamento?" : "Parar a sessão?")
                .setMessage(waiting ? "Nenhuma foto será tirada em " + whenText(CaptureService.scheduledWall) + "."
                        : CaptureService.taken + " fotos já foram salvas e continuam na galeria.")
                .setNegativeButton(waiting ? "Manter" : "Continuar", null)
                .setPositiveButton(waiting ? "Cancelar agendamento" : "Parar", (d, w) -> startService(
                        new Intent(this, CaptureService.class).setAction(CaptureService.ACTION_STOP)))
                .show();
    }

    // ------------------------------------------------------------ agendamento e pasta

    /** "Hoje às 14:30", "Amanhã às 08:00" ou "15/11 às 08:00". */
    private static String whenText(long wall) {
        return dayWord(wall) + " às " + clock(wall);
    }

    private static String whenShort(long wall) {
        String d = dayWord(wall);
        return (d.equals("Hoje") ? "" : d + " ") + clock(wall);
    }

    private static String dayWord(long wall) {
        java.util.Calendar now = java.util.Calendar.getInstance(), d = java.util.Calendar.getInstance();
        d.setTimeInMillis(wall);
        if (sameDay(now, d)) return "Hoje";
        now.add(java.util.Calendar.DAY_OF_YEAR, 1);
        if (sameDay(now, d)) return "Amanhã";
        return new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new Date(wall));
    }

    private static boolean sameDay(java.util.Calendar a, java.util.Calendar b) {
        return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR)
                && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR);
    }

    /** Escolhe a data e depois a hora do início. */
    private void pickSchedule() {
        final java.util.Calendar cal = java.util.Calendar.getInstance();
        if (schedAt > System.currentTimeMillis()) cal.setTimeInMillis(schedAt);
        else cal.add(java.util.Calendar.MINUTE, 10);
        android.app.DatePickerDialog dp = new android.app.DatePickerDialog(this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert,
                (view, y, m, day) -> {
                    cal.set(y, m, day);
                    new android.app.TimePickerDialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert,
                            (tv, h, min) -> {
                                cal.set(java.util.Calendar.HOUR_OF_DAY, h);
                                cal.set(java.util.Calendar.MINUTE, min);
                                cal.set(java.util.Calendar.SECOND, 0);
                                cal.set(java.util.Calendar.MILLISECOND, 0);
                                if (cal.getTimeInMillis() < System.currentTimeMillis() + 10_000) {
                                    toast("Esse horário já passou. Escolha um horário no futuro.");
                                } else {
                                    schedAt = cal.getTimeInMillis();
                                }
                                saveAndRefresh();
                            },
                            cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show();
                },
                cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH));
        dp.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dp.setOnCancelListener(d -> {
            if (schedAt < System.currentTimeMillis()) whenSeg.select(0); // desistiu sem escolher
            saveAndRefresh();
        });
        dp.show();
    }

    /** Escolhe em qual pasta as próximas fotos serão salvas. */
    private void pickFolder() {
        final List<String> names = Folders.list(this);
        final List<String> labels = new ArrayList<>();
        labels.add(Folders.ROOT_LABEL);
        labels.addAll(names);
        labels.add("Nova pasta…");
        labels.add("Gerenciar pastas…");
        String dest = Folders.destination(this);
        int checked = dest.isEmpty() ? 0 : names.indexOf(dest) + 1;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Salvar as fotos em")
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (d, w) -> {
                    d.dismiss();
                    if (w == 0) Folders.setDestination(this, "");
                    else if (w <= names.size()) Folders.setDestination(this, names.get(w - 1));
                    else if (w == names.size() + 1) {
                        FoldersActivity.askName(this, "Nova pasta", "", name -> {
                            if (!Folders.exists(this, name)) Folders.create(this, name);
                            Folders.setDestination(this, name);
                            saveAndRefresh();
                        });
                    } else {
                        startActivity(new Intent(this, FoldersActivity.class));
                    }
                    saveAndRefresh();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void openGallery() {
        startActivity(new Intent(this, GalleryActivity.class));
    }

    interface IntCallback { void on(int v); }

    private void askNumber(String title, String hint, int current, int min, int max, IntCallback cb) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(current));
        e.setSelectAllOnFocus(true);
        e.setHint(hint);
        e.setTextSize(20);
        FrameLayout wrap = new FrameLayout(this);
        int p = Ui.dp(this, 22);
        wrap.setPadding(p, Ui.dp(this, 8), p, 0);
        wrap.addView(e);
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(wrap)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("OK", (dd, w) -> {
                    try {
                        int n = Integer.parseInt(e.getText().toString().trim());
                        if (n < min || n > max) toast("Use um valor entre " + min + " e " + max);
                        else cb.on(n);
                    } catch (NumberFormatException ex) { toast("Digite um número"); }
                })
                .create();
        d.setOnShowListener(x -> { e.requestFocus(); d.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE); });
        d.show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) start();
        else toast("O FotoTimer precisa da permissão de câmera");
    }

    @Override protected void onResume() {
        super.onResume();
        updateBatteryBanner();
        if (folderValue != null) folderValue.setText(Folders.label(Folders.destination(this)));
        lastRunning = !CaptureService.running; // força aplicar o estado
        refreshLastFromStore();
        ui.post(tick);
    }

    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(tick); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
