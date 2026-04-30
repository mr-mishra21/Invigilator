package app.invigilator.core.auth

import android.app.Activity
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton holder so [AuthRepositoryImpl] can access the current Activity for
 * Firebase Phone Auth's reCAPTCHA / silent-auth flow without a direct Activity dep on the repo.
 *
 * MainActivity calls [set] in onCreate and [clear] in onDestroy.
 */
@Singleton
class ActivityHolder @Inject constructor() {
    private var ref: WeakReference<Activity>? = null

    fun set(activity: Activity) {
        ref = WeakReference(activity)
    }

    fun clear() {
        ref = null
    }

    fun get(): Activity? = ref?.get()
}
