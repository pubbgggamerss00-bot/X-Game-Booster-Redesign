package com.bytecycle.xgamebooster.record;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.io.File;

import com.bytecycle.xgamebooster.R;
import com.bytecycle.xgamebooster.ui.MainActivity;
import com.bytecycle.xgamebooster.util.Prefs;

/**
 * Screen recorder engine: MediaProjection + MediaRecorder.
 * Output goes to Movies/XGameBooster (MediaStore on Android 10+).
 */
public class RecordService extends Service {

    public static final String ACTION_START = "com.bytecycle.xgamebooster.REC_START";
    public static final String ACTION_STOP = "com.bytecycle.xgamebooster.REC_STOP";

    public static volatile boolean running = false;

    MediaProjection mp;
    MediaRecorder mr;
    VirtualDisplay vd;
    ParcelFileDescriptor recPfd;
    Uri storeUri;
    int w, hpx, dpi;
    Handler recHandler = new Handler();

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent it, int f, int id) {
        if (it != null && ACTION_STOP.equals(it.getAction())) {
            stopRec();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (it == null || !it.hasExtra("code")) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startFg("در حال ضبط صفحه…");
        if (mr == null) {
            try {
                startRec(it.getIntExtra("code", -1), (Intent) it.getParcelableExtra("data"));
            } catch (Throwable t) {
                update("خطا در شروع ضبط: " + t.getClass().getSimpleName());
                stopRec();
                stopSelf();
            }
        }
        return START_STICKY;
    }

    void metrics() {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display disp = wm.getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= 23) {
            disp.getRealMetrics(dm);
        } else {
            disp.getMetrics(dm);
        }
        w = dm.widthPixels & ~1;
        hpx = dm.heightPixels & ~1;
        dpi = dm.densityDpi;
    }

    void startRec(int code, Intent data) throws Exception {
        metrics();
        mr = new MediaRecorder();
        boolean mic = false;
        try {
            if (Build.VERSION.SDK_INT < 23) {
                mic = true;
            } else {
                mic = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED;
            }
        } catch (Throwable t) {
            mic = false;
        }
        if (mic && Prefs.getBool(this, "rec_mic", true)) {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC);
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mr.setAudioEncodingBitRate(128000);
            mr.setAudioSamplingRate(44100);
        }
        mr.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mr.setVideoEncodingBitRate(8000000);
        mr.setVideoFrameRate(30);
        mr.setVideoSize(w, hpx);

        String name = "XGB_" + System.currentTimeMillis() + ".mp4";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Video.Media.DISPLAY_NAME, name);
            cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            cv.put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/XGameBooster");
            cv.put(MediaStore.Video.Media.IS_PENDING, 1);
            storeUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            if (storeUri == null) throw new Exception("media store null");
            recPfd = getContentResolver().openFileDescriptor(storeUri, "rw");
            mr.setOutputFile(recPfd.getFileDescriptor());
        } else {
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "XGameBooster");
            dir.mkdirs();
            File outFile = new File(dir, name);
            mr.setOutputFile(outFile.getAbsolutePath());
        }

        mr.prepare();
        MediaProjectionManager mpm =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mp = mpm.getMediaProjection(code, data);
        if (mp == null) throw new Exception("projection null");
        mp.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                recHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        stopRec();
                        stopSelf();
                    }
                });
            }
        }, recHandler);
        vd = mp.createVirtualDisplay("xgbrec", w, hpx, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mr.getSurface(), null, null);
        mr.start();
        running = true;
        update("در حال ضبط صفحه…");
    }

    void stopRec() {
        running = false;
        try {
            if (mr != null) mr.stop();
        } catch (Throwable t) { }
        try {
            if (mr != null) mr.release();
        } catch (Throwable t) { }
        mr = null;
        try {
            if (vd != null) vd.release();
        } catch (Throwable t) { }
        vd = null;
        try {
            if (mp != null) mp.stop();
        } catch (Throwable t) { }
        mp = null;
        try {
            if (recPfd != null) recPfd.close();
        } catch (Throwable t) { }
        recPfd = null;
        if (storeUri != null && Build.VERSION.SDK_INT >= 29) {
            try {
                ContentValues up = new ContentValues();
                up.put(MediaStore.Video.Media.IS_PENDING, 0);
                getContentResolver().update(storeUri, up, null, null);
            } catch (Throwable t) { }
        }
        storeUri = null;
        update("ذخیره شد در Movies/XGameBooster");
    }

    void startFg(String txt) {
        Notification n = notif(txt);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(23, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(23, n);
        }
    }

    void update(String txt) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(23, notif(txt));
    }

    Notification notif(String txt) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "rec")
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_camera)
                .setContentTitle("X Screen Recorder")
                .setContentText(txt)
                .setOngoing(true);
        Intent st = new Intent(this, RecordService.class);
        st.setAction(ACTION_STOP);
        b.addAction(0, "توقف ضبط", PendingIntent.getService(this, 6, st,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        b.setContentIntent(PendingIntent.getActivity(this, 7,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_LOW);
        }
        return b.build();
    }
}
