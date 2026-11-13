package com.medsel.mobilescope.ui

/** Modules triés selon l'ordre enregistré ; les modules absents de la liste suivent dans l'ordre d'origine. */
fun orderedCats(saved: List<String>): List<Cat> {
    val all = Cat.values().toList()
    val known = saved.mapNotNull { n -> all.firstOrNull { it.name == n } }
    return known + all.filter { it !in known }
}
