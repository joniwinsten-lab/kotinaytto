package fi.kotinaytto.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import fi.kotinaytto.tv.ui.DashboardScreen
import fi.kotinaytto.tv.ui.DashboardViewModel
import fi.kotinaytto.tv.ui.KotiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KotiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: DashboardViewModel = viewModel()
                    DashboardScreen(vm = vm)
                }
            }
        }
    }
}
