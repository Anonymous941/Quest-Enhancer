package org.codeberg.anonymous950.questenhancer.util

import java.io.Serializable

data class Single<out T>(val value: T) : Serializable {
    override fun toString() = "($value)"
}

inline fun <T> Single<T>?.unwrapOrElse(block: () -> T): T = if (this == null) block() else value