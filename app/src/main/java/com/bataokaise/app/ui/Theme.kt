package com.bataokaise.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bataokaise.app.R

object C {
    val Bg = Color(0xFFFBF7F0)        // warm off-white
    val Surface = Color(0xFFFFFFFF)
    val Green = Color(0xFF1F5E3B)     // the one accent
    val GreenSoft = Color(0xFFE3EFE7)
    val Ink = Color(0xFF1B1B18)
    val Muted = Color(0xFF5B5A55)
    val Line = Color(0xFFE4DED3)
    val Danger = Color(0xFF9E2A1E)
    val Amber = Color(0xFFF2B33D)
}

val Mukta = FontFamily(
    Font(R.font.mukta_regular, FontWeight.Normal),
    Font(R.font.mukta_semibold, FontWeight.SemiBold),
    Font(R.font.mukta_bold, FontWeight.Bold),
)

private val base = TextStyle(fontFamily = Mukta, color = C.Ink)
private val typography = Typography(
    displaySmall = base.copy(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineMedium = base.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = base.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.copy(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.copy(fontSize = 19.sp, lineHeight = 27.sp),
    bodyMedium = base.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodySmall = base.copy(fontSize = 15.sp, lineHeight = 21.sp, color = C.Muted),
    labelLarge = base.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun BataoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.Green, onPrimary = Color.White, background = C.Bg, surface = C.Surface,
            onBackground = C.Ink, onSurface = C.Ink, secondaryContainer = C.GreenSoft, error = C.Danger,
        ),
        typography = typography,
        content = content
    )
}

/** Primary action: full width, at least 56 dp, label always visible. */
@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, icon: String? = null, secondary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = if (secondary) ButtonDefaults.outlinedButtonColors(contentColor = C.Green)
    else ButtonDefaults.buttonColors(containerColor = C.Green, contentColor = Color.White)
    val shape = RoundedCornerShape(16.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) Text(icon, fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    if (secondary) OutlinedButton(onClick, modifier.heightIn(min = 60.dp), enabled = enabled, shape = shape, colors = colors,
        border = androidx.compose.foundation.BorderStroke(2.dp, C.Green), content = content)
    else Button(onClick, modifier.heightIn(min = 60.dp), enabled = enabled, shape = shape, colors = colors, content = content)
}

@Composable
fun DemoChip(text: String = "DEMO") {
    Surface(color = C.Amber.copy(alpha = 0.25f), shape = RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B4A00), fontFamily = Mukta)
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) {
            Text("← Peeche", style = MaterialTheme.typography.titleMedium, color = C.Green)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = if (onBack == null) 12.dp else 4.dp))
        trailing()
    }
}
