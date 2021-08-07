package com.developerphil.adbidea.adb

import com.android.ddmlib.IDevice
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.developerphil.adbidea.ui.DeviceChooserDialog
import com.developerphil.adbidea.ui.ModuleChooserDialogHelper
import com.developerphil.adbidea.ui.NotificationHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils


class DeviceResultFetcher constructor(
    private val project: Project,
    private val useSameDevicesHelper: UseSameDevicesHelper,
    private val bridge: Bridge
) {

    fun fetch(): DeviceResult? {
        val facet = getFacet(AndroidUtils.getApplicationFacets(project))

        if (facet == null) {
            NotificationHelper.error("No facet found")
            return null
        }

        val model = AndroidModuleModel.get(facet)
        if (model == null) {
            NotificationHelper.error("No model found")
            return null
        }

        val packageName: String? =
            model.androidProject.defaultConfig.productFlavor.applicationId

        if (packageName.isNullOrEmpty()) {
            NotificationHelper.error("No package found")
            return null
        }

        if (!bridge.isReady()) {
            NotificationHelper.error("No platform configured")
            return null
        }

        val rememberedDevices = useSameDevicesHelper.getRememberedDevices()
        if (rememberedDevices.isNotEmpty()) {
            return DeviceResult(rememberedDevices, facet, packageName)
        }

        val devices = bridge.connectedDevices()
        return when {
            devices.size == 1 -> {
                DeviceResult(devices, facet, packageName)
            }
            devices.size > 1 -> {
                showDeviceChooserDialog(facet, packageName)
            }
            else -> {
                null
            }
        }
    }

    private fun getFacet(facets: List<AndroidFacet>): AndroidFacet? {
        if (facets.isEmpty()) {
            return null
        }
        return if (facets.size > 1) {
            ModuleChooserDialogHelper.showDialogForFacets(project, facets)
        } else {
            facets[0]
        }
    }

    private fun showDeviceChooserDialog(facet: AndroidFacet, packageName: String): DeviceResult? {
        val chooser = DeviceChooserDialog(facet)
        chooser.show()

        if (chooser.exitCode != DialogWrapper.OK_EXIT_CODE) {
            return null
        }


        val selectedDevices = chooser.selectedDevices

        if (chooser.useSameDevices()) {
            useSameDevicesHelper.rememberDevices()
        }

        if (selectedDevices.isEmpty()) {
            return null
        }

        return DeviceResult(selectedDevices.asList(), facet, packageName)
    }
}


data class DeviceResult(
    val devices: List<IDevice>,
    val facet: AndroidFacet,
    val packageName: String
)