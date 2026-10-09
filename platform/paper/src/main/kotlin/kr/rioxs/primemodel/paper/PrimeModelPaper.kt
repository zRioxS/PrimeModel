package kr.rioxs.primemodel.paper

import kr.rioxs.primemodel.api.PrimeModelPlatform
import kr.rioxs.primemodel.bukkit.PrimeModelPlugin

@Suppress("UNUSED")
class PrimeModelPaper : PrimeModelPlugin() {

    override fun jarType(): PrimeModelPlatform.JarType {
        return PrimeModelPlatform.JarType.PAPER
    }
}
