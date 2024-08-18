package io.github.zadagu.sscvolumecontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import io.github.zadagu.sscvolumecontrol.ssc.Device
import io.github.zadagu.sscvolumecontrol.ssc.wrapElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.launch

const val channelId = "VolumeServiceChannel"

class VolumeService : Service() {
    private lateinit var volumeObserver: VolumeObserver

    override fun onCreate() {
        super.onCreate()

        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel(channelId, "My Volume Service")
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Volume Service")
            .setContentText("Listening for volume changes...")
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        volumeObserver = VolumeObserver(this, Handler())
        contentResolver.registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, volumeObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(volumeObserver)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val chan = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    inner class VolumeObserver(private val context: Context, handler: Handler) : ContentObserver(handler) {
        private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val scope = CoroutineScope(Dispatchers.IO)
        private var controlledDevices = listOf<Device>()

        override fun onChange(selfChange: Boolean) {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            val settings = getVolumeServiceSettings(context) ?: VolumeServiceSettings()

            val targetVolume = if (currentVolume == 0) {
                0.0
            } else {
                val volumeRange = settings.userMaximumVolume - settings.userMinimumVolume
                val volumePercentage = currentVolume.toDouble() / maxVolume
                settings.userMinimumVolume + volumeRange * volumePercentage
            }
            Log.i("VolumeService", "Volume changed to $currentVolume, setting target volume to $targetVolume")

            val newControlledDevices = getControlledDevices(context)
            if (newControlledDevices != controlledDevices) {
                // Since the connection instances are bound to the devices, we only want to update the list of devices, if there really is a change
                // Otherwise, we would have to re-establish the connection to the devices.
                controlledDevices = newControlledDevices
                Log.i("VolumeService", "Controlled devices changed to ${controlledDevices.map { it.ipv6Address }}")
            }

            scope.launch {
                controlledDevices.forEach() { device ->
                    run {
                        device.withConnection() {
                            it.send(
                                wrapElement(settings.sscVolumePath, JsonPrimitive(targetVolume))
                            )
                        }
                    }
                }
            }
        }
    }
}