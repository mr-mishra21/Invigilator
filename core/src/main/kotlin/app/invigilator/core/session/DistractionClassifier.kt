package app.invigilator.core.session

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DistractionClassifier @Inject constructor() {

    fun classify(packageName: String): AppCategory {
        return when {
            packageName == "app.invigilator" -> AppCategory.STUDY
            packageName in STUDY_APPS -> AppCategory.STUDY
            packageName in ESSENTIAL_APPS -> AppCategory.ESSENTIAL
            packageName.startsWith("com.android.systemui") -> AppCategory.ESSENTIAL
            packageName.startsWith("com.android.launcher") -> AppCategory.ESSENTIAL
            packageName.endsWith(".launcher") -> AppCategory.ESSENTIAL
            packageName in DISTRACTING_APPS -> AppCategory.DISTRACTING
            else -> AppCategory.UNKNOWN
        }
    }

    private companion object {
        // Study apps — Indian-context aware
        val STUDY_APPS = setOf(
            // Generic study tools
            "org.khanacademy.android",
            "com.google.android.apps.docs",
            "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets",
            "com.google.android.apps.docs.editors.slides",
            "com.google.android.keep",
            "org.wikipedia",
            // India-focused
            "com.byjus.thelearningapp",
            "com.unacademy.consumption.unacademyapp",
            "com.toppr.app",
            "com.vedantu.app",
            "com.physicswallah.app",
            // Reading
            "com.amazon.kindle",
            "com.google.android.apps.books",
        )

        val ESSENTIAL_APPS = setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.contacts",
            "com.google.android.contacts",
            "com.android.settings",
            "com.google.android.deskclock",   // Clock / Alarm
            "com.android.deskclock",
            "com.android.camera",
            "com.android.camera2",
            "com.google.android.GoogleCamera",
            "com.android.calculator2",
            "com.google.android.calculator",
            "com.android.messaging",          // Stock SMS
            "com.google.android.apps.messaging",
            "com.android.emergency",
        )

        val DISTRACTING_APPS = setOf(
            // Social
            "com.instagram.android",
            "com.snapchat.android",
            "com.zhiliaoapp.musically",       // TikTok
            "com.facebook.katana",
            "com.facebook.lite",
            "com.twitter.android",
            "com.reddit.frontpage",
            // Video / streaming
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "in.startv.hotstar",
            "com.amazon.avod.thirdpartyclient",
            // Messaging-as-distraction
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.facebook.orca",              // Messenger
            "org.telegram.messenger",
            "com.discord",
            // Games (popular in India)
            "com.dts.freefireth",
            "com.tencent.ig",                 // PUBG / BGMI
            "com.activision.callofduty.shooter",
            "com.king.candycrushsaga",
            "com.supercell.clashofclans",
            "com.ludo.king",
            // Shopping / browsing distractions
            "in.amazon.mShop.android.shopping",
            "com.flipkart.android",
            "com.myntra.android",
        )
    }
}
