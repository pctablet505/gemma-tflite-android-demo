package com.example.gemmademo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gemmademo.data.inference.GemmaTokenizer
import com.example.gemmademo.data.inference.InferenceEngine
import com.example.gemmademo.data.sampler.GreedySampler
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InferenceEngineTest {
    @Test
    fun testGenerateBothBackends() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenizer = GemmaTokenizer(context)
        val sampler = GreedySampler()

        for (modelFile in listOf("gemma3_270m_torch.tflite", "gemma3_270m_tf.tflite")) {
            val engine = InferenceEngine(context, modelFile)
            val result = engine.generate(
                tokenizer, "What is Keras?",
                maxLength = 16, sampler = sampler, stripPrompt = false
            )
            println("$modelFile -> $result")
            assert(!result.startsWith("Error:")) { "Generation failed: $result" }
            assert(result.length > "What is Keras?".length) {
                "$modelFile produced no new tokens: $result"
            }
            // Interpreter is closed in engine.generate() via try/finally
        }
    }
}
