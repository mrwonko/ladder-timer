package de.mrwonko.laddertimer.presentation

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import kotlin.time.toKotlinDuration

object Constants {
    val WORKOUT_DURATION: Duration = Duration.ofMinutes(7).plusSeconds(30)
    val SHORT_BUZZ = longArrayOf(0, 200)
    val LONG_BUZZ = longArrayOf(0, 690)
    val QUICK_DOUBLE_BUZZ = longArrayOf(0, 200, 100, 200)
    val LONG_TRIPLE_BUZZ = longArrayOf(0, 500, 200, 500, 200, 500)
    const val ACTION_STOP_RESTING = "de.mrwonko.laddertimer.ACTION_STOP_RESTING"
    const val ACTION_FINISH_WORKOUT = "de.mrwonko.laddertimer.ACTION_FINISH_WORKOUT"
}

// Alarms target this receiver rather than MainActivity directly: starting an Activity from a
// background alarm is subject to background-activity-launch restrictions and can be deferred
// until the app becomes foregrounded, which delayed the vibration signalling rest's end.
// Broadcast delivery isn't subject to that restriction, so it fires (and vibrates) on time.
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val viewModel = (context.applicationContext as LadderApplication).viewModel
        when (intent.action) {
            Constants.ACTION_STOP_RESTING -> viewModel.stopResting()
            Constants.ACTION_FINISH_WORKOUT -> viewModel.finishWorkout()
        }
    }
}

class LadderApplication : Application() {
    val viewModel = LadderViewModel()
    private val vibrator by lazy { (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator }
    private val alarmManager by lazy { getSystemService(ALARM_SERVICE) as AlarmManager }

    private fun createAlarmIntent(action: String): PendingIntent {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    private fun scheduleAlarm(time: Instant, action: String) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            time.toEpochMilli(),
            createAlarmIntent(action)
        )
    }

    private fun cancelAlarm(action: String) {
        alarmManager.cancel(createAlarmIntent(action))
    }

    override fun onCreate() {
        super.onCreate()
        viewModel.onVibrateRequest = { pattern ->
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        }
        viewModel.onScheduleAlarm = ::scheduleAlarm
        viewModel.onCancelAlarm = ::cancelAlarm
    }
}

class MainActivity : ComponentActivity() {
    private val callbacks = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            if (viewModel.currentState == WorkoutState.IDLE) {
                // move to background to avoid ambient transition digital clock and go straight to watch face
                moveTaskToBack(true)
            }
        }

        override fun onExitAmbient() {
        }
    }
    private var ambientObserver = AmbientLifecycleObserver(this, callbacks)
    private val viewModel: LadderViewModel get() = (application as LadderApplication).viewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)

        setContent {
            LadderApp(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // If launched while repping, assume it's a rest intent.
        // (Apparently I can't bind specific intents to buttons, only launch the app.)
        if (viewModel.currentState == WorkoutState.REPPING) {
            viewModel.startResting()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_POWER && viewModel.currentState != WorkoutState.IDLE) {
           if (viewModel.currentState == WorkoutState.REPPING) {
               viewModel.startResting()
           }
           return true // (attempt to) consume the event (if the OS likes us)
        }

        return super.onKeyDown(keyCode, event)
    }
}

enum class WorkoutState {
    IDLE, REPPING, RESTING
}

class LadderViewModel : ViewModel() {
    var onVibrateRequest: ((LongArray) -> Unit)? = null
    var onScheduleAlarm: ((Instant, String) -> Unit)? = null
    var onCancelAlarm: ((String) -> Unit)? = null

    var currentState by mutableStateOf(WorkoutState.IDLE)
        private set
    var workoutEnd: Instant = Instant.EPOCH
    var setStart: Instant = Instant.EPOCH

    // TODO keep track of current rep count
    // TODO keep track of whether we're going up or down the ladder

