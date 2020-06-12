package com.developerphil.adbidea.ui.databasechooser

import com.intellij.openapi.Disposable
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.containers.ContainerUtil
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel


class DatabaseChooserPanel(okAction: Action) : Disposable {

    private val myListeners = ContainerUtil.createLockFreeCopyOnWriteList<DatabaseChooserListener>()

    private var myPanel: JComponent
    private var myDatabaseTable: JBTable = JBTable()

    val panel: JComponent?
        get() = myPanel

    val selectedDatabaseName: String?
        get() {
            val index = myDatabaseTable.selectedRow
            return if (index >= 0) {
                myDatabaseTable.getValueAt(index, 0) as String
            } else {
                null
            }
        }

    init {
        myPanel = ScrollPaneFactory.createScrollPane(myDatabaseTable)
        myPanel.preferredSize = Dimension(450, 220)

        myDatabaseTable.model = DatabaseTableModel(arrayListOf())
        myDatabaseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION) // Todo change
        myDatabaseTable.selectionModel.addListSelectionListener {
            for (listener in myListeners) {
                listener.selectedDatabaseChanged()
            }
        }

        object : DoubleClickListener() {
            override fun onDoubleClick(e: MouseEvent): Boolean {
                if (myDatabaseTable.isEnabled && okAction.isEnabled) {
                    okAction.actionPerformed(null)
                    return true
                }
                return false
            }
        }.installOn(myDatabaseTable)

        myDatabaseTable.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && okAction.isEnabled) {
                    okAction.actionPerformed(null)
                }
            }
        })

        // Do not recreate columns on every model update - this should help maintain the column sizes set above
        myDatabaseTable.autoCreateColumnsFromModel = false

        // Allow sorting by columns (in lexicographic order)
        myDatabaseTable.autoCreateRowSorter = true
    }

    override fun dispose() {}

    fun init(databaseList: List<String>) {
        myDatabaseTable.model = DatabaseTableModel(databaseList)
    }

    fun addListener(listener: DatabaseChooserListener) {
        myListeners.add(listener)
    }

    private inner class DatabaseTableModel(val databases: List<String>) : AbstractTableModel() {
        override fun getColumnName(column: Int): String {
            return "Files"
        }

        override fun getRowCount(): Int {
            return databases.size
        }

        override fun getColumnCount(): Int {
            return 1
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            if (rowIndex >= databases.size) {
                return null
            }
            return databases[rowIndex]
        }

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return String::class.java
        }
    }
}