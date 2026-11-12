package io.github.houssam2021.artify.io

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import io.github.houssam2021.artify.model.Camera
import io.github.houssam2021.artify.model.ViewTransform
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object Exporter {
    private const val FOLDER = "Artify"

    /** A view transform that frames world bounds into an image whose longest side is [maxSide]. */
    fun framing(bounds: DoubleArray, maxSide: Int, padding: Double = 0.04, even: Boolean = false): ViewTransform {
        val bw = (bounds[2] - bounds[0]) * (1 + 2 * padding)
        val bh = (bounds[3] - bounds[1]) * (1 + 2 * padding)
        val scale = maxSide / max(bw, bh)
        var w = max(2, (bw * scale).roundToInt())
        var h = max(2, (bh * scale).roundToInt())
        if (even) { w -= w % 2; h -= h % 2 }
        val cam = Camera((bounds[0] + bounds[2]) / 2, (bounds[1] + bounds[3]) / 2, scale.coerceIn(Camera.MIN_SCALE, Camera.MAX_SCALE * 100), 0.0)
        return ViewTransform(cam, w, h)
    }

    /** Safe file name: letters, digits, space, dash and underscore only. */
    fun safeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().take(60).ifBlank { "Artwork" }

    private fun exportDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        // Old exports are temporary; clear anything older than a day.
        val cutoff = System.currentTimeMillis() - 24 * 3600 * 1000L
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        return dir
    }

    fun tempExportFile(context: Context, baseName: String, ext: String): File =
        File(exportDir(context), "${safeName(baseName)}.$ext")

    fun writeTemp(context: Context, baseName: String, ext: String, write: (OutputStream) -> Unit): File {
        val f = tempExportFile(context, baseName, ext)
        FileOutputStream(f).use(write)
        return f
    }

    fun shareIntent(context: Context, file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".exports", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Saves to Pictures/Artify through MediaStore (no storage permission required). */
    fun saveImageToGallery(context: Context, bitmap: Bitmap, baseName: String, png: Boolean): Uri {
        val mime = if (png) "image/png" else "image/jpeg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${safeName(baseName)}.${if (png) "png" else "jpg"}")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create image")
        try {
            resolver.openOutputStream(uri)?.use {
                bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 95, it)
            } ?: throw IOException("Could not open image")
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    fun saveVideoToGallery(context: Context, file: File, baseName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${safeName(baseName)}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$FOLDER")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create video")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Could not open video")
            values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }
}

/** H.264 MP4 encoder fed by a hardware Canvas on the codec's input surface. */
object VideoEncoder {
    fun encode(
        out: File, width: Int, height: Int, fps: Int, frameCount: Int,
        progress: (Float) -> Unit = {}, drawFrame: (Canvas, Int) -> Unit,
    ) {
        require(width % 2 == 0 && height % 2 == 0 && frameCount > 0)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (width * height * fps * 0.25).toInt().coerceIn(1_000_000, 20_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()
            muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val info = MediaCodec.BufferInfo()
            var track = -1
            var written = 0L
            fun drain(endOfStream: Boolean) {
                var waits = 0
                while (true) {
                    val idx = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
                    when {
                        idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream || ++waits > 500) return
                        idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            track = muxer!!.addTrack(codec.outputFormat); muxer!!.start()
                        }
                        idx >= 0 -> {
                            val buf = codec.getOutputBuffer(idx)!!
                            val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            if (!isConfig && info.size > 0 && track >= 0) {
                                info.presentationTimeUs = written * 1_000_000L / fps
                                written++
                                buf.position(info.offset); buf.limit(info.offset + info.size)
                                muxer!!.writeSampleData(track, buf, info)
                            }
                            codec.releaseOutputBuffer(idx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }
            for (i in 0 until frameCount) {
                val c = surface.lockHardwareCanvas()
                try { drawFrame(c, i) } finally { surface.unlockCanvasAndPost(c) }
                drain(false)
                progress((i + 1f) / frameCount)
            }
            codec.signalEndOfInputStream()
            drain(true)
            surface.release()
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            try { muxer?.stop() } catch (_: Exception) {}
            muxer?.release()
        }
    }
}
