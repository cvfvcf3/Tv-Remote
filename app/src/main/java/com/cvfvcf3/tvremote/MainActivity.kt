package com.cvfvcf3.tvremote

import android.content.SharedPreferences
import android.os.*
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.*
import java.net.*
import java.util.concurrent.*

class MainActivity : AppCompatActivity() {

    private val PORT = 9000
    private val DEVICE_NAME = "Vivo Y20"
    private val APPS_LIST = "com.android.settings|com.android.browser|com.android.camera"

    private var tvSocket: Socket? = null
    private var tvWriter: PrintWriter? = null
    private var tvIp = ""
    private var isConnected = false
    private var isReconnecting = false
    private val executor = Executors.newSingleThreadExecutor()
    private val uiHandler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private lateinit var prefs: SharedPreferences

    private lateinit var layoutConnect: LinearLayout
    private lateinit var layoutRemote: LinearLayout
    private lateinit var etIpAddress: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnManualConnect: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvConnectedIp: TextView
    private lateinit var progressSearch: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("tvremote", MODE_PRIVATE)
        tvIp = prefs.getString("last_ip", "") ?: ""

        initViews()
        setupButtons()

        if (tvIp.isNotEmpty()) {
            etIpAddress.setText(tvIp)
            connectToTv(tvIp)
        }
    }

    private fun initViews() {
        layoutConnect = findViewById(R.id.layoutConnect)
        layoutRemote = findViewById(R.id.layoutRemote)
        etIpAddress = findViewById(R.id.etIpAddress)
        btnSearch = findViewById(R.id.btnSearch)
        btnManualConnect = findViewById(R.id.btnManualConnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvConnectedIp = findViewById(R.id.tvConnectedIp)
        progressSearch = findViewById(R.id.progressSearch)
    }

    private fun setupButtons() {
        btnSearch.setOnClickListener { autoSearchTv() }
        btnManualConnect.setOnClickListener {
            val ip = etIpAddress.text.toString().trim()
            if (ip.isNotEmpty()) connectToTv(ip)
            else toast("IP address darj karein")
        }
        findViewById<Button>(R.id.btnDisconnect).setOnClickListener {
            disconnectAndGoBack()
        }
        setKey(R.id.btnPower, 26)
        setKey(R.id.btnUp, 19)
        setKey(R.id.btnDown, 20)
        setKey(R.id.btnLeft, 21)
        setKey(R.id.btnRight, 22)
        setKey(R.id.btnOk, 23)
        setKey(R.id.btnHome, 3)
        setKey(R.id.btnBack, 4)
        setKey(R.id.btnMenu, 82)
        setKey(R.id.btnVolUp, 24)
        setKey(R.id.btnVolDown, 25)
        setKey(R.id.btnMute, 164)
        setKey(R.id.btnChUp, 166)
        setKey(R.id.btnChDown, 167)
        setKey(R.id.btnPlayPause, 85)
        setKey(R.id.btnStop, 86)
        setKey(R.id.btn0, 7)
        setKey(R.id.btn1, 8)
        setKey(R.id.btn2, 9)
        setKey(R.id.btn3, 10)
        setKey(R.id.btn4, 11)
        setKey(R.id.btn5, 12)
        setKey(R.id.btn6, 13)
        setKey(R.id.btn7, 14)
        setKey(R.id.btn8, 15)
        setKey(R.id.btn9, 16)
        setKey(R.id.btnSleep, 178)
    }

    private fun setKey(viewId: Int, keycode: Int) {
        findViewById<View>(viewId).setOnClickListener { sendKey(keycode) }
    }

    private fun sendKey(keycode: Int) {
        if (!isConnected) {
            toast("TV se connected nahi hai")
            reconnect()
            return
        }
        executor.execute {
            try {
                tvWriter?.print("key=action;$keycode&")
                tvWriter?.flush()
                if (tvWriter?.checkError() == true) throw IOException("Writer error")
            } catch (e: Exception) {
                isConnected = false
                uiHandler.post {
                    tvStatus.text = "● Disconnected - Reconnecting..."
                    tvStatus.setTextColor(resources.getColor(android.R.color.holo_orange_light, null))
                }
                reconnect()
            }
        }
    }

    private fun connectToTv(ip: String) {
        setStatus("Connecting to $ip...", false)
        executor.execute {
            try {
                tvSocket?.close()
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, PORT), 5000)
                socket.keepAlive = true
                socket.soTimeout = 0
                tvSocket = socket
                tvWriter = PrintWriter(BufferedWriter(OutputStreamWriter(socket.outputStream)), true)

                // Sahi handshake — fname + apps list
                tvWriter?.print("fname=$DEVICE_NAME&apps=$APPS_LIST&")
                tvWriter?.flush()

                tvIp = ip
                isConnected = true
                prefs.edit().putString("last_ip", ip).apply()

                uiHandler.post {
                    layoutConnect.visibility = View.GONE
                    layoutRemote.visibility = View.VISIBLE
                    tvConnectedIp.text = "TV: $ip"
                    tvStatus.text = "● Connected"
                    tvStatus.setTextColor(resources.getColor(android.R.color.holo_green_light, null))
                    cancelReconnect()
                }

            } catch (e: Exception) {
                isConnected = false
                uiHandler.post {
                    if (layoutRemote.visibility == View.VISIBLE) {
                        tvStatus.text = "● Disconnected"
                        tvStatus.setTextColor(resources.getColor(android.R.color.holo_red_light, null))
                        reconnect()
                    } else {
                        setStatus("Connect nahi hua — pehle TV pe remote app kholen!", true)
                        progressSearch.visibility = View.GONE
                        btnSearch.isEnabled = true
                        btnManualConnect.isEnabled = true
                    }
                }
            }
        }
    }

    private fun reconnect() {
        if (isReconnecting || tvIp.isEmpty()) return
        isReconnecting = true
        reconnectRunnable = Runnable {
            if (!isConnected && tvIp.isNotEmpty()) {
                connectToTv(tvIp)
                isReconnecting = false
            }
        }
        uiHandler.postDelayed(reconnectRunnable!!, 3000)
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { uiHandler.removeCallbacks(it) }
        isReconnecting = false
    }

    private fun autoSearchTv() {
        btnSearch.isEnabled = false
        btnManualConnect.isEnabled = false
        progressSearch.visibility = View.VISIBLE
        setStatus("TV dhundh raha hai — pehle TV pe remote app kholen!", false)

        val wifiMgr = applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
        val ipInt = wifiMgr.connectionInfo.ipAddress
        val myIp = String.format(
            "%d.%d.%d.%d",
            ipInt and 0xff,
            ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff,
            ipInt shr 24 and 0xff
        )
        val subnet = myIp.substringBeforeLast(".")

        executor.execute {
            val scanExecutor = Executors.newFixedThreadPool(50)
            val futures = mutableListOf<Future<String?>>()

            for (i in 1..254) {
                val ip = "$subnet.$i"
                futures.add(scanExecutor.submit<String?> {
                    try {
                        val s = Socket()
                        s.connect(InetSocketAddress(ip, PORT), 300)
                        s.close()
                        ip
                    } catch (e: Exception) { null }
                })
            }

            scanExecutor.shutdown()
            scanExecutor.awaitTermination(15, TimeUnit.SECONDS)

            var found = false
            for (f in futures) {
                val ip = try { f.get() } catch (e: Exception) { null }
                if (ip != null) {
                    found = true
                    uiHandler.post {
                        etIpAddress.setText(ip)
                        progressSearch.visibility = View.GONE
                        btnSearch.isEnabled = true
                        btnManualConnect.isEnabled = true
                        setStatus("TV mila: $ip", false)
                        connectToTv(ip)
                    }
                    break
                }
            }

            if (!found) {
                uiHandler.post {
                    progressSearch.visibility = View.GONE
                    btnSearch.isEnabled = true
                    btnManualConnect.isEnabled = true
                    setStatus("TV nahi mila — pehle TV pe remote app kholen!", true)
                }
            }
        }
    }

    private fun disconnectAndGoBack() {
        cancelReconnect()
        isConnected = false
        tvIp = ""
        executor.execute {
            try { tvSocket?.close() } catch (e: Exception) {}
            tvSocket = null
            tvWriter = null
        }
        layoutRemote.visibility = View.GONE
        layoutConnect.visibility = View.VISIBLE
        setStatus("Disconnected", false)
    }

    private fun setStatus(msg: String, isError: Boolean) {
        tvStatus.text = msg
        tvStatus.setTextColor(
            resources.getColor(
                if (isError) android.R.color.holo_red_light else android.R.color.white,
                null
            )
        )
    }

    private fun toast(msg: String) {
        uiHandler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelReconnect()
        executor.shutdownNow()
        try { tvSocket?.close() } catch (e: Exception) {}
    }
}
