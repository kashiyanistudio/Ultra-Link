package com.kashiyani.ultralink

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvChatHistory: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnSend: Button
    private lateinit var etMessage: EditText
    private lateinit var scrollView: ScrollView

    private val serviceId = "com.kashiyani.ultralink.OFFLINE_CHAT"
    private val myDeviceName = Build.MODEL
    private var connectedEndpointId: String? = null
    private var isSearching = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startUltraLink()
        } else {
            Toast.makeText(this, "Offline chat ke liye tamam permissions zaroori hain!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvChatHistory = findViewById(R.id.tvChatHistory)
        btnConnect = findViewById(R.id.btnConnect)
        btnSend = findViewById(R.id.btnSend)
        etMessage = findViewById(R.id.etMessage)
        scrollView = findViewById(R.id.scrollView)

        btnConnect.setOnClickListener {
            if (connectedEndpointId != null) {
                disconnectAll()
            } else if (!isSearching) {
                checkPermissionsAndStart()
            }
        }

        btnSend.setOnClickListener {
            val msg = etMessage.text.toString().trim()
            if (msg.isNotEmpty() && connectedEndpointId != null) {
                sendMessage(msg)
                etMessage.text.clear()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startUltraLink()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startUltraLink() {
        isSearching = true
        tvStatus.text = "Status: Nearby device dhoondh raha hai..."
        btnConnect.text = "Searching..."

        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()

        Nearby.getConnectionsClient(this)
            .startAdvertising(myDeviceName, serviceId, connectionLifecycleCallback, options)

        Nearby.getConnectionsClient(this)
            .startDiscovery(serviceId, endpointDiscoveryCallback, discoveryOptions)
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            tvStatus.text = "Status: ${info.endpointName} mil gaya! Connecting..."
            Nearby.getConnectionsClient(this@MainActivity)
                .requestConnection(myDeviceName, endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Nearby.getConnectionsClient(this@MainActivity)
                .acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpointId = endpointId
                isSearching = false
                Nearby.getConnectionsClient(this@MainActivity).stopAdvertising()
                Nearby.getConnectionsClient(this@MainActivity).stopDiscovery()

                tvStatus.text = "Status: Connected (Offline Ready)"
                btnConnect.text = "Disconnect"
                btnSend.isEnabled = true
                appendChat("--- Device Connected Offline! Ab aap chat kar sakte hain ---")
            } else {
                tvStatus.text = "Status: Connection Failed. Dobara try karein."
                btnConnect.text = "Search & Connect"
                isSearching = false
            }
        }

        override fun onDisconnected(endpointId: String) {
            disconnectAll()
            appendChat("--- Device Disconnected ---")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                val receivedText = String(bytes, StandardCharsets.UTF_8)
                appendChat("Friend: $receivedText")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun sendMessage(text: String) {
        val endpoint = connectedEndpointId ?: return
        val payload = Payload.fromBytes(text.toByteArray(StandardCharsets.UTF_8))
        Nearby.getConnectionsClient(this).sendPayload(endpoint, payload)
        appendChat("You: $text")
    }

    private fun disconnectAll() {
        connectedEndpointId?.let {
            Nearby.getConnectionsClient(this).disconnectFromEndpoint(it)
        }
        Nearby.getConnectionsClient(this).stopAllEndpoints()
        connectedEndpointId = null
        isSearching = false
        btnSend.isEnabled = false
        btnConnect.text = "Search & Connect"
        tvStatus.text = "Status: Disconnected"
    }

    private fun appendChat(message: String) {
        tvChatHistory.append("$message\n")
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectAll()
    }
}
