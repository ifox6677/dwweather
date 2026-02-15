package org.zhangjq0908.weather.services;

import static com.android.volley.toolbox.ImageRequest.DEFAULT_IMAGE_BACKOFF_MULT;
import static com.android.volley.toolbox.ImageRequest.DEFAULT_IMAGE_MAX_RETRIES;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.icu.util.LocaleData;
import android.icu.util.ULocale;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import android.util.Log;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.Volley;

import org.zhangjq0908.weather.BuildConfig;
import org.zhangjq0908.weather.R;
import org.zhangjq0908.weather.activities.NavigationActivity;
import org.zhangjq0908.weather.activities.RainViewerActivity;
import org.zhangjq0908.weather.database.CityToWatch;
import org.zhangjq0908.weather.database.SQLiteHelper;
import org.zhangjq0908.weather.ui.Help.StringFormatUtils;
import org.zhangjq0908.weather.weather_api.IHttpRequestForWeatherAPI;
import org.zhangjq0908.weather.weather_api.open_meteo.OMHttpRequestForWeatherAPI;
import org.zhangjq0908.weather.widget.RadarWidget;
import org.zhangjq0908.weather.widget.WeatherWidgetAllInOne;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class provides the functionality to fetch forecast data for a given city as a background
 * task.
 */
public class UpdateDataService extends Worker {

    public static final String UPDATE_SINGLE_ACTION = "org.services.weather.zhangjq0908.UpdateDataService.UPDATE_SINGLE_ACTION";
    public static final String UPDATE_RADAR = "org.services.weather.zhangjq0908.UpdateDataService.UPDATE_RADAR";
    public static final String SKIP_UPDATE_INTERVAL = "skipUpdateInterval";
    public static final String ACTION = "action";
    public static final String CITY_ID = "cityId";

    private SQLiteHelper dbHelper;
    private Context context;

    public UpdateDataService(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
        this.dbHelper = SQLiteHelper.getInstance(context);
    }

