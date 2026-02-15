package org.zhangjq0908.weather.ui.viewPager;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.zhangjq0908.weather.database.CityToWatch;
import org.zhangjq0908.weather.database.CurrentWeatherData;
import org.zhangjq0908.weather.database.HourlyForecast;
import org.zhangjq0908.weather.database.SQLiteHelper;
import org.zhangjq0908.weather.database.WeekForecast;
import org.zhangjq0908.weather.services.UpdateDataService;
import org.zhangjq0908.weather.ui.WeatherCityFragment;
import org.zhangjq0908.weather.ui.updater.IUpdateableCityUI;

import java.util.Collections;
import java.util.List;

/**
 * Created by thomagglaser on 07.08.2017.
 */

public class WeatherPagerAdapter extends FragmentStateAdapter implements IUpdateableCityUI {

    private Context mContext;

    private SQLiteHelper database;

    private List<CityToWatch> cities;


    public WeatherPagerAdapter(Context context, @NonNull FragmentManager supportFragmentManager, @NonNull Lifecycle lifecycle) {
        super(supportFragmentManager,lifecycle);
        this.mContext = context;
        this.database = SQLiteHelper.getInstance(context);

        loadCities();
    }

    public void loadCities() {
        this.cities = database.getAllCitiesToWatch();
        Collections.sort(cities, (o1, o2) -> o1.getRank() - o2.getRank());
    }

    @NonNull
    @Override
    public WeatherCityFragment createFragment(int position) {
        Bundle args = new Bundle();
        args.putInt("city_id", cities.get(position).getCityId());

        return WeatherCityFragment.newInstance(args);
    }

    @Override
    public int getItemCount() {
        return cities.size();
    }

    public CharSequence getPageTitle(int position) {
         return cities.get(position).getCityName();
    }

    public static void refreshSingleData(Context context, Boolean asap, int cityId) {
        Data inputData = new Data.Builder()
                .putString(UpdateDataService.ACTION, UpdateDataService.UPDATE_SINGLE_ACTION)
                .putBoolean(UpdateDataService.SKIP_UPDATE_INTERVAL, asap)
                .putInt(UpdateDataService.CITY_ID, cityId)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(UpdateDataService.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(context).enqueue(workRequest);
    }


    @Override
    public void processNewCurrentWeatherData(CurrentWeatherData data) {

    }

    @Override
    public void processNewForecasts(List<HourlyForecast> hourlyForecasts) {
        //empty because Fragments are subscribers themselves
    }

    @Override
    public void processNewWeekForecasts(List<WeekForecast> forecasts) {
        //empty because Fragments are subscribers themselves
    }

    public int getCityIDForPos(int pos) {
            CityToWatch city = cities.get(pos);
                 return city.getCityId();
    }

    public int getPosForCityID(int cityID) {
        for (int i = 0; i < cities.size(); i++) {
            CityToWatch city = cities.get(i);
            if (city.getCityId() == cityID) {
                return i;
            }
        }
        return -1;  //item not found
    }

    public float getLatForPos(int pos) {
        CityToWatch city = cities.get(pos);
        return city.getLatitude();
    }

    public float getLonForPos(int pos) {
        CityToWatch city = cities.get(pos);
        return city.getLongitude();
    }

}
