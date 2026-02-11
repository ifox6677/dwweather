package org.zhangjq0908.weather.ui.updater;

import org.zhangjq0908.weather.database.CurrentWeatherData;
import org.zhangjq0908.weather.database.HourlyForecast;
import org.zhangjq0908.weather.database.WeekForecast;

import java.util.List;

/**
 * Created by chris on 24.01.2017.
 */
public interface IUpdateableCityUI {
    void processNewCurrentWeatherData(CurrentWeatherData data);

    void processNewForecasts(List<HourlyForecast> hourlyForecasts);

    void processNewWeekForecasts(List<WeekForecast> forecasts);
}
