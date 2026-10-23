package it.sunw.widget

import android.location.Address
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** What a found place is called and whether it is what was asked for. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaceSearchTest {

    private fun address(feature: String?, locality: String?, street: String? = null) = Address(Locale.ITALY).apply {
        featureName = feature
        this.locality = locality
        thoroughfare = street
        latitude = 44.4
        longitude = 10.0
    }

    @Test
    fun aPlaceKeepsItsOwnNameNotTheMunicipalitys() {
        // "Prato Spilla" lies in the municipality of Monchio delle Corti: the place is named as searched.
        assertEquals("Prato Spilla", PlaceSearch.shortName(address("Prato Spilla", "Monchio delle Corti")))
        assertEquals("Parma", PlaceSearch.shortName(address("Parma", "Parma")))
    }

    @Test
    fun aStreetAddressIsNamedAfterItsLocality() {
        assertEquals("Parma", PlaceSearch.shortName(address("12", "Parma", "Via Roma"))) // a house number
        assertEquals("Parma", PlaceSearch.shortName(address("Via Roma", "Parma", "Via Roma"))) // the street itself
    }

    @Test
    fun aResultMatchesWhenTheNamesContainEachOtherIgnoringCaseAndAccents() {
        assertTrue(PlaceSearch.matches("prato spilla", address("Prato Spilla", "Monchio delle Corti")))
        assertTrue(PlaceSearch.matches("Forlì", address("Forli", "Forli")))
        assertTrue(PlaceSearch.matches("Monchio delle Corti, Parma", address(null, "Monchio delle Corti")))
        // The geocoder offers the closest thing it knows: that is not what was asked, so it is offered, not taken.
        assertFalse(PlaceSearch.matches("Prato Spilla", address("Monchio delle Corti", "Monchio delle Corti")))
    }
}
