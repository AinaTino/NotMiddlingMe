package com.arda.stopmiddlingme

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arda.stopmiddlingme.ui.MainApp
import com.arda.stopmiddlingme.ui.theme.StopMiddlingMeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StopMiddlingMeTheme {
                MainApp()
            }
        }
    }
}
