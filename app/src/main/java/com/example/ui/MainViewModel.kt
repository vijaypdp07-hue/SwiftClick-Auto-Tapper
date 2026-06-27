package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ScriptEntity
import com.example.data.ScriptRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ScriptRepository

    val allScripts: StateFlow<List<ScriptEntity>>

    init {
        val scriptDao = AppDatabase.getDatabase(application).scriptDao()
        repository = ScriptRepository(scriptDao)
        allScripts = repository.allScripts.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
    }

    fun insert(script: ScriptEntity) = viewModelScope.launch {
        repository.insert(script)
    }

    fun update(script: ScriptEntity) = viewModelScope.launch {
        repository.update(script)
    }

    fun delete(script: ScriptEntity) = viewModelScope.launch {
        repository.delete(script)
    }
    
    private var pendingExportScript: ScriptEntity? = null
    
    fun setPendingExport(script: ScriptEntity) {
        pendingExportScript = script
    }
    
    fun exportPendingScript(uri: android.net.Uri, context: android.content.Context) {
        val script = pendingExportScript ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(script.gesturesJson.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            pendingExportScript = null
        }
    }
}
