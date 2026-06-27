package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScriptRepository(private val scriptDao: ScriptDao) {
    val allScripts: Flow<List<ScriptEntity>> = scriptDao.getAllScripts()

    suspend fun insert(script: ScriptEntity) {
        withContext(Dispatchers.IO) {
            scriptDao.insertScript(script)
        }
    }

    suspend fun update(script: ScriptEntity) {
        withContext(Dispatchers.IO) {
            scriptDao.updateScript(script)
        }
    }

    suspend fun delete(script: ScriptEntity) {
        withContext(Dispatchers.IO) {
            scriptDao.deleteScript(script)
        }
    }
}
