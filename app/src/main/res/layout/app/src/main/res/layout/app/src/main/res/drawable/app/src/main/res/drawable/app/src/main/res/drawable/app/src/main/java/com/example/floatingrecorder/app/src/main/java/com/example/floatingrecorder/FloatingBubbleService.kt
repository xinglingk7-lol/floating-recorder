package com.example.floatingrecorder

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var params: WindowManager.LayoutParams

    enum class State { IDLE, RECORDING, PAUSED }
    private var state = State.IDLE

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        bubbleView = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        windowManager.addView(bubbleView, params)
        setupDrag()
        setupButtons()
        updateLabel()
    }

    private fun setupDrag() {
        val bubbleIcon = bubbleView.findViewById<ImageView>(R.id.bubbleIcon)
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        bubbleIcon.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(bubbleView, params)
                    moved = true
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleExpand()
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleExpand() {
        val controls = bubbleView.findViewById<View>(R.id.controlsPanel)
        controls.visibility = if (controls.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun setupButtons() {
        bubbleView.findViewById<View>(R.id.btnRecord).setOnClickListener {
            when (state) {
                State.IDLE -> {
                    startService(
                        Intent(this, ScreenRecordService::class.java)
                            .setAction(ScreenRecordService.ACTION_START)
                    )
                    state = State.RECORDING
                }
                State.PAUSED -> {
                    startService(
                        Intent(this, ScreenRecordService::class.java)
                            .setAction(ScreenRecordService.ACTION_RESUME)
                    )
                    state = State.RECORDING
                }
                State.RECORDING -> {
                    startService(
                        Intent(this, ScreenRecordService::class.java)
                            .setAction(ScreenRecordService.ACTION_PAUSE)
                    )
                    state = State.PAUSED
                }
            }
            updateLabel()
        }

        bubbleView.findViewById<View>(R.id.btnStop).setOnClickListener {
            startService(
                Intent(this, ScreenRecordService::class.java)
                    .setAction(ScreenRecordService.ACTION_STOP)
            )
            state = State.IDLE
            updateLabel()
        }

        bubbleView.findViewById<View>(R.id.btnClose).setOnClickListener {
            stopSelf()
        }
    }

    private fun updateLabel() {
        val label = bubbleView.findViewById<TextView>(R.id.statusLabel)
        label.text = when (state) {
            State.IDLE -> "Parado"
            State.RECORDING -> "Gravando..."
            State.PAUSED -> "Pausado"
        }
        val recordBtn = bubbleView.findViewById<TextView>(R.id.btnRecord)
        recordBtn.text = when (state) {
            State.IDLE -> "Gravar"
            State.RECORDING -> "Pausar"
            State.PAUSED -> "Retomar"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::bubbleView.isInitialized) windowManager.removeView(bubbleView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
