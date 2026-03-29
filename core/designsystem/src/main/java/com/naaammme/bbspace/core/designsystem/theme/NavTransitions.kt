package com.naaammme.bbspace.core.designsystem.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

data class NavTransitions(
    val enter: EnterTransition,
    val exit: ExitTransition,
    val popEnter: EnterTransition,
    val popExit: ExitTransition
)

fun buildNavTransitions(style: TransitionStyle, speed: AnimationSpeed): NavTransitions {
    val dur = when (speed) {
        AnimationSpeed.OFF -> 1
        AnimationSpeed.FAST -> 150
        AnimationSpeed.NORMAL -> 300
        AnimationSpeed.SLOW -> 450
    }
    val shortDur = (dur * 0.6f).toInt().coerceAtLeast(1)

    return when (style) {
        TransitionStyle.SHARED_AXIS_X -> NavTransitions(
            enter = slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(shortDur, easing = FastOutSlowInEasing)),
            exit = slideOutHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(shortDur, easing = FastOutSlowInEasing)),
            popEnter = slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(shortDur, easing = FastOutSlowInEasing)),
            popExit = slideOutHorizontally(tween(dur, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(shortDur, easing = FastOutSlowInEasing))
        )
        TransitionStyle.SHARED_AXIS_Y -> NavTransitions(
            enter = slideInVertically(tween(dur, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(shortDur, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(tween(dur, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(shortDur, easing = FastOutSlowInEasing)),
            popEnter = slideInVertically(tween(dur, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(shortDur, easing = FastOutSlowInEasing)),
            popExit = slideOutVertically(tween(dur, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(shortDur, easing = FastOutSlowInEasing))
        )
        TransitionStyle.SHARED_AXIS_Z -> NavTransitions(
            enter = scaleIn(tween(dur, easing = FastOutSlowInEasing), initialScale = 0.9f) + fadeIn(tween(shortDur, easing = FastOutSlowInEasing)),
            exit = scaleOut(tween(dur, easing = FastOutSlowInEasing), targetScale = 1.1f) + fadeOut(tween(shortDur, easing = FastOutSlowInEasing)),
            popEnter = scaleIn(tween(dur, easing = FastOutSlowInEasing), initialScale = 1.1f) + fadeIn(tween(shortDur, easing = FastOutSlowInEasing)),
            popExit = scaleOut(tween(dur, easing = FastOutSlowInEasing), targetScale = 0.9f) + fadeOut(tween(shortDur, easing = FastOutSlowInEasing))
        )
        TransitionStyle.FADE_THROUGH -> NavTransitions(
            enter = scaleIn(tween(dur, easing = FastOutSlowInEasing), initialScale = 0.92f) + fadeIn(tween(dur, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(shortDur, easing = FastOutSlowInEasing)),
            popEnter = scaleIn(tween(dur, easing = FastOutSlowInEasing), initialScale = 0.92f) + fadeIn(tween(dur, easing = FastOutSlowInEasing)),
            popExit = fadeOut(tween(shortDur, easing = FastOutSlowInEasing))
        )
        TransitionStyle.SLIDE -> NavTransitions(
            enter = slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { it },
            exit = slideOutHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 3 },
            popEnter = slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 3 },
            popExit = slideOutHorizontally(tween(dur, easing = FastOutSlowInEasing)) { it }
        )
    }
}
