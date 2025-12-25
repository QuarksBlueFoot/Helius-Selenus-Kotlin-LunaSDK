package com.selenus.luna.sample

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.selenus.luna.Cluster
import com.selenus.luna.LunaHeliusClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // TODO: Replace with your actual Helius API key
    private val apiKey = "REDACTED_API_KEY"
    private val client = LunaHeliusClient(apiKey, Cluster.MAINNET)

    private lateinit var tvOutput: TextView
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Premium Dark Theme Setup
        val backgroundColor = Color.parseColor("#121212")
        val cardColor = Color.parseColor("#1E1E1E")
        val accentColor = Color.parseColor("#BB86FC")
        val textColor = Color.WHITE

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
        }
        
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        scrollView.addView(container)
        
        // Header
        val header = TextView(this).apply {
            text = "LunaSDK Demo"
            textSize = 24f
            setTextColor(accentColor)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 32)
        }
        container.addView(header)

        // Output Console
        tvOutput = TextView(this).apply {
            text = "Select a feature to test..."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2C2C2C"))
                cornerRadius = 16f
                setStroke(2, Color.parseColor("#333333"))
            }
            setPadding(32, 32, 32, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 48)
            }
        }
        container.addView(tvOutput)

        // Feature Buttons
        FeatureRegistry.getFeatures().forEach { feature ->
            val btn = Button(this).apply {
                text = "${feature.category}: ${feature.name}"
                setTextColor(textColor)
                background = GradientDrawable().apply {
                    setColor(cardColor)
                    cornerRadius = 24f
                    setStroke(2, accentColor)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 24)
                }
                setOnClickListener {
                    runFeature(feature)
                }
            }
            container.addView(btn)
        }

        setContentView(scrollView)

        if (apiKey == "YOUR_API_KEY") {
            Toast.makeText(this, "Please set your API Key in MainActivity.kt", Toast.LENGTH_LONG).show()
        }
    }

    private fun runFeature(feature: FeatureDemo) {
        tvOutput.text = "Running ${feature.name}..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                feature.action(client) { message ->
                    runOnUiThread {
                        tvOutput.text = "${feature.name}:\n\n$message"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvOutput.text = "Error running ${feature.name}:\n${e.message}"
                }
            }
        }
    }
}
