package com.nexchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.nexchat.core.auth.LogoutEventBus
import dagger.hilt.android.AndroidEntryPoint
import com.nexchat.design.NexChatTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var logoutEventBus: LogoutEventBus

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            logoutEventBus.events.collect {
                // Finish and restart — clears back stack, forces nav host to restart at auth.
                finish()
                startActivity(intent)
            }
        }

        setContent {
            NexChatTheme {
                NexChatNavHost()
            }
        }
    }
}
