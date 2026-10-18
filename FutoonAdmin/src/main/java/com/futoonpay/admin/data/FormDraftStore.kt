package com.futoonpay.admin.data

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/** Only configuration values that are not credentials are eligible for a draft. */
class FormDraftStore(context: Context) {
    private val preferences = context.getSharedPreferences("futoon_form_drafts", Context.MODE_PRIVATE)
    private fun key(backend: String, form: NativeForm): String {
        val id = form.fields.firstOrNull { it.name == "id" }?.value.orEmpty()
        val scope = "$backend|${form.action}|$id"
        return MessageDigest.getInstance("SHA-256").digest(scope.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun save(backend: String, form: NativeForm) {
        preferences.edit().putString(key(backend, form), JSONObject(form.draftValues()).toString()).apply()
    }
    fun restore(backend: String, form: NativeForm): NativeForm = runCatching {
        val raw = preferences.getString(key(backend, form), null) ?: return form
        val json = JSONObject(raw)
        form.restoreDraft(json.keys().asSequence().associateWith { json.optString(it) })
    }.getOrDefault(form)
    fun remove(backend: String, form: NativeForm) { preferences.edit().remove(key(backend, form)).apply() }
}
