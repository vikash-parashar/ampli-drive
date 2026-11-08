package com.vikash.ampli.drive.system

import android.os.Build
import android.util.Log
import java.net.URLConnection
import java.security.Provider
import java.security.Security
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

/**
 * In-app routing (router.project-osrm.org) fails on the TS7 (Android 8.1) with
 * SSLHandshakeException. The likely cause is the old platform TLS stack (no TLS 1.3, old cipher
 * list); that is inferred, not verified on the unit. Below Android 10 the bundled Conscrypt
 * (BoringSSL) is installed as the first security provider at app start, and routing and place
 * search use its socket factory explicitly. Certificates are still checked against the system's
 * trusted roots. Android 10 and newer already ship a modern Conscrypt and are left alone.
 */
object ModernTls {
    private const val TAG = "ModernTls"

    @Volatile
    private var provider: Provider? = null

    @Volatile
    private var factory: SSLSocketFactory? = null

    /** True once Conscrypt is the first provider (API < 29 only). */
    val installed: Boolean get() = provider != null

    /** Call first thing in Application.onCreate, before any HTTPS connection is made. */
    fun install() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || provider != null) return
        // JVM unit tests (Robolectric) have no Android native library for Conscrypt.
        if (Build.FINGERPRINT == "robolectric") return
        var inserted: Provider? = null
        try {
            // Loads the native library; false on an odd ABI or a ROM that refuses it.
            if (!org.conscrypt.Conscrypt.isAvailable()) {
                Log.w(TAG, "Conscrypt not available: keeping the platform TLS")
                return
            }
            val p = org.conscrypt.Conscrypt.newProvider()
            // A fully working context first, then the provider goes in front.
            val ctx = SSLContext.getInstance("TLS", p)
            ctx.init(null, null, null)
            Security.insertProviderAt(p, 1)
            inserted = p
            factory = ctx.socketFactory
            provider = p
            // Every other HttpsURLConnection in the app (weather, news, feedback) gets it too.
            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.socketFactory)
        } catch (t: Throwable) {
            // Something failed half way: take Conscrypt out again and keep the platform TLS.
            inserted?.let { Security.removeProvider(it.name) }
            factory = null
            provider = null
            Log.w(TAG, "Conscrypt not installed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Routing and place search: use Conscrypt's sockets on this connection when it is installed. */
    fun apply(conn: URLConnection) {
        val f = factory ?: return
        if (conn is HttpsURLConnection) conn.sslSocketFactory = f
    }
}
