package me.rerere.ai.util

import android.media.ExifInterface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import android.util.Base64OutputStream
import androidx.core.net.toUri
import me.rerere.ai.ui.UIMessagePart
import java.io.ByteArrayOutputStream
import java.io.File

private val supportedTypes = setOf(
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp",
)

/** 单张图片编码后的目标字节上限，超过则降质量/降分辨率重压，避免请求体过大导致 OOM */
private const val MAX_ENCODED_IMAGE_BYTES = 1_500_000

/** JPEG 压缩时的初始缓冲区 */
private const val DEFAULT_JPEG_BUFFER_SIZE = 64 * 1024

/** 动图超过该大小后不再原样发送：base64 体积对内存和提供商都不可接受，退化成静态图 */
private const val MAX_RAW_GIF_BYTES = 4 * 1024 * 1024L

/** 降分辨率的下限：长边再小就没有压缩意义了，避免把小图压成马赛克 */
private const val MIN_ENCODED_IMAGE_LONG_SIDE = 512

/**
 * 图片压缩重试阶梯：先把质量降下来，最后才降分辨率。
 * 这样长截图等文字图片能保住分辨率，而细节很多的照片会自动缩到合理体积。
 */
internal val IMAGE_ENCODE_ATTEMPTS = listOf(
    ImageEncodeAttempt(quality = 85, scale = 1),
    ImageEncodeAttempt(quality = 70, scale = 1),
    ImageEncodeAttempt(quality = 55, scale = 1),
    ImageEncodeAttempt(quality = 45, scale = 1),
    ImageEncodeAttempt(quality = 70, scale = 2),
    ImageEncodeAttempt(quality = 55, scale = 2),
    ImageEncodeAttempt(quality = 65, scale = 4),
)

internal data class ImageEncodeAttempt(
    val quality: Int,
    val scale: Int,
)

/** data url / base64 图片解码结果 */
data class DecodedImageData(
    val bytes: ByteArray,
    val mimeType: String,
)

data class EncodedImage(
    val base64: String,
    val mimeType: String
)

internal enum class ExifTransformType {
    NONE,
    FLIP_HORIZONTAL,
    ROTATE_180,
    FLIP_VERTICAL,
    TRANSPOSE,
    ROTATE_90,
    TRANSVERSE,
    ROTATE_270,
}

internal fun mapExifOrientationToTransform(orientation: Int): ExifTransformType = when (orientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifTransformType.FLIP_HORIZONTAL
    ExifInterface.ORIENTATION_ROTATE_180 -> ExifTransformType.ROTATE_180
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifTransformType.FLIP_VERTICAL
    ExifInterface.ORIENTATION_TRANSPOSE -> ExifTransformType.TRANSPOSE
    ExifInterface.ORIENTATION_ROTATE_90 -> ExifTransformType.ROTATE_90
    ExifInterface.ORIENTATION_TRANSVERSE -> ExifTransformType.TRANSVERSE
    ExifInterface.ORIENTATION_ROTATE_270 -> ExifTransformType.ROTATE_270
    ExifInterface.ORIENTATION_NORMAL,
    ExifInterface.ORIENTATION_UNDEFINED
    -> ExifTransformType.NONE

    else -> ExifTransformType.NONE
}

