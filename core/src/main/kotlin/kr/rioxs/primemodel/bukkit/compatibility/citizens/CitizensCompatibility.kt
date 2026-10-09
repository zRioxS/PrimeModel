package kr.rioxs.primemodel.bukkit.compatibility.citizens

import kr.rioxs.primemodel.bukkit.compatibility.Compatibility

import kr.rioxs.primemodel.bukkit.compatibility.citizens.command.AnimateCommand
import kr.rioxs.primemodel.bukkit.compatibility.citizens.command.LimbCommand
import kr.rioxs.primemodel.bukkit.compatibility.citizens.command.ModelCommand
import kr.rioxs.primemodel.bukkit.compatibility.citizens.trait.ModelTrait
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.trait.TraitInfo

class CitizensCompatibility : Compatibility {
    override fun start() {
        CitizensAPI.getTraitFactory()
            .registerTrait(TraitInfo.create(ModelTrait::class.java))
        CitizensAPI.getCommandManager().run {
            register(ModelCommand::class.java)
            register(AnimateCommand::class.java)
            register(LimbCommand::class.java)
        }
    }
}

