package com.termux.app.driver

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64
import com.droidrun.portal.config.ConfigManager
import lunar.land.ui.core.theme.LunarTheme
import lunar.land.ui.core.ui.LunarButtonBlack

/**
 * Activity to test all Portal SDK endpoints.
 * Tests both GET and POST endpoints by making HTTP requests to localhost:8081.
 */
class PortalEndpointTestActivity : ComponentActivity() {
    private val LOG_TAG = "PortalEndpointTest"
    private val SERVER_URL = "http://localhost:8081"
    private val DEFAULT_PORT = 8081

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PortalEndpointTestScreen()
        }
    }

    @Composable
    fun PortalEndpointTestScreen() {
        var serverUrl by remember { mutableStateOf(SERVER_URL) }
        var responseText by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        // Get auth token from ConfigManager
        val authToken = remember {
            ConfigManager.getInstance(this@PortalEndpointTestActivity).authToken
        }

        // Initialize with actual port from config
        // For Android emulator, use 10.0.2.2 instead of localhost
        // For physical device or same-app execution, localhost should work
        LaunchedEffect(Unit) {
            try {
                val configManager = ConfigManager.getInstance(this@PortalEndpointTestActivity)
                val port = configManager.socketServerPort
                // Try localhost first (works when running in same app/process)
                // If that doesn't work, user can manually change to 10.0.2.2 for emulator
                serverUrl = "http://localhost:$port"
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error initializing server URL", e)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Portal SDK Endpoint Tester",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.Black
                )

                // Server URL input
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "GET Endpoints",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth()
                )

                // GET Endpoints
                EndpointButton("Health", isLoading) {
                    Log.d(LOG_TAG, "Health button clicked")
                    scope.launch {
                        withContext(Dispatchers.Main) { 
                            isLoading = true
                            Log.d(LOG_TAG, "Loading started")
                        }
                        try {
                            Log.d(LOG_TAG, "Testing GET /health endpoint")
                            val result = testGetEndpoint(serverUrl, "/health", "")
                            withContext(Dispatchers.Main) { 
                                responseText = result
                                Log.d(LOG_TAG, "Response received: ${result.take(100)}")
                            }
                        } catch (e: Exception) {
                            Log.e(LOG_TAG, "Error in Health endpoint", e)
                            withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                        } finally {
                            withContext(Dispatchers.Main) { 
                                isLoading = false
                                Log.d(LOG_TAG, "Loading finished")
                            }
                        }
                    }
                }

                EndpointButton("Ping", isLoading) {
                    scope.launch {
                        withContext(Dispatchers.Main) { isLoading = true }
                        try {
                            val result = testGetEndpoint(serverUrl, "/ping", authToken)
                            withContext(Dispatchers.Main) { responseText = result }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                        } finally {
                            withContext(Dispatchers.Main) { isLoading = false }
                        }
                    }
                }

                EndpointButton("A11y Tree", isLoading) {
                    scope.launch {
                        withContext(Dispatchers.Main) { isLoading = true }
                        try {
                            val result = testGetEndpoint(serverUrl, "/a11y_tree", authToken)
                            withContext(Dispatchers.Main) { responseText = result }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                        } finally {
                            withContext(Dispatchers.Main) { isLoading = false }
                        }
                    }
                }

                EndpointButton("A11y Tree Full", isLoading) {
                    scope.launch {
                        withContext(Dispatchers.Main) { isLoading = true }
                        try {
                            val result = testGetEndpoint(serverUrl, "/a11y_tree_full?filter=true", authToken)
                            withContext(Dispatchers.Main) { responseText = result }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                        } finally {
                            withContext(Dispatchers.Main) { isLoading = false }
                        }
                    }
                }

                EndpointButton("State", isLoading) {
                    scope.launch {
                        withContext(Dispatchers.Main) { isLoading = true }
                        try {
                            val result = testGetEndpoint(serverUrl, "/state", authToken)
                            withContext(Dispatchers.Main) { responseText = result }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                        } finally {
                            withContext(Dispatchers.Main) { isLoading = false }
                        }
                    }
                }

                EndpointButton("State Full", isLoading) {
                    scope.launch {
                        withContext(Dispatchers.Main) { isLoading = true }
                        try {
                            val result = testGetEndpoint(serverUrl, "/state_full?filter=true", authToken)
                            withContext(Dispatchers.Main) { responseText = result }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                        } finally {
                            withContext(Dispatchers.Main) { isLoading = false }
                        }
                    }
                }

                EndpointButton("Phone State", isLoading) {
                    scope.launch {
                        withContext(Dispatchers.Main) { isLoading = true }
                        try {
                            val result = testGetEndpoint(serverUrl, "/phone_state", authToken)
                            withContext(Dispatchers.Main) { responseText = result }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                        } finally {
                            withContext(Dispatchers.Main) { isLoading = false }
                        }
                    }
                }

                EndpointButton("Version", isLoading) {
                    scope.launch {
                        withContext(Dispatchers.Main) { isLoading = true }
                        try {
                            val result = testGetEndpoint(serverUrl, "/version", authToken)
                            withContext(Dispatchers.Main) { responseText = result }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                        } finally {
                            withContext(Dispatchers.Main) { isLoading = false }
                        }
                    }
                }

                EndpointButton("Packages", isLoading) {
                    scope.launch {
                        withContext(Dispatchers.Main) { isLoading = true }
                        try {
                            val result = testGetEndpoint(serverUrl, "/packages", authToken)
                            withContext(Dispatchers.Main) { responseText = result }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                        } finally {
                            withContext(Dispatchers.Main) { isLoading = false }
                        }
                    }
                }

                EndpointButton("Screenshot", isLoading) {
                    scope.launch {
                        withContext(Dispatchers.Main) { isLoading = true }
                        try {
                            val result = testGetEndpoint(serverUrl, "/screenshot?hideOverlay=true", authToken)
                            withContext(Dispatchers.Main) { responseText = result }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                        } finally {
                            withContext(Dispatchers.Main) { isLoading = false }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "POST Endpoints",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth()
                )

                // POST Endpoints - Tap
                var tapX by remember { mutableStateOf("500") }
                var tapY by remember { mutableStateOf("500") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tapX,
                        onValueChange = { tapX = it },
                        label = { Text("X") },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    )
                    OutlinedTextField(
                        value = tapY,
                        onValueChange = { tapY = it },
                        label = { Text("Y") },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    )
                    LunarButtonBlack(
                        text = "Tap",
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.Main) { isLoading = true }
                                try {
                                    val params = JSONObject().apply {
                                        put("x", tapX.toIntOrNull() ?: 500)
                                        put("y", tapY.toIntOrNull() ?: 500)
                                    }
                                    val result = testPostEndpoint(serverUrl, "/tap", params, authToken)
                                    withContext(Dispatchers.Main) { responseText = result }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                                } finally {
                                    withContext(Dispatchers.Main) { isLoading = false }
                                }
                            }
                        },
                        enabled = !isLoading
                    )
                }

                // POST Endpoints - Swipe
                var swipeStartX by remember { mutableStateOf("100") }
                var swipeStartY by remember { mutableStateOf("500") }
                var swipeEndX by remember { mutableStateOf("900") }
                var swipeEndY by remember { mutableStateOf("500") }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = swipeStartX,
                            onValueChange = { swipeStartX = it },
                            label = { Text("Start X") },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        )
                        OutlinedTextField(
                            value = swipeStartY,
                            onValueChange = { swipeStartY = it },
                            label = { Text("Start Y") },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = swipeEndX,
                            onValueChange = { swipeEndX = it },
                            label = { Text("End X") },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        )
                        OutlinedTextField(
                            value = swipeEndY,
                            onValueChange = { swipeEndY = it },
                            label = { Text("End Y") },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        )
                    }
                    LunarButtonBlack(
                        text = "Swipe",
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.Main) { isLoading = true }
                                try {
                                    val params = JSONObject().apply {
                                        put("startX", swipeStartX.toIntOrNull() ?: 100)
                                        put("startY", swipeStartY.toIntOrNull() ?: 500)
                                        put("endX", swipeEndX.toIntOrNull() ?: 900)
                                        put("endY", swipeEndY.toIntOrNull() ?: 500)
                                    }
                                    val result = testPostEndpoint(serverUrl, "/swipe", params, authToken)
                                    withContext(Dispatchers.Main) { responseText = result }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                                } finally {
                                    withContext(Dispatchers.Main) { isLoading = false }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )
                }

                // POST Endpoints - Keyboard Input
                var keyboardText by remember { mutableStateOf("Hello, Portal!") }
                var clearBeforeInput by remember { mutableStateOf(true) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = keyboardText,
                        onValueChange = { keyboardText = it },
                        label = { Text("Text to input") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = clearBeforeInput,
                            onCheckedChange = { clearBeforeInput = it },
                            enabled = !isLoading
                        )
                        Text("Clear before input", color = Color.Black)
                        Spacer(modifier = Modifier.weight(1f))
                        LunarButtonBlack(
                            text = "Input",
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.Main) { isLoading = true }
                                    try {
                                        val base64Text = Base64.encodeToString(
                                            keyboardText.toByteArray(Charsets.UTF_8),
                                            Base64.NO_WRAP
                                        )
                                        val params = JSONObject().apply {
                                            put("base64_text", base64Text)
                                            put("clear", clearBeforeInput)
                                        }
                                        val result = testPostEndpoint(serverUrl, "/keyboard/input", params, authToken)
                                        withContext(Dispatchers.Main) { responseText = result }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                                    } finally {
                                        withContext(Dispatchers.Main) { isLoading = false }
                                    }
                                }
                            },
                            enabled = !isLoading
                        )
                    }
                    LunarButtonBlack(
                        text = "Clear Keyboard",
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.Main) { isLoading = true }
                                try {
                                    val params = JSONObject()
                                    val result = testPostEndpoint(serverUrl, "/keyboard/clear", params, authToken)
                                    withContext(Dispatchers.Main) { responseText = result }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                                } finally {
                                    withContext(Dispatchers.Main) { isLoading = false }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )
                }

                // POST Endpoints - Overlay Offset
                var overlayOffset by remember { mutableStateOf("0") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = overlayOffset,
                        onValueChange = { overlayOffset = it },
                        label = { Text("Offset") },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    )
                    LunarButtonBlack(
                        text = "Set Offset",
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.Main) { isLoading = true }
                                try {
                                    val params = JSONObject().apply {
                                        put("offset", overlayOffset.toIntOrNull() ?: 0)
                                    }
                                    val result = testPostEndpoint(serverUrl, "/overlay_offset", params, authToken)
                                    withContext(Dispatchers.Main) { responseText = result }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                                } finally {
                                    withContext(Dispatchers.Main) { isLoading = false }
                                }
                            }
                        },
                        enabled = !isLoading
                    )
                }

                // POST Endpoints - App Launch
                var appPackage by remember { mutableStateOf("com.android.settings") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = appPackage,
                        onValueChange = { appPackage = it },
                        label = { Text("Package Name") },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    )
                    LunarButtonBlack(
                        text = "Launch",
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.Main) { isLoading = true }
                                try {
                                    val params = JSONObject().apply {
                                        put("package", appPackage)
                                    }
                                    val result = testPostEndpoint(serverUrl, "/app", params, authToken)
                                    withContext(Dispatchers.Main) { responseText = result }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                                } finally {
                                    withContext(Dispatchers.Main) { isLoading = false }
                                }
                            }
                        },
                        enabled = !isLoading
                    )
                }

                // POST Endpoints - Global Action
                var globalAction by remember { mutableStateOf("3") } // 3 = HOME
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = globalAction,
                        onValueChange = { globalAction = it },
                        label = { Text("Action ID (3=HOME, 4=BACK)") },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    )
                    LunarButtonBlack(
                        text = "Global Action",
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.Main) { isLoading = true }
                                try {
                                    val params = JSONObject().apply {
                                        put("action", globalAction.toIntOrNull() ?: 3)
                                    }
                                    val result = testPostEndpoint(serverUrl, "/global", params, authToken)
                                    withContext(Dispatchers.Main) { responseText = result }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { responseText = "Error: ${e.message}" }
                                } finally {
                                    withContext(Dispatchers.Main) { isLoading = false }
                                }
                            }
                        },
                        enabled = !isLoading
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Response Display
                Text(
                    text = "Response:",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }

                OutlinedTextField(
                    value = responseText,
                    onValueChange = { },
                    label = { Text("Response") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    enabled = false,
                    readOnly = true
                )
            }
        }
    }

    @Composable
    fun EndpointButton(
        text: String,
        isLoading: Boolean,
        onClick: () -> Unit
    ) {
        LunarButtonBlack(
            text = text,
            onClick = onClick,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )
    }

    private suspend fun testGetEndpoint(
        baseUrl: String,
        path: String,
        authToken: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl$path")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            // Health endpoint doesn't require auth
            if (path != "/health" && path != "/ping" && authToken.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer $authToken")
            }
            connection.connectTimeout = 5000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            val inputStream = if (responseCode >= 200 && responseCode < 300) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            val response = reader.readText()
            reader.close()

            if (path.startsWith("/screenshot")) {
                "Screenshot received (binary data, ${response.length} bytes)"
            } else {
                "Status: $responseCode\n\n$response"
            }
        } catch (e: java.net.ConnectException) {
            Log.e(LOG_TAG, "Connection error testing GET endpoint $path", e)
            "Connection Error: ${e.message}\n\nTips:\n- Make sure portal server is running\n- For same-app access, localhost should work\n- For emulator from host, use 10.0.2.2 instead of localhost\n- Check server URL: $baseUrl"
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error testing GET endpoint $path", e)
            "Error: ${e.message}\n\nMake sure:\n1. Accessibility service is enabled\n2. Portal server is running on $baseUrl\n3. Auth token is correct"
        }
    }

    private suspend fun testPostEndpoint(
        baseUrl: String,
        path: String,
        params: JSONObject,
        authToken: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl$path")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            if (authToken.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer $authToken")
            }
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 10000

            val outputStream = OutputStreamWriter(connection.outputStream)
            outputStream.write(params.toString())
            outputStream.flush()
            outputStream.close()

            val responseCode = connection.responseCode
            val inputStream = if (responseCode >= 200 && responseCode < 300) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            val response = reader.readText()
            reader.close()
            connection.disconnect()

            "Status: $responseCode\n\nRequest: ${params.toString()}\n\nResponse: $response"
        } catch (e: java.net.ConnectException) {
            Log.e(LOG_TAG, "Connection error testing POST endpoint $path", e)
            "Connection Error: ${e.message}\n\nTips:\n- Make sure portal server is running\n- Try using 10.0.2.2 instead of localhost (for emulator)\n- Check server URL: $baseUrl"
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error testing POST endpoint $path", e)
            "Error: ${e.message}\n\nMake sure:\n1. Accessibility service is enabled\n2. Portal server is running on $baseUrl\n3. Auth token is correct\n4. For emulator, use http://10.0.2.2:8081 instead of localhost"
        }
    }
}