    public static void enqueueWork(Context context, String action, int cityId, boolean skipUpdateInterval) {
        Data inputData = new Data.Builder()
                .putString(ACTION, action)
                .putInt(CITY_ID, cityId)
                .putBoolean(SKIP_UPDATE_INTERVAL, skipUpdateInterval)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(UpdateDataService.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(context).enqueue(workRequest);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!isOnline(2000)) {
            Handler h = new Handler(context.getMainLooper());
            h.post(() -> {
                if (NavigationActivity.isVisible) Toast.makeText(context, context.getResources().getString(R.string.error_no_internet), Toast.LENGTH_LONG).show();
            });
            return Result.failure();
        }

        String action = getInputData().getString(ACTION);
        int cityId = getInputData().getInt(CITY_ID, -1);
        boolean skipUpdateInterval = getInputData().getBoolean(SKIP_UPDATE_INTERVAL, false);

        if (action != null) {
            if (UPDATE_SINGLE_ACTION.equals(action)) {
                handleUpdateSingle(cityId);
            } else if (UPDATE_RADAR.equals(action)) {
                if (cityId == SQLiteHelper.getWidgetCityID(context)) {
                    int numRadarWidgets = AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, RadarWidget.class)).length;
                    int numAllInOneWidgets = AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, WeatherWidgetAllInOne.class)).length;
                    if (numRadarWidgets + numAllInOneWidgets > 0) handleUpdateRadar(cityId);
                }
            }
        }
        return Result.success();
    }

    private void handleUpdateRadar(int cityId) {
        CityToWatch city = dbHelper.getCityToWatch(cityId);
        RequestQueue queue = Volley.newRequestQueue(context);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis() - 45000L);

        int currentMinute = calendar.get(Calendar.MINUTE);
        int roundedMinute = (currentMinute / 10) * 10;

        calendar.set(Calendar.MINUTE, roundedMinute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long radarTimeGMT = calendar.getTimeInMillis();
        int zoneseconds = dbHelper.getCurrentWeatherByCityId(cityId).getTimeZoneSeconds();

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] radarWidgetIDs = appWidgetManager.getAppWidgetIds(new ComponentName(context, RadarWidget.class));
        int[] allInOneWidgetIDs = appWidgetManager.getAppWidgetIds(new ComponentName(context, WeatherWidgetAllInOne.class));

        if (radarWidgetIDs.length > 0) {
            int zoom = RainViewerActivity.rainViewerWidgetZoom;
            String radarUrl = "https://tilecache.rainviewer.com/v2/radar/" + radarTimeGMT/1000 + "/256/" + zoom +"/"+ city.getLatitude() +"/" + city.getLongitude() + "/2/1_1.png";

            ImageRequest imageRequest = new ImageRequest(radarUrl,
                    response1 -> {
                        RadarWidget.radarBitmap = response1;
                        RadarWidget.radarTimeGMT = radarTimeGMT;
                        RadarWidget.radarZoom = zoom;

                        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.radar_widget);
                        views.setImageViewBitmap(R.id.widget_radar_view, UpdateDataService.prepareRadarWidget(context, city, zoom, radarTimeGMT + zoneseconds *1000L, response1));
                        appWidgetManager.partiallyUpdateAppWidget(radarWidgetIDs, views);
                    },
                    0, 0, ImageView.ScaleType.CENTER_CROP, Bitmap.Config.RGB_565,
                    error1 -> {
                        Log.d("DownloadRadarTile:", error1.toString()+" "+radarUrl);
                    });
            imageRequest.setRetryPolicy(
                    new DefaultRetryPolicy(
                            3000,
                            DEFAULT_IMAGE_MAX_RETRIES,
                            DEFAULT_IMAGE_BACKOFF_MULT));
            queue.add(imageRequest);
        }

        if (allInOneWidgetIDs.length > 0) {
            int zoomAllInOne = RainViewerActivity.rainViewerAllInOneWidgetZoom;
            String radarUrlAllInOne = "https://tilecache.rainviewer.com/v2/radar/" + radarTimeGMT/1000 + "/256/" + zoomAllInOne +"/"+ city.getLatitude() +"/" + city.getLongitude() + "/2/1_1.png";

            ImageRequest imageRequestAllInOne = new ImageRequest(radarUrlAllInOne,
                    response1 -> {
                        WeatherWidgetAllInOne.radarBitmap = response1;
                        WeatherWidgetAllInOne.radarTimeGMT = radarTimeGMT;
                        WeatherWidgetAllInOne.radarZoom = zoomAllInOne;

                        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather_widget_all_in_one);
                        views.setImageViewBitmap(R.id.widget_radar_view, UpdateDataService.prepareAllInOneWidget(context, city, zoomAllInOne, radarTimeGMT + zoneseconds *1000L, response1));
                        appWidgetManager.partiallyUpdateAppWidget(allInOneWidgetIDs, views);
                    },
                    0, 0, ImageView.ScaleType.CENTER_CROP, Bitmap.Config.RGB_565,
                    error1 -> {
                        Log.d("DownloadRadarTile:", error1.toString()+" "+radarUrlAllInOne);
                    });
            imageRequestAllInOne.setRetryPolicy(
                    new DefaultRetryPolicy(
                            3000,
                            DEFAULT_IMAGE_MAX_RETRIES,
                            DEFAULT_IMAGE_BACKOFF_MULT));
            queue.add(imageRequestAllInOne);
        }
    }

    @NonNull
    public static Bitmap prepareAllInOneWidget(Context context, CityToWatch city, int zoom, long radarTime, Bitmap response1) {
        Bitmap textBitmap = Bitmap.createBitmap(response1.getWidth(), response1.getHeight(), response1.getConfig());
        Canvas canvas = new Canvas(textBitmap);
        canvas.drawBitmap(response1, 0, 0, null);

        Paint paint = new Paint();
        paint.setColor(ContextCompat.getColor(context, R.color.lightgrey));
        paint.setTextSize(30);
        paint.setStrokeWidth(3.0f);

        int widthTotalDistance = (int) (2 * 3.14 * 6378 * Math.abs(Math.cos(city.getLatitude() / 180 * 3.14)) / (Math.pow(2, zoom) * 256) * 256); ;
        String distanceUnit = context.getString(R.string.units_km);;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (LocaleData.getMeasurementSystem(ULocale.forLocale(Locale.getDefault())) != LocaleData.MeasurementSystem.SI){
                distanceUnit = context.getString(R.string.units_mi);
                widthTotalDistance = (int) (2 * 3.14 * 6378 * 0.6214 * Math.abs(Math.cos(city.getLatitude() / 180 * 3.14)) / (Math.pow(2, zoom) * 256) * 256);
            }
        }

        int widthDistanceMarker = getClosestMarker(widthTotalDistance / 10);
        int widthDistanceMarkerPixel = widthDistanceMarker * 256 / widthTotalDistance;

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(widthDistanceMarker + " " + distanceUnit, 7 + widthDistanceMarkerPixel + 5, 238 + 8, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(StringFormatUtils.formatTimeWithoutZone(context, radarTime), 248, 238 + 8, paint);

        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(7, 238, 7 + widthDistanceMarkerPixel, 238, paint);

        int maxI = 100 / widthDistanceMarkerPixel;
        for (int i = 1; i <= maxI; i++) {
            int radius = i * widthDistanceMarkerPixel;
            canvas.drawCircle(128, 128, radius, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(128, 128, 2, paint);

        Paint clearPaint = new Paint();
        clearPaint.setStyle(Paint.Style.STROKE);
        clearPaint.setStrokeWidth(20.0f);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawRoundRect(-10, -10,265, 265, 30, 30, clearPaint);
        return textBitmap;
    }

    @NonNull
    public static Bitmap prepareRadarWidget(Context context, CityToWatch city, int zoom, long radarTime, Bitmap response1) {
        Bitmap textBitmap = Bitmap.createBitmap(response1.getWidth(), response1.getHeight(), response1.getConfig());
        Canvas canvas = new Canvas(textBitmap);
        canvas.drawBitmap(response1, 0, 0, null);
        Paint paint = new Paint();
        paint.setColor(ContextCompat.getColor(context, R.color.lightgrey));
        paint.setTextSize(16);

        int widthTotalDistance = (int) (2 * 3.14 * 6378 * Math.abs(Math.cos(city.getLatitude() / 180 * 3.14)) / (Math.pow(2, zoom) * 256) * 256);

        String distanceUnit = context.getString(R.string.units_km);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (LocaleData.getMeasurementSystem(ULocale.forLocale(Locale.getDefault())) != LocaleData.MeasurementSystem.SI){
                distanceUnit = context.getString(R.string.units_mi);
                widthTotalDistance = (int) (2 * 3.14 * 6378 * 0.6214 * Math.abs(Math.cos(city.getLatitude() / 180 * 3.14)) / (Math.pow(2, zoom) * 256) * 256);
            }
        }

        int widthDistanceMarker = getClosestMarker(widthTotalDistance / 10);
        int widthDistanceMarkerPixel = widthDistanceMarker * 256 / widthTotalDistance;

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(widthDistanceMarker + " " + distanceUnit, 10 + widthDistanceMarkerPixel + 10, 240 + 5, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(StringFormatUtils.formatTimeWithoutZone(context, radarTime), 240, 240 + 5, paint);

        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(10, 240, 10 + widthDistanceMarkerPixel, 240, paint);

        int maxI = 100 / widthDistanceMarkerPixel;
        for (int i = 1; i <= maxI; i++) {
            int radius = i * widthDistanceMarkerPixel;
            canvas.drawCircle(128, 128, radius, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(128, 128, 2, paint);

        Paint clearPaint = new Paint();
        clearPaint.setStyle(Paint.Style.STROKE);
        clearPaint.setStrokeWidth(20.0f);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawRoundRect(-10, -10,265, 265, 30, 30, clearPaint);
        return textBitmap;
    }

    private static int getClosestMarker(int value) {
        int[] markers = {1, 2, 3, 5, 10, 20, 30, 50, 100};
        int closest = markers[0];
        int minDiff = Math.abs(value - closest);
        for (int i = 1; i < markers.length; i++) {
            int diff = Math.abs(value - markers[i]);
            if (diff < minDiff) {
                minDiff = diff;
                closest = markers[i];
            }
        }
        return closest;
    }

    private void handleUpdateSingle(int cityId) {
        CityToWatch city = dbHelper.getCityToWatch(cityId);
        if (city == null) {
            return;
        }

        IHttpRequestForWeatherAPI omHttpRequestForWeatherAPI = new OMHttpRequestForWeatherAPI(context);
        omHttpRequestForWeatherAPI.perform(city.getLatitude(), city.getLongitude(), cityId);
    }

    private boolean isOnline(int timeOut) {
        InetAddress inetAddress = null;
        try {
            Future<InetAddress> future = Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    URL url = new URL(BuildConfig.BASE_URL);
                    return InetAddress.getByName(url.getHost());
                } catch ( IOException e) {
                    return null;
                }
            });
            inetAddress = future.get(timeOut, TimeUnit.MILLISECONDS);
            future.cancel(true);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
        }
        return inetAddress!=null && !inetAddress.toString().isEmpty();
    }

}
