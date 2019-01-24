package com.chrynan.klutter.core.math

interface Matrix<T> {

    val storage: List<T>

    val dimension: Int
}