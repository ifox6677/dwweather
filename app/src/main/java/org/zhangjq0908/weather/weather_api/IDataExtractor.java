package org.zhangjq0908.weather.weather_api;

import org.zhangjq0908.weather.database.CurrentWeatherData;
import org.zhangjq0908.weather.database.HourlyForecast;
import org.zhangjq0908.weather.database.QuarterHourlyForecast;
import org.zhangjq0908.weather.database.WeekForecast;
import java.util.List;

/**
 * This interface defines the frame of the functionality to extractCurrentWeatherData weather information from which
 * is returned by some API.
 */
public interface IDataExtractor {

    /**
     * @param data The data that contains the information to instantiate a CurrentWeatherData
     *             object. In the easiest case this is the (HTTP) response of the One Call API.
     * @return Returns the extracted information as a CurrentWeatherData instance.
     */
    CurrentWeatherData extractCurrentWeather(String data);

    /**
     * @param data The data that contains the information to instantiate a Forecast object.
     * @return Returns the extracted weather forecast information. In case some error occurs, null
     * will be returned.
     */
    List<WeekForecast> extractWeekForecast(String data);

    /**
     * @param data The data that contains the information to instantiate a Forecast object.
     * @return Returns the extracted weather forecast information. In case some error occurs, null
     * will be returned.
     */

    List<HourlyForecast> extractHourlyForecast(String data);

    List<QuarterHourlyForecast> extractQuarterHourlyForecast(String data);

}
