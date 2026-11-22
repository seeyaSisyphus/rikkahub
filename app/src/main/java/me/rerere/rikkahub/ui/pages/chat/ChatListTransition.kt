package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

private val PreviewTransitionSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)

/**
 * 概览页露出的比例，0 是聊天页，1 是概览页。
 * 外部切换 [previewMode]（如顶栏按钮、点击跳转）时通过动画过渡。
 */
@Composable
internal fun rememberPreviewProgress(previewMode: Boolean): Animatable<Float, *> {
    val progress = remember { Animatable(if (previewMode) 1f else 0f) }
    LaunchedEffect(previewMode) {
        val target = if (previewMode) 1f else 0f
        if (progress.targetValue != target) {
            progress.animateTo(target, PreviewTransitionSpec)
        }
    }
    return progress
}
