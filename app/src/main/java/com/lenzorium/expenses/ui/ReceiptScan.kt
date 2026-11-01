package com.lenzorium.expenses.ui

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lenzorium.expenses.domain.ReceiptGuess
import com.lenzorium.expenses.domain.ReceiptParser
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Reads text from a receipt photo entirely on the phone (Google ML Kit, bundled model: no download,
 * no upload). Returns suggestions only; the user confirms them before anything is saved.
 */
object ReceiptScan {
    suspend fun read(context: Context, file: File, decimals: Int): Result<ReceiptGuess> = runCatching {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = suspendCancellableCoroutine<Result<String>> { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(Result.success(it.text)) }
                    .addOnFailureListener { cont.resume(Result.failure(it)) }
            }.getOrThrow()
            ReceiptParser.parse(text, decimals)
        } finally {
            recognizer.close()
        }
    }
}
