package com.example.gemmademo.data.inference

import android.content.Context
import java.io.File

/**
 * Finds model assets in the app's external files directory.
 *
 * On real devices, push files to:
 *   /sdcard/Android/data/com.example.gemmademo/files/
 *
 * This location does not require root and needs no permissions.
 */
object ModelLocator {

    private const val VOCAB_FILENAME = "vocabulary.spm"

    fun findModelFile(context: Context, filename: String = "gemma3_270m_it_tf.tflite"): File? {
        val file = File(context.getExternalFilesDir(null), filename)
        return if (file.exists() && file.canRead()) file else null
    }

    fun findVocabFile(context: Context): File? {
        val file = File(context.getExternalFilesDir(null), VOCAB_FILENAME)
        return if (file.exists() && file.canRead()) file else null
    }
}
