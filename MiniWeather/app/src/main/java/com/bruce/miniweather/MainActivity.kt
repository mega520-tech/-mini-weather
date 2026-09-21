package com.bruce.miniweather

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast

/** 無畫面：要權限 → 排程更新 → 關閉。點小工具也會開這裡來手動刷新。 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            done()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        done()
    }

    private fun done() {
        Scheduler.schedule(this)
        Scheduler.refreshNow(this)
        Toast.makeText(this, "天氣更新中…", Toast.LENGTH_SHORT).show()
        finish()
    }
}
