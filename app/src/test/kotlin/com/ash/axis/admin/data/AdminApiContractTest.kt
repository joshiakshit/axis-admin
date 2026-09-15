package com.ash.axis.admin.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

class AdminApiContractTest {
    @Test
    fun `admin calls map to the worker routes`() {
        assertEquals("v1/admin/device-session", post("createDeviceSession"))
        assertEquals("v1/admin/users", get("listUsers"))
        assertEquals("v1/admin/users/{admno}/allow", post("allow"))
        assertEquals("v1/admin/users/{admno}/kick", post("kick"))
        assertEquals("v1/admin/users/{admno}/ban", post("ban"))
        assertEquals("v1/admin/approve-all", post("approveAll"))
        assertEquals("v1/admin/health", get("health"))
        assertEquals("v1/admin/config", get("getConfig"))
        assertEquals("v1/admin/config", put("putConfig"))
    }

    private fun method(name: String) = AdminApi::class.java.methods.single { it.name == name }

    private fun get(name: String) = requireNotNull(method(name).getAnnotation(GET::class.java)).value

    private fun post(name: String) = requireNotNull(method(name).getAnnotation(POST::class.java)).value

    private fun put(name: String) = requireNotNull(method(name).getAnnotation(PUT::class.java)).value
}
