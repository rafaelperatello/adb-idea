package com.developerphil.adbidea.ui.databasechooser

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.facet.AndroidFacet
import javax.swing.JComponent
import javax.swing.JPanel

class DatabaseFileChooserDialog(facet: AndroidFacet, databaseList: List<String>) : DialogWrapper(facet.module.project, true) {

    lateinit var myPanel: JPanel
    lateinit var myDatabaseChooserWrapper: JPanel
    private val myDatabaseChooser: DatabaseChooserPanel

    val selectedDatabase: String?
        get() = myDatabaseChooser.selectedDatabaseName

    init {
        title = "Select the database"

        // Initial ok setup
        okAction.isEnabled = false

        // Setup list
        myDatabaseChooser = DatabaseChooserPanel(okAction)
        myDatabaseChooser.addListener(object : DatabaseChooserListener {
            override fun selectedDatabaseChanged() {
                updateOkButton()
            }
        })

        // Register disposer
        Disposer.register(myDisposable, myDatabaseChooser)

        // Init list
        myDatabaseChooserWrapper.add(myDatabaseChooser.panel)
        myDatabaseChooser.init(databaseList)

        // Update status
        init()
    }

    private fun updateOkButton() {
        okAction.isEnabled = selectedDatabase != null
    }

    override fun getDimensionServiceKey() = javaClass.canonicalName
    override fun createCenterPanel(): JComponent = myPanel
}