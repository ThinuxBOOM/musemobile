package com.musemobile.app.searchEngine

fun interface SearchableFieldExtractor<T> {
    fun getSearchableFields(item: T): Array<String>
}