    fun startWorkout() {
        if (currentState != WorkoutState.IDLE) return
        onVibrateRequest?.invoke(Constants.LONG_BUZZ)
        setStart = Instant.now()
        workoutEnd = setStart.plus(Constants.WORKOUT_DURATION)
        onScheduleAlarm?.invoke(workoutEnd, Constants.ACTION_FINISH_WORKOUT)
        currentState = WorkoutState.REPPING
    }

    fun startResting() {
        if (currentState != WorkoutState.REPPING) return
        onVibrateRequest?.invoke(Constants.SHORT_BUZZ)
        val setDuration = Duration.between(setStart, Instant.now())
        setStart = setStart.plus(setDuration.multipliedBy(2))
        onScheduleAlarm?.invoke(setStart, Constants.ACTION_STOP_RESTING)
        currentState = WorkoutState.RESTING
    }

    fun stopResting() {
        if (currentState != WorkoutState.RESTING) return
        onVibrateRequest?.invoke(Constants.QUICK_DOUBLE_BUZZ)
        onCancelAlarm?.invoke(Constants.ACTION_STOP_RESTING)
        currentState = WorkoutState.REPPING
    }

    fun finishWorkout() {
        if (currentState == WorkoutState.IDLE) return
        onVibrateRequest?.invoke(Constants.LONG_TRIPLE_BUZZ)
        abortWorkout()
    }

    fun abortWorkout() {
        if (currentState == WorkoutState.IDLE) return
        onCancelAlarm?.invoke(Constants.ACTION_FINISH_WORKOUT)
        onCancelAlarm?.invoke(Constants.ACTION_STOP_RESTING)
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
    updateInterval: Duration = Duration.ofSeconds(1),
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
            val updateDelay = value % updateInterval.plus(Duration.ofMillis(1))

            delay(updateDelay.toKotlinDuration())
        }
        onFinished()
    }
    content(remainingDuration)
}

@Composable
fun CountUp(
    from: Instant,
    updateInterval: Duration = Duration.ofSeconds(1),
    content: @Composable (passedDuration: State<Duration>) -> Unit,
) {
    // State to hold the current passed time
    val passedDuration = produceState(
        Duration.between(from, Instant.now()),
        from,
    ) {
        while (true) {
            // e.g. 1300ms passed -> update in 1000ms-300ms=700ms
            val updateDelay = updateInterval.minus(value % updateInterval)

            delay(updateDelay.toKotlinDuration())

            value = Duration.between(from, Instant.now())
        }
    }
    content(passedDuration)
}

@Composable
fun TimeText(duration: Duration) {
    Text(
        String.format(
            java.util.Locale.ROOT,
            "%02d:%02d",
            duration.toMinutes(),
            duration.toSecondsPart()
        ),
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
fun WorkoutScreen(viewModel: LadderViewModel) {
    // keep screen on while this composable is visible
    val view = LocalView.current
    DisposableEffect(true) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }
    ScreenScaffold {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (viewModel.currentState == WorkoutState.REPPING) {
                CountUp(viewModel.setStart) { passedDuration ->
                    TimeText(passedDuration.value)
                }
            } else {
                CountDown(
                    viewModel.setStart,
                ) { passedDuration ->
                    TimeText(passedDuration.value)
                }
            }
            CountDown(
                viewModel.workoutEnd,
                onFinished = viewModel::finishWorkout
            ) { remainingDuration ->
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 4.dp,
                    startAngle = 290f,
                    endAngle = 250f,
                    progress = {
                        remainingDuration.value.toMillis()
                            .toFloat() / Constants.WORKOUT_DURATION.toMillis()
                    },
                    colors = ProgressIndicatorDefaults.colors(
                        trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .1f),
                        indicatorColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .4f),
                    ),
                )
            }
        }
    }
}

operator fun Duration.rem(other: Duration): Duration {
    return Duration.ofNanos(this.toNanos() % other.toNanos())
}

//@WearPreviewDevices
//@WearPreviewFontScales
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun SplashScreenPreview() {
    LadderApp(LadderViewModel())
}

//@WearPreviewDevices
//@WearPreviewFontScales
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun ReppingPreview() {
    val model = LadderViewModel()
    model.startWorkout()
    LadderApp(model)
}