package com.clearguard.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Runtime Application Self-Protection (RASP) + Anti-tamper stubs.
 * High complexity in full form (native hooks, real-time monitoring).
 * This provides basic on-device checks: root/jailbreak, hook detection, basic integrity.
 * Call checkIntegrity() early in MainActivity or Application.
 * For production, integrate with libraries like Google Play Integrity or commercial RASP.
 */
object RaspGuard {

    private const val TAG = "RaspGuard"

    data class IntegrityReport(
        val isRooted: Boolean,
        val isEmulator: Boolean,
        val hasHooks: Boolean,
        val isDebuggable: Boolean,
        val score: Int, // 0-100 risk
        val warnings: List<String>
    )

    fun checkIntegrity(context: Context): IntegrityReport {
        val warnings = mutableListOf<String>()
        var risk = 0

        val isRooted = isDeviceRooted()
        if (isRooted) {
            warnings += "Device is rooted - high tampering risk"
            risk += 40
        }

        val isEmulator = isRunningOnEmulator()
        if (isEmulator) {
            warnings += "Running on emulator - development/debug risk"
            risk += 15
        }

        val hasHooks = detectCommonHooks()
        if (hasHooks) {
            warnings += "Possible runtime hooking (Frida/Xposed/etc.) detected"
            risk += 35
        }

        // RASP hardening: additional anti-tamper checks (e.g., more hooks, basic app integrity).
        if (detectFridaSpecific()) {
            warnings += "Frida-specific hook detected (advanced RASP)"
            risk += 20
        }
        if (detectXposedOrSubstrate()) {
            warnings += "Xposed/Substrate framework detected"
            risk += 15
        }

        val isDebuggable = isAppDebuggable(context)
        if (isDebuggable) {
            warnings += "App is debuggable - credential theft risk"
            risk += 20
        }

        // Basic app signature / tampering check (stub - expand with real cert pinning)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                // In real: compare hash to known good
                if (info.signingInfo?.hasMultipleSigners() == true) {
                    warnings += "Multiple signers - possible repack"
                    risk += 10
                }
            } catch (e: Exception) { /* ignore */ }
        }

        return IntegrityReport(
            isRooted = isRooted,
            isEmulator = isEmulator,
            hasHooks = hasHooks,
            isDebuggable = isDebuggable,
            score = risk.coerceIn(0, 100),
            warnings = warnings
        )
    }

    private fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        // Check build tags
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) return true
        return false
    }

    private fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.lowercase().contains("vbox")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.lowercase().contains("emulator")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")))
    }

    private fun detectCommonHooks(): Boolean {
        // Simple heuristic for Frida/Xposed etc.
        val suspicious = listOf("frida", "xposed", "substrate", "cydia")
        return suspicious.any { System.getProperty(it) != null || File("/proc/self/maps").let { f ->
            if (f.exists()) f.readText().contains(it, ignoreCase = true) else false
        }}
    }

    private fun isAppDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun detectFridaSpecific(): Boolean {
        // Advanced RASP: check for Frida specific files/processes.
        val fridaPaths = arrayOf("/data/local/tmp/frida-server", "/data/local/tmp/re.frida.server")
        return fridaPaths.any { File(it).exists() } || System.getProperty("frida") != null
    }

    private fun detectXposedOrSubstrate(): Boolean {
        // Check for Xposed/Substrate indicators.
        return try {
            Class.forName("de.robv.android.xposed.XposedBridge") != null ||
            Class.forName("com.saurik.substrate.MS") != null
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    fun logReport(report: IntegrityReport) {
        Log.w(TAG, "RASP Report: Risk=${report.score}, Root=${report.isRooted}, Hooks=${report.hasHooks}, Warnings=${report.warnings}")
    }
}