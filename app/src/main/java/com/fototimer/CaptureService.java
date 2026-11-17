package com.fototimer;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Size;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * Serviço em primeiro plano que tira fotos em intervalo, mesmo com a tela apagada.
 * Para cada foto: abre a câmera, espera ~1,5 s para foco/exposição, fotografa, salva e fecha.
 * total = 0 significa "sem limite" (até o usuário parar).
 */
public class CaptureService extends Service {

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_BEGIN = "begin";
    private static final String CHANNEL = "fototimer";
    private static final int NOTIF_ID = 1;
    private static final long WARMUP_MS = 1500;
    private static final long SHOT_TIMEOUT_MS = 15000;

    // Estado lido pela tela (mesmo processo).
    public static volatile boolean running = false;
    public static volatile boolean shooting = false;
    public static volatile int total, intervalSec, taken, failed, consecutiveFails;
    public static volatile long startedAt, nextShotAt; // SystemClock.elapsedRealtime()
    public static volatile Uri lastUri;
    public static volatile long lastShotWall;
    public static volatile String lastSummary;
    public static volatile boolean waiting = false;   // agendado, esperando a hora
    public static volatile long scheduledWall = 0;     // hora marcada (System.currentTimeMillis)
    private boolean began = false;
    private String relPath = "Pictures/FotoTimer";

    private HandlerThread thread;
    private Handler handler;
    private PowerManager.WakeLock wakeLock;

