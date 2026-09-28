package app.dudebooru.ui.face

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.dudebooru.R
import app.dudebooru.ui.icons.DudeIcons

/**
 * Экран блокировки поверх приложения (Настройки → Приватность). Отпечаток спрашивается сам один раз,
 * дальше — по кнопке: отмена системного окна не зацикливает запрос. «Назад» сворачивает приложение.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit, onLeave: () -> Unit) {
    var asked by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (!asked) {
            asked = true
            onUnlock()
        }
        onPauseOrDispose { }
    }
    BackHandler(onBack = onLeave)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Dudi(DudiEmotion.SLEEPY, Modifier.size(120.dp))
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) {
                Icon(DudeIcons.TileFingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.lock_unlock))
            }
        }
    }
}
