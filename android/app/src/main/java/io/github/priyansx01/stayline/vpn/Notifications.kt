package io.github.priyansx01.stayline.vpn

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.priyansx01.stayline.MainActivity
import io.github.priyansx01.stayline.R

object Notifications {
    const val STATUS_ID = 1
    private const val ATTENTION_ID = 2
    private const val STATUS = "status"
    private const val ATTENTION = "attention"
    private const val EMERALD = 0xFF19C58A.toInt()
    private const val RUBY = 0xFFFF3B6B.toInt()
    private const val TOPAZ = 0xFFF5B544.toInt()

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
                NotificationChannel(ATTENTION, context.getString(R.string.channel_attention), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The ongoing notification while the tunnel is being kept up. */
    fun status(context: Context, state: TunnelState, profileName: String?): Notification {
        val disconnect = PendingIntent.getService(
            context,
            1,
            Intent(context, StaylineVpnService::class.java).setAction(StaylineVpnService.ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val title = context.getString(
            when (state) {
                is TunnelState.Connected -> R.string.notif_protected
                // Failed attempts before the tunnel was ever up are still connecting.
                is TunnelState.Reconnecting ->
                    if (TunnelRepository.session.value.wasUp) R.string.notif_reconnecting else R.string.notif_connecting
                TunnelState.WaitingForNetwork -> R.string.notif_waiting
                else -> R.string.notif_connecting
            },
        )
        val text = listOfNotNull(profileName, (state as? TunnelState.Connected)?.localIp).joinToString(" · ")
        return NotificationCompat.Builder(context, STATUS)
            .setSmallIcon(R.drawable.ic_stat_gem)
            .setColor(if (state is TunnelState.Connected) EMERALD else TOPAZ)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp(context))
            .addAction(0, context.getString(R.string.notif_disconnect), disconnect)
            .apply {
                if (state is TunnelState.Connected && state.sinceUnix > 0) {
                    setWhen(state.sinceUnix * 1000).setShowWhen(true).setUsesChronometer(true)
                } else {
                    setShowWhen(false)
                }
            }
            .build()
    }

    /** The tunnel stopped and only the user can fix it. */
    fun attention(context: Context, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val notification = NotificationCompat.Builder(context, ATTENTION)
            .setSmallIcon(R.drawable.ic_stat_gem)
            .setColor(RUBY)
            .setContentTitle(context.getString(R.string.notif_attention_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ATTENTION_ID, notification)
    }

    fun clearAttention(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ATTENTION_ID)
    }
}
