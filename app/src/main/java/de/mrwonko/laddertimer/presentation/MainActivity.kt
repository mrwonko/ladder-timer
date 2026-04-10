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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import de.mrwonko.laddertimer.R
import de.mrwonko.laddertimer.presentation.theme.LadderTimerTheme

class MainActivity : ComponentActivity() {
    private val callbacks = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            // move to background to avoid ambient transition digital clock and go straight to watch face
            moveTaskToBack(true)
        }

        override fun onExitAmbient() {
        }
    }
    private var ambientObserver = AmbientLifecycleObserver(this, callbacks)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        setContent {
            LadderApp(LadderViewModel{keepScreenOn ->
                if (keepScreenOn) {
                    this.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    this.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            })
        }
    }
}

enum class WorkoutState {
    IDLE, REPPING, RESTING
}

class LadderViewModel(keepScreenOn: (Boolean) -> Unit) : ViewModel() {
    private val keepScreenOn = keepScreenOn;

    var currentState by mutableStateOf(WorkoutState.IDLE)
        private set
    var progress: Float = 0f

    // TODO keep track of remaining time of workout (declaratively via end timestamp?)
    // TODO keep track of current rep count
    // TODO keep track of whether we're going up or down the ladder

    fun startWorkout() {
        keepScreenOn(true)
        currentState = WorkoutState.REPPING
    }

    fun abortWorkout() {
        keepScreenOn(false)
        currentState = WorkoutState.IDLE
    }

    // TODO after transitioning to resting, let the user choose if they have started going down, intend to go down, or keep going up
}

@Composable
fun LadderApp(viewModel: LadderViewModel) {
    val swipeToDismissBoxState = rememberSwipeToDismissBoxState()
    LadderTimerTheme {
        AppScaffold {
            SwipeToDismissBox(
                state = swipeToDismissBoxState,
                userSwipeEnabled = viewModel.currentState != WorkoutState.IDLE,
                onDismissed = { viewModel.abortWorkout() }
            ) { isBackground ->
                if (isBackground || viewModel.currentState == WorkoutState.IDLE) {
                    SplashScreen { viewModel.startWorkout() }
                } else {
                    WorkoutScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onStart: () -> Unit = {}) {
    ScreenScaffold {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = onStart) {
                Text(stringResource(R.string.start_button_text))
            }
        }
    }
}

@Composable
fun WorkoutScreen(viewModel: LadderViewModel) {
    ScreenScaffold {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("13:37")
            CircularProgressIndicator(
                modifier= Modifier.fillMaxSize(),
                strokeWidth = 4.dp,
                startAngle = 290f,
                endAngle = 250f,
                progress = { 1f - viewModel.progress },
                colors = ProgressIndicatorDefaults.colors(
                    trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha=.1f),
                    indicatorColor = MaterialTheme.colorScheme.onBackground.copy(alpha=.4f),
                ),
            )
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun SplashScreenPreview() {
    LadderApp(LadderViewModel({}))
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun ReppingPreview() {
    val model = LadderViewModel({})
    model.startWorkout()
    LadderApp(model)
}