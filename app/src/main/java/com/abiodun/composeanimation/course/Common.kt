package com.abiodun.composeanimation.course

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The course palette. Plain constants, so demos can use them outside composition too. */
object Ink {
    val Bg = Color(0xFF0B0B12)
    val Surface = Color(0xFF16161F)
    val SurfaceHi = Color(0xFF232331)
    val Accent = Color(0xFF7C5CFF)
    val Mint = Color(0xFF3CFFD0)
    val Pink = Color(0xFFFF4FA3)
    val Amber = Color(0xFFFFB23C)
    val Text = Color(0xFFECECF4)
    val Dim = Color(0xFF8A8AA0)
}

class Lesson(
    val number: Int,
    val title: String,
    val tagline: String,
    val level: String,
    val fullScreen: Boolean = false,
    val content: @Composable () -> Unit,
)

fun levelColor(level: String): Color = when (level) {
    "BASIC" -> Ink.Mint
    "INTERMEDIATE" -> Ink.Amber
    "ADVANCED" -> Ink.Pink
    else -> Ink.Accent
}

@Composable
fun LessonPage(lesson: Lesson, onBack: () -> Unit) {
    if (lesson.fullScreen) {
        Box(Modifier.fillMaxSize()) {
            lesson.content()
            BackPill(onBack, Modifier.statusBarsPadding().padding(16.dp))
        }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackPill(onBack)
            Spacer(Modifier.padding(start = 12.dp))
            Text(
                "LESSON %02d  ·  %s".format(lesson.number, lesson.level),
                color = levelColor(lesson.level),
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(lesson.title, color = Ink.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(lesson.tagline, color = Ink.Dim, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        lesson.content()
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
fun BackPill(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        "←  Back",
        color = Ink.Text,
        fontSize = 13.sp,
        modifier = modifier
            .clip(CircleShape)
            .background(Ink.SurfaceHi.copy(alpha = 0.8f))
            .clickable(onClick = onBack)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun Heading(text: String) {
    Spacer(Modifier.height(28.dp))
    Text(text, color = Ink.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun Body(text: String) {
    Text(text, color = Ink.Dim, fontSize = 14.sp, lineHeight = 21.sp)
    Spacer(Modifier.height(12.dp))
}

@Composable
fun Code(text: String) {
    Spacer(Modifier.height(12.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F0F18))
            .border(1.dp, Ink.SurfaceHi, RoundedCornerShape(14.dp))
            .horizontalScroll(rememberScrollState())
            .padding(14.dp),
    ) {
        Text(
            text.trimIndent(),
            color = Color(0xFFB9C4FF),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
    Spacer(Modifier.height(12.dp))
}

/** The "internals" card every lesson ends its sections with. */
@Composable
fun UnderTheHood(vararg points: String) {
    val shape = RoundedCornerShape(16.dp)
    Spacer(Modifier.height(12.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Surface)
            .border(1.dp, Ink.Accent.copy(alpha = 0.4f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "UNDER THE HOOD",
            color = Ink.Accent,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
        )
        points.forEach { point ->
            Row {
                Text("›  ", color = Ink.Mint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(point, color = Ink.Text, fontSize = 13.5.sp, lineHeight = 20.sp)
            }
        }
    }
}

/** The dark rounded box demos live in. */
@Composable
fun Stage(
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.Surface),
        content = content,
    )
}

@Composable
fun <T> Chips(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            val bg by animateColorAsState(if (isSelected) Ink.Accent else Ink.SurfaceHi, label = "chip")
            Text(
                label,
                color = if (isSelected) Color.White else Ink.Dim,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(CircleShape)
            .background(Ink.Accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
