package com.sprinttracker.utils

/**
 * Returns a new list replacing the elements that match the predicate for the ones that are created via the supplier.
 */
fun <E> List<E>.replaceElement(predicate: (elem: E) -> Boolean, supplier: () -> E): List<E> =
    this.map { e -> if (predicate(e)) supplier() else e }