package com.bataokaise.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bataokaise.app.core.Phase
import com.bataokaise.app.core.Picture
import com.bataokaise.app.core.UiState

/** Big mic with a pulsing ring while listening. */
@Composable
fun MicButton(phase: Phase, size: Int = 132, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 1.25f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val listening = phase == Phase.LISTENING
    val label = when (phase) {
        Phase.LISTENING -> "Sun raha hoon"
        Phase.PROCESSING -> "Samajh raha hoon"
        Phase.SPEAKING -> "Bol raha hoon (dabaiye to rukunga)"
        Phase.GUIDING -> "Mic dabaiye"
        Phase.IDLE -> "Mic dabaiye"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size((size * 1.35).dp)) {
            if (listening) Box(Modifier.size(size.dp).scale(pulse).border(6.dp, C.Green.copy(alpha = 0.35f), CircleShape))
            Box(
                Modifier.size(size.dp).background(if (listening) C.Danger else C.Green, CircleShape)
                    .clickable(onClick = onClick).semantics { contentDescription = "Mic. $label" },
                contentAlignment = Alignment.Center
            ) { Text("🎤", fontSize = (size / 2.6).sp) }
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = C.Muted)
    }
}

@Composable
fun Bubble(text: String, live: String, phase: Phase) {
    Surface(color = C.Surface, shape = RoundedCornerShape(22.dp), shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().border(1.dp, C.Line, RoundedCornerShape(22.dp))) {
        Column(Modifier.padding(20.dp)) {
            Text(text, style = MaterialTheme.typography.headlineMedium)
            if (live.isNotBlank() && (phase == Phase.LISTENING || phase == Phase.PROCESSING)) {
                Text("“$live”", style = MaterialTheme.typography.titleMedium, color = C.Green, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
fun YesNoRow(onYes: () -> Unit, onNo: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        BigButton("Haan", Modifier.weight(1f).height(72.dp), icon = "✅", onClick = onYes)
        BigButton("Nahi", Modifier.weight(1f).height(72.dp), icon = "❌", secondary = true, onClick = onNo)
    }
}

@Composable
fun PictureButtons(onPick: (Picture) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Picture.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { p ->
                    Surface(
                        color = C.GreenSoft, shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(118.dp).clickable { onPick(p) }
                    ) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(p.emoji, fontSize = 40.sp)
                            Text(p.label, style = MaterialTheme.typography.titleLarge, color = C.Green)
                        }
                    }
                }
            }
        }
    }
}

/** "Type instead" box; also where Wispr Flow app dictation lands. */
@Composable
fun TypeBox(hint: String = "Ya yahan likhiye…", onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, placeholder = { Text(hint) },
            modifier = Modifier.weight(1f).heightIn(min = 60.dp), singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { onSend(text); text = "" } })
        )
        Spacer(Modifier.width(10.dp))
        BigButton("Bhejo", onClick = { if (text.isNotBlank()) { onSend(text); text = "" } })
    }
}

@Composable
fun VoicePanel(
    s: UiState, onMic: () -> Unit, onYes: () -> Unit, onNo: () -> Unit, onPick: (Picture) -> Unit,
    onDone: () -> Unit, onType: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Bubble(s.bubble, s.live, s.phase)
        when {
            s.showYesNo -> YesNoRow(onYes, onNo)
            s.showPictures -> PictureButtons(onPick)
            s.showDone -> BigButton("Ho gaya", Modifier.fillMaxWidth(), icon = "👍", onClick = onDone)
        }
        MicButton(s.phase, onClick = onMic)
        TypeBox(onSend = onType)
        Text(s.voiceLabel + "  ·  " + s.intentLabel, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
fun Placeholder(text: String) {
    Box(Modifier.fillMaxWidth().background(C.GreenSoft, RoundedCornerShape(16.dp)).padding(20.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = C.Ink)
    }
}