fun UIMessagePart.Image.encodeBase64(withPrefix: Boolean = true): Result<EncodedImage> = runCatching {
    when {
        this.url.startsWith("file://") -> {
            val filePath =
                this.url.toUri().path ?: throw IllegalArgumentException("Invalid file URI: ${this.url}")
            val file = File(filePath)
            if (!file.exists()) {
                throw IllegalArgumentException("File does not exist: ${this.url}")
            }
            val mimeType = file.guessMimeType().getOrThrow()
            // 统一进行压缩处理
            val (encoded, outputMimeType) = file.compressAndEncode(mimeType)
            EncodedImage(
                base64 = if (withPrefix) "data:$outputMimeType;base64,$encoded" else encoded,
                mimeType = outputMimeType
            )
        }

        this.url.startsWith("data:") -> {
            // 从 data URL 提取 mime type 与纯 base64 数据（部分提供商不接受带前缀的 data url）
            val mimeType = url.substringAfter("data:").substringBefore(";")
            EncodedImage(
                base64 = if (withPrefix) url else url.substringAfter("base64,", url),
                mimeType = mimeType,
            )
        }
        this.url.startsWith("http") -> {
            // HTTP URL 无法确定 mime type，默认使用 image/png
            EncodedImage(base64 = url, mimeType = "image/png")
        }
        else -> throw IllegalArgumentException("Unsupported URL format: $url")
    }
}

/**
 * 解析 data url（或裸 base64）图片，返回原始字节与 mime type。
 *
 * 只解码、不重新编码：上游返回的图片本身已是压缩格式，
 * 再解码成 Bitmap 并用 PNG 重压既费内存（大图容易 OOM），文件也会变大。
 */
fun decodeImageData(image: String): DecodedImageData {
    val isDataUrl = image.startsWith("data:")
    val base64Data = if (isDataUrl) image.substringAfter("base64,", image) else image
    // URL safe 变体（- 和 _）用 DEFAULT 解出来会静默丢字符，这里单独处理
    val flags = if (base64Data.contains('-') || base64Data.contains('_')) {
        Base64.URL_SAFE
    } else {
        Base64.DEFAULT
    }
    val bytes = Base64.decode(base64Data, flags)
    val declaredMime = if (isDataUrl) {
        image.substringAfter("data:").substringBefore(";").takeIf { it.isNotBlank() }
    } else {
        null
    }
    // data url 里的 mime 可能缺失或不准确，优先按文件头判断
    val mimeType = sniffImageMimeType(bytes) ?: declaredMime ?: "image/png"
    return DecodedImageData(bytes, mimeType)
}

/** 通过文件头识别常见图片格式 */
internal fun sniffImageMimeType(bytes: ByteArray): String? = when {
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() ->
        "image/jpeg"

    bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_FILE_HEADER) -> "image/png"
    bytes.size >= 12 && bytes.asciiString(0, 4) == "RIFF" && bytes.asciiString(8, 4) == "WEBP" -> "image/webp"
    bytes.size >= 6 && bytes.asciiString(0, 3) == "GIF" -> "image/gif"
    bytes.size >= 12 && bytes.asciiString(4, 4) == "ftyp" -> "image/heic"
    else -> null
}

private fun ByteArray.asciiString(offset: Int, length: Int): String =
    String(this, offset, length, Charsets.US_ASCII)

private val PNG_FILE_HEADER = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

fun UIMessagePart.Video.encodeBase64(withPrefix: Boolean = true): Result<String> = runCatching {
    when {
        this.url.startsWith("file://") -> {
            val filePath =
                this.url.toUri().path ?: throw IllegalArgumentException("Invalid file URI: ${this.url}")
            val file = File(filePath)
            if (!file.exists()) {
                throw IllegalArgumentException("File does not exist: ${this.url}")
            }
            val encoded = file.encodeToBase64Streaming()
            if (withPrefix) "data:video/mp4;base64,$encoded" else encoded
        }

        else -> throw IllegalArgumentException("Unsupported URL format: $url")
    }
}

fun UIMessagePart.Audio.encodeBase64(withPrefix: Boolean = true): Result<String> = runCatching {
    when {
        this.url.startsWith("file://") -> {
            val filePath =
                this.url.toUri().path ?: throw IllegalArgumentException("Invalid file URI: ${this.url}")
            val file = File(filePath)
            if (!file.exists()) {
                throw IllegalArgumentException("File does not exist: ${this.url}")
            }
            val encoded = file.encodeToBase64Streaming()
            if (withPrefix) "data:audio/mp3;base64,$encoded" else encoded
        }

        else -> throw IllegalArgumentException("Unsupported URL format: $url")
    }
}

