package com.developerphil.adbidea.adb.command

import com.android.ddmlib.IDevice
import com.developerphil.adbidea.ObjectGraph
import com.developerphil.adbidea.adb.AdbUtil.isAppInstalled
import com.developerphil.adbidea.adb.command.receiver.GenericReceiver
import com.developerphil.adbidea.ui.NotificationHelper.error
import com.developerphil.adbidea.ui.NotificationHelper.info
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeleteDatabaseCommand : Command {

    override fun run(context: CommandContext): Boolean = with(context)  {
        try {
            if (isAppInstalled(device, packageName)) {
                val listReceiver = GenericReceiver()

                // Fetch files
                device.executeShellCommand("run-as $packageName ls databases", listReceiver, 15L, TimeUnit.SECONDS)
                if (listReceiver.hasOutput() && listReceiver.adbOutputLines[0].contains("No such file", true)) {
                    error("No databases found")
                    return true
                }

                // Filter result
                val filteredResult = listReceiver.adbOutputLines
                    .flatMap { it.split(" ") }
                    .flatMap { it.split("\n") }
                    .filter { it.isNotBlank() }
                    .sorted()

                // Check set
                if (filteredResult.isEmpty()) {
                    error("No databases found")
                    return true
                }

                // Info
                info(String.format("Databases: <b>%s</b> ", listReceiver.adbOutputLines.toString()))

                // Select database to delete
                val selectedDatabase = getDatabaseName(project, facet, filteredResult) ?: return false
                info(String.format("Database to remove: <b>%s</b> ", selectedDatabase))

                // Delete
                device.executeShellCommand("run-as $packageName rm databases/$selectedDatabase", listReceiver, 15L, TimeUnit.SECONDS)

                // Feedback
                info("Deleted $selectedDatabase database")
                return true
            } else {
                error(String.format("<b>%s</b> is not installed on %s", packageName, device.name))
            }
        } catch (e: Exception) {
            error("Delete database failed with: " + e.message)
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
        }
        return false
    }

    private fun getDatabaseName(project: Project, facet: AndroidFacet, databaseList: List<String>): String? {
        var selectedDatabase: String? = null
        val chooserLatch = CountDownLatch(1)

        // Required to run on the main thread
        ApplicationManager.getApplication().invokeLater {
            selectedDatabase = project.getComponent(ObjectGraph::class.java)
                .databaseFileChooser
                .get(facet, databaseList)

            chooserLatch.countDown()
        }

        chooserLatch.await()
        return selectedDatabase
    }
}