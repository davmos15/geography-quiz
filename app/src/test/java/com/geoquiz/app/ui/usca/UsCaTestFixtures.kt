package com.geoquiz.app.ui.usca

import com.geoquiz.app.domain.map.GeoBounds
import com.geoquiz.app.domain.map.GeoFeature
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.GeoPart
import com.geoquiz.app.domain.map.GeoProperties
import com.geoquiz.app.domain.map.GeoPropertyDef
import com.geoquiz.app.domain.map.GeoPropertyType
import com.geoquiz.app.domain.map.GeometryKind

/** A tiny fake admin1 feature (task 6.2 tests): just the four string properties this mode reads. */
private val admin1Schema = listOf(
    GeoPropertyDef("name", GeoPropertyType.STRING),
    GeoPropertyDef("name_en", GeoPropertyType.STRING),
    GeoPropertyDef("postal", GeoPropertyType.STRING),
    GeoPropertyDef("country", GeoPropertyType.STRING)
)

fun fakeAdmin1Feature(id: String, name: String, postal: String, country: String): GeoFeature {
    val part = GeoPart(0, floatArrayOf(0f, 1f, 1f, 0f), floatArrayOf(0f, 0f, 1f, 1f))
    return GeoFeature(
        id,
        GeoProperties(admin1Schema, arrayOf(name, name, postal, country)),
        GeoBounds(0.0, 0.0, 1.0, 1.0),
        0.5,
        0.5,
        listOf(part)
    )
}

fun fakeAdmin1Layer(vararg features: GeoFeature): GeoLayer = GeoLayer(
    GeoLayerId.ADMIN1_50M,
    GeometryKind.POLYGON,
    GeoBounds(-180.0, -90.0, 180.0, 90.0),
    "test",
    admin1Schema,
    features.toList()
)

/** Three US states, sorted California/New York/Texas, and Washington DC. */
val FAKE_CALIFORNIA = fakeAdmin1Feature("US-CA", "California", "CA", "USA")
val FAKE_NEW_YORK = fakeAdmin1Feature("US-NY", "New York", "NY", "USA")
val FAKE_TEXAS = fakeAdmin1Feature("US-TX", "Texas", "TX", "USA")
val FAKE_DC = fakeAdmin1Feature("US-DC", "District of Columbia", "DC", "USA")
val FAKE_ONTARIO = fakeAdmin1Feature("CA-ON", "Ontario", "ON", "CAN")

val FAKE_ADMIN1_LAYER: GeoLayer =
    fakeAdmin1Layer(FAKE_CALIFORNIA, FAKE_NEW_YORK, FAKE_TEXAS, FAKE_DC, FAKE_ONTARIO)
