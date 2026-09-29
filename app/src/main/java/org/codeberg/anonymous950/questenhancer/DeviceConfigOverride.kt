package org.codeberg.anonymous950.questenhancer

import org.codeberg.anonymous950.questenhancer.util.BetterAbstractMutableMap
import org.codeberg.anonymous950.questenhancer.util.Single
import java.io.Serializable
import kotlin.reflect.KClass

class DeviceConfigOverride : BetterAbstractMutableMap<String, DeviceConfigValue>() {
    companion object {
        private val TAG = "DeviceConfigOverride"
    }

    private val overriddenValues = mutableMapOf<String, DeviceConfigValue>()

    private var memoryState: Any? = null
    fun setMemoryState(memoryState: Any) {
        this.memoryState = memoryState
        // the values weren't applied yet
        overriddenValues.forEach(::applyValue)
    }

    private fun applyValue(key: String, value: DeviceConfigValue?) {
        check(this.memoryState != null) { "cannot apply without a memory state" }
    }

    override val keys = overriddenValues.keys

    override fun get(key: String) = overriddenValues[key]

    override fun put(key: String, value: DeviceConfigValue): DeviceConfigValue? =
        overriddenValues.put(key, value)

    override fun remove(key: String): DeviceConfigValue? = overriddenValues.remove(key)

    // Single(null) means force to default, whereas null means leave unchanged
    fun getType(
        key: String, type: KClass<out DeviceConfigValue>
    ): Single<Single<Serializable?>?>? =
        Single((overriddenValues[key] ?: return null).getType(type))
}