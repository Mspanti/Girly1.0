package com.pant.girly

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.zxing.integration.android.IntentIntegrator
import java.util.Date
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


data class BluetoothDeviceInfo(val name: String?, val address: String, val isConnected: Boolean = false)
data class RecentWatch(val name: String, val address: String, val lastConnected: Long)

class WatchActivity : AppCompatActivity() {

    private lateinit var heartRateTextView: TextView
    private lateinit var connectionStatusTextView: TextView
    private lateinit var batteryLevelTextView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private lateinit var scanQrButton: Button
    private lateinit var scanButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var recentWatchesRecyclerView: RecyclerView
    private lateinit var devicesAdapter: BluetoothDeviceAdapter
    private lateinit var recentWatchesAdapter: RecentWatchesAdapter
    private val scannedDevices = mutableListOf<BluetoothDeviceInfo>()
    private val recentWatches = mutableListOf<RecentWatch>()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var connectedGatt: BluetoothGatt? = null
    private var isWatchConnected = false
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 1001
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var currentDeviceAddress: String? = null

    // Bluetooth UUIDs
    private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
    private val HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val deviceInfo = BluetoothDeviceInfo(device.name ?: "Unknown", device.address)
                if (!scannedDevices.any { it.address == deviceInfo.address }) {
                    runOnUiThread {
                        scannedDevices.add(deviceInfo)
                        devicesAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isWatchConnected = true
                    runOnUiThread {
                        connectionStatusTextView.text = "Connected"
                        connectionStatusTextView.setTextColor(ContextCompat.getColor(this@WatchActivity, R.color.holo_green_dark))
                        disconnectButton.visibility = View.VISIBLE
                        Toast.makeText(this@WatchActivity, "Connected to device", Toast.LENGTH_SHORT).show()
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isWatchConnected = false
                    runOnUiThread {
                        connectionStatusTextView.text = "Disconnected"
                        connectionStatusTextView.setTextColor(ContextCompat.getColor(this@WatchActivity, R.color.holo_red_dark))
                        disconnectButton.visibility = View.GONE
                        Toast.makeText(this@WatchActivity, "Disconnected from device", Toast.LENGTH_SHORT).show()
                    }
                    tryReconnect()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setupNotifications(gatt)
                currentDeviceAddress?.let { address ->
                    runOnUiThread {
                        recentWatches.removeAll { it.address == address }
                        recentWatches.add(0, RecentWatch(
                            gatt.device.name ?: "Unknown",
                            address,
                            System.currentTimeMillis()
                        ))
                        recentWatchesAdapter.notifyDataSetChanged()
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            when (characteristic.uuid) {
                HEART_RATE_MEASUREMENT_UUID -> {
                    val heartRate = parseHeartRate(characteristic)
                    runOnUiThread {
                        heartRateTextView.text = getString(R.string.heart_rate_format, heartRate)
                        if (heartRate > 120 || heartRate < 50) {
                            triggerSosFromWatch()
                        }
                    }
                }
                BATTERY_LEVEL_UUID -> {
                    val batteryLevel = characteristic.value?.get(0)?.toInt() ?: -1
                    runOnUiThread {
                        batteryLevelTextView.text = getString(R.string.watch_battery_format, batteryLevel)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch)

        // Initialize views
        heartRateTextView = findViewById(R.id.heartRateTextView)
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView)
        batteryLevelTextView = findViewById(R.id.batteryLevelTextView)
        scanQrButton = findViewById(R.id.scanQrButton)
        scanButton = findViewById(R.id.scanButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        recentWatchesRecyclerView = findViewById(R.id.recentWatchesRecyclerView)

        // Setup RecyclerViews
        devicesRecyclerView.layoutManager = LinearLayoutManager(this)
        recentWatchesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        devicesAdapter = BluetoothDeviceAdapter(scannedDevices) { deviceInfo ->
            connectToDevice(deviceInfo.address)
        }

        recentWatchesAdapter = RecentWatchesAdapter(recentWatches) { watch ->
            connectToDevice(watch.address)
        }

        devicesRecyclerView.adapter = devicesAdapter
        recentWatchesRecyclerView.adapter = recentWatchesAdapter

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Initialize Bluetooth
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        // Set up button click listeners
        scanButton.setOnClickListener {
            if (checkBluetoothPermissions()) {
                startBluetoothScan()
            } else {
                requestBluetoothPermissions()
            }
        }

        scanQrButton.setOnClickListener {
            startQRScan()
        }

        disconnectButton.setOnClickListener {
            disconnectFromDevice()
        }


        startHeartRateMonitoring()
        registerBatteryReceiver()
    }

    private fun startQRScan() {
        IntentIntegrator(this).apply {
            setPrompt("Scan Watch QR Code")
            setOrientationLocked(true)
            initiateScan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null && result.contents != null) {
            if (result.contents.startsWith("WATCH:")) {
                connectToDevice(result.contents.substringAfter("WATCH:"))
            } else {
                Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(deviceAddress: String) {
        if (!checkBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }

        try {
            bluetoothAdapter?.getRemoteDevice(deviceAddress)?.let { device ->
                currentDeviceAddress = deviceAddress
                connectedGatt = device.connectGatt(this, false, gattCallback)
                Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, "Invalid device address", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectFromDevice() {
        if (!checkBluetoothPermissions()) return

        connectedGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        connectedGatt = null
        currentDeviceAddress = null
        isWatchConnected = false
    }

    private fun tryReconnect() {
        currentDeviceAddress?.let { address ->
            executor.schedule({
                if (!isWatchConnected) {
                    connectToDevice(address)
                }
            }, 5, TimeUnit.SECONDS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupNotifications(gatt: BluetoothGatt) {
        gatt.services?.forEach { service ->
            when (service.uuid) {
                HEART_RATE_SERVICE_UUID -> {
                    service.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)?.let { characteristic ->
                        gatt.setCharacteristicNotification(characteristic, true)
                        characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?.let { descriptor ->
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                }
                BATTERY_SERVICE_UUID -> {
                    service.getCharacteristic(BATTERY_LEVEL_UUID)?.let { characteristic ->
                        gatt.setCharacteristicNotification(characteristic, true)
                        characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?.let { descriptor ->
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                        gatt.readCharacteristic(characteristic)
                    }
                }
            }
        }
    }

    private fun parseHeartRate(characteristic: BluetoothGattCharacteristic): Int {
        val data = characteristic.value ?: return 0
        if (data.isEmpty()) return 0

        val flags = data[0].toInt()
        return if (flags and 0x01 != 0 && data.size >= 3) {
            (data[2].toInt() shl 8) or data[1].toInt()
        } else if (data.size >= 2) {
            data[1].toInt()
        } else {
            0
        }
    }

    private fun startHeartRateMonitoring() {
        isMonitoring = true
        handler.post(object : Runnable {
            override fun run() {
                if (!isMonitoring) return
                if (!isWatchConnected) {
                    heartRateTextView.text = "Connect Watch"
                }
                handler.postDelayed(this, 5000)
            }
        })
    }

    private fun triggerSosFromWatch() {
        val intent = Intent(this, SosTriggerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        Toast.makeText(this, "Abnormal Heart Rate Detected!", Toast.LENGTH_SHORT).show()
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        triggerSosFromWatch()
                    }
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        if (scale > 0) {
                            val batteryPct = level * 100 / scale
                            batteryLevelTextView.text = getString(R.string.phone_battery_format, batteryPct)
                        }
                    }
                }
            }
        }, filter)
    }

    private fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ), BLUETOOTH_PERMISSION_REQUEST_CODE)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            ), BLUETOOTH_PERMISSION_REQUEST_CODE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothScan() {
        if (!checkBluetoothPermissions()) return

        scannedDevices.clear()
        devicesAdapter.notifyDataSetChanged()
        bluetoothLeScanner?.startScan(scanCallback)

        handler.postDelayed({
            bluetoothLeScanner?.stopScan(scanCallback)
        }, 10000)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startBluetoothScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
        handler.removeCallbacksAndMessages(null)

        executor.shutdown()
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }

        if (checkBluetoothPermissions()) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                return
            }
            connectedGatt?.disconnect()
            connectedGatt?.close()
            bluetoothLeScanner?.stopScan(scanCallback)
        }
    }

    inner class BluetoothDeviceAdapter(
        private val deviceList: List<BluetoothDeviceInfo>,
        private val itemClickListener: (BluetoothDeviceInfo) -> Unit
    ) : RecyclerView.Adapter<BluetoothDeviceAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameTextView: TextView = itemView.findViewById(R.id.deviceName)
            val addressTextView: TextView = itemView.findViewById(R.id.deviceAddress)
            val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_bluetooth_device, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = deviceList[position]
            holder.nameTextView.text = device.name
            holder.addressTextView.text = device.address
            holder.statusIndicator.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context,
                    if (device.isConnected) R.color.holo_green_dark else R.color.gray
                )
            )
            holder.itemView.setOnClickListener { itemClickListener(device) }
        }

        override fun getItemCount() = deviceList.size
    }

    inner class RecentWatchesAdapter(
        private val recentWatches: List<RecentWatch>,
        private val itemClickListener: (RecentWatch) -> Unit
    ) : RecyclerView.Adapter<RecentWatchesAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameTextView: TextView = itemView.findViewById(R.id.watchName)
            val timeTextView: TextView = itemView.findViewById(R.id.lastConnected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recent_watch, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val watch = recentWatches[position]
            holder.nameTextView.text = watch.name
            holder.timeTextView.text = "Last: ${Date(watch.lastConnected).toString().substring(4, 10)}"
            holder.itemView.setOnClickListener { itemClickListener(watch) }
        }

        override fun getItemCount() = recentWatches.size
    }
}