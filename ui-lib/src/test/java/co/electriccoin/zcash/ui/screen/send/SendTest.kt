package co.electriccoin.zcash.ui.screen.send

import kotlin.test.Test
import kotlin.test.assertEquals
import cash.z.ecc.android.sdk.type.AddressType as SdkAddressType
import cash.z.ecc.sdk.model.AddressType as RouteAddressType

class SendTest {
    @Test
    fun validatedAddressTypesOpenSendAsTheirRouteType() {
        assertEquals(RouteAddressType.UNIFIED, SdkAddressType.Unified.toRouteAddressType())
        assertEquals(RouteAddressType.UNIFIED, SdkAddressType.Shielded.toRouteAddressType())
        assertEquals(RouteAddressType.TRANSPARENT, SdkAddressType.Transparent.toRouteAddressType())
        assertEquals(RouteAddressType.TEX, SdkAddressType.Tex.toRouteAddressType())
    }

    @Test
    fun everyRouteTypeMapsBackToItsSdkType() {
        assertEquals(
            mapOf(
                RouteAddressType.UNIFIED to SdkAddressType.Unified,
                RouteAddressType.TRANSPARENT to SdkAddressType.Transparent,
                RouteAddressType.SAPLING to SdkAddressType.Shielded,
                RouteAddressType.TEX to SdkAddressType.Tex
            ),
            RouteAddressType.entries.associateWith { it.toSdkAddressType() }
        )
    }
}
