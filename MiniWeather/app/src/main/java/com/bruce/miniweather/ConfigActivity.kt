package com.bruce.miniweather

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val WIDGET_PREFS = "mini_weather_widget_prefs"
const val DEFAULT_OPACITY = 20

/**
 * 放置小工具時系統會自動開這裡；之後點小工具上的齒輪圖示也會重新開這裡，
 * 用來調整「底色透明度」，每個小工具各自記自己的設定。
 */
class ConfigActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.activity_config)

        val prefs = getSharedPreferences(WIDGET_PREFS, MODE_PRIVATE)
        val current = prefs.getInt(opacityKey(appWidgetId), DEFAULT_OPACITY)

        val seekBar = findViewById<SeekBar>(R.id.opacitySeekBar)
        val percentLabel = findViewById<TextView>(R.id.percentLabel)
        val previewBg = findViewById<android.view.View>(R.id.previewBg)

        fun render(progress: Int) {
            percentLabel.text = "$progress%"
            previewBg.background.mutate().alpha = (progress * 255) / 100
        }

        seekBar.progress = current
        render(current)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) =
                render(progress)
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        findViewById<Button>(R.id.confirmButton).setOnClickListener {
            prefs.edit().putInt(opacityKey(appWidgetId), seekBar.progress).apply()

            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, result)

            // 立刻套用，不用等系統下一次自動更新
            CoroutineScope(Dispatchers.Main).launch {
                runCatching {
                    val manager = GlanceAppWidgetManager(this@ConfigActivity)
                    val glanceId = manager.getGlanceIdBy(appWidgetId)
                    WeatherWidget().update(this@ConfigActivity, glanceId)
                }
                finish()
            }
        }
    }
}

fun opacityKey(appWidgetId: Int) = "opacity_$appWidgetId"
