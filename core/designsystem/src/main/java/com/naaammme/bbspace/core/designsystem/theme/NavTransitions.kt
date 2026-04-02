package com.naaammme.bbspace.core.designsystem.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

private typealias NavEnter<T> = AnimatedContentTransitionScope<T>.() -> EnterTransition
private typealias NavExit<T> = AnimatedContentTransitionScope<T>.() -> ExitTransition

data class NavTransitions<T>(
    val enter: NavEnter<T>,
    val exit: NavExit<T>,
    val popEnter: NavEnter<T>,
    val popExit: NavExit<T>
)

fun <T> buildNavTransitions(style: TransitionStyle, speed: AnimationSpeed): NavTransitions<T> {
    val dur = when (speed) {
        AnimationSpeed.OFF -> 1
        AnimationSpeed.FAST -> 150
        AnimationSpeed.NORMAL -> 300
        AnimationSpeed.SLOW -> 450
    }
    val shortDur = (dur * 0.6f).toInt().coerceAtLeast(1)

    return when (style) {
        TransitionStyle.SHARED_AXIS_X -> NavTransitions(
            enter = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(dur, easing = Emphasized),
                    initialOffset = { it / 6 }
                ) + fadeIn(tween(shortDur, easing = Standard))
            },
            exit = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(dur, easing = Emphasized),
                    targetOffset = { it / 6 }
                ) + fadeOut(tween(shortDur, easing = Standard))
            },
            popEnter = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(dur, easing = Emphasized),
                    initialOffset = { it / 6 }
                ) + fadeIn(tween(shortDur, easing = Standard))
            },
            popExit = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(dur, easing = Emphasized),
                    targetOffset = { it / 6 }
                ) + fadeOut(tween(shortDur, easing = Standard))
            }
        )
        TransitionStyle.SHARED_AXIS_Y -> NavTransitions(
            enter = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(dur, easing = Emphasized),
                    initialOffset = { it / 6 }
                ) + fadeIn(tween(shortDur, easing = Standard))
            },
            exit = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(dur, easing = Emphasized),
                    targetOffset = { it / 6 }
                ) + fadeOut(tween(shortDur, easing = Standard))
            },
            popEnter = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(dur, easing = Emphasized),
                    initialOffset = { it / 6 }
                ) + fadeIn(tween(shortDur, easing = Standard))
            },
            popExit = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(dur, easing = Emphasized),
                    targetOffset = { it / 6 }
                ) + fadeOut(tween(shortDur, easing = Standard))
            }
        )
        TransitionStyle.SHARED_AXIS_Z -> NavTransitions(
            enter = {
                scaleIn(
                    animationSpec = tween(dur, easing = Emphasized),
                    initialScale = 0.94f
                ) + fadeIn(tween(shortDur, easing = Standard))
            },
            exit = {
                scaleOut(
                    animationSpec = tween(dur, easing = Emphasized),
                    targetScale = 1.03f
                ) + fadeOut(tween(shortDur, easing = Standard))
            },
            popEnter = {
                scaleIn(
                    animationSpec = tween(dur, easing = Emphasized),
                    initialScale = 1.03f
                ) + fadeIn(tween(shortDur, easing = Standard))
            },
            popExit = {
                scaleOut(
                    animationSpec = tween(dur, easing = Emphasized),
                    targetScale = 0.94f
                ) + fadeOut(tween(shortDur, easing = Standard))
            }
        )
        TransitionStyle.FADE_THROUGH -> NavTransitions(
            enter = {
                scaleIn(
                    animationSpec = tween(dur, easing = Emphasized),
                    initialScale = 0.96f
                ) + fadeIn(tween(dur, easing = Standard))
            },
            exit = {
                fadeOut(tween(shortDur, easing = Standard))
            },
            popEnter = {
                scaleIn(
                    animationSpec = tween(dur, easing = Emphasized),
                    initialScale = 0.96f
                ) + fadeIn(tween(dur, easing = Standard))
            },
            popExit = {
                fadeOut(tween(shortDur, easing = Standard))
            }
        )
        TransitionStyle.SLIDE -> NavTransitions(
            enter = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(dur, easing = Emphasized)
                )
            },
            exit = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(dur, easing = Emphasized),
                    targetOffset = { it / 3 }
                )
            },
            popEnter = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(dur, easing = Emphasized),
                    initialOffset = { it / 3 }
                )
            },
            popExit = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(dur, easing = Emphasized)
                )
            }
        )
    }
}
