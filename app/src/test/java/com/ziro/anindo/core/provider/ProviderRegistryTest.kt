package com.ziro.anindo.core.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {

    @Test
    fun testProviderRegistryContainsRequiredProviders() {
        val providers = ProviderRegistry.all()
        assertTrue(providers.isNotEmpty())

        val otakudesu = ProviderRegistry.get("otakudesu")
        assertNotNull(otakudesu)
        assertEquals("otakudesu", otakudesu?.name)

        val nontonAnime = ProviderRegistry.get("nontonanimeid")
        assertNotNull(nontonAnime)
        assertEquals("nontonanime", nontonAnime?.name)
    }


    @Test
    fun testProviderNamesCaseInsensitive() {
        assertNotNull(ProviderRegistry.get("OTAKUDESU"))
        assertNotNull(ProviderRegistry.get("Otakudesu"))
        assertNotNull(ProviderRegistry.get("NONTONANIMEID"))
    }
}
