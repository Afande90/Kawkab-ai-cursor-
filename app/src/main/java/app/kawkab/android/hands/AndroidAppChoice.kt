package app.kawkab.android.hands

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import app.kawkab.android.domain.decision.DecisionResult
import app.kawkab.android.domain.decision.TypedJudge
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** App identities come from PackageManager; the judge can only pick an installed allowlisted option. */
@Singleton
class AndroidAppChoice @Inject constructor(
    @ApplicationContext private val context: Context,
    private val judge: TypedJudge,
) : AppChoice {
    private val prefs = context.getSharedPreferences("app_choices", Context.MODE_PRIVATE)
    private val pending = MutableStateFlow<PendingApp?>(null)
    private val choices = MutableStateFlow(read())
    override val state: StateFlow<PendingApp?> = pending
    override val learned: StateFlow<Map<String, AppOption>> = choices

    override suspend fun resolve(name: String, utterance: String): String? {
        val manager = context.packageManager
        if (manager.getLaunchIntentForPackage(name) != null) return name
        val key = name.lowercase().trim()
        choices.value[key]?.takeIf { manager.getLaunchIntentForPackage(it.packageName) != null }?.let {
            return it.packageName
        }
        val installed = manager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0,
        ).map {
            AppOption(it.loadLabel(manager).toString(), it.activityInfo.packageName)
        }.filter { it.label.contains(name, ignoreCase = true) }.distinctBy { it.packageName }.take(MAX_OPTIONS)
        if (installed.size < 2) return installed.singleOrNull()?.packageName
        val labels = installed.mapIndexed { i, app -> "${i + 1}: ${app.label}" }
        val decision = judge.choose(utterance, "Which app do you mean?", labels)
        val selected = (decision as? DecisionResult.Chosen)?.let { labels.indexOf(it.option) }
            ?.takeIf { it >= 0 }?.let { installed[it] }
        if (selected != null) return selected.packageName
        pending.value = PendingApp(name, installed)
        return null
    }

    override fun answer(text: String): String? {
        val request = pending.value ?: return null
        val chosen = AppChoiceRules.pick(text, request.options) ?: return null
        val json = JSONObject().put("label", chosen.label).put("package", chosen.packageName)
        prefs.edit { putString(request.spoken.lowercase().trim(), json.toString()) }
        choices.value = read()
        clear()
        return chosen.packageName
    }

    override fun clear() {
        pending.value = null
    }
    override fun forget(name: String) {
        prefs.edit { remove(name) }
        choices.value = read()
    }

    private fun read(): Map<String, AppOption> = prefs.all.mapNotNull { (name, value) ->
        runCatching {
            val json = JSONObject(value as String)
            name to AppOption(json.getString("label"), json.getString("package"))
        }.getOrNull()
    }.toMap()

    private companion object {
        const val MAX_OPTIONS = 5
    }
}
