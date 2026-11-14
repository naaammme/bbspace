package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * 双指交替滚动手势保护。
 *
 * 适用于列表类页面：当已有手指在屏幕上滑动（超出触摸 slop，即正在滚动）时，
 * 其余手指的按下/抬起会被消费掉，使子项的点击不生效，而是加入滚动；
 * 所有手指都抬起后恢复正常点击。
 * 滚动本身不受影响：移动事件不消费，滚动仍由各手指共同驱动，
 * 松开其中一根手指后可无缝继续单指滚动。
 */
fun Modifier.scrollTapGuard(): Modifier = composed {
    pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val moveDist = HashMap<PointerId, Float>()
            var scrolling = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                for (change in event.changes) {
                    if (change.pressed) {
                        val prev = moveDist.getOrPut(change.id) { 0f }
                        val dist = prev +
                            (change.position - change.previousPosition).getDistance()
                        moveDist[change.id] = dist
                        if (!scrolling && dist >= slop) {
                            scrolling = true
                        }
                    } else {
                        moveDist.remove(change.id)
                    }
                }
                // 注意：即使这是最后一根手指的抬起，也要先消费再退出，
                // 否则滚动手指先抬起后，另一根手指的点击会穿透
                if (scrolling) {
                    for (change in event.changes) {
                        if (change.changedToDown() || change.changedToUp()) {
                            change.consume()
                        }
                    }
                }
                if (moveDist.isEmpty()) break
            }
        }
    }
}
