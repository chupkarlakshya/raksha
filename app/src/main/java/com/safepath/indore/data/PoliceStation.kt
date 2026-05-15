package com.safepath.indore.data

import com.google.android.gms.maps.model.LatLng

data class PoliceStation(
    val name: String,
    val location: LatLng
)

object PoliceStationProvider {
    val stations = listOf(
        PoliceStation("Central Kotwali Police Station", LatLng(22.7161, 75.8584)),
        PoliceStation("M.G. Road Police Station", LatLng(22.7215, 75.8640)),
        PoliceStation("Palasia Police Station", LatLng(22.7250, 75.8885)),
        PoliceStation("Bhawarkua Police Station", LatLng(22.6917, 75.8675)),
        PoliceStation("Vijay Nagar Police Station", LatLng(22.7533, 75.8937)),
        PoliceStation("Annapurna Police Station", LatLng(22.6998, 75.8368)),
        PoliceStation("Chhoti Gwaltoli Police Station", LatLng(22.7145, 75.8692)),
        PoliceStation("Sanyogitaganj Police Station", LatLng(22.7102, 75.8755)),
        PoliceStation("Tukoganj Police Station", LatLng(22.7230, 75.8790)),
        PoliceStation("Khajrana Police Station", LatLng(22.7275, 75.9085)),
        PoliceStation("Rajendra Nagar Police Station", LatLng(22.6750, 75.8350)),
        PoliceStation("Bhanwarkuan Police Station", LatLng(22.6900, 75.8650)),
        PoliceStation("Sarafa Police Station", LatLng(22.7175, 75.8525)),
        PoliceStation("Aerodrome Police Station", LatLng(22.7260, 75.8150)),
        PoliceStation("Lasudia Police Station", LatLng(22.7750, 75.9120)),
        PoliceStation("Heera Nagar Police Station", LatLng(22.7600, 75.8850)),
        PoliceStation("Banganga Police Station", LatLng(22.7580, 75.8550)),
        PoliceStation("Malharganj Police Station", LatLng(22.7280, 75.8450)),
        PoliceStation("Juni Indore Police Station", LatLng(22.7050, 75.8620)),
        PoliceStation("Chhatripura Police Station", LatLng(22.7120, 75.8420))
    )
}
