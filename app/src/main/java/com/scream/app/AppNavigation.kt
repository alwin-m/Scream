package com.scream.app

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scream.app.identity.IdentityViewModel
import com.scream.app.identity.OnboardingScreen
import com.scream.app.ui.BluetoothTransferScreen
import com.scream.app.ui.ChatScreen
import com.scream.app.ui.HomeScreen
import com.scream.app.ui.MainViewModel

@Composable
fun AppNavigation(
    identityViewModel: IdentityViewModel = viewModel(),
    mainViewModel: MainViewModel = viewModel()
) {
    val navController = rememberNavController()
    val userProfile by identityViewModel.userProfile.collectAsState()

    if (userProfile == null) {
        return
    }

    val startDestination = if (userProfile?.isRegistered == true) "home" else "onboarding"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("onboarding") {
            OnboardingScreen(
                viewModel = identityViewModel,
                onRegistrationComplete = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                viewModel = mainViewModel,
                onOpenRoom = { room ->
                    val safeId = Uri.encode(room.id)
                    navController.navigate("chat/$safeId")
                },
                onOpenBluetoothTransfer = {
                    navController.navigate("bluetooth")
                }
            )
        }

        composable("bluetooth") {
            BluetoothTransferScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "chat/{roomId}",
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("roomId") ?: ""
            val decodedId = Uri.decode(rawId)
            val rooms by mainViewModel.rooms.collectAsState()
            val activeRoom = rooms.find { it.id == decodedId || it.id == rawId }

            if (activeRoom != null) {
                ChatScreen(
                    room = activeRoom,
                    viewModel = mainViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
