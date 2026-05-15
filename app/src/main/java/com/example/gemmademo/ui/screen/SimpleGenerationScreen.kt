package com.example.gemmademo.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemmademo.data.inference.GemmaTokenizer
import com.example.gemmademo.data.inference.InferenceEngine
import com.example.gemmademo.data.sampler.GreedySampler
import com.example.gemmademo.ui.theme.KerasRed
import com.example.gemmademo.ui.theme.MonospaceBody
import com.example.gemmademo.ui.theme.OnSurfaceDark
import com.example.gemmademo.ui.theme.SurfaceVariantDark
import com.example.gemmademo.ui.theme.TerminalBackground
import com.example.gemmademo.ui.theme.TerminalText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Minimal generation screen: prompt input, generate button, output.
 * Hardcoded: GreedySampler, maxLength=32, model=gemma3_270m_it_tf.tflite, stripPrompt=true.
 * Keeps animations, theme, and visual design from the full app.
 */
@Composable
fun SimpleGenerationScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val scrollState = rememberScrollState()

    var prompt by remember { mutableStateOf("The future of artificial intelligence is") }
    var output by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var tokenCount by remember { mutableStateOf(0) }
    var generationTimeMs by remember { mutableStateOf(0) }

    val canGenerate = prompt.isNotBlank() && !isGenerating
    val scale by animateFloatAsState(
        targetValue = if (canGenerate) 1f else 0.98f,
        label = "button_scale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── PROMPT ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "PROMPT",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Enter your prompt") },
                    placeholder = { Text("e.g. The quick brown fox...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KerasRed,
                        focusedLabelColor = KerasRed,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = KerasRed
                    ),
                    maxLines = 5,
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (canGenerate) generate(
                            context, scope, prompt, clipboard,
                            onStart = {
                                isGenerating = true
                                error = ""
                                output = ""
                            },
                            onResult = { text, count, timeMs ->
                                isGenerating = false
                                output = text
                                tokenCount = count
                                generationTimeMs = timeMs
                            },
                            onError = { msg ->
                                isGenerating = false
                                error = msg
                            }
                        )
                    }),
                    trailingIcon = {
                        if (prompt.isNotEmpty()) {
                            IconButton(onClick = { prompt = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear prompt",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }
        }

        // ── GENERATE ──
        Button(
            onClick = {
                generate(
                    context, scope, prompt, clipboard,
                    onStart = {
                        isGenerating = true
                        error = ""
                        output = ""
                    },
                    onResult = { text, count, timeMs ->
                        isGenerating = false
                        output = text
                        tokenCount = count
                        generationTimeMs = timeMs
                    },
                    onError = { msg ->
                        isGenerating = false
                        error = msg
                    }
                )
            },
            enabled = canGenerate,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .scale(scale),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KerasRed,
                disabledContainerColor = SurfaceVariantDark,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp,
                disabledElevation = 0.dp
            )
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = OnSurfaceDark,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = "Generating...",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.25.sp
                    )
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "GENERATE",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.25.sp
                    )
                )
            }
        }

        // ── OUTPUT ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TerminalBackground)
                .padding(16.dp)
        ) {
            AnimatedContent(
                targetState = when {
                    isGenerating -> OutputState.Loading
                    error.isNotEmpty() -> OutputState.Error(error)
                    output.isNotEmpty() -> OutputState.Success(output, tokenCount, generationTimeMs)
                    else -> OutputState.Idle
                },
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                            fadeOut(animationSpec = tween(150))
                },
                label = "output_state"
            ) { state ->
                when (state) {
                    is OutputState.Loading -> LoadingContent()
                    is OutputState.Idle -> IdleContent()
                    is OutputState.Success -> SuccessContent(
                        text = state.text,
                        tokenCount = state.tokenCount,
                        generationTimeMs = state.generationTimeMs,
                        onCopy = {
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Generated text", state.text)
                            )
                        }
                    )
                    is OutputState.Error -> ErrorContent(message = state.message)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private sealed class OutputState {
    data object Idle : OutputState()
    data object Loading : OutputState()
    data class Success(val text: String, val tokenCount: Int, val generationTimeMs: Int) : OutputState()
    data class Error(val message: String) : OutputState()
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = TerminalText,
            strokeWidth = 2.5.dp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Running inference...",
            style = MonospaceBody,
            color = TerminalText,
            modifier = Modifier.alpha(0.85f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "This may take a few moments on CPU",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(0.6f)
        )
    }
}

@Composable
private fun IdleContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "// Output will appear here",
            style = MonospaceBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(0.5f)
        )
    }
}

@Composable
private fun SuccessContent(
    text: String,
    tokenCount: Int,
    generationTimeMs: Int,
    onCopy: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = text,
                style = MonospaceBody,
                color = OnSurfaceDark,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy output",
                    tint = TerminalText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            val timeSec = generationTimeMs / 1000f
            val tps = if (generationTimeMs > 0) tokenCount * 1000f / generationTimeMs else 0f
            Text(
                text = "${tokenCount} tokens · ${String.format("%.1f", timeSec)}s · ${String.format("%.1f", tps)} tok/s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = "Generation failed",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MonospaceBody,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
            modifier = Modifier.alpha(0.9f)
        )
    }
}

private fun generate(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    prompt: String,
    clipboard: ClipboardManager,
    onStart: () -> Unit,
    onResult: (String, Int, Int) -> Unit,
    onError: (String) -> Unit
) {
    if (prompt.isBlank()) return
    onStart()

    scope.launch {
        val startTime = SystemClock.elapsedRealtime()
        val resultText = withContext(Dispatchers.IO) {
            try {
                val tokenizer = GemmaTokenizer(context)
                val engine = InferenceEngine(context, "gemma3_270m_it_tf.tflite")
                engine.generate(
                    tokenizer = tokenizer,
                    prompt = prompt,
                    maxLength = 32,
                    sampler = GreedySampler(),
                    stripPrompt = true
                )
            } catch (e: Exception) {
                "Error: ${e.message ?: "Unknown error"}"
            }
        }
        val elapsed = (SystemClock.elapsedRealtime() - startTime).toInt()

        if (resultText.startsWith("Error:")) {
            onError(resultText)
        } else {
            val count = resultText.split(Regex("\\s+|[.,;:!?]")).filter { it.isNotBlank() }.size
            onResult(resultText, count, elapsed)
        }
    }
}
