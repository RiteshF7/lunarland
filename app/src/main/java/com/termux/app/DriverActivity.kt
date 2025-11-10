package com.termux.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.termux.R
import kotlinx.coroutines.launch

/**
 * Minimal activity that bridges user-entered commands to [TermuxService].
 * Uses Kotlin Flow to observe ViewModel state.
 */
class DriverActivity : AppCompatActivity() {

    private lateinit var commandInput: EditText
    private lateinit var statusView: TextView
    private lateinit var executeButton: Button
    private lateinit var logsContainer: NestedScrollView
    private lateinit var logsView: TextView
    private var receiverRegistered: Boolean = false
    private lateinit var viewModel: DriverViewModel

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val extras = DriverViewModel.BundleExtras.from(intent)
            runOnUiThread {
                if (extras == null) {
                    viewModel.handleMissingResult()
                } else {
                    viewModel.handleCommandResult(extras)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver)

        commandInput = findViewById(R.id.driver_command_input)
        statusView = findViewById(R.id.driver_status_view)
        executeButton = findViewById(R.id.driver_execute_button)
        logsContainer = findViewById(R.id.driver_logs_container)
        logsView = findViewById(R.id.driver_logs_view)

        viewModel = ViewModelProvider(this).get(DriverViewModel::class.java)

        // Collect Flows with lifecycle awareness
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.statusText.collect { status ->
                        statusView.text = status
                    }
                }
                launch {
                    viewModel.executeEnabled.collect { enabled ->
                        executeButton.isEnabled = enabled
                    }
                }
                launch {
                    viewModel.logsText.collect { logs ->
                        logsView.text = logs
                        logsContainer.post { logsContainer.fullScroll(View.FOCUS_DOWN) }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            DriverViewModel.CommandEventType.CLEAR_COMMAND_INPUT -> commandInput.text.clear()
                        }
                    }
                }
            }
        }

        executeButton.setOnClickListener {
            viewModel.executeCommand(this, commandInput.text.toString())
        }
        commandInput.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        registerResultReceiver()
        viewModel.ensureBootstrapSetup(this)
    }

    override fun onStop() {
        super.onStop()
        unregisterResultReceiver()
        viewModel.stopLogcatWatcher()
    }

    private fun registerResultReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(DriverViewModel.ACTION_DRIVER_RESULT)
        registerReceiver(resultReceiver, filter)
        receiverRegistered = true
    }

    private fun unregisterResultReceiver() {
        if (!receiverRegistered) return
        try {
            unregisterReceiver(resultReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver already unregistered; ignore.
        }
        receiverRegistered = false
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopLogcatWatcher()
    }
}