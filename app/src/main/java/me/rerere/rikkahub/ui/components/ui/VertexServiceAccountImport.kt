package me.rerere.rikkahub.ui.components.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.rerere.common.google.GoogleServiceAccount
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.ui.toast.ToastType

/**
 * 「导入服务账号 JSON 并自动解析配置」的共用入口。
 *
 * 模型配置里的 Vertex AI、Gemini Vertex TTS / Vertex Cloud TTS、Chirp 3 ASR 对接的是
 * 同一套 Google Cloud 服务账号鉴权，所以都从这里选文件、解析并回填，
 * 避免每个 Vertex 相关页面各写一份 JSON 解析逻辑。
 *
 * 返回的函数用于触发系统文件选择器；解析成功后通过 [onImported] 把字段交给调用方。
 */
@Composable
fun rememberVertexServiceAccountImporter(
    onImported: (GoogleServiceAccount) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    // 文件选择结果在 Activity 恢复后才回调，用 rememberUpdatedState 确保拿到最新的设置项
    val latestOnImported by rememberUpdatedState(onImported)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw IllegalStateException("cannot read the selected file")
            latestOnImported(GoogleServiceAccount.parse(content))
            toaster.show("Service account imported", type = ToastType.Success)
        } catch (e: Exception) {
            toaster.show("Failed to import: ${e.message}", type = ToastType.Error)
        }
    }

    return remember(launcher) {
        { launcher.launch(arrayOf("application/json", "text/plain", "*/*")) }
    }
}

/**
 * CardGroup 里的「导入服务账号 JSON」入口项，点击后触发文件选择。
 */
fun CardGroupScope.vertexServiceAccountImportItem(
    onClick: () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
) = item(
    onClick = onClick,
    supportingContent = supportingContent,
    headlineContent = { Text(stringResource(R.string.setting_provider_page_import_service_account_json)) },
    trailingContent = { Icon(HugeIcons.FileImport, contentDescription = null) },
)
