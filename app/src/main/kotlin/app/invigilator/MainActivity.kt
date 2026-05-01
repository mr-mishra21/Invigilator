package app.invigilator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import app.invigilator.core.auth.ActivityHolder
import app.invigilator.ui.nav.InvigilatorNavHost
import androidx.compose.ui.Modifier
import app.invigilator.ui.theme.InvigilatorTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var activityHolder: ActivityHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityHolder.set(this)
        enableEdgeToEdge()
        setContent {
            InvigilatorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    InvigilatorNavHost(modifier = Modifier.fillMaxSize().padding(padding))
                }
            }
        }
    }

    override fun onDestroy() {
        activityHolder.clear()
        super.onDestroy()
    }
}
