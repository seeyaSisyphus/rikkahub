package me.rerere.rikkahub.ui.components.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

private const val TAG = "AutoFocus"

/**
 * 生成一个"内容出现即聚焦"的 [FocusRequester]。
 *
 * 用于「点按钮才出现/打开」的输入场景：搜索条、重命名/新建弹窗、输入型 BottomSheet 等。
 * 这类输入框不是页面常驻元素，Compose 不会自动把焦点交给它，必须显式 requestFocus，
 * 否则用户点开按钮后还要再点一次输入框，输入法也不会弹出。
 *
 * 时序：输入框通常要等首帧才完成挂载（AnimatedVisibility / Dialog / ModalBottomSheet 都是如此），
 * 因此先等一帧再请求焦点，并在聚焦成功后主动拉起输入法。
 *
 * @param requestKey 聚焦请求的触发键。默认 `Unit` 表示"所在内容每次进入组合都聚焦"（适合只在该内容
 *   可见时才组合的弹窗）。常驻组合的输入框应传入一个自增 token；传 `null` 表示当前不需要聚焦。
 * @param showKeyboard 聚焦成功后是否主动拉起输入法。只读输入框应传 false。
 * @param onRequestHandled 聚焦请求处理完成后的回调。常驻组件用它把 token 复位，避免重建组合时重放旧请求。
 */
@Composable
fun rememberAutoFocusRequester(
    requestKey: Any? = Unit,
    showKeyboard: Boolean = true,
    onRequestHandled: () -> Unit = {},
): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(requestKey) {
        if (requestKey == null) return@LaunchedEffect
        // 等输入框真正上树，避免 FocusRequester is not initialized
        withFrameNanos { }
        val focused = runCatching { focusRequester.requestFocus() }
            .onFailure { Log.w(TAG, "requestFocus failed, input may not be attached yet", it) }
            .isSuccess
        if (!focused) return@LaunchedEffect
        if (showKeyboard) {
            withFrameNanos { }
            keyboardController?.show()
        }
        onRequestHandled()
    }
    return focusRequester
}
