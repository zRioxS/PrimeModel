package kr.rioxs.primemodel.bukkit.compatibility.citizens.trait

import kr.rioxs.primemodel.api.PrimeModel
import kr.rioxs.primemodel.api.data.renderer.ModelRenderer
import kr.rioxs.primemodel.bukkit.util.wrap
import net.citizensnpcs.api.trait.Trait
import net.citizensnpcs.api.trait.TraitName
import net.citizensnpcs.api.util.DataKey

@TraitName("model")
class ModelTrait : Trait("model") {
    private var _renderer: ModelRenderer? = null
    var renderer
        get() = _renderer
        set(value) {
            npc?.entity?.let {
                value?.create(it.wrap()) ?: PrimeModel.registryOrNull(it.uniqueId)?.close()
            }
            _renderer = value
        }

    override fun load(key: DataKey) {
        key.getString("")?.let {
            PrimeModel.modelOrNull(it)?.let { model ->
                renderer = model
            }
        }
    }

    override fun save(key: DataKey) {
        npc?.entity?.uniqueId?.let { uuid ->
            key.setString("", PrimeModel.registryOrNull(uuid)?.first()?.name())
        }
    }

    override fun onSpawn() {
        npc?.entity?.let {
            if (PrimeModel.registryOrNull(it.uniqueId) == null) {
                renderer?.create(it.wrap())
            }
        }
    }

    override fun onCopy() {
        onSpawn()
    }

    override fun onDespawn() {
        npc?.entity?.uniqueId?.let {
            PrimeModel.registryOrNull(it)?.close()
        }
    }

    override fun onRemove() {
        npc?.entity?.uniqueId?.let {
            PrimeModel.registryOrNull(it)?.close()
        }
    }
}
