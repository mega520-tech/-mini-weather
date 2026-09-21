package com.bruce.miniweather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.glance.appwidget.updateAll
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

const val PREFS = "mini_weather"
const val KEY_TEMP = "temp"
private const val KEY_LAT = "lat"
private const val KEY_LON = "lon"

class WeatherWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // 有新位置就用新的並記下來；拿不到就用上次的
        lastLocation(ctx)?.let {
            prefs.edit()
                .putFloat(KEY_LAT, it.latitude.toFloat())
                .putFloat(KEY_LON, it.longitude.toFloat())
                .apply()
        }
        if (!prefs.contains(KEY_LAT)) return Result.retry()
        val lat = prefs.getFloat(KEY_LAT, 0f).toDouble()
        val lon = prefs.getFloat(KEY_LON, 0f).toDouble()

        return try {
            val temp = withContext(Dispatchers.IO) { fetchTemp(lat, lon) }
            prefs.edit().putInt(KEY_TEMP, temp).apply()
            WeatherWidget().updateAll(ctx)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /** Open-Meteo：免費、免金鑰，預設攝氏 */
    private fun fetchTemp(lat: Double, lon: Double): Int {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon&current=temperature_2m"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
                .getJSONObject("current")
                .getDouble("temperature_2m")
                .roundToInt()
        } finally {
            conn.disconnect()
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastLocation(ctx: Context): Location? {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        return lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}

object Scheduler {
    private const val PERIODIC = "weather_periodic"
    private const val NOW = "weather_now"

    private val netOnly = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<WeatherWorker>(30, TimeUnit.MINUTES)
            .setConstraints(netOnly)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun refreshNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<WeatherWorker>()
            .setConstraints(netOnly)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
    }
}
