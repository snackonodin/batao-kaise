package com.bataokaise.app.bill

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** On-device ML Kit OCR (Devanagari model also reads Latin). The photo never leaves the phone. */
object BillOcr {
    private val client by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }

    sealed class Out {
        data class Ok(val lines: List<OcrLine>) : Out()
        data class Err(val modelMissing: Boolean, val message: String) : Out()
    }

    suspend fun read(bitmap: Bitmap): Out = suspendCancellableCoroutine { cont ->
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                val lines = text.textBlocks.flatMap { b -> b.lines }.mapNotNull { l ->
                    val r = l.boundingBox ?: return@mapNotNull null
                    OcrLine(l.text, r.left, r.top, r.right, r.bottom)
                }
                cont.resume(Out.Ok(lines))
            }
            .addOnFailureListener { e ->
                val msg = e.message.orEmpty()
                cont.resume(Out.Err(msg.contains("download", ignoreCase = true) || msg.contains("model", ignoreCase = true), msg))
            }
    }
}
