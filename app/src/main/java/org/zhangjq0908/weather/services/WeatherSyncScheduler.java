package org.zhangjq0908.weather.services;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.preference.PreferenceManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class WeatherSyncScheduler {
    public static final String PREF_SYNC_INTERVAL_MINUTES = "pref_sync_interval_minutes";
    private static final String PERIODIC_WORK_NAME = "weatherSyncPeriodic";
    private static final String ONE_TIME_WORK_NAME = "weatherSyncImmediate";
    private static final long DEFAULT_PERIODIC_MINUTES = 30;

    private WeatherSyncScheduler() {
    }

    public static void ensureScheduled(@NonNull Context context, boolean runImmediately) {
        WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        long intervalMinutes = getIntervalMinutes(context);
        PeriodicWorkRequest periodicRequest =
                new PeriodicWorkRequest.Builder(WeatherSyncWorker.class, intervalMinutes, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .build();

        workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                periodicRequest
        );

        if (runImmediately) {
            OneTimeWorkRequest oneTimeRequest =
                    new OneTimeWorkRequest.Builder(WeatherSyncWorker.class)
                            .setConstraints(constraints)
                            .build();
            workManager.enqueueUniqueWork(
                    ONE_TIME_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    oneTimeRequest
            );
        }
    }

    private static long getIntervalMinutes(@NonNull Context context) {
        String rawValue = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .getString(PREF_SYNC_INTERVAL_MINUTES, String.valueOf(DEFAULT_PERIODIC_MINUTES));
        try {
            long minutes = Long.parseLong(rawValue);
            return Math.max(15, minutes);
        } catch (NumberFormatException e) {
            return DEFAULT_PERIODIC_MINUTES;
        }
    }
}
