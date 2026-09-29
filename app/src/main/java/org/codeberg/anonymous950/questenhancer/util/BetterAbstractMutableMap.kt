package org.codeberg.anonymous950.questenhancer.util

// same as AbstractMutableMap, except instead of implementing entries, you implement get, put, remove and keys
abstract class BetterAbstractMutableMap<K, V> : AbstractMutableMap<K, V>() {
    abstract override operator fun get(key: K): V?

    abstract override fun remove(key: K): V?

    abstract override val keys: MutableSet<K>

    // because it's internal for no reason in the standard library
    private inline fun getOrElseNullable(key: K, defaultValue: () -> V): V {
        val value = get(key)
        if (value == null && !containsKey(key)) {
            return defaultValue()
        } else {
            @Suppress("UNCHECKED_CAST") return value as V
        }
    }

    protected inner class MutableEntry(override val key: K, value: V) :
        MutableMap.MutableEntry<K, V> {
        override var value: V = value
            private set

        override fun setValue(newValue: V): V {
            // if you modify the map other then through setValue(), the behavior is unspecified according to https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-mutable-map/-mutable-entry/
            // in this case, the value won't synchronize with the map and if the key is deleted, calling setValue() will recreate it
            val oldValue = value
            this@BetterAbstractMutableMap[key] = newValue
            value = newValue
            return oldValue
        }

        override fun equals(other: Any?): Boolean =
            other is Map.Entry<*, *> && key == other.key && value == other.value

        override fun hashCode(): Int = key.hashCode() xor value.hashCode()

        override fun toString(): String = "$key=$value"
    }

    private inner class EntryIterator : MutableIterator<MutableMap.MutableEntry<K, V>> {
        private val keysIterator = keys.iterator()

        // because V might allow null, I'm using Single as a wrapper to tell the difference between the key being null and not having a key for removal
        private var keyForRemoval: Single<K>? = null

        override operator fun hasNext(): Boolean = keysIterator.hasNext()

        override operator fun next(): MutableEntry {
            val key = keysIterator.next()
            keyForRemoval = Single(key)
            // the unchecked cast is because get() can return null if the item isn't in the map, but if it's in the keys it has to be
            // however, I cannot use !! because V might allow null
            return MutableEntry(key, getValue(key))
        }

        override fun remove() {
            val keyForRemovalValue = (keyForRemoval ?: throw IllegalStateException()).value
            keysIterator.remove()
            remove(keyForRemovalValue)
            keyForRemoval = null
        }
    }

    protected inner class MutableEntries : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            return EntryIterator()
        }

        // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html#entrySet()
        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean =
            throw UnsupportedOperationException()

        override val size
            get() = keys.size

        // for performance
        override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean =
            this@BetterAbstractMutableMap.getOrElseNullable(
                element.key
            ) { return@contains false } == element.value
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> by lazy { MutableEntries() }
}