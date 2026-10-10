package kr.rioxs.primemodel.bukkit.configuration
import kr.rioxs.primemodel.api.PrimeModel

import kr.rioxs.primemodel.bukkit.util.toYaml
import kr.rioxs.primemodel.util.DATA_FOLDER
import kr.rioxs.primemodel.util.PLATFORM
import kr.rioxs.primemodel.util.ifNull
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

enum class PluginConfiguration(
    private val dir: String
) {
    CONFIG("config.yml"),
    ;

    fun create(): YamlConfiguration {
        val file = File(DATA_FOLDER, dir)
        val exists = file.exists()
        if (!exists) PLATFORM.saveResource(dir)
        val yaml = file.toYaml()
        val newYaml = PLATFORM.getResource(dir).ifNull { "Resource '$dir' not found." }.use {
            it.toYaml()
        }
        yaml.getKeys(true).forEach {
            if (!newYaml.contains(it)) yaml.set(it, null)
        }
        newYaml.getKeys(true).forEach {
            if (!yaml.contains(it)) yaml.set(it, newYaml.get(it))
            yaml.setComments(it ,newYaml.getComments(it))
        }
        return yaml.apply {
            save(file)
        }
    }
}
