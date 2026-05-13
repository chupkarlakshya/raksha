package com.safepath.indore.data

data class EmergencyGuideItem(
    val emoji: String,
    val question: String,
    val answer: String,
    var isExpanded: Boolean = false
)
