package com.developerphil.adbidea.adb.command

import com.developerphil.adbidea.adb.AdbUtil.isAppInstalled
import com.developerphil.adbidea.adb.command.receiver.GenericReceiver
import com.developerphil.adbidea.adb.command.receiver.isNoSuchFileError
import com.developerphil.adbidea.ui.NotificationHelper.error
import com.developerphil.adbidea.ui.NotificationHelper.info
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooser.FileChooserConsumer
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Ref https://medium.com/@liwp.stephen/how-does-android-studio-device-file-explorer-works-62685330e8c8
 */
class CopyDatabaseCommand : Command {

    private var destination: String? = null

    override fun run(context: CommandContext): Boolean = with(context) {
        try {
            if (isAppInstalled(device, packageName)) {

                // Check if the database folder exists
                val listReceiver = GenericReceiver()
                device.executeShellCommand("run-as $packageName ls databases", listReceiver, 15L, TimeUnit.SECONDS)
                if (listReceiver.hasOutput() && listReceiver.adbOutputLines[0].contains("No such file", true)) {
                    kotlin.error("No databases found")
                }

                val deviceDestinationBasePath = "/data/local/tmp/$packageName"
                val deviceDestinationDatabasePath = "$deviceDestinationBasePath/databases"

                // Create destination folder
                createDestinationFolder(deviceDestinationBasePath)

                // Cleanup in case the folder already exists
                cleanupPreviousFiles(deviceDestinationDatabasePath)

                val result = tryCopyDatabase(deviceDestinationDatabasePath)
                if (!result) {
                    // Failed to copy database
                    return true
                }

                val selectedDestination = getDestination(project) ?: return true
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                val stamp = format.format(Date())
                val destination = "$selectedDestination/$stamp"

                File(destination).mkdir()

                // Process file names
                val filteredResult = listReceiver.adbOutputLines
                    .flatMap { it.split(" ") }
                    .flatMap { it.split("\n") }
                    .filter { it.isNotBlank() }
                    .sorted()

                filteredResult.forEach {
                    device.pullFile(
                        "$deviceDestinationDatabasePath/$it",
                        "$destination/$it"
                    )
                }

                info("Databases copied to: <b>$destination</b>")
                return true
            } else {
                error(String.format("<b>%s</b> is not installed on %s", packageName, device.name))
            }
        } catch (e1: Exception) {
            error("Copy database failed with: " + e1.message)
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
        }
        return false
    }

    private fun getDestination(project: Project): String? {
        val chooserLatch = CountDownLatch(1)
        val fileDescriptor = FileChooserDescriptor(
            false,
            true,
            false,
            false,
            false,
            false
        )
        ApplicationManager.getApplication().invokeLater {
            FileChooser.chooseFiles(fileDescriptor, project, project.workspaceFile, object : FileChooserConsumer {
                override fun cancelled() {
                    chooserLatch.countDown()
                }

                override fun consume(virtualFiles: List<VirtualFile>) {
                    destination = virtualFiles[0].path
                    chooserLatch.countDown()
                }
            })
        }
        chooserLatch.await()
        return destination
    }

    private fun CommandContext.createDestinationFolder(deviceDestinationBasePath: String) {
        val mkDirReceiver = GenericReceiver()
        device.executeShellCommand(
            "mkdir $deviceDestinationBasePath",
            mkDirReceiver,
            15L,
            TimeUnit.SECONDS
        )
    }

    private fun CommandContext.cleanupPreviousFiles(deviceDestinationDatabasePath: String) {
        val deleteReceiver = GenericReceiver()
        device.executeShellCommand(
            "rm -r $deviceDestinationDatabasePath",
            deleteReceiver,
            15L,
            TimeUnit.SECONDS
        )
    }

    private fun CommandContext.tryCopyDatabase(deviceDestinationDatabasePath: String): Boolean {
        // Try with run-as
        val copyReceiver = GenericReceiver()
        device.executeShellCommand(
            "run-as $packageName cp -R databases $deviceDestinationDatabasePath",
            copyReceiver,
            15L,
            TimeUnit.SECONDS
        )
        if (copyReceiver.hasOutput()) {
            if (copyReceiver.isNoSuchFileError()) {
                error("No database found")
                return false
            } else {

                // Fall back to using the absolute path
                val fallbackCopyReceiver = GenericReceiver()
                device.executeShellCommand(
                    "cp -R /data/data/$packageName/databases $deviceDestinationDatabasePath",
                    fallbackCopyReceiver,
                    15L,
                    TimeUnit.SECONDS
                )

                if (fallbackCopyReceiver.hasOutput()) {
                    if (fallbackCopyReceiver.isNoSuchFileError()) {
                        error("No database found")
                        return false
                    } else {
                        error("Error not mapped: ${copyReceiver.adbOutputLines.first()} | ${fallbackCopyReceiver.adbOutputLines.first()}")
                        return false
                    }
                }
            }
        }

        return true
    }
}