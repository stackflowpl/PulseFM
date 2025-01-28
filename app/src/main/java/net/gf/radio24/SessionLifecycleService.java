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
        logDebug("SessionLifecycleService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logDebug("SessionLifecycleService started");

        executorService.execute(() -> {
            try {
                logDebug("Performing background operation");

                performLongOperation();

                mainHandler.post(() -> {
                    logDebug("Background operation completed");
                    stopSelf(startId);
                });
            } catch (Exception e) {
                logError("Error in background operation", e);
                stopSelf(startId);
            }
        });

        return START_NOT_STICKY;
    }

    private void performLongOperation() throws InterruptedException {
        Thread.sleep(5000);
        logDebug("Long operation completed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
        logDebug("SessionLifecycleService destroyed");
    }

    private void logDebug(String message) {
        System.out.println("[DEBUG] " + message);
    }

    private void logError(String message, Exception e) {
        System.err.println("[ERROR] " + message);
        e.printStackTrace();
    }
}