private fun File.compressAndEncode(
    mimeType: String,
    maxDimension: Int = 10_000,
    maxPixels: Long = 16_000_000L,
): Pair<String, String> {
    // 小动图保持原样（可能是动图），过大的仍走压缩流程，避免 base64 体积失控
    if (mimeType == "image/gif" && length() <= MAX_RAW_GIF_BYTES) {
        return Pair(encodeToBase64Streaming(), mimeType)
    }

    val bitmap = decodeFileBounded(absolutePath, maxDimension, maxPixels)
    val normalizedBitmap = normalizeByExif(bitmap)

    return try {
        // 强制使用 JPEG 格式，因为很多提供商不支持 webp
        val jpegBytes = normalizedBitmap.encodeJpegWithinBudget()
        Pair(Base64.encodeToString(jpegBytes, Base64.NO_WRAP), "image/jpeg")
    } finally {
        if (normalizedBitmap !== bitmap) {
            normalizedBitmap.recycle()
        }
        bitmap.recycle()
    }
}

/** 按尺寸/像素预算解码图片，避免直接把几千万像素的原图读进内存 */
private fun decodeFileBounded(path: String, maxDimension: Int, maxPixels: Long): Bitmap {
    // 读取图片尺寸
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(path, options)

    options.inSampleSize = calculateImageInSampleSize(
        width = options.outWidth,
        height = options.outHeight,
        maxDimension = maxDimension,
        maxPixels = maxPixels
    )
    options.inJustDecodeBounds = false

    return BitmapFactory.decodeFile(path, options)
        ?: throw IllegalArgumentException("Failed to decode image: $path")
}

/**
 * 按图片尺寸过滤压缩阶梯：小图不再降分辨率，只降质量，
 * 避免把本来就不大的图片压成一团马赛克。
 */
internal fun encodeAttemptsFor(
    width: Int,
    height: Int,
    attempts: List<ImageEncodeAttempt> = IMAGE_ENCODE_ATTEMPTS,
): List<ImageEncodeAttempt> {
    val longSide = maxOf(width, height)
    val usable = attempts.filter {
        it.scale <= 1 || longSide / it.scale >= MIN_ENCODED_IMAGE_LONG_SIDE
    }
    return usable.ifEmpty { listOf(attempts.first()) }
}

/**
 * 把 bitmap 压成不超过 [maxBytes] 的 JPEG。
 *
 * 先降质量、再降分辨率逐级重试：长截图这类文字图片通常前几轮就能塞进预算，
 * 细节很多的照片则会自动降到合理分辨率，避免单张图片的 base64 过大把堆撑爆。
 */
internal fun Bitmap.encodeJpegWithinBudget(
    maxBytes: Int = MAX_ENCODED_IMAGE_BYTES,
    attempts: List<ImageEncodeAttempt> = IMAGE_ENCODE_ATTEMPTS,
): ByteArray {
    var smallest: ByteArray? = null
    for (attempt in encodeAttemptsFor(width, height, attempts)) {
        val target = if (attempt.scale <= 1) this else scaledDown(attempt.scale)
        try {
            val bytes = target.encodeJpeg(attempt.quality)
            val current = smallest
            if (current == null || bytes.size < current.size) {
                smallest = bytes
            }
            if (bytes.size <= maxBytes) {
                return bytes
            }
        } finally {
            if (target !== this) {
                target.recycle()
            }
        }
    }
    return checkNotNull(smallest) { "Failed to encode image" }
}

private fun Bitmap.scaledDown(scale: Int): Bitmap {
    val width = (this.width / scale).coerceAtLeast(1)
    val height = (this.height / scale).coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, width, height, true)
}

