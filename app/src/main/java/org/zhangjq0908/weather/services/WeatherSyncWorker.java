package org.zhangjq0908.weather.services;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.zhangjq0908.weather.database.CityToWatch;
import org.zhangjq0908.weather.database.SQLiteHelper;

import java.util.List;

public class WeatherSyncWorker extends Worker {
    public WeatherSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SQLiteHelper db = SQLiteHelper.getInstance(getApplicationContext());
        List<CityToWatch> cities = db.getAllCitiesToWatch();
        if (cities.isEmpty()) {
            return Result.success();
        }

        for (CityToWatch city : cities) {
            UpdateDataService.enqueueWork(getApplicationContext(), UpdateDataService.UPDATE_SINGLE_ACTION, city.getCityId(), false);
        }

        return Result.success();
    }
}
