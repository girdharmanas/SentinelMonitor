package com.sentinel.monitor.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaDrm
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.sentinel.monitor.data.model.CableGraderReport
import com.sentinel.monitor.data.model.CodecDrmProfile
import com.sentinel.monitor.data.model.DnsRaceEntry
import com.sentinel.monitor.data.model.HopDiagnosticReport
import com.sentinel.monitor.data.model.InstalledProcessEntry
import com.sentinel.monitor.data.model.SystemSnapshot
import com.sentinel.monitor.data.model.TelemetryFormatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

object NetworkAndHardwareLabEngine {

    private val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

    suspend fun runHopAndDnsDiagnostics(context: Context): HopDiagnosticReport = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val linkProps = activeNet?.let { cm.getLinkProperties(it) }

        val localIp = linkProps?.linkAddresses
            ?.map { it.address }
            ?.sortedBy { if (it is Inet4Address) 0 else 1 }
            ?.firstOrNull()
            ?.hostAddress ?: "Offline"

        val gatewayIp = linkProps?.routes
            ?.mapNotNull { route ->
                val gw = route.gateway
                if (gw != null && !gw.isAnyLocalAddress && !gw.isLoopbackAddress) gw else null
            }
            ?.sortedBy { if (it is Inet4Address) 0 else 1 }
            ?.firstOrNull()
            ?.hostAddress ?: "Unavailable"

        val dnsServers = linkProps?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
        val mtu = linkProps?.mtu?.takeIf { it > 0 } ?: 1500
        val privateDns = linkProps?.privateDnsServerName
            ?: runCatching { Settings.Global.getString(appCtx.contentResolver, "private_dns_mode") }.getOrNull()
            ?: "opportunistic"

        if (activeNet == null || linkProps == null) {
            return@withContext HopDiagnosticReport(
                localIp = "Offline",
                gatewayIp = "Unavailable",
                dnsServers = emptyList(),
                mtu = mtu,
                privateDnsMode = privateDns,
                gatewayLatencyMs = null,
                ispRawIpLatencyMs = null,
                dnsResolveLatencyMs = null,
                verdict = classifyHopVerdict(
                    isOnline = false,
                    gatewayMs = null,
                    ispMs = null,
                    dnsMs = null,
                    fastestDnsLabel = null
                ),
                dnsRace = emptyList()
            )
        }

        coroutineScope {
            val gatewayDeferred = async {
                if (gatewayIp != "Unavailable") measureHostReachabilityMs(gatewayIp) else null
            }
            val ispDeferred = async {
                measureTcpConnectOrRstMs("1.1.1.1", 443, timeoutMs = 1300)
                    ?: measureTcpConnectOrRstMs("8.8.8.8", 443, timeoutMs = 1300)
            }
            val sysDnsDeferred = async {
                val primaryDns = dnsServers.firstOrNull { !it.contains(':') } ?: dnsServers.firstOrNull()
                if (primaryDns != null) {
                    measureUdpDnsQueryMs(primaryDns, timeoutMs = 1200) ?: measureSystemResolverMs()
                } else {
                    measureSystemResolverMs()
                }
            }

            val candidates = buildList {
                val sysIp = dnsServers.firstOrNull { !it.contains(':') }
                if (sysIp != null && sysIp !in setOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "94.140.14.14")) {
                    add("System Router DNS" to sysIp)
                }
                add("Cloudflare" to "1.1.1.1")
                add("Google Public DNS" to "8.8.8.8")
                add("Quad9 Security" to "9.9.9.9")
                add("AdGuard DNS" to "94.140.14.14")
            }

            val raceDeferred = candidates.map { (name, ip) ->
                async {
                    val ms = measureUdpDnsQueryMs(ip, timeoutMs = 1200)
                    DnsRaceEntry(
                        providerName = name,
                        serverAddress = ip,
                        latencyMs = ms ?: 9999,
                        isReachable = ms != null
                    )
                }
            }

            val gwMs = gatewayDeferred.await()
            val ispMs = ispDeferred.await()
            val dnsMs = sysDnsDeferred.await()
            val raceSorted = raceDeferred.awaitAll().sortedWith(
                compareBy<DnsRaceEntry> { !it.isReachable }.thenBy { it.latencyMs }
            )
            val bestDns = raceSorted.firstOrNull { it.isReachable }
                ?.let { "${it.providerName} (${it.latencyMs}ms)" }

