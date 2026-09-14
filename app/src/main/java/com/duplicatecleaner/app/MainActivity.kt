package com.duplicatecleaner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.duplicatecleaner.app.ui.AppRoot
import com.duplicatecleaner.app.ui.theme.DuplicateCleanerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DuplicateCleanerTheme {
                AppRoot()
            }
        }
    }
}
