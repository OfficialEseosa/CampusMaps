package com.campusmaps.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.campusmaps.data.campus.CoreBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

// One DataStore file for the whole app. Must be declared at the top level (Android rule).
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// Everything the user can switch in Settings, plus the saved building.
data class AppSettings(
    val speakInstructions: Boolean = true,
    val avoidStairs: Boolean = false,
    val demoMode: Boolean = false,
    val buildingId: String = CoreBridge.DEFAULT_BUILDING_ID,
    // "I carry a PantherCard" (BuzzCard on Georgia Tech): card-only entrances are usable after hours.
    val hasCard: Boolean = false,
)

// Saves settings on the phone so they survive restarts ("Avoid stairs" is persisted, per the handoff).
class SettingsRepository(private val context: Context) {

    private object Keys {
        val speak = booleanPreferencesKey("speak_instructions")
        val avoidStairs = booleanPreferencesKey("avoid_stairs")
        val demoMode = booleanPreferencesKey("demo_mode")
        val building = stringPreferencesKey("building_id")
        val hasCard = booleanPreferencesKey("has_campus_card")
        val deviceId = stringPreferencesKey("device_id")
        fun recents(buildingId: String) = stringPreferencesKey("recents_$buildingId")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            speakInstructions = p[Keys.speak] ?: true,
            avoidStairs = p[Keys.avoidStairs] ?: false,
            demoMode = p[Keys.demoMode] ?: false,
            buildingId = p[Keys.building] ?: CoreBridge.DEFAULT_BUILDING_ID,
            hasCard = p[Keys.hasCard] ?: false,
        )
    }

    suspend fun setSpeak(on: Boolean) = context.settingsStore.edit { it[Keys.speak] = on }
    suspend fun setAvoidStairs(on: Boolean) = context.settingsStore.edit { it[Keys.avoidStairs] = on }
    suspend fun setDemoMode(on: Boolean) = context.settingsStore.edit { it[Keys.demoMode] = on }
    suspend fun setHasCard(on: Boolean) = context.settingsStore.edit { it[Keys.hasCard] = on }
    suspend fun setBuilding(id: String) = context.settingsStore.edit { it[Keys.building] = id }

    // Recent destinations per building, newest first, at most three.
    fun recents(buildingId: String): Flow<List<String>> = context.settingsStore.data.map { p ->
        p[Keys.recents(buildingId)]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
    }

    suspend fun addRecent(buildingId: String, roomId: String) {
        context.settingsStore.edit { p ->
            val current = p[Keys.recents(buildingId)]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
            p[Keys.recents(buildingId)] = (listOf(roomId) + current.filter { it != roomId }).take(3).joinToString(",")
        }
    }

    suspend fun clearRecents(buildingId: String) {
        context.settingsStore.edit { it.remove(Keys.recents(buildingId)) }
    }

    // A random ID made on first launch and kept forever (section 16.1).
    suspend fun deviceId(): String {
        val existing = context.settingsStore.data.first()[Keys.deviceId]
        if (existing != null) return existing
        val fresh = UUID.randomUUID().toString()
        context.settingsStore.edit { it[Keys.deviceId] = fresh }
        return fresh
    }
}

// Gives each install a stable anonymous ID. Swap this class out when sign in exists.
interface SubmitterIdProvider {
    suspend fun currentId(): String
}

// For now the "user" is this install.
class DeviceIdProvider(private val settings: SettingsRepository) : SubmitterIdProvider {
    override suspend fun currentId(): String = settings.deviceId()
}
