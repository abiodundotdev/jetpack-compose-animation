package com.abiodun.composeanimation.course

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abiodun.composeanimation.course.lessons.Lesson01FrameClock
import com.abiodun.composeanimation.course.lessons.Lesson02AnimateAsState
import com.abiodun.composeanimation.course.lessons.Lesson03Animatable
import com.abiodun.composeanimation.course.lessons.Lesson04Transition
import com.abiodun.composeanimation.course.lessons.Lesson05Infinite
import com.abiodun.composeanimation.course.lessons.Lesson06Gestures
import com.abiodun.composeanimation.course.lessons.Lesson07Layout
import com.abiodun.composeanimation.course.lessons.Lesson08GraphicsLayer
import com.abiodun.composeanimation.course.lessons.Lesson09Shaders
import com.abiodun.composeanimation.singularity.SingularityScreen
import kotlinx.coroutines.delay

val lessons = listOf(
    Lesson(1, "The Frame Clock", "What an animation really is", "BASIC") { Lesson01FrameClock() },
    Lesson(2, "animate*AsState", "Declare a target, get the motion", "BASIC") { Lesson02AnimateAsState() },
    Lesson(3, "Animatable", "The engine, driven by coroutines", "BASIC") { Lesson03Animatable() },
    Lesson(4, "Transition", "Many values, one clock", "INTERMEDIATE") { Lesson04Transition() },
    Lesson(5, "Infinite & Easing", "Loops, keyframes, custom curves", "INTERMEDIATE") { Lesson05Infinite() },
    Lesson(6, "Gestures & Physics", "Drag, fling, spring back", "INTERMEDIATE") { Lesson06Gestures() },
    Lesson(7, "Layout Animations", "Size, lists, shared elements", "ADVANCED") { Lesson07Layout() },
    Lesson(8, "graphicsLayer & Canvas", "Animating in the draw phase", "ADVANCED") { Lesson08GraphicsLayer() },
    Lesson(9, "AGSL Shaders", "Animating every pixel", "ADVANCED") { Lesson09Shaders() },
    Lesson(10, "Singularity", "6,000 particles, everything combined", "CAPSTONE", fullScreen = true) {
        SingularityScreen()
    },
)

@Composable
fun CourseApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Ink.Accent, background = Ink.Bg, surface = Ink.Surface),
    ) {
        // Navigation is one nullable Int and an AnimatedContent (lesson 4). No library needed.
        var open by rememberSaveable { mutableStateOf<Int?>(null) }
        BackHandler(enabled = open != null) { open = null }

        AnimatedContent(
            targetState = open,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            modifier = Modifier.fillMaxSize().background(Ink.Bg),
            label = "navigation",
        ) { index ->
            if (index == null) {
                Home(onOpen = { open = it })
            } else {
                LessonPage(lessons[index], onBack = { open = null })
            }
        }
    }
}

@Composable
private fun Home(onOpen: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text("Compose\nAnimation", color = Ink.Text, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("From the frame clock to a particle singularity.", color = Ink.Dim, fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))
        lessons.forEachIndexed { index, lesson ->
            LessonCard(index, lesson) { onOpen(index) }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun LessonCard(index: Int, lesson: Lesson, onClick: () -> Unit) {
    // Staggered entrance (lesson 3): each card owns an Animatable and waits a little longer.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
    }
    // Press feedback (lesson 2): a boolean becomes a spring-animated scale.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 36.dp.toPx()
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.Surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "%02d".format(lesson.number),
            color = levelColor(lesson.level),
            fontSize = 22.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(lesson.title, color = Ink.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(lesson.tagline, color = Ink.Dim, fontSize = 13.sp)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(levelColor(lesson.level).copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(lesson.level, color = levelColor(lesson.level), fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        }
    }
}
