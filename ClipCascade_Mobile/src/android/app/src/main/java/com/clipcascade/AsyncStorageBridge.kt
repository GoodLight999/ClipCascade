// android\app\src\main\java\com\clipcascade\AsyncStorageBridge.kt
package com.clipcascade

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.reactnativecommunity.asyncstorage.ReactDatabaseSupplier
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Native access to the same SQLite database owned by React Native AsyncStorage.
 *
 * ReactDatabaseSupplier is process-wide. Bridge instances may drop their local
 * database reference, but must never close the supplier-owned database because
 * JavaScript AsyncStorage and other native bridge instances may still use it.
 * Values use the same JSON-string representation as AsyncStorageManagement.js.
 */
class AsyncStorageBridge(context: Context) {
    companion object {
        private const val TABLE_CATALYST = "catalystLocalStorage"
        private const val KEY_COLUMN = "key"
        private const val VALUE_COLUMN = "value"
        private const val TAG = "AsyncStorageBridge"
    }

    private val applicationContext = context.applicationContext
    private var db: SQLiteDatabase? = null

    init {
        connect()
    }

    fun connect() {
        try {
            db = ReactDatabaseSupplier.getInstance(applicationContext).get()
        } catch (error: Exception) {
            Log.e(TAG, "Error connecting to database", error)
            db = null
        }
    }

    private fun ensureConnection() {
        if (db?.isOpen != true) {
            connect()
        }
    }

    /**
     * Releases only this bridge's reference. The singleton supplier owns the
     * database lifecycle and may still be serving JavaScript AsyncStorage.
     */
    fun disconnect() {
        db = null
    }

    fun checkConnection(): Boolean {
        if (db?.isOpen != true) {
            Log.e(TAG, "Database is not connected.")
            return false
        }
        return true
    }

    fun getValue(key: String): String? {
        ensureConnection()

        var cursor: Cursor? = null
        return try {
            cursor = db?.query(
                TABLE_CATALYST,
                arrayOf(VALUE_COLUMN),
                "$KEY_COLUMN = ?",
                arrayOf(key),
                null,
                null,
                null
            ) ?: return null

            if (!cursor.moveToFirst()) return null

            val rawValue = cursor.getString(cursor.getColumnIndexOrThrow(VALUE_COLUMN))
            when (val decoded = JSONTokener(rawValue).nextValue()) {
                JSONObject.NULL -> null
                is String -> decoded
                else -> decoded.toString()
            }
        } catch (error: Exception) {
            Log.e(TAG, "Error retrieving value for key $key", error)
            null
        } finally {
            cursor?.close()
        }
    }

    fun getValuesForKeys(keys: List<String>): String {
        ensureConnection()
        val map: Map<String, String?> = keys.associateWith { getValue(it) }
        return JSONObject(map).toString()
    }

    @Synchronized
    fun setValue(key: String, value: String): Boolean {
        ensureConnection()

        return try {
            val database = db
            if (database?.isOpen != true) {
                Log.e(TAG, "Database is unavailable while setting key $key")
                return false
            }
            val values = ContentValues().apply {
                put(KEY_COLUMN, key)
                put(VALUE_COLUMN, JSONObject.quote(value))
            }
            database.insertWithOnConflict(
                TABLE_CATALYST,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            ) != -1L
        } catch (error: Exception) {
            Log.e(TAG, "Error setting value for key $key", error)
            false
        }
    }
}
