package com.marco.streammore.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/** State and the private code live only for this composition, never in saved state. */
@Composable
internal fun LoginFlow(
    loading: Boolean,
    error: String?,
    start: suspend () -> DeviceLoginCode,
    poll: suspend (String) -> JSONObject?,
    cancel: suspend (String) -> Unit,
    onApproved: (JSONObject) -> Unit,
    onLogin: (String, String) -> Unit,
) {
    var codeMode by remember { mutableStateOf(false) }
    if (!codeMode) {
        LoginScreen(loading, error, onCodeLogin = { codeMode = true }, onLogin = onLogin)
    } else {
        DeviceCodeLoginScreen(start, poll, cancel, onApproved, onBack = { codeMode = false })
    }
}

@Composable
internal fun DeviceCodeLoginScreen(
    start: suspend () -> DeviceLoginCode,
    poll: suspend (String) -> JSONObject?,
    cancel: suspend (String) -> Unit,
    onApproved: (JSONObject) -> Unit,
    onBack: () -> Unit,
) {
    var state by remember { mutableStateOf(DeviceLoginState()) }
    var attempt by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    val currentApproved by rememberUpdatedState(onApproved)
    val currentBack by rememberUpdatedState(onBack)
    BackHandler { currentBack() }
    LaunchedEffect(attempt) {
        try {
            val response = runDeviceLogin(start, poll, cancel, onState = { state = it })
            currentCoroutineContext().ensureActive()
            running = false
            response?.let { currentApproved(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            state = DeviceLoginState(message = "Could not create a code. Check your connection and try again.", retry = true)
            running = false
        }
    }
    DeviceCodeContent(state.copy(retry = state.retry && !running), onRetry = {
        if (!running && state.retry) {
            running = true
            state = DeviceLoginState()
            attempt++
        }
    }, onBack = { currentBack() })
}

@Composable
internal fun DeviceCodeContent(state: DeviceLoginState, onRetry: () -> Unit, onBack: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(state.retry) {
        repeat(4) {
            withFrameNanos { }
            (if (state.retry) retryFocus else cancelFocus).requestFocus()
        }
    }
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Sign in with a code", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text("On your phone or computer, sign in at", color = Muted, fontSize = 18.sp)
        Text("https://streammore.mmcloud.co.za/account", fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))
        Text("Choose Link a TV, then enter this code.", color = Muted, fontSize = 18.sp)
        state.userCode?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, fontSize = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(state.message, fontSize = 16.sp, color = Muted)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (state.retry) {
                TvButton(onRetry, primary = true, modifier = Modifier.focusRequester(retryFocus).focusProperties {
                    canFocus = true; right = cancelFocus
                }) { Text("Get a new code") }
            }
            TvButton(onBack, modifier = Modifier.focusRequester(cancelFocus).focusProperties {
                canFocus = true
                if (state.retry) left = retryFocus
            }) { Text("Cancel") }
        }
    }
}
