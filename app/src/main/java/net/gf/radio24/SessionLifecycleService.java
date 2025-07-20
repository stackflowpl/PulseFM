package net.gf.radio24;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionLifecycleService extends Service {
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        executorService.execute(() -> {
            try {
                performLongOperation();

                mainHandler.post(() -> {
                    stopSelf(startId);
                });
            } catch (Exception e) {
                stopSelf(startId);
            }
        });

        return START_NOT_STICKY;
    }

    private void performLongOperation() throws InterruptedException {
        Thread.sleep(5000);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}

