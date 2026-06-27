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
import kotlinx.coroutines.flow.asStateFlow
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
    
    private val _isPremium = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    fun upgradeToPremium() {
        _isPremium.value = true
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

    fun importScript(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (!_isPremium.value && allScripts.value.size >= 3) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Free tier cap: Max 3 saved scripts.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (jsonString != null) {
                    // Try to decode to ensure it's valid
                    val decoded = kotlinx.serialization.json.Json.decodeFromString<com.example.data.ScriptData>(jsonString)
                    
                    val finalJson = if (!_isPremium.value && decoded.events.size > 3) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Free tier: Truncated to 3 steps.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        kotlinx.serialization.json.Json.encodeToString(decoded.copy(events = decoded.events.take(3)))
                    } else {
                        jsonString
                    }
                    
                    val entity = ScriptEntity(
                        name = "Imported Script ${System.currentTimeMillis()}",
                        gesturesJson = finalJson
                    )
                    insert(entity)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Script Imported!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to import script.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
