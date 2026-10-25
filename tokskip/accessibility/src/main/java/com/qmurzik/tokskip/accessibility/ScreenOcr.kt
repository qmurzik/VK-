package com.qmurzik.tokskip.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/**
 * Offline OCR of TikTok frames with Tesseract (tessdata_fast rus+eng shipped in assets).
 *
 * TikTok's own UI is painted over before recognition (the bounds of every accessibility node
 * that carries text or is clickable), so what is left is text drawn *inside the video*: burned-in
 * subtitles, stickers and ad banners.
 */
class ScreenOcr(private val context: Context) {
    private var api: TessBaseAPI? = null
    private var failed = false

    val available: Boolean get() = !failed

    /** Recognised lines (confidence ≥ [MIN_CONFIDENCE]); null if OCR cannot run on this device. */
    @Synchronized
    fun recognize(frame: Bitmap): List<String>? {
        val tess = api ?: init() ?: return null
        return try {
            tess.setImage(frame)
            tess.getUTF8Text() // runs recognition
            val out = ArrayList<String>()
            val it = tess.resultIterator ?: return out
            it.begin()
            do {
                val text = it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)?.trim().orEmpty()
                val conf = it.confidence(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                if (conf >= MIN_CONFIDENCE && text.count { c -> c.isLetter() } >= 3) out += text.replace('\n', ' ')
            } while (it.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
            it.delete()
            tess.clear()
            out
        } catch (t: Throwable) {
            null
        }
    }

    @Synchronized
    fun close() {
        api?.recycle()
        api = null
    }

    private fun init(): TessBaseAPI? {
        if (failed) return null
        return try {
            val root = File(context.filesDir, "tess")
            val dir = File(root, "tessdata").apply { mkdirs() }
            // Assets are compressed in the APK, so their size is unknown up front: copy once per model version.
            val stamp = File(dir, "models.version")
            if (stamp.takeIf { it.exists() }?.readText() != MODELS_VERSION) {
                for (lang in LANGS) context.assets.open("tessdata/$lang.traineddata").use { input ->
                    File(dir, "$lang.traineddata").outputStream().use { input.copyTo(it) }
                }
                stamp.writeText(MODELS_VERSION)
            }
            val tess = TessBaseAPI()
            if (!tess.init(root.absolutePath, LANGS.joinToString("+"), TessBaseAPI.OEM_LSTM_ONLY)) {
                tess.recycle(); failed = true; return null
            }
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
            api = tess
            tess
        } catch (t: Throwable) {
            failed = true
            null
        }
    }

    companion object {
        private val LANGS = listOf("rus", "eng")
        private const val MODELS_VERSION = "tessdata_fast-4.1.0"
        private const val MIN_CONFIDENCE = 45f
        private const val TARGET_WIDTH = 720

        /**
         * Downscales a screenshot, blanks TikTok's UI ([masks] in screen coordinates) and the
         * status/navigation strips, and returns a software bitmap ready for OCR.
         */
        fun prepare(screenshot: Bitmap, masks: List<Rect>): Bitmap {
            val scale = TARGET_WIDTH.toFloat() / screenshot.width
            val w = TARGET_WIDTH
            val h = (screenshot.height * scale).toInt()
            val soft = screenshot.copy(Bitmap.Config.ARGB_8888, false)
            val out = Bitmap.createScaledBitmap(soft, w, h, true)
            if (soft !== out) soft.recycle()
            val mutable = if (out.isMutable) out else out.copy(Bitmap.Config.ARGB_8888, true).also { out.recycle() }
            val canvas = Canvas(mutable)
            val paint = Paint().apply { color = Color.BLACK }
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.06f, paint)
            canvas.drawRect(0f, h * 0.93f, w.toFloat(), h.toFloat(), paint)
            for (r in masks) canvas.drawRect(r.left * scale, r.top * scale, r.right * scale, r.bottom * scale, paint)
            return mutable
        }
    }
}
