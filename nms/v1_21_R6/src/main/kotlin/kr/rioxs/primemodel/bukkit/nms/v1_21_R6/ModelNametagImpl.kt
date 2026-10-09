package kr.rioxs.primemodel.bukkit.nms.v1_21_R6
import kr.rioxs.primemodel.api.bone.Bones.BoneMovement
import kr.rioxs.primemodel.api.bone.Bones.BonePosition

import com.mojang.math.Transformation
import kr.rioxs.primemodel.api.PrimeModel
import kr.rioxs.primemodel.api.bone.RenderedBone
import kr.rioxs.primemodel.api.nms.ModelNametag
import kr.rioxs.primemodel.api.nms.PacketBundler
import kr.rioxs.primemodel.api.platform.PlatformLocation
import kr.rioxs.primemodel.api.platform.PlatformPlayer
import kr.rioxs.primemodel.api.util.Utils.EntityUtil
import net.kyori.adventure.text.Component
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal class ModelNametagImpl(
    private val bone: RenderedBone
) : ModelNametag {
    private companion object {
        private val emptyVector = Vector3f()
        private val emptyTransformation = Transformation(
            Vector3f(-1F / 40F, -0.2F - 1F / 40F, 0F),
            null,
            null,
            null
        )
    }

    private val viewedPlayer = ConcurrentHashMap.newKeySet<UUID>()
    private val display = Display.TextDisplay(
        EntityType.TEXT_DISPLAY,
        MinecraftServer.getServer().overworld()
    ).apply {
        entityData[Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID] = 3
        setTransformation(emptyTransformation)
        billboardConstraints = Display.BillboardConstraints.CENTER
    }
    private val posCache = BoneMovement()
    private var alwaysVisible = false
    private var location = PrimeModel.platform().adapter().zero()

    override fun component(component: Component?) {
        display.text = component?.asVanilla() ?: VanillaComponent.empty()
    }

    override fun teleport(location: PlatformLocation) {
        this.location = location
    }

    override fun alwaysVisible(alwaysVisible: Boolean) {
        this.alwaysVisible = alwaysVisible
    }

    override fun send(player: PlatformPlayer) {
        if (display.text == VanillaComponent.empty()) return
        val hb = bone.group.hitBoxPoint
        val pos = bone.worldPosition(BonePosition(emptyVector, hb, player.uuid()), posCache)
        display.moveTo(Vec3(
            location.x() + pos.x,
            location.y() + pos.y,
            location.z() + pos.z
        ))
        val inPoint = alwaysVisible || EntityUtil.isCustomNameVisible(player.location(), location)
        when {
            inPoint && viewedPlayer.add(player.uuid()) -> bundlerOfNotNull(
                addPacket,
                display.entityData.pack()?.let {
                    ClientboundSetEntityDataPacket(display.id, it)
                }
            )
            inPoint -> bundlerOfNotNull(
                ClientboundEntityPositionSyncPacket(display.id, PositionMoveRotation.of(display), false),
                display.entityData.packDirty()?.let {
                    ClientboundSetEntityDataPacket(display.id, it)
                }
            )
            viewedPlayer.remove(player.uuid()) -> bundlerOf(removePacket)
            else -> null
        }?.send(player)
    }

    override fun remove(bundler: PacketBundler) {
        bundler += removePacket
    }

    private val addPacket get() = ClientboundAddEntityPacket(
        display.id,
        display.uuid,
        display.x,
        display.y,
        display.z,
        display.xRot,
        display.yRot,
        display.type,
        0,
        display.deltaMovement,
        display.yHeadRot.toDouble()
    )

    private val removePacket get() = ClientboundRemoveEntitiesPacket(display.id)
}
