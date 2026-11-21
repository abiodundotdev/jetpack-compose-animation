package com.abiodun.composeanimation.course.lessons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abiodun.composeanimation.course.Body
import com.abiodun.composeanimation.course.Code
import com.abiodun.composeanimation.course.Heading
import com.abiodun.composeanimation.course.Ink
import com.abiodun.composeanimation.course.PillButton
import com.abiodun.composeanimation.course.Stage
import com.abiodun.composeanimation.course.UnderTheHood

@Composable
fun Lesson07Layout() {
    Body(
        "So far we animated values we computed ourselves. Layout animations are different: the " +
            "layout system decides the final size and position, and the animation has to find " +
            "out what they are before it can move anything.",
    )

    Heading("1 · animateContentSize")
    ContentSizeDemo()
    UnderTheHood(
        "It is a layout modifier. When the content changes it measures the child at its new " +
            "size straight away, but reports an animated size to its parent and clips the child. " +
            "Siblings below slide because the parent is re-laid out each frame with that size.",
        "Position in the chain matters. It animates whatever is measured after it, so it goes " +
            "before padding and before any fixed size modifier, and after the clip/background " +
            "you want to grow with it.",
    )

    Heading("2 · LazyColumn item animations")
    ListDemo()
    Code(
        """
        items(tiles, key = { it.id }) { tile ->
            Row(Modifier.animateItem()) { ... }
        }
        """,
    )
    UnderTheHood(
        "The lazy list remembers each key's last offset. After a data change it lays the items " +
            "out at their new positions, then, for any key whose position moved, animates a " +
            "placement offset from old to new. Items are not re-measured for this, only shifted.",
        "This only works with stable keys. Without key = { it.id } the list identifies items by " +
            "index, sees 'item 0 changed content' and has nothing to move.",
        "animateItem covers three cases with three specs: fadeInSpec for new items, " +
            "placementSpec for moves, fadeOutSpec for removed ones (kept alive until faded).",
    )

    Heading("3 · Shared elements")
    Body("Tap the row. The artwork and the title travel between two completely different layouts.")
    SharedElementDemo()
    Code(
        """
        SharedTransitionLayout {
            AnimatedContent(expanded) { isExpanded ->
                Box(Modifier.sharedElement(
                    rememberSharedContentState(key = "art"),
                    animatedVisibilityScope = this@AnimatedContent,
                ))
            }
        }
        """,
    )
    UnderTheHood(
        "The two layouts are separate composables that never know about each other. They are " +
            "matched only by the key in rememberSharedContentState.",
        "SharedTransitionLayout is a LookaheadScope. Compose runs an extra 'lookahead' measure " +
            "pass that calculates where everything will end up, before the real pass. With the " +
            "current bounds and the future bounds both known, it animates between them.",
        "During the flight the element is drawn in an overlay on top of the whole " +
            "SharedTransitionLayout, so it is not cut off by the clips of either parent. " +
            "sharedElement is for identical content; sharedBounds is for content that differs " +
            "(like text at two sizes) and cross-fades while the bounds morph.",
    )
}

@Composable
private fun ContentSizeDemo() {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.Surface)
            .clickable { expanded = !expanded }
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
            .padding(18.dp),
    ) {
        Text(
            "Tap me. This text is set to two lines when collapsed and unlimited when expanded. " +
                "Nothing here specifies a height or an animation target: the text is simply " +
                "measured at its new size, and animateContentSize animates the size it reports " +
                "upward, which is why the content below this card moves smoothly instead of " +
                "jumping to its new place.",
            color = Ink.Text,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text("↑ I am pushed by the card above", color = Ink.Dim, fontSize = 12.sp)
}

private data class Tile(val id: Int, val color: Color)

private val tileColors = listOf(Ink.Accent, Ink.Pink, Ink.Mint, Ink.Amber)

@Composable
private fun ListDemo() {
    var nextId by remember { mutableIntStateOf(5) }
    var tiles by remember { mutableStateOf(List(5) { Tile(it, tileColors[it % tileColors.size]) }) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Shuffle") { tiles = tiles.shuffled() }
        PillButton("Add") {
            tiles = listOf(Tile(nextId, tileColors[nextId % tileColors.size])) + tiles
            nextId++
        }
        PillButton("Remove") { tiles = tiles.drop(1) }
    }
    Spacer(Modifier.height(12.dp))
    Stage(height = 270.dp) {
        LazyColumn(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tiles, key = { it.id }) { tile ->
                Row(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ink.SurfaceHi)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(tile.color))
                    Spacer(Modifier.width(12.dp))
                    Text("Item ${tile.id}", color = Ink.Text, fontSize = 15.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedElementDemo() {
    var expanded by remember { mutableStateOf(false) }
    val art = remember { Brush.linearGradient(listOf(Ink.Accent, Ink.Pink, Ink.Amber)) }

    SharedTransitionLayout(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.Surface)
            .clickable { expanded = !expanded },
    ) {
        AnimatedContent(targetState = expanded, label = "shared") { isExpanded ->
            if (!isExpanded) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .sharedElement(
                                rememberSharedContentState(key = "art"),
                                animatedVisibilityScope = this@AnimatedContent,
                            )
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(art),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Nebula",
                        color = Ink.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.sharedBounds(
                            rememberSharedContentState(key = "title"),
                            animatedVisibilityScope = this@AnimatedContent,
                        ),
                    )
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Box(
                        Modifier
                            .sharedElement(
                                rememberSharedContentState(key = "art"),
                                animatedVisibilityScope = this@AnimatedContent,
                            )
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(art),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Nebula",
                        color = Ink.Text,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.sharedBounds(
                            rememberSharedContentState(key = "title"),
                            animatedVisibilityScope = this@AnimatedContent,
                        ),
                    )
                    Text(
                        "This paragraph exists only in the expanded layout, so it simply fades in " +
                            "with AnimatedContent while the shared pieces fly.",
                        color = Ink.Dim,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}
