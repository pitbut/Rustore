package uz.robutpit.znatoki.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import uz.robutpit.znatoki.MainActivity
import uz.robutpit.znatoki.R

/** Foreground-service (тип microphone) на время игры — голос не пропадает при сворачивании. */
class GameAudioService : LifecycleService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        createChannel(this)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.voice_service_title))
            .setContentText(getString(R.string.voice_service_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        runCatching { ServiceCompat.startForeground(this, 42, n, type) }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "game_voice"
        private const val ACTION_STOP = "stop"

        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.voice_channel_name), NotificationManager.IMPORTANCE_LOW))
        }

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, GameAudioService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, GameAudioService::class.java)) }
        }
    }
}
