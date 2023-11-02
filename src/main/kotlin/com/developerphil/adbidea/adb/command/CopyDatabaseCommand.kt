package com.developerphil.adbidea.adb.command

import com.android.ddmlib.FileListingService
import com.android.ddmlib.IDevice
import com.android.ddmlib.SyncService.ISyncProgressMonitor
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
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CopyDatabaseCommand : Command, SyncProgressMonitor {
    private var destination: String? = null
    private val pullLatch = CountDownLatch(1)

    override fun run(project: Project, device: IDevice, facet: AndroidFacet, packageName: String): Boolean {
        try {
            if (isAppInstalled(device, packageName)) {
                device.executeShellCommand(
                    "rm -r /sdcard/databases",
                    GenericReceiver(),
                    15L,
                    TimeUnit.SECONDS
                )

                // Try with run-as
                val copyReceiver = GenericReceiver()
                device.executeShellCommand(
                    "run-as $packageName cp -R databases /sdcard",
                    copyReceiver,
                    15L,
                    TimeUnit.SECONDS
                )
                if (copyReceiver.hasOutput()) {
                    if (copyReceiver.isNoSuchFileError()) {
                        error("No database found")
                        return true
                    } else {

                        // Fall back to using the absolute path
                        val fallbackCopyReceiver = GenericReceiver()
                        device.executeShellCommand(
                            "cp -R /data/data/$packageName/databases /sdcard",
                            fallbackCopyReceiver,
                            15L,
                            TimeUnit.SECONDS
                        )

                        if (fallbackCopyReceiver.hasOutput()) {
                            if (fallbackCopyReceiver.isNoSuchFileError()) {
                                error("No database found")
                                return true
                            } else {
                                error("Not mapped: ${copyReceiver.adbOutputLines.first()}")
                            }
                        }
                    }
                }

                info(String.format("<b>%s</b> database copied to sdcard", packageName))

                val selectedDestination = getDestination(project) ?: return true
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                val stamp = format.format(Date())
                val destination = "$selectedDestination/$stamp"

                File(destination).mkdir()

                val entry = arrayOf(getEntry("sdcard/databases", device))

                device.syncService.pull(entry, destination, this)

                val flag = pullLatch.await(10, TimeUnit.SECONDS)
                if (!flag) {
                    throw RuntimeException("Failed to download database")
                }

                info("Database copied to: $destination")
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

    override fun stop() = pullLatch.countDown()

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

    private fun getEntry(remotePath: String, device: IDevice): FileListingService.FileEntry? {
        val listingService = device.fileListingService
        var entry = listingService.root
        val segments = remotePath.split("/").toTypedArray()
        for (segment in segments) {
            listingService.getChildren(entry, false, null)
            entry = entry!!.findChild(segment)
            if (entry == null) {
                throw Exception("File not found")
            }
        }
        return entry
    }
}

interface SyncProgressMonitor : ISyncProgressMonitor {
    override fun advance(work: Int) {}
    override fun isCanceled(): Boolean = false
    override fun start(totalWork: Int) {}
    override fun startSubTask(name: String?) {}
    override fun stop() {}
}