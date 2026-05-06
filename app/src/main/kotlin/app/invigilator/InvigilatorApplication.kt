package app.invigilator

import android.app.Application
import app.invigilator.core.intervention.TtsManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class InvigilatorApplication : Application() {

    @Inject lateinit var ttsManager: TtsManager

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        ttsManager.initialize()
    }
}
