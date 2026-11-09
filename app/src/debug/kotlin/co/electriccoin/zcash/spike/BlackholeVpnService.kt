@file:Suppress("MagicNumber", "TooGenericExceptionCaught")

package co.electriccoin.zcash.spike

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.net.InetAddress

private const val TAG = "OhttpBlackhole"

/** The Debug menu reads this to show the real state; the service is the only writer. */
private const val PREFS = "ohttp_blackhole"
private const val KEY_ON = "on"

/** Spike: routes only relay IPs into a TUN for this app and drops every packet. */
class BlackholeVpnService : VpnService() {
    @Volatile private var tun: ParcelFileDescriptor? = null

    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The relay host comes from the caller (the Debug menu asks the SDK for it): none is written here.
        val hosts = intent?.getStringExtra("hosts")?.split(",")?.filter { it.isNotBlank() }
        when {
            intent?.action == "stop" -> {
                stopVpn()
                stopSelf()
            }

            hosts.isNullOrEmpty() -> {
                Log.w(TAG, "no relay host given, nothing to cut")
                stopSelf()
            }

            else -> {
                Thread { startVpn(hosts) }.start()
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn(hosts: List<String>) {
        stopVpn()
        val addrs =
            hosts
                .flatMap { h ->
                    runCatching { InetAddress.getAllByName(h).toList() }
                        .onFailure { Log.e(TAG, "resolve $h failed", it) }
                        .getOrDefault(emptyList())
                        .also { Log.i(TAG, "resolved $h -> ${it.map { a -> a.hostAddress }}") }
                }.distinct()
        val b =
            Builder()
                .setSession("ohttp-blackhole")
                .setBlocking(true)
                .addAddress("10.77.0.2", 32)
                .addAddress("fd00:77::2", 128)
                .addAllowedApplication(packageName)
        addrs.forEach { b.addRoute(it, if (it.address.size == 4) 32 else 128) }
        val fd =
            b.establish() ?: run {
                Log.e(TAG, "establish() null (no consent?)")
                return
            }
        tun = fd
        running = true
        setOn(true)
        Log.i(TAG, "VPN UP, routes=${addrs.size}")
        val ins = FileInputStream(fd.fileDescriptor)
        val buf = ByteArray(32768)
        var n = 0L
        try {
            while (running) {
                val r = ins.read(buf)
                if (r <= 0) break
                if (n++ % 20 == 0L) Log.i(TAG, "dropped packet #$n len=$r ver=${buf[0].toInt() shr 4}")
            }
        } catch (e: Exception) {
            Log.i(TAG, "reader ended: $e")
        }
    }

    private fun stopVpn() {
        running = false
        runCatching { tun?.close() }
        tun = null
        setOn(false)
        Log.i(TAG, "VPN DOWN")
    }

    private fun setOn(on: Boolean) =
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}

class BlackholeTriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent) {
        if (i.getStringExtra("op") == "stop") {
            startService(Intent(this, BlackholeVpnService::class.java).setAction("stop"))
            finish()
            return
        }
        val prep = VpnService.prepare(this)
        if (prep != null) {
            startActivityForResult(prep, 1)
        } else {
            go()
        }
    }

    private fun go() {
        startService(
            Intent(this, BlackholeVpnService::class.java).apply {
                intent.getStringExtra("hosts")?.let { putExtra("hosts", it) }
            }
        )
        finish()
    }

    @Deprecated("spike")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) go() else finish()
    }
}