    private boolean front, stamp;
    private String sessionTag;

    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader jpegReader, previewReader;
    private boolean shotDone;
    private long shotWall;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            if (running) finish(true);
            else stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_BEGIN.equals(action)) { // alarme do agendamento
            if (running && waiting) begin();
            else if (!running) stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action) || running) return START_NOT_STICKY;

        total = Math.max(0, intent.getIntExtra("count", 100));
        intervalSec = Math.max(3, intent.getIntExtra("interval", 30));
        front = intent.getBooleanExtra("front", false);
        stamp = intent.getBooleanExtra("stamp", true);
        String folder = intent.getStringExtra("folder");
        relPath = Folders.path(folder);
        long startAt = intent.getLongExtra("startAt", 0);
        taken = 0; failed = 0; consecutiveFails = 0;
        scheduledWall = startAt;
        waiting = startAt > System.currentTimeMillis() + 2000;
        running = true;

        createChannel();
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
        } else {
            startForeground(NOTIF_ID, n);
        }

        thread = new HandlerThread("camera");
        thread.start();
        handler = new Handler(thread.getLooper());

        if (waiting) {
            // Agendado: o serviço fica em espera (sem segurar o processador) e um alarme o acorda na hora.
            scheduleAlarm(startAt);
            handler.postDelayed(beginRunnable, Math.max(0, startAt - System.currentTimeMillis())); // reserva
        } else {
            begin();
        }
        return START_NOT_STICKY;
    }

    private final Runnable beginRunnable = () -> { if (running && waiting) begin(); };

    /** Começa a sessão de fotos (na hora ou quando o agendamento dispara). */
    private synchronized void begin() {
        if (!running || (began)) return;
        began = true;
        waiting = false;
        cancelAlarm();
        if (handler != null) handler.removeCallbacks(beginRunnable);
        sessionTag = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FotoTimer::capture");
        wakeLock.setReferenceCounted(false);
        if (total > 0) wakeLock.acquire((long) total * intervalSec * 1000L + 10 * 60 * 1000L);
        else wakeLock.acquire(); // sem limite: segura até o usuário parar

        startedAt = SystemClock.elapsedRealtime();
        nextShotAt = startedAt;
        updateNotification();
        handler.post(this::takeShot);
    }

    private PendingIntent alarmIntent() {
        Intent i = new Intent(this, CaptureService.class).setAction(ACTION_BEGIN);
        return PendingIntent.getService(this, 7, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void scheduleAlarm(long atWall) {
        try {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atWall, alarmIntent());
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atWall, alarmIntent());
        } catch (Throwable ignored) {}
    }

    private void cancelAlarm() {
        try { ((AlarmManager) getSystemService(ALARM_SERVICE)).cancel(alarmIntent()); } catch (Throwable ignored) {}
    }

    // ---------------- captura ----------------

    @SuppressLint("MissingPermission")
    private void takeShot() {
        if (!running) return;
        shotDone = false;
        shooting = true;
        shotWall = System.currentTimeMillis();
        handler.postDelayed(timeoutCheck, SHOT_TIMEOUT_MS);
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            String id = pickCamera(cm);
            CameraCharacteristics ch = cm.getCameraCharacteristics(id);
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size big = largest(map.getOutputSizes(ImageFormat.JPEG));
            Size small = smallPreview(map.getOutputSizes(ImageFormat.YUV_420_888));
            Integer sensor = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            final int orientation = sensor == null ? 90 : sensor;

            jpegReader = ImageReader.newInstance(big.getWidth(), big.getHeight(), ImageFormat.JPEG, 2);
            jpegReader.setOnImageAvailableListener(r -> {
                Image img = r.acquireNextImage();
                if (img == null) return;
                boolean ok;
                try { save(img); ok = true; }
                catch (Exception e) { ok = false; }
                finally { img.close(); }
                if (ok) { taken++; consecutiveFails = 0; shotFinished(); }
                else shotFailed();
            }, handler);

            // fluxo invisível só para foco e exposição se ajustarem antes da foto
            previewReader = ImageReader.newInstance(small.getWidth(), small.getHeight(), ImageFormat.YUV_420_888, 3);
            previewReader.setOnImageAvailableListener(r -> {
                Image img = r.acquireLatestImage();
                if (img != null) img.close();
            }, handler);

            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice c) {
                    camera = c;
                    try { startSession(orientation); }
                    catch (Exception e) { shotFailed(); }
                }
                @Override public void onDisconnected(CameraDevice c) { c.close(); camera = null; shotFailed(); }
                @Override public void onError(CameraDevice c, int err) { c.close(); camera = null; shotFailed(); }
            }, handler);
        } catch (Exception e) {
            shotFailed();
        }
    }

    @SuppressWarnings("deprecation")
    private void startSession(int orientation) throws CameraAccessException {
        camera.createCaptureSession(Arrays.asList(previewReader.getSurface(), jpegReader.getSurface()),
                new CameraCaptureSession.StateCallback() {
                    @Override public void onConfigured(CameraCaptureSession s) {
                        session = s;
                        try {
                            CaptureRequest.Builder p = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            p.addTarget(previewReader.getSurface());
                            p.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            p.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                            s.setRepeatingRequest(p.build(), null, handler);
                            handler.postDelayed(() -> capture(orientation), WARMUP_MS);
                        } catch (Exception e) { shotFailed(); }
                    }
                    @Override public void onConfigureFailed(CameraCaptureSession s) { shotFailed(); }
                }, handler);
    }

    private void capture(int orientation) {
        if (shotDone || camera == null || session == null) return;
        try {
            shotWall = System.currentTimeMillis();
            CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(jpegReader.getSurface());
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            b.set(CaptureRequest.JPEG_ORIENTATION, orientation);
            b.set(CaptureRequest.JPEG_QUALITY, (byte) 95);
            session.capture(b.build(), null, handler);
        } catch (Exception e) { shotFailed(); }
    }

    private final Runnable timeoutCheck = () -> { if (!shotDone) shotFailed(); };

    private void shotFailed() {
        if (shotDone) return;
        failed++;
        consecutiveFails++;
        shotFinished();
    }

    private void shotFinished() {
        if (shotDone) return;
        shotDone = true;
        shooting = false;
        handler.removeCallbacks(timeoutCheck);
        closeCamera();

        int done = taken + failed;
        if (total > 0 && done >= total) {
            finish(false);
            return;
        }
        // agenda a próxima no horário certo (não acumula atraso)
        nextShotAt = startedAt + (long) done * intervalSec * 1000L;
        long delay = Math.max(0, nextShotAt - SystemClock.elapsedRealtime());
        updateNotification();
        handler.postDelayed(this::takeShot, delay);
    }

    private void closeCamera() {
        try { if (session != null) session.close(); } catch (Exception ignored) {}
        try { if (camera != null) camera.close(); } catch (Exception ignored) {}
        try { if (jpegReader != null) jpegReader.close(); } catch (Exception ignored) {}
        try { if (previewReader != null) previewReader.close(); } catch (Exception ignored) {}
        session = null; camera = null; jpegReader = null; previewReader = null;
    }

    // ---------------- salvar ----------------

    private void save(Image img) throws Exception {
        ByteBuffer buf = img.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        if (stamp) bytes = stampDate(bytes, shotWall);

        String name = String.format(Locale.US, "FT_%s_%04d.jpg", sessionTag, taken + 1);
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, relPath);
        v.put(MediaStore.Images.Media.DATE_TAKEN, shotWall);
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        ContentResolver cr = getContentResolver();
        Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new Exception("insert failed");
        try (OutputStream os = cr.openOutputStream(uri)) {
            os.write(bytes);
        }
        v.clear();
        v.put(MediaStore.Images.Media.IS_PENDING, 0);
        cr.update(uri, v, null, null);
        lastUri = uri;
        lastShotWall = shotWall;
    }

    /** Escreve "dd/MM/yyyy  HH:mm:ss" no canto inferior direito. Se faltar memória, salva sem carimbo. */
    private static byte[] stampDate(byte[] jpeg, long when) {
        Bitmap bmp = null;
        try {
            int rot = 0;
            ExifInterface ex = new ExifInterface(new ByteArrayInputStream(jpeg));
            switch (ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90: rot = 90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: rot = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: rot = 270; break;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inMutable = true;
            bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
            if (bmp == null) return jpeg;
            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                Bitmap r = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                bmp.recycle();
                bmp = r.isMutable() ? r : r.copy(Bitmap.Config.ARGB_8888, true);
                if (bmp != r) r.recycle();
            }
            int w = bmp.getWidth(), h = bmp.getHeight();
            String s = new SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.getDefault()).format(new Date(when));

            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFFFFFFF);
            p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            p.setTextSize(Math.min(w, h) * 0.038f);
            float tw = p.measureText(s);
            Paint.FontMetrics fm = p.getFontMetrics();
            float th = fm.descent - fm.ascent;
            float pad = p.getTextSize() * 0.45f, margin = p.getTextSize() * 0.7f;

            RectF box = new RectF(w - margin - tw - pad * 2, h - margin - th - pad * 2, w - margin, h - margin);
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(0x8C000000);
            Canvas c = new Canvas(bmp);
            c.drawRoundRect(box, pad, pad, bg);
            c.drawText(s, box.left + pad, box.top + pad - fm.ascent, p);

            ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length);
            bmp.compress(Bitmap.CompressFormat.JPEG, 93, out);
            return out.toByteArray();
        } catch (Throwable t) {
            return jpeg;
        } finally {
            if (bmp != null) bmp.recycle();
        }
    }

    private String pickCamera(CameraManager cm) throws CameraAccessException {
        int want = front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
        String[] ids = cm.getCameraIdList();
        for (String id : ids) {
            Integer f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (f != null && f == want) return id;
        }
        return ids[0];
    }

    private static Size largest(Size[] sizes) {
        Size best = sizes[0];
        for (Size s : sizes)
            if ((long) s.getWidth() * s.getHeight() > (long) best.getWidth() * best.getHeight()) best = s;
        return best;
    }

    private static Size smallPreview(Size[] sizes) {
        Size best = null;
        for (Size s : sizes) {
            long a = (long) s.getWidth() * s.getHeight();
            if (a >= 320 * 240 && (best == null || a < (long) best.getWidth() * best.getHeight())) best = s;
        }
        return best != null ? best : sizes[0];
    }

    // ---------------- fim / notificação ----------------

    private void finish(boolean byUser) {
        if (!running) return;
        boolean wasWaiting = waiting;
        running = false;
        shooting = false;
        waiting = false;
        cancelAlarm();
        String msg = wasWaiting ? "agendamento cancelado"
                : taken + (taken == 1 ? " foto salva" : " fotos salvas")
                + (failed > 0 ? " · " + failed + " falharam" : "")
                + (byUser ? " · parado por você" : " · concluído");
        lastSummary = msg;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            final HandlerThread t = thread;
            handler.post(() -> { closeCamera(); t.quitSafely(); });
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        getSystemService(NotificationManager.class).notify(2, new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_camera)
                .setColor(Ui.ACCENT)
                .setContentTitle(byUser ? "Sessão encerrada" : "Sessão concluída")
                .setContentText(msg)
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build());
        stopSelf();
    }

    private void updateNotification() {
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification());
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, CaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE);
        if (waiting) {
            String when = new SimpleDateFormat("dd/MM 'às' HH:mm", Locale.getDefault()).format(new Date(scheduledWall));
            Notification.Builder w = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(R.drawable.ic_camera)
                    .setColor(Ui.ACCENT)
                    .setContentTitle("Agendado para " + when)
                    .setContentText((total > 0 ? total + " fotos" : "Sem limite") + " · a cada " + Ui.interval(intervalSec))
                    .setSubText("começa em")
                    .setWhen(scheduledWall)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(openApp())
                    .addAction(new Notification.Action.Builder(null, "Cancelar", stopPi).build());
            if (Build.VERSION.SDK_INT >= 31) w.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
            return w.build();
        }
        String title = total > 0 ? "Fotos " + taken + " de " + total : taken + (taken == 1 ? " foto" : " fotos") + " · sem limite";
        long nextWall = System.currentTimeMillis() + Math.max(0, nextShotAt - SystemClock.elapsedRealtime());
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_camera)
                .setColor(Ui.ACCENT)
                .setContentTitle(title)
                .setContentText("A cada " + Ui.interval(intervalSec) + (failed > 0 ? " · " + failed + " falhas" : ""))
                .setSubText("próxima foto")
                .setWhen(nextWall)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentIntent(openApp())
                .addAction(new Notification.Action.Builder(null, "Parar", stopPi).build());
        if (total > 0) b.setProgress(total, taken + failed, false);
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return b.build();
    }

    private PendingIntent openApp() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Captura de fotos", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override
    public void onDestroy() {
        if (running) finish(true);
        super.onDestroy();
    }
}
