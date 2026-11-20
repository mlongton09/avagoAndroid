package com.avago.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AccessDenialTest {
    @Test
    fun `reads the code from REST and v2 bodies`() {
        assertEquals(
            AccessDenial.MEMBERSHIP_SUSPENDED,
            AccessDenial.fromBody("""{"error":"Your membership is suspended","code":"membership_suspended","message":"x"}"""),
        )
        assertEquals(AccessDenial.USER_DEACTIVATED, AccessDenial.fromBody("""{"error":"user_deactivated","message":"x"}"""))
    }

    @Test
    fun `any other 403 body is not a denial`() {
        assertNull(AccessDenial.fromBody("""{"error":"forbidden","message":"Not allowed"}"""))
        assertNull(AccessDenial.fromBody("""{"error":"Insufficient role","code":"insufficient_role"}"""))
        assertNull(AccessDenial.fromBody("<html>403</html>"))
        assertNull(AccessDenial.fromBody(""))
        assertNull(AccessDenial.fromBody(null))
    }

    @Test
    fun `the account comes from REST and v2 account paths`() {
        assertEquals("a1", AccessDenial.accountOf("/accounts/a1/assets"))
        assertEquals("a1", AccessDenial.accountOf("/v2/accounts/a1/sync/sessions"))
        assertNull(AccessDenial.accountOf("/me"))
    }
}
