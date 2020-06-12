package com.developerphil.adbidea.adb

import com.developerphil.adbidea.ui.databasechooser.DatabaseFileChooserDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.android.facet.AndroidFacet


class DatabaseFileChooser constructor(private val project: Project) {

    fun get(facet: AndroidFacet, databaseList: List<String>): String? {
        val chooser = DatabaseFileChooserDialog(facet, databaseList)
        chooser.show()

        if (chooser.exitCode != DialogWrapper.OK_EXIT_CODE) {
            return null
        }

        return chooser.selectedDatabase
    }
}
