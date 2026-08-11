package com.yami.autoreply

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private val scope = CoroutineScope(Dispatchers.Default)

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
        params.y = 60

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                Log.e(TAG, "onSingleTapConfirmed: respondToAllUnread (flujo principal)")
                scope.launch {
                    TimoAccessibilityService.instance?.respondToAllUnread()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                Log.e(TAG, "onDoubleTap: autoReplyWithAI en chat actual")
                scope.launch {
                    val ok = TimoAccessibilityService.instance?.autoReplyWithAI()
                    Log.e(TAG, "resultado autoReplyWithAI: " + ok)
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                Log.e(TAG, "onLongPress: escaneo de diagnostico")
                TimoAccessibilityService.instance?.scanAndNotify()
            }
        })

        bubbleView?.setOnTouchListener { view, event ->
            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (Math.abs(dx) > 15 || Math.abs(dy) > 15) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(bubbleView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
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
