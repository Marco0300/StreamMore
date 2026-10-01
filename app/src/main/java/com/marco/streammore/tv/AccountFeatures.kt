package com.marco.streammore.tv

import org.json.JSONObject

/**
 * The backend resolves `liveTvEnabled` from the account's assigned package and
 * includes the effective value on the authenticated user returned by `/api/auth/me`
 * and `/api/auth/login`. Older responses omit the field; those packages retain the
 * historical default of Live TV being enabled.
 */
internal fun accountHasLiveTvAccess(authResponse: JSONObject?): Boolean =
    authResponse?.optJSONObject("user")?.optBoolean("liveTvEnabled", true) ?: true