private fun Bitmap.encodeJpeg(quality: Int): ByteArray {
    val output = ByteArrayOutputStream(DEFAULT_JPEG_BUFFER_SIZE)
    return try {
        compress(Bitmap.CompressFormat.JPEG, quality, output)
        output.toByteArray()
    } finally {
        output.close()
    }
}
private fun File.normalizeByExif(bitmap: Bitmap): Bitmap {
    val orientation = runCatching {
        ExifInterface(absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val transform = mapExifOrientationToTransform(orientation)
    return applyExifTransform(bitmap, transform)
}

private fun applyExifTransform(bitmap: Bitmap, transform: ExifTransformType): Bitmap {
    if (transform == ExifTransformType.NONE) return bitmap

    val matrix = Matrix()
    when (transform) {
        ExifTransformType.NONE -> return bitmap
        ExifTransformType.FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifTransformType.ROTATE_180 -> matrix.setRotate(180f)
        ExifTransformType.FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifTransformType.TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifTransformType.ROTATE_90 -> matrix.setRotate(90f)
        ExifTransformType.TRANSVERSE -> {
            matrix.setRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        ExifTransformType.ROTATE_270 -> matrix.setRotate(270f)
    }

    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrElse { bitmap }
}

private fun File.encodeToBase64Streaming(): String {
    val estimatedSize = (length() * 4 / 3).coerceIn(DEFAULT_JPEG_BUFFER_SIZE.toLong(), Int.MAX_VALUE.toLong())
    val byteArrayOutputStream = ByteArrayOutputStream(estimatedSize.toInt())
    Base64OutputStream(byteArrayOutputStream, Base64.NO_WRAP).use { base64Stream ->
        inputStream().use { input ->
            input.copyTo(base64Stream, bufferSize = 8 * 1024)
        }
    }
    return byteArrayOutputStream.toString(Charsets.ISO_8859_1.name())
}

internal fun calculateImageInSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int,
    maxPixels: Long
): Int {
    if (width <= 0 || height <= 0) return 1

    var inSampleSize = 1
    while (
        (height / inSampleSize) > maxDimension ||
        (width / inSampleSize) > maxDimension ||
        (width.toLong() / inSampleSize) * (height.toLong() / inSampleSize) > maxPixels
    ) {
        inSampleSize *= 2
    }
    return inSampleSize
}

private fun File.guessMimeType(): Result<String> = runCatching {
    inputStream().use { input ->
        val bytes = ByteArray(16)
        val read = input.read(bytes)
        if (read < 12) error("File too short to determine MIME type")

        // 判断 HEIF/HEIC/AVIF 格式：ISO-BMFF 容器，"ftyp" box 位于字节 4..8，主品牌码位于 8..12
        // 新手机的 HDR HEIF 照片常用 heix/hevc/mif1/msf1 等品牌码，而非仅 heic，需全部识别
        if (bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp") {
            when (bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII)) {
                "heic", "heix", "heim", "heis",
                "hevc", "hevx", "hevm", "hevs",
                "mif1", "msf1", "heif",
                    -> return@runCatching "image/heic"

                "avif", "avis" -> return@runCatching "image/avif"
            }
        }

        // 判断 JPEG 格式：开头为 0xFF 0xD8
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            return@runCatching "image/jpeg"
        }

        // 判断 PNG 格式：开头为 89 50 4E 47 0D 0A 1A 0A
        if (bytes.copyOfRange(0, 8).contentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            )
        ) {
            return@runCatching "image/png"
        }

        // 判断WebP格式：开头为 "RIFF" + 4字节长度 + "WEBP"
        if (bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" && bytes.copyOfRange(8, 12)
                .toString(Charsets.US_ASCII) == "WEBP"
        ) {
            return@runCatching "image/webp"
        }

        // 判断 GIF 格式：开头为 "GIF89a" 或 "GIF87a"
        val header = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
        if (header == "GIF89a" || header == "GIF87a") {
            return@runCatching "image/gif"
        }

        error(
            "Failed to guess MIME type: $header, ${
                bytes.joinToString(",") {
                    it.toUByte().toString()
                }
            }"
        )
    }
}
