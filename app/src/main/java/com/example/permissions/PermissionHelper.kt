package com.example.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.PermissionStatus

object PermissionHelper {

  fun requiredRuntimePermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      add(Manifest.permission.BLUETOOTH_CONNECT)
      add(Manifest.permission.BLUETOOTH_ADVERTISE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      add(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  fun missingRuntimePermissions(context: Context): List<String> =
    requiredRuntimePermissions().filter { permission ->
      ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }

  fun checkStatus(context: Context): PermissionStatus {
    val connectGranted =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
          PackageManager.PERMISSION_GRANTED
      } else {
        true
      }

    val advertiseGranted =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) ==
          PackageManager.PERMISSION_GRANTED
      } else {
        true
      }

    val notificationsGranted =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      } else {
        true
      }

    return PermissionStatus(
      bluetoothConnectGranted = connectGranted,
      bluetoothAdvertiseGranted = advertiseGranted,
      postNotificationsGranted = notificationsGranted,
    )
  }
}
