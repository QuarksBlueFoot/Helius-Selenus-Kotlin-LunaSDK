package com.selenus.luna.sample

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.selenus.luna.Cluster
import com.selenus.luna.LunaHeliusClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    // TODO: Replace with your actual Helius API key
    private val apiKey = "1d638e81-b3b4-45ed-a997-a15f79163a0c"
    private val client = LunaHeliusClient(apiKey, Cluster.MAINNET)

    
    private lateinit var tvOutput: TextView
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Programmatic UI for flexibility
        val scrollView = ScrollView(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            padding = 32 // dp conversion omitted for brevity
        }
        scrollView.addView(container)
        
        tvOutput = TextView(this).apply {
            text = "Select a feature to test..."
            textSize = 16f
            setPadding(0, 0, 0, 32)
        }
        container.addView(tvOutput)

        FeatureRegistry.getFeatures().forEach { feature ->
            val btn = Button(this).apply {
                text = "${feature.category}: ${feature.name}"
                setOnClickListener {
                    runFeature(feature)
                }
            }
            container.addView(btn)
        }

        setContentView(scrollView)
    }

    private fun runFeature(feature: FeatureDemo) {
        tvOutput.text = "Running ${feature.name}..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                feature.action(client) { message ->
                    runOnUiThread {
                        tvOutput.text = "${feature.name}:\n$message"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvOutput.text = "Error running ${feature.name}:\n${e.message}"
                }
            }
        }
    }
    
    // Helper for padding (simplified)
    private var LinearLayout.padding: Int
        get() = 0
        set(value) { setPadding(value, value, value, value) }
}