            HopDiagnosticReport(
                localIp = localIp,
                gatewayIp = gatewayIp,
                dnsServers = dnsServers,
                mtu = mtu,
                privateDnsMode = privateDns,
                gatewayLatencyMs = gwMs,
                ispRawIpLatencyMs = ispMs,
                dnsResolveLatencyMs = dnsMs,
                verdict = classifyHopVerdict(
                    isOnline = true,
                    gatewayMs = gwMs,
                    ispMs = ispMs,
                    dnsMs = dnsMs,
                    fastestDnsLabel = bestDns
                ),
                dnsRace = raceSorted
            )
        }
    }

    internal fun classifyHopVerdict(
        isOnline: Boolean,
        gatewayMs: Int?,
        ispMs: Int?,
        dnsMs: Int?,
        fastestDnsLabel: String?
    ): String {
        if (!isOnline) return "Offline • No active Wi-Fi or Cellular link"
        if (gatewayMs == null && ispMs == null) {
            return "Local Link Fault • Neither router gateway nor WAN responded"
        }
        if (gatewayMs != null && ispMs == null) {
            return "ISP / WAN Outage • Router gateway OK (${gatewayMs}ms), upstream internet unreachable"
        }
        if (ispMs != null && dnsMs == null) {
            return "DNS Resolver Fault • Raw IP internet works (${ispMs}ms), switch Private DNS"
        }
        if (gatewayMs != null && gatewayMs >= 80) {
            return "High Wi-Fi / LAN Jitter • ${gatewayMs}ms to local router"
        }
        if (ispMs != null && ispMs >= 180) {
            return "Elevated WAN Latency • ${ispMs}ms to backbone"
        }
        val dnsSuffix = if (fastestDnsLabel != null) " • Fastest DNS: $fastestDnsLabel" else ""
        return "All Hops Healthy$dnsSuffix"
    }

    private fun measureHostReachabilityMs(ip: String): Int? {
        measureUdpDnsQueryMs(ip, timeoutMs = 650)?.let { return it }
        for (port in intArrayOf(80, 443, 53)) {
            measureTcpConnectOrRstMs(ip, port, timeoutMs = 650)?.let { return it }
        }
        return runCatching {
            val t0 = SystemClock.elapsedRealtime()
            val ok = InetAddress.getByName(ip).isReachable(700)
            if (ok) (SystemClock.elapsedRealtime() - t0).toInt().coerceAtLeast(1) else null
        }.getOrNull()
    }

    private fun measureTcpConnectOrRstMs(ip: String, port: Int, timeoutMs: Int): Int? {
        val t0 = SystemClock.elapsedRealtime()
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(ip, port), timeoutMs)
            }
            (SystemClock.elapsedRealtime() - t0).toInt().coerceAtLeast(1)
        } catch (ce: ConnectException) {
            // Immediate TCP RST from target host confirms round-trip IP reachability
            val elapsed = (SystemClock.elapsedRealtime() - t0).toInt()
            if (ce.message?.contains("refused", ignoreCase = true) == true && elapsed < timeoutMs) {
                elapsed.coerceAtLeast(1)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    internal fun buildDnsQueryPacket(domain: String = "example.com", txId: Int = 0x534D): ByteArray {
        val out = ByteArrayOutputStream(64)
        out.write((txId ushr 8) and 0xFF)
        out.write(txId and 0xFF)
        out.write(0x01) // standard query, recursion desired
        out.write(0x00)
        out.write(0x00); out.write(0x01) // QDCOUNT = 1
        out.write(0x00); out.write(0x00) // ANCOUNT = 0
        out.write(0x00); out.write(0x00) // NSCOUNT = 0
        out.write(0x00); out.write(0x00) // ARCOUNT = 0
        for (label in domain.split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0x00) // end of QNAME
        out.write(0x00); out.write(0x01) // QTYPE = A
        out.write(0x00); out.write(0x01) // QCLASS = IN
        return out.toByteArray()
    }

    private fun measureUdpDnsQueryMs(dnsServerIp: String, timeoutMs: Int): Int? {
        return runCatching {
            val query = buildDnsQueryPacket("example.com", txId = 0x534D)
            val addr = InetAddress.getByName(dnsServerIp)
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val req = DatagramPacket(query, query.size, addr, 53)
                val respBuf = ByteArray(512)
                val resp = DatagramPacket(respBuf, respBuf.size)
                val t0 = SystemClock.elapsedRealtime()
                socket.send(req)
                socket.receive(resp)
                val elapsed = (SystemClock.elapsedRealtime() - t0).toInt().coerceAtLeast(1)
                if (resp.length >= 12 && respBuf[0] == 0x53.toByte() && respBuf[1] == 0x4D.toByte()) {
                    elapsed
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun measureSystemResolverMs(): Int? {
        return runCatching {
            val t0 = SystemClock.elapsedRealtime()
            val resolved = InetAddress.getAllByName("cloudflare.com")
            if (resolved.isNotEmpty()) {
                (SystemClock.elapsedRealtime() - t0).toInt().coerceAtLeast(1)
            } else {
                null
            }
        }.getOrNull()
    }

    suspend fun runCableAndChargerTest(
        context: Context,
        onProgressPercent: (Int) -> Unit = {}
    ): CableGraderReport = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val bm = appCtx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val voltages = ArrayList<Int>(6)
        val currentsMa = ArrayList<Float>(6)
        val wattsList = ArrayList<Float>(6)
        var anyCharging = false

        val totalSamples = 6
        for (i in 1..totalSamples) {
            val intent = runCatching {
                appCtx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }.getOrNull()
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            if (charging) anyCharging = true

            val vMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000)
                ?.let { if (it in 1..20) it * 1000 else it } ?: 4000
            val rawCur = runCatching {
                bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L
            }.getOrDefault(0L)
            val curMa = abs(TelemetryFormatters.normalizeBatteryCurrentToMilliamps(rawCur))
            val w = TelemetryFormatters.computeBatteryWatts(curMa, vMv)

            voltages.add(vMv)
            currentsMa.add(curMa)
            wattsList.add(w)
            onProgressPercent(((i * 100f) / totalSamples).toInt())
            if (i < totalSamples) delay(650L)
        }

        gradeCableSamples(
            isCharging = anyCharging,
            voltagesMv = voltages,
            currentsMa = currentsMa,
            wattsSamples = wattsList
        )
    }

    internal fun gradeCableSamples(
        isCharging: Boolean,
        voltagesMv: List<Int>,
        currentsMa: List<Float>,
        wattsSamples: List<Float>
    ): CableGraderReport {
        val minV = voltagesMv.minOrNull() ?: 4000
        val maxV = voltagesMv.maxOrNull() ?: 4000
        val swing = (maxV - minV).coerceAtLeast(0)
        val avgW = if (wattsSamples.isNotEmpty()) wattsSamples.average().toFloat() else 0f
        val peakW = wattsSamples.maxOrNull() ?: 0f
        val avgMa = if (currentsMa.isNotEmpty()) currentsMa.average().toFloat() else 0f

        if (!isCharging) {
            return CableGraderReport(
                isCharging = false,
                avgWatts = avgW,
                peakWatts = peakW,
                avgCurrentMa = avgMa,
                voltageMinMv = minV,
                voltageMaxMv = maxV,
                gradeTitle = "Unplugged (Discharging)",
                verdict = "Connect a USB-C charger & cable and re-run to benchmark negotiated Wattage and voltage sag."
            )
        }

        val (grade, verdict) = when {
            swing > 340 ->
                "Grade D • High Voltage Sag" to "Detected ${swing} mV drop under load — inspect cable resistance or loose USB-C port."
            peakW >= 22f ->
                "Grade S • USB-PD PPS High-Speed" to String.format(Locale.US, "Sustaining %.1f W peak with tight %d mV rail stability.", peakW, swing)
            peakW >= 14f ->
                "Grade A • Fast USB-PD / QC Link" to String.format(Locale.US, "Clean %.1f W fast-charge delivery (%d mV variance).", peakW, swing)
            peakW >= 7.5f ->
                "Grade B • Standard 10W–12W USB" to String.format(Locale.US, "Stable %.1f W standard charge (%d mV variance).", peakW, swing)
            else ->
                "Grade C • 5W Trickle / CV Taper" to "Low power draw — either a legacy 5V/1A brick/cable or battery is >85% in constant-voltage taper."
        }

        return CableGraderReport(
            isCharging = true,
            avgWatts = avgW,
            peakWatts = peakW,
            avgCurrentMa = avgMa,
            voltageMinMv = minV,
            voltageMaxMv = maxV,
            gradeTitle = grade,
            verdict = verdict
        )
    }

    suspend fun inspectCodecAndDrmProfile(): CodecDrmProfile = withContext(Dispatchers.IO) {
        var widevineLevel = "Unsupported"
        var widevineVendor = "Unavailable"
        var drm: MediaDrm? = null
        try {
            if (MediaDrm.isCryptoSchemeSupported(WIDEVINE_UUID)) {
                drm = MediaDrm(WIDEVINE_UUID)
                widevineLevel = runCatching { drm.getPropertyString("securityLevel") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "Supported"
                widevineVendor = runCatching { drm.getPropertyString(MediaDrm.PROPERTY_VENDOR) }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "Google Widevine"
            }
        } catch (_: Throwable) {
            widevineLevel = "Unavailable"
        } finally {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    drm?.close()
                } else {
                    @Suppress("DEPRECATION")
                    drm?.release()
                }
            }
        }

        var hwAv1 = false
        var hwHevc10 = false
        var hwVp9 = false
        var hwVideoDecoders = 0

        runCatching {
            val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            for (info in infos) {
                if (info.isEncoder) continue
                val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isHardwareAccelerated && !info.isSoftwareOnly
                } else {
                    val lower = info.name.lowercase(Locale.US)
                    !lower.startsWith("omx.google.") && !lower.startsWith("c2.android.") && !lower.contains("sw")
                }
                if (!isHw) continue

                val videoTypes = info.supportedTypes.filter { it.startsWith("video/", ignoreCase = true) }
                if (videoTypes.isNotEmpty()) hwVideoDecoders++

                for (mime in videoTypes) {
                    when (mime.lowercase(Locale.US)) {
                        "video/av01" -> hwAv1 = true
                        "video/x-vnd.on2.vp9" -> hwVp9 = true
                        "video/hevc" -> {
                            val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull()
                            val has10Bit = caps?.profileLevels?.any { pl ->
                                pl.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                                    pl.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                                    pl.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
                            } == true
                            if (has10Bit) hwHevc10 = true
                        }
                    }
                }
            }
        }

        CodecDrmProfile(
            widevineLevel = widevineLevel,
            widevineVendor = widevineVendor,
            hasHardwareAv1 = hwAv1,
            hasHardwareHevc10Bit = hwHevc10,
            hasHardwareVp9 = hwVp9,
            hardwareVideoDecoderCount = hwVideoDecoders
        )
    }

    fun buildDeviceHealthMarkdownDossier(
        snapshot: SystemSnapshot,
        codecProfile: CodecDrmProfile?,
        processes: List<InstalledProcessEntry>,
        nonEssentialCount: Int
    ): String {
        val userApps = processes.filter { !it.isSystemApp }
        val dozeVampires = userApps.count { it.isIgnoringBatteryOptimizations }
        val sideloaded = userApps.count { it.isSideloaded }
        val totalCache = processes.sumOf { it.cacheBytes }
        return buildString {
            appendLine("# Sentinel Monitor — Device Health Dossier")
            appendLine("- **Device:** ${snapshot.deviceModel} (`${snapshot.socModel}` • `${snapshot.primaryAbi}`)")
            appendLine("- **OS:** ${snapshot.androidVersionLabel} • Display `${snapshot.displayRefreshHz} Hz`")
            appendLine("- **Kernel Uptime:** ${TelemetryFormatters.formatUptime(snapshot.uptimeMillis)} (**${snapshot.deepSleepPercent}% Deep Sleep**)")
            appendLine("- **Memory:** ${(snapshot.ramUsageFraction * 100).toInt()}% used (${TelemetryFormatters.formatBytes(snapshot.availableRamBytes)} free of ${TelemetryFormatters.formatBytes(snapshot.totalRamBytes)}) • ZRAM ${TelemetryFormatters.formatBytes(snapshot.swapUsedBytes)}/${TelemetryFormatters.formatBytes(snapshot.swapTotalBytes)}")
            appendLine("- **Storage (/data):** ${TelemetryFormatters.formatBytes(snapshot.freeStorageBytes)} free of ${TelemetryFormatters.formatBytes(snapshot.totalStorageBytes)} • Reclaimable Cache: ${TelemetryFormatters.formatBytes(totalCache)}")
            appendLine(String.format(Locale.US, "- **Power & Thermal:** %d%% • %s • %.1f°C (%s)", snapshot.batteryPercent, TelemetryFormatters.formatWatts(snapshot.batteryPowerWatts, snapshot.isCharging), snapshot.batteryTempCelsius, snapshot.thermalStatusLabel))
            if (codecProfile != null) {
                appendLine("- **DRM & Silicon Media:** Widevine `${codecProfile.widevineLevel}` • HW AV1: `${codecProfile.hasHardwareAv1}` • HEVC 10-bit: `${codecProfile.hasHardwareHevc10Bit}` • VP9: `${codecProfile.hasHardwareVp9}`")
            }
            appendLine("- **Process & Security Audit:** ${userApps.size} user apps • $nonEssentialCount active non-essential • $dozeVampires Doze-exempt • $sideloaded sideloaded")
        }
    }
}
