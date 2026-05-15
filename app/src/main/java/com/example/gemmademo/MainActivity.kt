package com.example.gemmademo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.gemmademo.ui.components.KerasTopBar
import com.example.gemmademo.ui.screen.SimpleGenerationScreen
import com.example.gemmademo.ui.theme.GemmaDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GemmaDemoTheme(darkTheme = true) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { KerasTopBar() },
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    SimpleGenerationScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
