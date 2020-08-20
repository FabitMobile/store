package ru.fabit.store


fun distinctChangeBoolean(value1: Boolean, value2: Boolean): Boolean {
    return value1 && value1 != value2
}

fun <T> distinctChangeObjectOrNull(value1: T?, value2: T?): T? {
    return if (value1 != null && value1 != value2) {
        value1
    } else {
        null
    }
}