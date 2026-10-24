package com.zemmy.sigurvita.core.util

import com.zemmy.sigurvita.core.model.Series

fun Series.matchesQuery(query: String): Boolean =
    name.contains(query, ignoreCase = true) ||
            (localizedName?.contains(query, ignoreCase = true) == true)