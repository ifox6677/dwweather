package org.zhangjq0908.weather.services;

import static androidx.core.app.JobIntentService.enqueueWork;

import android.content.Context;
import android.content.Intent;

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
            Intent intent = new Intent(getApplicationContext(), UpdateDataService.class);
            intent.setAction(UpdateDataService.UPDATE_SINGLE_ACTION);
            intent.putExtra("cityId", city.getCityId());
            enqueueWork(getApplicationContext(), UpdateDataService.class, 0, intent);
        }

        return Result.success();
    }
}
