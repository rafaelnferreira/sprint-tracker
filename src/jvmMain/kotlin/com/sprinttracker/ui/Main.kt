package com.sprinttracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.sprinttracker.ui.components.MainView
import org.slf4j.LoggerFactory

fun main() = application {

    val logger = LoggerFactory.getLogger("Main")

    var isOpen by remember { mutableStateOf(true) }

    val trayState = rememberTrayState()

    Tray(
        state = trayState,
        icon = TrayIcon,
        menu = {
            if (!isOpen) {

                Item(
                    "Show",
                    onClick = {
                        isOpen = true
                    }
                )
            }
            Item(
                "Exit",
                onClick = ::exitApplication
            )
        }
    )

    try {
        Window(
            title = "Sprint Time Tracker",
            state = WindowState(size = DpSize(960.dp, 640.dp)),
            visible = isOpen,
            onCloseRequest = { isOpen = false },
            resizable = false,
            icon = MyAppIcon,
        ) {
            MainView(onNotification = { trayState.sendNotification(it) }, onExit = ::exitApplication)
        }
    } catch (e: Exception) {
        logger.error("Uncaught error, terminating", e)
        throw e
    }

}

object MyAppIcon : Painter() {
    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        drawOval(Color.Green, Offset(size.width / 4, 0f), Size(size.width / 2f, size.height))
        drawOval(Color.Blue, Offset(0f, size.height / 4), Size(size.width, size.height / 2f))
        drawOval(Color.Red, Offset(size.width / 4, size.height / 4), Size(size.width / 2f, size.height / 2f))
    }
}

object TrayIcon : Painter() {
    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        drawOval(Color(0xFFFFA500))
    }
}
