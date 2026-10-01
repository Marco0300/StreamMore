package com.marco.streammore.tv

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountFeaturesTest {
    @Test
    fun packageEntitlementDisablesLiveTvWhenTheAccountFlagIsFalse() {
        val response = JSONObject("""{"user":{"liveTvEnabled":false}}""")

        assertFalse(accountHasLiveTvAccess(response))
    }

    @Test
    fun packageEntitlementEnablesLiveTvWhenTheAccountFlagIsTrue() {
        val response = JSONObject("""{"user":{"liveTvEnabled":true}}""")

        assertTrue(accountHasLiveTvAccess(response))
    }

    @Test
    fun olderServerResponsesWithoutTheFlagKeepLiveTvEnabled() {
        assertTrue(accountHasLiveTvAccess(JSONObject("""{"user":{}}""")))

    }
}
