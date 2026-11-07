package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AgentRole
import com.example.domain.model.PermissionPolicy
import com.example.ui.theme.AccentPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "piash_agent_preferences")

data class AgentSettingsState(
    val darkTheme: Boolean = true,
    val amoledMode: Boolean = true,
    val accentPreset: AccentPreset = AccentPreset.CYAN,
    val selectedModelId: String = "gemini-3.8-flash",
    val maxIterations: Int = 6,
    val timeoutSeconds: Int = 60,
    val maxTokenBudget: Int = 128_000,
    val sessionCostLimitUsd: Double = 5.0,
    val autoCompactContext: Boolean = true,
    val agentModels: Map<AgentRole, String> = AgentRole.entries.associateWith { it.defaultModelId },
    val toolPolicies: Map<String, PermissionPolicy> = emptyMap()
)

class UserPreferencesStore(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val AMOLED_MODE = booleanPreferencesKey("amoled_mode")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val MAX_ITERATIONS = intPreferencesKey("max_iterations")
        val TIMEOUT_SECONDS = intPreferencesKey("timeout_seconds")
        val MAX_TOKEN_BUDGET = intPreferencesKey("max_token_budget")
        val COST_LIMIT_USD = doublePreferencesKey("cost_limit_usd")
        val AUTO_COMPACT = booleanPreferencesKey("auto_compact")
    }

    val settingsFlow: Flow<AgentSettingsState> = context.dataStore.data.map { prefs ->
        val accentName = prefs[Keys.ACCENT_PRESET] ?: AccentPreset.CYAN.name
        val accent = AccentPreset.entries.firstOrNull { it.name == accentName } ?: AccentPreset.CYAN

        val roleMap = AgentRole.entries.associateWith { role ->
            prefs[stringPreferencesKey("role_model_${role.name}")] ?: role.defaultModelId
        }

        val policies = mutableMapOf<String, PermissionPolicy>()
        prefs.asMap().forEach { (k, v) ->
            if (k.name.startsWith("policy_") && v is String) {
                val toolName = k.name.removePrefix("policy_")
                val policy = PermissionPolicy.entries.firstOrNull { it.name == v }
                if (policy != null) {
                    policies[toolName] = policy
                }
            }
        }

        AgentSettingsState(
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            amoledMode = prefs[Keys.AMOLED_MODE] ?: true,
            accentPreset = accent,
            selectedModelId = prefs[Keys.SELECTED_MODEL] ?: "gemini-3.8-flash",
            maxIterations = prefs[Keys.MAX_ITERATIONS] ?: 6,
            timeoutSeconds = prefs[Keys.TIMEOUT_SECONDS] ?: 60,
            maxTokenBudget = prefs[Keys.MAX_TOKEN_BUDGET] ?: 128_000,
            sessionCostLimitUsd = prefs[Keys.COST_LIMIT_USD] ?: 5.0,
            autoCompactContext = prefs[Keys.AUTO_COMPACT] ?: true,
            agentModels = roleMap,
            toolPolicies = policies
        )
    }

    suspend fun updateTheme(darkTheme: Boolean, amoledMode: Boolean, accent: AccentPreset) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = darkTheme
            prefs[Keys.AMOLED_MODE] = amoledMode
            prefs[Keys.ACCENT_PRESET] = accent.name
        }
    }

    suspend fun setSelectedModel(modelId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_MODEL] = modelId
        }
    }

    suspend fun setAgentRoleModel(role: AgentRole, modelId: String) {
        context.dataStore.edit { prefs ->
            prefs[stringPreferencesKey("role_model_${role.name}")] = modelId
        }
    }

    suspend fun setToolPolicy(toolName: String, policy: PermissionPolicy) {
        context.dataStore.edit { prefs ->
            prefs[stringPreferencesKey("policy_$toolName")] = policy.name
        }
    }

    suspend fun updateAgentLimits(
        maxIterations: Int,
        timeoutSeconds: Int,
        maxTokenBudget: Int,
        costLimitUsd: Double,
        autoCompact: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MAX_ITERATIONS] = maxIterations.coerceIn(1, 15)
            prefs[Keys.TIMEOUT_SECONDS] = timeoutSeconds.coerceIn(15, 180)
            prefs[Keys.MAX_TOKEN_BUDGET] = maxTokenBudget.coerceAtLeast(4_000)
            prefs[Keys.COST_LIMIT_USD] = costLimitUsd.coerceAtLeast(0.10)
            prefs[Keys.AUTO_COMPACT] = autoCompact
        }
    }
}
