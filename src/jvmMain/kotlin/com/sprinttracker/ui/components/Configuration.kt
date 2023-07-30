package com.sprinttracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sprinttracker.models.Configuration

@Composable
fun ConfigurationComponent(configuration: Configuration, onSaveConfiguration: (Configuration) -> Unit) {

    var url by remember { mutableStateOf(configuration.servicesUrl) }
    var project by remember { mutableStateOf(configuration.project) }
    var team by remember { mutableStateOf(configuration.team) }
    var pat by remember { mutableStateOf(configuration.pat) }

    var showSnackBar by remember { mutableStateOf(false) }

    Box {

        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            OutlinedTextField(
                value = url,
                onValueChange = { newValue -> url = newValue },
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                singleLine = true,
                label = { Text("Instance name") },
                placeholder = { Text("mydevopsinstance") },
            )

            OutlinedTextField(
                value = project,
                onValueChange = { newValue -> project = newValue },
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                singleLine = true,
                label = { Text("Project name") },
                placeholder = { Text("My Project") },
            )

            OutlinedTextField(
                value = team,
                onValueChange = { newValue -> team = newValue },
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                singleLine = true,
                label = { Text("Team name") },
                placeholder = { Text("My Team") },
            )

            OutlinedTextField(
                value = pat,
                onValueChange = { newValue -> pat = newValue },
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                singleLine = true,
                label = { Text("Personal Access Token (PAT)") },
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text("XYZ") },
            )

            val editedConfiguration = Configuration(url, project, team, pat)

            Button(onClick = {
                onSaveConfiguration(editedConfiguration)
                showSnackBar = true
            }, enabled = editedConfiguration.isValid(), modifier = Modifier.padding(8.dp).align(Alignment.End)) {
                Text("Save")
            }

            if (showSnackBar) {
                Snackbar(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp) ) {
                    Text(text = "Configuration saved")
                }
            }
        }

    }

}