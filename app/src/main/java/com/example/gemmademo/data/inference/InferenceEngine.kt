package com.example.gemmademo.data.inference

import android.content.Context
import android.util.Log
import com.example.gemmademo.data.sampler.Sampler
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Low-level TFLite inference for the Gemma model.
 * Uses signature runner for named inputs/outputs.
 */
class InferenceEngine(context: Context, private val modelFilename: String = "gemma3_270m_it_tf.tflite") {

    private val modelFile: File? = ModelLocator.findModelFile(context, modelFilename)

    /**
     * Run autoregressive generation.
     *
     * @param tokenizer Already-loaded tokenizer.
     * @param prompt Raw user prompt.
     * @param maxLength Max tokens to generate.
     * @param sampler Sampling strategy.
     * @param stripPrompt If true, remove prompt tokens from the returned string.
     * @return Generated text, or an error message prefixed with "Error:".
     */
    fun generate(
        tokenizer: GemmaTokenizer,
        prompt: String,
        maxLength: Int,
        sampler: Sampler,
        stripPrompt: Boolean
    ): String {
        if (modelFile == null) {
            return "Error: Model not found. Please push $modelFilename to the app external files dir."
        }

        val interpreter = createInterpreter()
        return try {
            runGeneration(interpreter, tokenizer, prompt, maxLength, sampler, stripPrompt)
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            "Error: ${e.message}"
        } finally {
            interpreter.close()
        }
    }

    private fun createInterpreter(): Interpreter {
        val modelBuffer = FileInputStream(modelFile).channel.map(
            FileChannel.MapMode.READ_ONLY, 0, modelFile!!.length()
        )
        return Interpreter(
            modelBuffer,
            Interpreter.Options().apply { setNumThreads(4) }
        )
    }

    private fun findTensorIndex(interpreter: Interpreter, shortName: String): Int {
        for (i in 0 until interpreter.inputTensorCount) {
            val name = interpreter.getInputTensor(i).name()
            if (name.contains(shortName)) return i
        }
        throw IllegalArgumentException("Tensor for $shortName not found")
    }

    private fun runGeneration(
        interpreter: Interpreter,
        tokenizer: GemmaTokenizer,
        prompt: String,
        maxLength: Int,
        sampler: Sampler,
        stripPrompt: Boolean
    ): String {
        val bosTokenId = tokenizer.bosId
        val padTokenId = tokenizer.padId

        // Detect the model's expected sequence length from its input tensor shape.
        // TF backend auto-export may report [1, 1] (dynamic); signature runner
        // resizes automatically when larger inputs are passed.
        // Torch backend exports with a static length (e.g. 256).
        val inputTensor = interpreter.getInputTensor(0)
        val shape = inputTensor.shape()
        val seqLength = if (shape.size >= 2 && shape[1] > 1) shape[1] else GemmaTokenizer.SEQUENCE_LENGTH
        Log.d(TAG, "Model input shape=${shape.contentToString()}, using seqLength=$seqLength")

        val promptTokens = listOf(bosTokenId) + tokenizer.tokenize(prompt)
        val initialLength = minOf(promptTokens.size, seqLength)
        Log.d(TAG, "Prompt tokens: $promptTokens")

        val tokenIds = IntArray(seqLength) { padTokenId }
        for (i in promptTokens.indices) {
            if (i < seqLength) tokenIds[i] = promptTokens[i]
        }

        // Detect signature input names.
        val shortNames = interpreter.getSignatureInputs("serving_default").toList()
        Log.d(TAG, "Signature inputs (short): $shortNames")

        val shortNamesSet = shortNames.toSet()
        val (maskShort, tokenShort) = when {
            "padding_mask" in shortNamesSet && "token_ids" in shortNamesSet ->
                "padding_mask" to "token_ids"
            shortNamesSet == setOf("args_0", "args_1") ->
                // Torch backend: args_0 = token_ids, args_1 = padding_mask.
                "args_1" to "args_0"
            shortNamesSet == setOf("args_0", "args_0_1") ->
                // TF backend: args_0 = padding_mask, args_0_1 = token_ids.
                "args_0" to "args_0_1"
            shortNames.size >= 2 ->
                shortNames[0] to shortNames[1]
            else -> throw IllegalStateException("Unexpected signature inputs: $shortNames")
        }
        Log.d(TAG, "Using maskShort=$maskShort, tokenShort=$tokenShort")

        // Some torch exports produce BOOL for padding_mask; TF exports use INT32.
        val maskTensorIdx = findTensorIndex(interpreter, maskShort)
        val maskIsBool = interpreter.getInputTensor(maskTensorIdx).dataType() ==
            org.tensorflow.lite.DataType.BOOL
        val inputPaddingMask: Any = if (maskIsBool) {
            Array(1) { BooleanArray(seqLength) }
        } else {
            Array(1) { IntArray(seqLength) }
        }
        val inputTokenIds = Array(1) { tokenIds.copyOf() }
        val outputBuffer = Array(1) { Array(seqLength) { FloatArray(tokenizer.vocabSize) } }

        // runSignature expects signature input names (short names), not full tensor names.
        val inputs = mutableMapOf<String, Any>(
            maskShort to inputPaddingMask,
            tokenShort to inputTokenIds
        )
        val outputs = mapOf<String, Any>(
            "output_0" to outputBuffer
        )

        val stopTokens = setOf(tokenizer.eosId, tokenizer.endOfTurnId)
        var currentLength = initialLength
        val generatedTokens = mutableListOf<Int>()

        while (currentLength < seqLength && generatedTokens.size < maxLength) {
            for (i in 0 until seqLength) {
                if (maskIsBool) {
                    (inputPaddingMask as Array<BooleanArray>)[0][i] = i < currentLength
                } else {
                    (inputPaddingMask as Array<IntArray>)[0][i] = if (i < currentLength) 1 else 0
                }
            }
            System.arraycopy(tokenIds, 0, inputTokenIds[0], 0, seqLength)

            interpreter.runSignature(inputs, outputs, "serving_default")

            val logits = outputBuffer[0][currentLength - 1]
            val probabilities = sampler.computeProbabilities(logits)
            val nextTokenId = sampler.getNextToken(probabilities)

            if (nextTokenId in stopTokens) break

            tokenIds[currentLength] = nextTokenId
            generatedTokens.add(nextTokenId)
            currentLength++
        }

        val allTokenIds = if (stripPrompt) generatedTokens else promptTokens + generatedTokens
        Log.d(TAG, "Generated token IDs: $allTokenIds")
        val result = tokenizer.detokenize(allTokenIds)
        Log.d(TAG, "Generated: $result")
        return result
    }

    companion object {
        private const val TAG = "InferenceEngine"
    }
}
