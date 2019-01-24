package com.chrynan.klutter.platformandroid.util

// TODO(dnfield): remove this if/when we can use appcompat to support it.
// java.util.function.Predicate isn't available until API24
interface Predicate<T> {

    fun test(t: T): Boolean
}