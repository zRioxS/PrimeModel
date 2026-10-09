package kr.rioxs.primemodel.bukkit.nms.v1_21_R6

import kr.rioxs.primemodel.api.armor.ArmorItem
import kr.rioxs.primemodel.api.armor.PlayerArmor
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.equipment.EquipmentAssets
import org.bukkit.craftbukkit.entity.CraftPlayer

internal data class PlayerArmorImpl(
    private val player: CraftPlayer
) : PlayerArmor {

    override fun helmet(): ArmorItem? {
        return player.handle.getItemBySlot(EquipmentSlot.HEAD).toArmorItem()
    }

    override fun leggings(): ArmorItem? {
        return player.handle.getItemBySlot(EquipmentSlot.LEGS).toArmorItem()
    }

    override fun chestplate(): ArmorItem? {
        return player.handle.getItemBySlot(EquipmentSlot.CHEST).toArmorItem()
    }

    override fun boots(): ArmorItem? {
        return player.handle.getItemBySlot(EquipmentSlot.FEET).toArmorItem()
    }

    private fun VanillaItemStack.toArmorItem(): ArmorItem? = get(DataComponents.EQUIPPABLE)?.assetId?.map {
        val trim = get(DataComponents.TRIM)
        ArmorItem(
            get(DataComponents.DYED_COLOR)?.rgb ?: if (it === EquipmentAssets.LEATHER) DyedItemColor.LEATHER_COLOR else 0xFFFFFF,
            it.location().path,
            trim?.pattern?.value()?.assetId?.path,
            trim?.material?.value()?.assets?.base?.suffix
        )
    }?.orElse(null)
}
