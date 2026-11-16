package com.example.magicwhatsappisland

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.max

class WhatsAppIslandListener : NotificationListenerService() {

    private val CHANNEL_ID = "whatsapp_privacy_banner"
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

        val notification = sbn.notification
        val extras = notification.extras

        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (isGroupSummary || isOngoing) return

        val rawSender = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: return

        val senderName = rawSender.toString().trim()
        if (senderName.isEmpty() || senderName.equals("WhatsApp", ignoreCase = true)) return

        val contentIntent = notification.contentIntent
        val avatarDrawable = extractWhatsAppAvatar(notification)

        // Cancel original WhatsApp notification after extracting avatar
        cancelNotification(sbn.key)

        if (Settings.canDrawOverlays(this)) {
            mainHandler.post {
                showMagicIslandOverlay(this, senderName, avatarDrawable, contentIntent)
            }
        }

        postSanitizedBanner(sbn.id, senderName, contentIntent)
    }

    private fun extractWhatsAppAvatar(notification: Notification): Drawable? {
        val extras = notification.extras
        var avatarIcon: Icon? = null

        val messagesParcelables: Array<Parcelable>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }

        if (!messagesParcelables.isNullOrEmpty()) {
            val lastMessageBundle = messagesParcelables.last() as? Bundle
            val senderPerson: Person? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lastMessageBundle?.getParcelable("sender_person", Person::class.java)
            } else {
                @Suppress("DEPRECATION")
                lastMessageBundle?.getParcelable("sender_person")
            }
            avatarIcon = senderPerson?.icon
        }

        if (avatarIcon == null) {
            val messagingPerson: Person? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON)
            }
            avatarIcon = messagingPerson?.icon
        }

        if (avatarIcon == null) {
            avatarIcon = notification.getLargeIcon()
        }

        return avatarIcon?.loadDrawable(this)
    }

    private fun postSanitizedBanner(id: Int, senderName: String, contentIntent: PendingIntent?) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(senderName)
            .setContentText("New WhatsApp message")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        manager.notify(id, builder.build())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WhatsApp Privacy Banner",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows WhatsApp sender name without message preview"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private var activeOverlayRoot: View? = null
        private var autoDismissRunnable: Runnable? = null
        private val staticHandler = Handler(Looper.getMainLooper())

        fun triggerTestOverlay(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            showMagicIslandOverlay(
                context.applicationContext,
                "Mahmoud • WhatsApp",
                null,
                null
            )
        }

        private fun dpToPx(context: Context, dp: Int): Int {
            return (dp * context.resources.displayMetrics.density).toInt()
        }

        private fun drawableToCircularBitmap(drawable: Drawable, sizePx: Int): Bitmap {
            val rawBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val rawCanvas = Canvas(rawBitmap)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(rawCanvas)

            val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = Rect(0, 0, sizePx, sizePx)

            canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(rawBitmap, rect, rect, paint)

            return output
        }

        fun showMagicIslandOverlay(
            context: Context,
            senderName: String,
            avatarDrawable: Drawable?,
            pendingIntent: PendingIntent?
        ) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            autoDismissRunnable?.let { staticHandler.removeCallbacks(it) }
            activeOverlayRoot?.let {
                try { windowManager.removeViewImmediate(it) } catch (_: Exception) {}
                activeOverlayRoot = null
            }

            var pillTopPx = dpToPx(context, 7)
            var pillWidthPx = dpToPx(context, 112)
            var pillHeightPx = dpToPx(context, 32)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cutout = windowManager.currentWindowMetrics.windowInsets.displayCutout
                if (cutout != null && cutout.boundingRects.isNotEmpty()) {
                    val pillRect = cutout.boundingRects.first()
                    pillTopPx = pillRect.top
                    pillWidthPx = pillRect.width()
                    pillHeightPx = pillRect.height()
                }
            }

            val contentRowHeight = dpToPx(context, 48)
            val expandedHeight = pillHeightPx + contentRowHeight + dpToPx(context, 6)

            val collapsedRadius = pillHeightPx / 2f
            val expandedRadius = dpToPx(context, 26).toFloat()

            val rootWrapper = FrameLayout(context)

            val capsuleBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = collapsedRadius
                setColor(Color.BLACK)
                setStroke(dpToPx(context, 1), Color.parseColor("#6625D366"))
            }

            val capsuleLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                background = capsuleBackground
                clipChildren = true
                clipToPadding = true
            }

            val cutoutSpacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    pillHeightPx
                )
            }
            capsuleLayout.addView(cutoutSpacer)

            val contentRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    contentRowHeight
                )
                setPadding(dpToPx(context, 14), dpToPx(context, 2), dpToPx(context, 18), dpToPx(context, 6))
                alpha = 0f
                translationY = -dpToPx(context, 8).toFloat()
            }

            val avatarSize = dpToPx(context, 34)
            val avatarView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                    marginEnd = dpToPx(context, 10)
                }
                if (avatarDrawable != null) {
                    setImageBitmap(drawableToCircularBitmap(avatarDrawable, avatarSize))
                } else {
                    setImageResource(android.R.drawable.sym_action_chat)
                }
            }
            contentRow.addView(avatarView)

            val textView = TextView(context).apply {
                text = senderName
                setTextColor(Color.WHITE)
                textSize = 15f
                paint.isFakeBoldText = true
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = dpToPx(context, 200)
            }
            contentRow.addView(textView)

            val badgeDot = View(context).apply {
                val dotSize = dpToPx(context, 8)
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginStart = dpToPx(context, 10)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#25D366"))
                }
            }
            contentRow.addView(badgeDot)

            capsuleLayout.addView(contentRow)

            contentRow.measure(
                View.MeasureSpec.makeMeasureSpec(context.resources.displayMetrics.widthPixels, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(contentRowHeight, View.MeasureSpec.EXACTLY)
            )
            val targetExpandedWidth = max(
                contentRow.measuredWidth + dpToPx(context, 12),
                max(pillWidthPx + dpToPx(context, 64), dpToPx(context, 190))
            )

            val capsuleParams = FrameLayout.LayoutParams(pillWidthPx, pillHeightPx).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            rootWrapper.addView(capsuleLayout, capsuleParams)

            val windowParams = WindowManager.LayoutParams(
                targetExpandedWidth + dpToPx(context, 48),
                expandedHeight + dpToPx(context, 32),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = pillTopPx
            }

            windowManager.addView(rootWrapper, windowParams)
            activeOverlayRoot = rootWrapper

            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 420L
                interpolator = OvershootInterpolator(1.1f)
                addUpdateListener { anim ->
                    val fraction = anim.animatedValue as Float
                    capsuleParams.width = (pillWidthPx + (targetExpandedWidth - pillWidthPx) * fraction).toInt()
                    capsuleParams.height = (pillHeightPx + (expandedHeight - pillHeightPx) * fraction).toInt()
                    capsuleLayout.layoutParams = capsuleParams

                    val currentRadius = collapsedRadius + (expandedRadius - collapsedRadius) * fraction.coerceIn(0f, 1f)
                    capsuleBackground.cornerRadius = currentRadius
                }
                start()
            }

            contentRow.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(110L)
                .setDuration(240L)
                .start()

            fun collapseAndRemove(onCollapsed: (() -> Unit)? = null) {
                autoDismissRunnable?.let { staticHandler.removeCallbacks(it) }

                contentRow.animate()
                    .alpha(0f)
                    .translationY(-dpToPx(context, 8).toFloat())
                    .setStartDelay(0L)
                    .setDuration(110L)
                    .start()

                val startW = capsuleParams.width
                val startH = capsuleParams.height

                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 280L
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { anim ->
                        val fraction = anim.animatedValue as Float
                        capsuleParams.width = (startW - (startW - pillWidthPx) * fraction).toInt()
                        capsuleParams.height = (startH - (startH - pillHeightPx) * fraction).toInt()
                        capsuleLayout.layoutParams = capsuleParams

                        val currentRadius = expandedRadius - (expandedRadius - collapsedRadius) * fraction.coerceIn(0f, 1f)
                        capsuleBackground.cornerRadius = currentRadius
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            try {
                                windowManager.removeViewImmediate(rootWrapper)
                            } catch (_: Exception) {}
                            if (activeOverlayRoot == rootWrapper) {
                                activeOverlayRoot = null
                            }
                            onCollapsed?.invoke()
                        }
                    })
                    start()
                }
            }

            fun scheduleAutoDismiss() {
                autoDismissRunnable?.let { staticHandler.removeCallbacks(it) }
                autoDismissRunnable = Runnable {
                    collapseAndRemove()
                }.also {
                    staticHandler.postDelayed(it, 4000L)
                }
            }

            attachTouchAndSwipeHandler(
                context = context,
                capsuleLayout = capsuleLayout,
                contentRow = contentRow,
                rootWrapper = rootWrapper,
                windowManager = windowManager,
                pendingIntent = pendingIntent,
                onCollapseIntoCutout = { afterCollapse -> collapseAndRemove(afterCollapse) },
                onResetAutoDismiss = { scheduleAutoDismiss() }
            )

            scheduleAutoDismiss()
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun attachTouchAndSwipeHandler(
            context: Context,
            capsuleLayout: LinearLayout,
            contentRow: View,
            rootWrapper: View,
            windowManager: WindowManager,
            pendingIntent: PendingIntent?,
            onCollapseIntoCutout: ((() -> Unit)?) -> Unit,
            onResetAutoDismiss: () -> Unit
        ) {
            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            val swipeUpThreshold = -dpToPx(context, 25).toFloat()
            val swipeSideThreshold = dpToPx(context, 65).toFloat()

            var downRawX = 0f
            var downRawY = 0f
            var isDragging = false

            capsuleLayout.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        autoDismissRunnable?.let { staticHandler.removeCallbacks(it) }
                        downRawX = event.rawX
                        downRawY = event.rawY
                        isDragging = false
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY

                        if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            isDragging = true
                        }

                        if (isDragging) {
                            if (abs(dy) > abs(dx) && dy < 0) {
                                val clampedDy = dy.coerceAtLeast(-dpToPx(context, 48).toFloat())
                                view.translationY = clampedDy * 0.45f
                                contentRow.alpha = (1f - (abs(clampedDy) / dpToPx(context, 36))).coerceIn(0f, 1f)
                            } else {
                                view.translationX = dx
                                view.translationY = (dy * 0.15f).coerceIn(-dpToPx(context, 20).toFloat(), dpToPx(context, 12).toFloat())
                                val progress = (abs(dx) / dpToPx(context, 140)).coerceIn(0f, 1f)
                                view.alpha = 1f - (progress * 0.75f)
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY

                        when {
                            !isDragging -> {
                                onCollapseIntoCutout {
                                    try {
                                        pendingIntent?.send()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }

                            dy < swipeUpThreshold && abs(dy) > abs(dx) -> {
                                onCollapseIntoCutout(null)
                            }

                            abs(dx) > swipeSideThreshold -> {
                                val targetX = if (dx > 0) dpToPx(context, 260).toFloat() else -dpToPx(context, 260).toFloat()
                                view.animate()
                                    .translationX(targetX)
                                    .alpha(0f)
                                    .scaleX(0.85f)
                                    .scaleY(0.85f)
                                    .setDuration(180L)
                                    .withEndAction {
                                        try {
                                            windowManager.removeViewImmediate(rootWrapper)
                                        } catch (_: Exception) {}
                                        if (activeOverlayRoot == rootWrapper) {
                                            activeOverlayRoot = null
                                        }
                                    }
                                    .start()
                            }

                            else -> {
                                view.animate()
                                    .translationX(0f)
                                    .translationY(0f)
                                    .alpha(1f)
                                    .setInterpolator(OvershootInterpolator(1.2f))
                                    .setDuration(220L)
                                    .start()

                                contentRow.animate()
                                    .alpha(1f)
                                    .setDuration(180L)
                                    .start()

                                onResetAutoDismiss()
                            }
                        }
                        true
                    }

                    else -> false
                }
            }
        }
    }
}
