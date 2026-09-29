package org.codeberg.anonymous950.questenhancer

import org.codeberg.anonymous950.questenhancer.util.Single
import java.io.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

interface HasType {
    val type: KClass<*>?
}

sealed class DeviceConfigValue : Serializable {
    abstract val value: Serializable?

    class Null(override val value: Nothing?) : DeviceConfigValue() {
        companion object : HasType {
            override val type = null
        }
    }

    class Boolean(override val value: kotlin.Boolean) : DeviceConfigValue() {
        companion object : HasType {
            override val type = kotlin.Boolean::class
        }
    }

    class Double(override val value: kotlin.Double) : DeviceConfigValue() {
        companion object : HasType {
            override val type = kotlin.Double::class
        }
    }

    class Long(override val value: kotlin.Long) : DeviceConfigValue() {
        companion object : HasType {
            override val type = kotlin.Long::class
        }
    }

    class String(override val value: kotlin.String) : DeviceConfigValue() {
        companion object : HasType {
            override val type = kotlin.String::class
        }
    }

    val type
        get() = (this::class.companionObjectInstance as HasType).type

    override fun toString() = "${this::class.simpleName}($value)"
    override fun equals(other: Any?) =
        this === other || (other is DeviceConfigValue && value == other.value)

    override fun hashCode() = value.hashCode()

    fun getType(
        type: KClass<out DeviceConfigValue>
    ): Single<Serializable?>? = if (type.isInstance(this)) Single(value) else if (this is Null) Single(null) else null

    companion object {
        private const val TAG = "DeviceConfigValue"

        operator fun invoke(value: Nothing?) = Null(value)
        operator fun invoke(value: kotlin.Boolean) = Boolean(value)
        operator fun invoke(value: kotlin.Double) = Double(value)
        operator fun invoke(value: kotlin.Long) = Long(value)
        operator fun invoke(value: kotlin.String) = String(value)
    }
}