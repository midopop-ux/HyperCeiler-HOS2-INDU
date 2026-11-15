package com.sevtinge.hyperceiler.libhook.corepatch

import com.sevtinge.hyperceiler.common.utils.PrefsBridge

object CorePatchConfig {
    const val PREF_DOWNGRADE = "prefs_key_system_framework_core_patch_downgr"
    const val PREF_AUTH_CREAK = "prefs_key_system_framework_core_patch_auth_creak"
    const val PREF_RESOURCE_ARSC = "prefs_key_system_framework_bypass_resource_arsc"
    const val PREF_DIGEST_CREAK = "prefs_key_system_framework_core_patch_digest_creak"
    const val PREF_EXACT_SIGNATURE_CHECK = "prefs_key_system_framework_core_patch_exact_signature_check"
    const val PREF_USE_PRE_SIGNATURE = "prefs_key_system_framework_core_patch_use_pre_signature"
    const val PREF_ALLOW_HIDDEN_APIS = "prefs_key_system_framework_allow_hidden_apis"
    const val PREF_SHARED_USER = "prefs_key_system_framework_core_patch_shared_user"
    const val PREF_DISABLE_VERIFICATION_AGENT = "prefs_key_system_framework_disable_verification_agent"
    const val PREF_BYPASS_BLOCK = "prefs_key_system_framework_bypass_block"

    private fun getBoolean(key: String, defValue: Boolean = false): Boolean {
        return try {
            PrefsBridge.getBoolean(key, defValue)
        } catch (_: Throwable) {
            defValue
        }
    }

    fun isBypassDowngradeEnabled(): Boolean = getBoolean(PREF_DOWNGRADE, false)
    fun isBypassMinSdkEnabled(): Boolean = false
    fun isBypassVerificationEnabled(): Boolean = getBoolean(PREF_AUTH_CREAK, false)
    fun isBypassResourceArscRestrictionsEnabled(): Boolean = getBoolean(PREF_RESOURCE_ARSC, false)
    fun isBypassDigestEnabled(): Boolean = getBoolean(PREF_DIGEST_CREAK, false)
    fun isBypassExactSignatureMatch(): Boolean = getBoolean(PREF_EXACT_SIGNATURE_CHECK, false)
    fun isUsePreviousSignaturesEnabled(): Boolean = getBoolean(PREF_USE_PRE_SIGNATURE, false)
    fun isAllowHiddenApisForSystemAppsEnabled(): Boolean = getBoolean(PREF_ALLOW_HIDDEN_APIS, false)
    fun isBypassSharedUserEnabled(): Boolean = getBoolean(PREF_SHARED_USER, false)
    fun isDisableVerificationAgentEnabled(): Boolean = getBoolean(PREF_DISABLE_VERIFICATION_AGENT, false)
    fun isDisableDeveloperVerificationEnabled(): Boolean = false
    fun isBypassBlockEnabled(): Boolean = getBoolean(PREF_BYPASS_BLOCK, false)
}
