package com.sprinttracker.ui.components

import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sprinttracker.ui.platform.Res

@Composable
fun AppBar(configurationComplete: Boolean, onPageChange: (Pages) -> Unit, onExit: () -> Unit, onRefresh: () -> Unit) {

    TopAppBar(
        title = { Text(text = "Time tracker") },
        backgroundColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface,
        elevation = 8.dp,
        navigationIcon = {
            IconButton(onClick = {onPageChange(Pages.INPUT)}, enabled = configurationComplete) {
                Icon(painterResource(Res.drawable.ic_home), contentDescription = "Home")
            }
        },
        actions = {
            IconButton(onClick = { onRefresh()}, enabled = configurationComplete) {
                Icon(painterResource(Res.drawable.ic_refresh), contentDescription = "Refresh")
            }
            IconButton(onClick = { onPageChange(Pages.CONFIG)}) {
                Icon(painterResource(Res.drawable.ic_config), contentDescription = "Configuration")
            }
            IconButton(onClick = onExit) {
                Icon(painterResource(Res.drawable.ic_exit), contentDescription = "Exit")
            }
        }
    )
}