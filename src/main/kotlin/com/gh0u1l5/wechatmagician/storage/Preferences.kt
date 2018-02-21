package com.gh0u1l5.wechatmagician.storage

import android.content.*
import com.gh0u1l5.wechatmagician.Global.ACTION_UPDATE_PREF
import com.gh0u1l5.wechatmagician.Global.FOLDER_SHARED_PREFS
import com.gh0u1l5.wechatmagician.Global.MAGICIAN_BASE_DIR
import com.gh0u1l5.wechatmagician.Global.PREFERENCE_STRING_LIST_KEYS
import com.gh0u1l5.wechatmagician.Global.tryWithLog
import com.gh0u1l5.wechatmagician.Global.tryWithThread
import com.gh0u1l5.wechatmagician.WaitChannel
import de.robv.android.xposed.XSharedPreferences
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

class Preferences(private val preferencesName: String) : SharedPreferences {

    // loadChannel resumes all the threads waiting for the preference loading.
    private val loadChannel = WaitChannel()

    // listCache caches the string lists in memory to speed up getStringList()
    private val listCache: MutableMap<String, List<String>> = ConcurrentHashMap()

    // content is the preferences generated by the frond end of Wechat Magician.
    private lateinit var content: XSharedPreferences

    // load reads the shared preferences or reloads the existing preferences
    fun load() {
        tryWithThread {
            if (loadChannel.done) {
                content.reload()
                cacheStringList()
                return@tryWithThread
            }

            try {
                val preferencesDir = "$MAGICIAN_BASE_DIR/$FOLDER_SHARED_PREFS/"
                content = XSharedPreferences(File(preferencesDir, "$preferencesName.xml"))
            } catch (_: FileNotFoundException) {
                // Ignore this one
            } finally {
                loadChannel.done()
                cacheStringList()
            }
        }
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadChannel.wait()
            content.reload()
            cacheStringList()
        }
    }

    // listen registers a receiver to listen the update events from the frontend.
    fun listen(context: Context) {
        tryWithLog {
            context.registerReceiver(updateReceiver, IntentFilter(ACTION_UPDATE_PREF))
        }
    }

    fun cacheStringList() {
        PREFERENCE_STRING_LIST_KEYS.forEach { key ->
            listCache[key] = getString(key, "").split(" ").filter { it.isNotEmpty() }
        }
    }

    override fun contains(key: String): Boolean = content.contains(key)

    override fun getAll(): MutableMap<String, *>? = content.all

    private fun getValue(key: String): Any? {
        loadChannel.wait()
        return all?.get(key)
    }

    private inline fun <reified T>getValue(key: String, defValue: T) = getValue(key) as? T ?: defValue

    override fun getInt(key: String, defValue: Int): Int = getValue(key, defValue)

    override fun getLong(key: String, defValue: Long): Long = getValue(key, defValue)

    override fun getFloat(key: String, defValue: Float): Float = getValue(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean = getValue(key, defValue)

    override fun getString(key: String, defValue: String): String = getValue(key, defValue)

    override fun getStringSet(key: String, defValue: MutableSet<String>): MutableSet<String> = getValue(key, defValue)

    fun getStringList(key: String, defValue: List<String>): List<String> {
        loadChannel.wait()
        return listCache[key] ?: defValue
    }

    override fun edit(): SharedPreferences.Editor {
        throw UnsupportedOperationException()
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        throw UnsupportedOperationException()
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        throw UnsupportedOperationException()
    }
}
