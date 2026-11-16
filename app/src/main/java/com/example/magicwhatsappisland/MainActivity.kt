package com.example.magicwhatsappisland

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#0F0F0F"))
            setPadding(64, 140, 64, 64)
        }

        val title = TextView(this).apply {
            text = "Honor Magic 8 Pro\nWhatsApp Island"
            textSize = 24f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        val subtitle = TextView(this).apply {
            text = "Hides WhatsApp message previews & displays only the sender's name and circular avatar underneath the camera pill cutout."
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 64)
        }

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#25D366"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }

        val btnNotifAccess = createStyledButton("1. Grant Notification Access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val btnOverlay = createStyledButton("2. Grant Magic Island Overlay Permission") {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        val btnTestCapsule = createStyledButton("3. Test Magic Island Capsule Now") {
            WhatsAppIslandListener.triggerTestOverlay(this)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(statusText)
        root.addView(btnNotifAccess)
        root.addView(btnOverlay)
        root.addView(btnTestCapsule)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val overlayGranted = Settings.canDrawOverlays(this)
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val notifGranted = enabledListeners.contains(packageName)

        statusText.text = "Notification Access: ${if (notifGranted) "ACTIVE ✅" else "MISSING ❌"}\n" +
                "Overlay Permission: ${if (overlayGranted) "ACTIVE ✅" else "MISSING ❌"}"
    }

    private fun createStyledButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            isAllCaps = false
            textSize = 15f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(Color.parseColor("#1E1E1E"))
                setStroke(3, Color.parseColor("#25D366"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 36
            }
            setPadding(32, 36, 32, 36)
            setOnClickListener { onClick() }
        }
    }
}
