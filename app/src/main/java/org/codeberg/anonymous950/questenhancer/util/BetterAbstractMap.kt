package org.codeberg.anonymous950.questenhancer.util

// same as AbstractMutableMap, except instead of implementing entries, you implement get() and keys
// this implements caching to some degree because it's supposed to be immutable, so make sure not to mutate!
abstract class BetterAbstractMap<K, V> : AbstractMap<K, V>() {
    abstract override fun get(key: K): V?

    abstract override val keys: Set<K>

    protected inner class Entry(override val key: K, override val value: V) : Map.Entry<K, V> {
        override fun equals(other: Any?): Boolean =
            other is Map.Entry<*, *> && key == other.key && value == other.value

        override fun hashCode(): Int = key.hashCode() xor value.hashCode()
    }

    protected inner class Entries : AbstractSet<Entry>() {
        override fun iterator(): Iterator<Entry> {
            // the unchecked cast is because get() can return null if the item isn't in the map, but if it's in the keys it has to be
            // however, I cannot use !! because V might allow null
            return keys.asSequence()
                .map { Entry(it, getValue(it)) }
                .iterator()
        }

        override val size by lazy {
            keys.size
        }
    }

    override val entries: Set<Map.Entry<K, V>> by lazy {
        Entries()
    }
}