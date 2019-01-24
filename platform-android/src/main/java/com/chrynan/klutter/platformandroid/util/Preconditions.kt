package com.chrynan.klutter.platformandroid.util

/**
 * Static convenience methods that help a method or constructor check whether
 * it was invoked correctly (that is, whether its *preconditions* were
 * met).
 */
object Preconditions {

    /**
     * Ensures that an object reference passed as a parameter to the calling
     * method is not null.
     *
     * @param reference an object reference
     * @return the non-null reference that was validated
     * @throws NullPointerException if `reference` is null
     */
    fun <T> checkNotNull(reference: T?): T = reference ?: throw NullPointerException()
}