package com.developerphil.adbidea.ui.databasechooser

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.facet.AndroidFacet
import javax.swing.JComponent
import javax.swing.JPanel

class DatabaseFileChooserDialog(
        facet: AndroidFacet,
        databaseList: List<String>
) : DialogWrapper(facet.module.project, true) {

    // Form binding views
    lateinit var myPanel: JPanel
    lateinit var myDatabaseChooserWrapper: JPanel

    // Panel
    private val myDatabaseChooserPanel: DatabaseChooserPanel

    val selectedDatabase: String?
        get() = myDatabaseChooserPanel.selectedDatabaseName

    init {
        title = "Select the database"

        // Initial ok setup
        okAction.isEnabled = false

        // Setup list
        myDatabaseChooserPanel = DatabaseChooserPanel(okAction)
        myDatabaseChooserPanel.addListener(object : DatabaseChooserListener {
            override fun selectedDatabaseChanged() {
                updateOkButton()
            }
        })

        // Register disposer
        Disposer.register(myDisposable, myDatabaseChooserPanel)

        // Init list
        myDatabaseChooserWrapper.add(myDatabaseChooserPanel.panel)
        myDatabaseChooserPanel.init(databaseList)

        // Update status
        init()
    }

    private fun updateOkButton() {
        okAction.isEnabled = selectedDatabase != null
    }

    override fun getDimensionServiceKey() = javaClass.canonicalName
    override fun createCenterPanel(): JComponent = myPanel
}