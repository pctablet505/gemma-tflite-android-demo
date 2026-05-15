package com.example.gemmademo.ui.screen

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gemmademo.data.inference.GemmaTokenizer
import com.example.gemmademo.data.inference.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SimpleGenerationScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("What is Keras?") }
    var output by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Prompt") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 5,
            minLines = 2
        )

        Button(
            onClick = {
                generate(context, scope, prompt,
                    onStart = {
                        isGenerating = true
                        error = ""
                        output = ""
                    },
                    onResult = { text ->
                        isGenerating = false
                        output = text
                    },
                    onError = { msg ->
                        isGenerating = false
                        error = msg
                    }
                )
            },
            enabled = prompt.isNotBlank() && !isGenerating,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                Text("Generating...")
            } else {
                Text("Generate")
            }
        }

        when {
            error.isNotEmpty() -> Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            output.isNotEmpty() -> Text(
                text = output,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun generate(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    prompt: String,
    onStart: () -> Unit,
    onResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    if (prompt.isBlank()) return
    onStart()

    scope.launch {
        val resultText = withContext(Dispatchers.IO) {
            try {
                val tokenizer = GemmaTokenizer(context)
                val engine = InferenceEngine(context, "gemma3_270m_it_tf.tflite")
                engine.generate(
                    tokenizer = tokenizer,
                    prompt = prompt,
                    maxLength = 32,
                    stripPrompt = true
                )
            } catch (e: Exception) {
                "Error: ${e.message ?: "Unknown error"}"
            }
        }

        if (resultText.startsWith("Error:")) {
            onError(resultText)
        } else {
            onResult(resultText)
        }
    }
}
