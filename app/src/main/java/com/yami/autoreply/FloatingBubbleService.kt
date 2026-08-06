package com.yami.autoreply

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class FloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null

    companion object {
        private const val CHANNEL_ID = "auto_reply_status"
        private const val NOTIF_ID = 1001
        private const val TAG = "AutoReplyDebug"
    }

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "FloatingBubbleService onCreate")
        startForeground(NOTIF_ID, buildForegroundNotification())
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildForegroundNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Estado de Auto Reply",
                NotificationManager.IMPORTANCE_MIN
                )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Auto Reply activo")
        .setContentText("Respondiendo mensajes entrantes automaticamente")
        .setSmallIcon(android.R.drawable.ic_secure)
        .setOngoing(true)
        .build()
    }

    private fun showBubble() {
        Log.e(TAG, "showBubble llamado")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        bubbleView = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null)
        bubbleView?.isClickable = true
        bubbleView?.isFocusable = true

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
            )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 150

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var downTime = 0L

        bubbleView?.setOnTouchListener { view, event ->
            Log.e(TAG, "evento touch recibido, action=" + event.action)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    Log.e(TAG, "ACTION_DOWN detectado")
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val movedDistance = Math.abs(event.rawX - touchX) + Math.abs(event.rawY - touchY)
                    val tapDuration = System.currentTimeMillis() - downTime
                    Log.e(TAG, "ACTION_UP moved=" + movedDistance + " duration=" + tapDuration)
                    if (movedDistance < 25 && tapDuration < 600) {
                        Log.e(TAG, "Fue un tap, instance es null? " + (TimoAccessibilityService.instance == null))
                        TimoAccessibilityService.instance?.scanAndNotify()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(bubbleView, params)
        Log.e(TAG, "Burbuja agregada a la ventana, x=" + params.x + " y=" + params.y)
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager?.removeView(it) }
    }
}
