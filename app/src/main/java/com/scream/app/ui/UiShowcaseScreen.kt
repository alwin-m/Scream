package com.scream.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scream.app.ui.components.PreparingBottomSheet
import com.scream.app.ui.components.SearchingItemsBottomSheet
import com.scream.app.ui.components.StepProgressTracker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiShowcaseScreen(onBack: () -> Unit) {
    var showSearching by remember { mutableStateOf(false) }
    var showPreparing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UI/UX Showcase") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF121212))
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Show the Tracker directly on the screen
            StepProgressTracker()
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { showSearching = true },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Show 'Searching Items' Overlay")
            }
            
            Button(
                onClick = { showPreparing = true },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Show 'Preparing' Overlay")
            }
        }
    }

    if (showSearching) {
        SearchingItemsBottomSheet(onDismiss = { showSearching = false })
    }

    if (showPreparing) {
        PreparingBottomSheet(onStop = { showPreparing = false })
    }
}
