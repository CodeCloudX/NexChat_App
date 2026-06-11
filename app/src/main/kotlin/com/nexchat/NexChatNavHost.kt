package com.nexchat

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.nexchat.core.auth.AuthState
import com.nexchat.core.network.dto.PublicUser
import com.nexchat.feature.auth.ui.LoginScreen
import com.nexchat.feature.auth.ui.OtpScreen
import com.nexchat.feature.auth.ui.PhoneScreen
import com.nexchat.feature.chat.ui.ChatListScreen
import com.nexchat.feature.chat.ui.ChatScreen
import com.nexchat.feature.contacts.ui.ContactsScreen
import com.nexchat.feature.contacts.ui.NewContactScreen
import com.nexchat.feature.contacts.ui.ProfileInfoScreen
import com.nexchat.feature.contacts.ui.SafetyNumberScreen
import com.nexchat.feature.group.ui.GroupAddMembersScreen
import com.nexchat.feature.group.ui.GroupCreateNameScreen
import com.nexchat.feature.contacts.ui.GroupInfoScreen
import com.nexchat.feature.onboarding.ui.E2EEInfoScreen
import com.nexchat.feature.onboarding.ui.ProfileSetupScreen
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private object Routes {
    const val LOGIN = "login?token={token}"
    const val PHONE = "phone"
    const val OTP = "otp/{verificationId}"
    const val PROFILE_SETUP = "profile_setup"
    const val E2EE_INFO = "e2ee_info"
    const val HOME = "home"
    const val CHAT = "chat/{chatId}"
    const val CONTACTS = "contacts"
    const val NEW_CONTACT = "new_contact/{userJson}"
    const val PROFILE = "profile/{userId}"
    const val GROUP_INFO = "group_info/{groupId}"
    const val SAFETY_NUMBER = "safety_number/{userId}"
    const val GROUP_CREATE = "group_create"
    const val GROUP_ADD_MEMBERS = "group_add_members/{groupName}"
}

@Composable
fun NexChatNavHost(appViewModel: AppViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val authState by appViewModel.authState.collectAsStateWithLifecycle()
    val e2eeShown by appViewModel.e2eeShown.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (authState) {
            is AuthState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                val startDestination = remember {
                    if (authState is AuthState.LoggedIn) Routes.HOME else Routes.LOGIN
                }

                NavHost(navController = navController, startDestination = startDestination) {
                    composable(
                        route = Routes.LOGIN,
                        deepLinks = listOf(navDeepLink { uriPattern = "nexchat://auth?token={token}" }),
                        arguments = listOf(navArgument("token") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        })
                    ) { backStackEntry ->
                        LoginScreen(
                            navController = navController,
                            deepLinkToken = backStackEntry.arguments?.getString("token"),
                            onNavigatePhone = { navController.navigate(Routes.PHONE) },
                            onNavigateHome = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onNavigateProfileSetup = {
                                navController.navigate(Routes.PROFILE_SETUP) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(Routes.PHONE) {
                        PhoneScreen(
                            onNavigateOtp = { verificationId ->
                                navController.navigate("otp/$verificationId")
                            },
                        )
                    }

                    composable(
                        route = Routes.OTP,
                        arguments = listOf(navArgument("verificationId") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        OtpScreen(
                            verificationId = backStackEntry.arguments?.getString("verificationId") ?: "",
                            onNavigateHome = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onNavigateProfileSetup = {
                                navController.navigate(Routes.PROFILE_SETUP) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(Routes.PROFILE_SETUP) {
                        ProfileSetupScreen(
                            onProfileComplete = {
                                navController.navigate(Routes.E2EE_INFO) {
                                    popUpTo(Routes.PROFILE_SETUP) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(Routes.E2EE_INFO) {
                        E2EEInfoScreen(
                            onDone = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(Routes.HOME) {
                        ChatListScreen(
                            onNavigateToChat = { chatId ->
                                navController.navigate("chat/$chatId")
                            },
                            onNavigateToProfile = { userId ->
                                navController.navigate("profile/$userId")
                            },
                            onComposeNewChat = {
                                navController.navigate(Routes.CONTACTS)
                            }
                        )
                    }

                    composable(
                        route = Routes.CHAT,
                        arguments = listOf(navArgument("chatId") { type = NavType.StringType })
                    ) {
                        ChatScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToContact = { userId ->
                                navController.navigate("profile/$userId")
                            },
                            onNavigateToMedia = { messageId ->
                                // Media Viewer overlay handled internally or via sheet
                            },
                            onNavigateToSafetyNumber = { userId ->
                                navController.navigate("safety_number/$userId")
                            }
                        )
                    }

                    composable(Routes.CONTACTS) {
                        ContactsScreen(
                            onNavigateToChat = { chatId ->
                                navController.navigate("chat/$chatId") {
                                    popUpTo(Routes.HOME) { inclusive = false }
                                }
                            },
                            onNavigateToCreateGroup = {
                                navController.navigate(Routes.GROUP_CREATE)
                            },
                            onNavigateToProfile = { userId ->
                                navController.navigate("profile/$userId")
                            }
                        )
                    }

                    composable(
                        route = Routes.NEW_CONTACT,
                        arguments = listOf(navArgument("userJson") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val userJson = backStackEntry.arguments?.getString("userJson") ?: ""
                        val user = Json.decodeFromString<PublicUser>(Uri.decode(userJson))
                        NewContactScreen(
                            serverUser = user,
                            onSaved = { navController.popBackStack() },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Routes.PROFILE,
                        arguments = listOf(navArgument("userId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val userId = backStackEntry.arguments?.getString("userId") ?: ""
                        ProfileInfoScreen(
                            userId = userId,
                            onNavigateToChat = { chatId -> 
                                navController.navigate("chat/$chatId") {
                                    popUpTo(Routes.HOME) { inclusive = false }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Routes.GROUP_INFO,
                        arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                        val myUserId = (authState as? AuthState.LoggedIn)?.userId ?: ""
                        GroupInfoScreen(
                            groupId = groupId,
                            myUserId = myUserId,
                            onBack = { navController.popBackStack() },
                            onNavigateToChat = { chatId -> 
                                navController.navigate("chat/$chatId") {
                                    popUpTo(Routes.HOME) { inclusive = false }
                                }
                            }
                        )
                    }

                    composable(
                        route = Routes.SAFETY_NUMBER,
                        arguments = listOf(navArgument("userId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val userId = backStackEntry.arguments?.getString("userId") ?: ""
                        SafetyNumberScreen(
                            contactId = userId,
                            onBack = { navController.popBackStack() },
                            onMarkedVerified = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.GROUP_CREATE) {
                        GroupCreateNameScreen(
                            onContinue = { groupName ->
                                val encodedName = Uri.encode(groupName)
                                navController.navigate("group_add_members/$encodedName") {
                                    popUpTo(Routes.GROUP_CREATE) { inclusive = true }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Routes.GROUP_ADD_MEMBERS,
                        arguments = listOf(navArgument("groupName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val groupName = Uri.decode(backStackEntry.arguments?.getString("groupName") ?: "")
                        GroupAddMembersScreen(
                            groupName = groupName,
                            onGroupCreated = { chatId -> 
                                navController.navigate("chat/$chatId") {
                                    popUpTo(Routes.HOME) { inclusive = false }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
