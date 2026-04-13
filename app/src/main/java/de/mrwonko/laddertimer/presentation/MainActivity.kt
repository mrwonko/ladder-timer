package de.mrwonko.laddertimer.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
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
import androidx.wear.tooling.preview.devices.WearDevices
import de.mrwonko.laddertimer.R
import de.mrwonko.laddertimer.presentation.theme.LadderTimerTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

object Constants {
    val WORKOUT_DURATION: Duration = Duration.ofMinutes(7).plusSeconds(30)
}

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
                    this.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    this.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            })
        }
    }
}

enum class WorkoutState {
    IDLE, REPPING, RESTING
}

class LadderViewModel(private val keepScreenOn: (Boolean) -> Unit) : ViewModel() {

    var currentState by mutableStateOf(WorkoutState.IDLE)
        private set
    var workoutEnd: Instant = Instant.EPOCH
    var setStart: Instant = Instant.EPOCH

    // TODO keep track of remaining time of workout (declaratively via end timestamp?)
    // TODO keep track of current rep count
    // TODO keep track of whether we're going up or down the ladder

    fun startWorkout() {
        keepScreenOn(true)
        setStart = Instant.now()
        workoutEnd = setStart.plus(Constants.WORKOUT_DURATION)
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
fun CountDown(
    until: Instant,
    updateIntervalMS: Long = 1000,
    onFinished: () -> Unit = {},
    content: @Composable (remainingDuration: State<Duration>) -> Unit,
    ) {
    // State to hold the current remaining time
    val remainingDuration = produceState(
        Duration.between(Instant.now(), until),
        until,
    ) {
        while (value.toMillis() > 0) {
            value = Duration.between(Instant.now(), until)
            // e.g. 1203ms remaining -> update in 203ms + 1ms (on 999ms, not 1000ms)
            val millisToUpdate = value.toMillis() % updateIntervalMS + 1

            delay(millisToUpdate)
        }
        onFinished()
    }
    content(remainingDuration)
}

@Composable
fun CountUp(
    from: Instant,
    updateIntervalMS: Long = 1000,
    content: @Composable (passedDuration: State<Duration>) -> Unit,
) {
    // State to hold the current passed time
    val passedDuration = produceState(
        Duration.between(from, Instant.now()),
        from,
    ) {
        while (true) {
            // e.g. 1300ms passed -> update in 1000ms-300ms=700ms
            val millisToUpdate = updateIntervalMS.minus(value.toMillis() % updateIntervalMS)

            delay(millisToUpdate)

            value = Duration.between(from, Instant.now())
        }
    }
    content(passedDuration)
}

@Composable
fun WorkoutScreen(viewModel: LadderViewModel) {
    ScreenScaffold {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CountUp(viewModel.setStart) {
                passedDuration ->
                Text(
                    String.format(
                        java.util.Locale.ROOT,
                        "%02d:%02d",
                        passedDuration.value.toMinutes(),
                        passedDuration.value.toSecondsPart()),
                    fontFamily = FontFamily.Monospace,
                )
            }
            CountDown(viewModel.workoutEnd, onFinished = viewModel::abortWorkout) {
                remainingDuration ->
                CircularProgressIndicator(
                    modifier= Modifier.fillMaxSize(),
                    strokeWidth = 4.dp,
                    startAngle = 290f,
                    endAngle = 250f,
                    progress = { remainingDuration.value.toMillis().toFloat() / Constants.WORKOUT_DURATION.toMillis() },
                    colors = ProgressIndicatorDefaults.colors(
                        trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha=.1f),
                        indicatorColor = MaterialTheme.colorScheme.onBackground.copy(alpha=.4f),
                    ),
                )
            }
        }
    }
}

//@WearPreviewDevices
//@WearPreviewFontScales
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun SplashScreenPreview() {
    LadderApp(LadderViewModel {})
}

//@WearPreviewDevices
//@WearPreviewFontScales
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun ReppingPreview() {
    val model = LadderViewModel {}
    model.startWorkout()
    LadderApp(model)
}