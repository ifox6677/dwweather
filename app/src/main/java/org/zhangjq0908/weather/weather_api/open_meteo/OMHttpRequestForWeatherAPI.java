package org.zhangjq0908.weather.weather_api.open_meteo;

import android.content.Context;

import org.zhangjq0908.weather.http.HttpRequestType;
import org.zhangjq0908.weather.http.IHttpRequest;
import org.zhangjq0908.weather.http.VolleyHttpRequest;
import org.zhangjq0908.weather.weather_api.IHttpRequestForWeatherAPI;

/**
 * This class provides the functionality for making and processing HTTP requests to Open-Meteo to retrieve the latest weather data for all stored cities.
 */
public class OMHttpRequestForWeatherAPI extends OMHttpRequest implements IHttpRequestForWeatherAPI {

    /**
     * Member variables.
     */
    private final Context context;

    /**
     * @param context The context to use.
     */
    public OMHttpRequestForWeatherAPI(Context context) {
        this.context = context;
    }



    @Override
    public void perform(float lat, float lon, int cityId) {
        IHttpRequest httpRequest = new VolleyHttpRequest(context, cityId);
        final String URL = getUrlForQueryingOMweatherAPI(context, lat, lon);
        httpRequest.make(URL, HttpRequestType.GET, new ProcessOMweatherAPIRequest(context));
    }
}
