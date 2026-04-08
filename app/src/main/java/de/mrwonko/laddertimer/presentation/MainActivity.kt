package de.mrwonko.laddertimer.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import de.mrwonko.laddertimer.R
import de.mrwonko.laddertimer.presentation.theme.LadderTimerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LadderApp(LadderViewModel())
        }
    }
}

enum class WorkoutState {
    IDLE, REPPING, RESTING
}

class LadderViewModel : ViewModel() {
    var currentState by mutableStateOf(WorkoutState.IDLE)
}

@Composable
fun LadderApp(viewModel: LadderViewModel) {
    LadderTimerTheme {
        AppScaffold {
            when (viewModel.currentState) {
                WorkoutState.IDLE -> SplashScreen({ viewModel.currentState = WorkoutState.REPPING })
                WorkoutState.REPPING -> { /* TODO */ }
                WorkoutState.RESTING -> { /* TODO */ }
            }
        }
    }
}

@Composable
fun SplashScreen(onStart: () -> Unit = {}) {
    ScreenScaffold { contentPadding -> // ScreenScaffold provides default padding; adjust as needed
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Material 3 Button (replaces the old Chip)
            Button(onClick = onStart) {
                Text(stringResource(R.string.start_button_text))
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    LadderApp(LadderViewModel())
}