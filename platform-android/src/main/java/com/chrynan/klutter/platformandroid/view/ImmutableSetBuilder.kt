package com.chrynan.klutter.platformandroid.view

import java.util.*

class ImmutableSetBuilder<T> private constructor() {

    companion object {

        fun <T> newInstance(): ImmutableSetBuilder<T> = ImmutableSetBuilder()
    }

    var set: HashSet<T> = HashSet()

    fun add(element: T): ImmutableSetBuilder<T> {
        set.add(element)
        return this
    }

    @SafeVarargs
    fun add(vararg elements: T): ImmutableSetBuilder<T> {
        for (element in elements) {
            set.add(element)
        }
        return this
    }

    fun build(): Set<T> = Collections.unmodifiableSet(set)
}