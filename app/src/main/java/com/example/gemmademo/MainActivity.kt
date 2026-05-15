package com.example.gemmademo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.gemmademo.ui.screen.SimpleGenerationScreen
import com.example.gemmademo.ui.theme.GemmaDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GemmaDemoTheme {
                SimpleGenerationScreen()
            }
        }
    }
}
