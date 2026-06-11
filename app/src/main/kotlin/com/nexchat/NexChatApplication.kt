package com.nexchat

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nexchat.core.auth.AuthManager
import com.nexchat.core.auth.AuthState
import com.nexchat.core.work.FcmTokenUpdateWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class NexChatApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var authManager: AuthManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        scheduleFcmSyncOnLogin()
    }

    // Observe auth state transitions. Every time the session moves from a
    // non-LoggedIn state into LoggedIn (i.e. a real login just completed, not
    // a cold-start replay), enqueue the FCM-token sync so the backend receives
    // the current device token immediately after the session is persisted.
    //
    // This is the ONLY place FcmTokenUpdateWorker should be enqueued for the
    // login case. NexChatFirebaseService.onNewToken() handles FCM token
    // rotation while the user is already logged in.
    private fun scheduleFcmSyncOnLogin() {
        appScope.launch {
            var prevState: AuthState = AuthState.Loading
            authManager.authState.collect { state ->
                if (prevState !is AuthState.LoggedIn && state is AuthState.LoggedIn) {
                    WorkManager.getInstance(this@NexChatApplication)
                        .enqueueUniqueWork(
                            "fcm_token_sync",
                            ExistingWorkPolicy.REPLACE,
                            OneTimeWorkRequestBuilder<FcmTokenUpdateWorker>().build()
                        )
                }
                prevState = state
            }
        }
    }
}
