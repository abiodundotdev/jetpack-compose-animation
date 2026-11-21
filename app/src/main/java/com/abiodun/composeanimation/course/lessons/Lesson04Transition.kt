package com.abiodun.composeanimation.course.lessons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abiodun.composeanimation.course.Body
import com.abiodun.composeanimation.course.Chips
import com.abiodun.composeanimation.course.Code
import com.abiodun.composeanimation.course.Heading
import com.abiodun.composeanimation.course.Ink
import com.abiodun.composeanimation.course.PillButton
import com.abiodun.composeanimation.course.Stage
import com.abiodun.composeanimation.course.UnderTheHood

private enum class CardState { Collapsed, Expanded }

@Composable
fun Lesson04Transition() {
    Body(
        "Real UI rarely animates one value. A card that expands changes height, corner, colour, " +
            "an arrow and its content together. A Transition animates many values from one piece " +
            "of state, on one clock.",
    )

    Heading("1 · updateTransition")
    ExpandingCard()
    Code(
        """
        val transition = updateTransition(state, label = "card")
        val height by transition.animateDp { if (it == Expanded) 210.dp else 76.dp }
        val tint   by transition.animateColor { ... }
        val alpha  by transition.animateFloat(
            transitionSpec = {
                if (Collapsed isTransitioningTo Expanded) tween(300, delayMillis = 120)
                else tween(90)
            }
        ) { if (it == Expanded) 1f else 0f }
        """,
    )
    UnderTheHood(
        "updateTransition creates one Transition object that owns a single play time. Each " +
            "animateDp / animateColor / animateFloat registers a child animation on it, and every " +
            "frame all children are evaluated at that same play time. They cannot drift apart.",
        "transitionSpec runs inside a Segment(initialState, targetState), so each property can " +
            "choose a different spec per direction: here the details fade in late but fade out " +
            "immediately.",
        "Because the whole thing is one object with labels, Android Studio's Animation Preview " +
            "can scrub it frame by frame. With five separate animate*AsState calls it could not.",
    )

    Heading("2 · AnimatedVisibility")
    VisibilityDemo()
    UnderTheHood(
        "AnimatedVisibility is a Transition over an internal EnterExitState: PreEnter → Visible → " +
            "PostExit. fadeIn, slideIn, expandIn, scaleIn are only data describing what PreEnter " +
            "looks like; the + operator merges them into one description.",
        "The important trick is at the end: when visible becomes false the content stays in " +
            "the composition until the exit animation finishes, and only then is it removed. " +
            "A plain if (visible) would delete it on the same frame.",
    )

    Heading("3 · AnimatedContent")
    CounterDemo()
    UnderTheHood(
        "AnimatedContent keeps the outgoing and incoming content composed at the same time, each " +
            "keyed by its state, and runs an exit on one and an enter on the other. " +
            "'a togetherWith b' pairs them into a ContentTransform.",
        "SizeTransform animates the container between the old and new content sizes. Crossfade " +
            "is the same mechanism with only alpha.",
        "The lambda receives the state as a parameter. Always use that parameter, not the outer " +
            "variable, or the outgoing content will show the new value while it leaves.",
    )
}

@Composable
private fun ExpandingCard() {
    var state by remember { mutableStateOf(CardState.Collapsed) }
    val transition = updateTransition(state, label = "card")

    val height by transition.animateDp(
        transitionSpec = {
            if (targetState == CardState.Expanded) {
                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
            } else {
                spring(stiffness = Spring.StiffnessMedium)
            }
        },
        label = "height",
    ) { if (it == CardState.Expanded) 210.dp else 76.dp }
    val corner by transition.animateDp(label = "corner") {
        if (it == CardState.Expanded) 30.dp else 18.dp
    }
    val tint by transition.animateColor(label = "tint") {
        if (it == CardState.Expanded) Ink.Accent else Ink.SurfaceHi
    }
    val arrow by transition.animateFloat(label = "arrow") {
        if (it == CardState.Expanded) 180f else 0f
    }
    val detailAlpha by transition.animateFloat(
        transitionSpec = {
            if (CardState.Collapsed isTransitioningTo CardState.Expanded) {
                tween(durationMillis = 300, delayMillis = 120)
            } else {
                tween(durationMillis = 90)
            }
        },
        label = "details",
    ) { if (it == CardState.Expanded) 1f else 0f }

    Column(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(tint)
            .clickable {
                state = if (state == CardState.Collapsed) CardState.Expanded else CardState.Collapsed
            }
            .padding(horizontal = 22.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tap to expand", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.weight(1f))
            Text("▾", color = Color.White, fontSize = 22.sp, modifier = Modifier.graphicsLayer { rotationZ = arrow })
        }
        Text(
            "Height, corner radius, colour, the arrow and this text are five child animations " +
                "of a single Transition, all evaluated at the same play time.",
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.graphicsLayer { alpha = detailAlpha },
        )
    }
}

@Composable
private fun VisibilityDemo() {
    val styles: List<Pair<String, Pair<EnterTransition, ExitTransition>>> = remember {
        listOf(
            "fade" to (fadeIn() to fadeOut()),
            "slide + fade" to ((slideInHorizontally { -it } + fadeIn()) to (slideOutHorizontally { it } + fadeOut())),
            "expand" to ((expandVertically() + fadeIn()) to (shrinkVertically() + fadeOut())),
            "scale" to ((scaleIn(spring(dampingRatio = 0.5f)) + fadeIn()) to (scaleOut() + fadeOut())),
        )
    }
    var styleIndex by remember { mutableIntStateOf(1) }
    var visible by remember { mutableStateOf(true) }
    val (enter, exit) = styles[styleIndex].second

    Stage(Modifier.clickable { visible = !visible }, height = 150.dp) {
        AnimatedVisibility(
            visible = visible,
            enter = enter,
            exit = exit,
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier
                    .size(width = 180.dp, height = 84.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Ink.Mint),
                contentAlignment = Alignment.Center,
            ) {
                Text("tap the stage", color = Ink.Bg, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Chips(styles.mapIndexed { i, (label, _) -> label to i }, styleIndex) { styleIndex = it }
}

@Composable
private fun CounterDemo() {
    var count by remember { mutableIntStateOf(0) }
    Stage(height = 140.dp) {
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            PillButton("  −  ") { count-- }
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    // Direction-aware: going up, the new number rises from below.
                    val transform = if (targetState > initialState) {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    } else {
                        (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                    }
                    transform.using(SizeTransform(clip = false))
                },
                label = "count",
            ) { value ->
                Text("$value", color = Ink.Text, fontSize = 52.sp, fontWeight = FontWeight.Bold)
            }
            PillButton("  +  ") { count++ }
        }
    }
    Spacer(Modifier.height(4.dp))
}
