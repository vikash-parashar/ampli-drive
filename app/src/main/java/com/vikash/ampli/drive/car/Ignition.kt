package com.vikash.ampli.drive.car

/** The car's ignition (ACC line) as reported by the head unit's own software. */
enum class IgnitionEvent { On, Off }

/**
 * One vendor broadcast to listen for.
 *
 * @property event what it means; null when an extra decides (see [extraKey]).
 * @property confirmed true when the action is seen in published open-source head-unit code or
 *   in a published guide; false for a guess (the "OFF" twin of a known "ON", or a common naming
 *   pattern). Guesses cost nothing: an action nobody sends is simply never received.
 * @property extraKey for actions that carry the state in a string extra ("accstate": "accon").
 */
data class IgnitionAction(
    val action: String,
    val event: IgnitionEvent?,
    val confirmed: Boolean,
    val platform: String,
    val extraKey: String? = null,
)

/**
 * Every ignition broadcast in one place, so more can be added as drivers report their units.
 * The screen-off and shutdown broadcasts stay as the fallback (see CarMonitorService).
 *
 * Sources for the confirmed entries:
 * - "com.microntek.acc" with extra "accstate" = "accon" / "accoff": petrows/RK3066-Headunit-service
 *   (SWCReceiver.java, MTCB/MTCD units).
 * - "com.fyt.boot.ACCON", "com.glsx.boot.ACCON", "com.cayboy.action.ACC_ON",
 *   "com.carboy.action.ACC_ON", "com.microntek.startApp", MediaTek "QUICKBOOT_POWERON" and
 *   "ACTION_BOOT_IPO": the HeadUnit Revived auto-start guide for Chinese head units
 *   (gist andrecuellar/3c39b4f1bdc03a5c4e97336217f13c38).
 * Everything else (the "OFF" twins, Autochips, Topway, generic names, the HTC quick-boot name and
 * MediaTek's ACTION_SHUTDOWN_IPO) is unverified and marked confirmed = false.
 */
object IgnitionActions {
    val ALL: List<IgnitionAction> = listOf(
        // Microntek MTCB / MTCD / MTCE (Joying, Xtrons, Eonon and other MTC-based units).
        IgnitionAction("com.microntek.acc", null, confirmed = true, platform = "Microntek MTC", extraKey = "accstate"),
        IgnitionAction("com.microntek.startApp", IgnitionEvent.On, confirmed = true, platform = "Microntek MTCD wake"),
        IgnitionAction("com.cayboy.action.ACC_ON", IgnitionEvent.On, confirmed = true, platform = "Microntek PX3"),
        IgnitionAction("com.cayboy.action.ACC_OFF", IgnitionEvent.Off, confirmed = false, platform = "Microntek PX3"),
        IgnitionAction("com.carboy.action.ACC_ON", IgnitionEvent.On, confirmed = true, platform = "Microntek"),
        IgnitionAction("com.carboy.action.ACC_OFF", IgnitionEvent.Off, confirmed = false, platform = "Microntek"),
        // FYT (Sofia, PX5, UIS7862 / TS10, TS18 and most "FYT" brands).
        IgnitionAction("com.fyt.boot.ACCON", IgnitionEvent.On, confirmed = true, platform = "FYT"),
        IgnitionAction("com.fyt.boot.ACCOFF", IgnitionEvent.Off, confirmed = false, platform = "FYT"),
        // GLSX-based units.
        IgnitionAction("com.glsx.boot.ACCON", IgnitionEvent.On, confirmed = true, platform = "GLSX"),
        IgnitionAction("com.glsx.boot.ACCOFF", IgnitionEvent.Off, confirmed = false, platform = "GLSX"),
        // MediaTek quick boot / IPO ("fast boot" sleep instead of a full shutdown).
        IgnitionAction("android.intent.action.QUICKBOOT_POWERON", IgnitionEvent.On, confirmed = true, platform = "MediaTek / HTC quick boot"),
        IgnitionAction("com.htc.intent.action.QUICKBOOT_POWERON", IgnitionEvent.On, confirmed = false, platform = "HTC-style quick boot"),
        IgnitionAction("android.intent.action.QUICKBOOT_POWEROFF", IgnitionEvent.Off, confirmed = false, platform = "Quick boot"),
        IgnitionAction("android.intent.action.ACTION_BOOT_IPO", IgnitionEvent.On, confirmed = true, platform = "MediaTek IPO"),
        IgnitionAction("android.intent.action.ACTION_SHUTDOWN_IPO", IgnitionEvent.Off, confirmed = false, platform = "MediaTek IPO"),
        // Autochips (AC8227 / AC8257 units) quick boot. Guessed from the platform's naming.
        IgnitionAction("autochips.intent.action.QB_POWERON", IgnitionEvent.On, confirmed = false, platform = "Autochips"),
        IgnitionAction("autochips.intent.action.QB_POWEROFF", IgnitionEvent.Off, confirmed = false, platform = "Autochips"),
        // Generic names some no-brand ROMs use. Guesses.
        IgnitionAction("android.intent.action.ACC_ON", IgnitionEvent.On, confirmed = false, platform = "Generic"),
        IgnitionAction("android.intent.action.ACC_OFF", IgnitionEvent.Off, confirmed = false, platform = "Generic"),
        IgnitionAction("com.ts.main.ACC_ON", IgnitionEvent.On, confirmed = false, platform = "TS / Topway"),
        IgnitionAction("com.ts.main.ACC_OFF", IgnitionEvent.Off, confirmed = false, platform = "TS / Topway"),
    )

    private val byAction = ALL.associateBy { it.action }

    val actions: List<String> get() = ALL.map { it.action }

    /**
     * What a received broadcast means, or null when it is not an ignition broadcast (or its
     * extra has a value we don't know). [extra] reads a string extra from the intent.
     */
    fun classify(action: String?, extra: (String) -> String?): IgnitionEvent? {
        val a = byAction[action ?: return null] ?: return null
        a.event?.let { return it }
        val key = a.extraKey ?: return null
        val v = extra(key)?.lowercase()?.replace("_", "")?.trim() ?: return null
        return when {
            v.endsWith("off") || v == "0" || v == "false" -> IgnitionEvent.Off
            v.endsWith("on") || v == "1" || v == "true" -> IgnitionEvent.On
            else -> null
        }
    }
}
