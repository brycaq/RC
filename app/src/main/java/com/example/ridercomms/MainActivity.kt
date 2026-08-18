package com.example.ridercomms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.ridercomms.ui.RiderCommsViewModel
import com.example.ridercomms.ui.screens.MainScreen

class MainActivity : ComponentActivity() {
    private val viewModel: RiderCommsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
