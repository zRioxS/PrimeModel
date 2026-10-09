package kr.rioxs.primemodel.bukkit.compatibility.citizens.command

import kr.rioxs.primemodel.api.animation.Animations.AnimationIterator
import kr.rioxs.primemodel.api.animation.Animations.AnimationModifier
import kr.rioxs.primemodel.api.PrimeModel
import kr.rioxs.primemodel.api.tracker.TrackerUtils.TrackerModifier
import kr.rioxs.primemodel.api.util.Utils.Functions.FloatSupplier
import kr.rioxs.primemodel.bukkit.compatibility.citizens.trait.ModelTrait
import kr.rioxs.primemodel.bukkit.util.wrap
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.command.Arg
import net.citizensnpcs.api.command.Arg.CompletionsProvider
import net.citizensnpcs.api.command.Command
import net.citizensnpcs.api.command.CommandContext
import net.citizensnpcs.api.command.CommandMessages
import net.citizensnpcs.api.npc.NPC
import net.citizensnpcs.api.util.Messaging
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class AnimateCommand {
    @Command(
        aliases = ["npc"],
        usage = "animate <id> <animation> [loop_type] [speed] [player]",
        desc = "",
        modifiers = ["animate"],
        min = 3,
        max = 6,
        permission = "citizens.npc.animate"
    )
    @Suppress("UNUSED")
    fun animate(
        args: CommandContext,
        sender: CommandSender,
        npc: NPC?,
        @Arg(1) id: String,
        @Arg(2) animation: String,
        @Arg(3) loopType: String?,
        @Arg(4) speed: String?,
        @Arg(5) player: String?
    ) {
        val targetNpc = CitizensAPI.getNPCRegistry().getById(id.toIntOrNull() ?: return) ?: return
        val modifier = AnimationModifier.builder()
            .player(player?.let(Bukkit::getPlayer)?.wrap())
            .start(0)
            .end(0)
            .speed(speed?.toFloatOrNull()?.let(FloatSupplier::of))
            .type(loopType?.runCatching {
                AnimationIterator.Type.valueOf(uppercase())
            }?.getOrNull() ?: AnimationIterator.Type.PLAY_ONCE)
            .build()
        PrimeModel.registryOrNull(targetNpc.entity.uniqueId)?.trackers()?.forEach {
            it.animate(animation, modifier)
        }
    }
}

class LimbCommand {
    @Command(
        aliases = ["npc"],
        usage = "limb <id> <model> <animation> [loop_type] [player]",
        desc = "",
        modifiers = ["limb"],
        min = 4,
        max = 6,
        permission = "citizens.npc.animate"
    )
    @Suppress("UNUSED")
    fun animate(
        args: CommandContext,
        sender: CommandSender,
        npc: NPC?,
        @Arg(1) id: String,
        @Arg(2) model: String,
        @Arg(3) animation: String,
        @Arg(4) type: String?,
        @Arg(5) player: String?
    ) {
        val targetNpc = CitizensAPI.getNPCRegistry().getById(id.toIntOrNull() ?: return) ?: return
        val npcEntity = (targetNpc.entity as? Player)?.wrap() ?: return
        val targetPlayer = player?.let(Bukkit::getPlayer)?.wrap()

        val animType = type
            ?.let { value ->
                runCatching {
                    AnimationIterator.Type.valueOf(value.uppercase())
                }.getOrNull()
            }
            ?: AnimationIterator.Type.PLAY_ONCE

        PrimeModel.limb(model)
            .map { renderer ->
                renderer.getOrCreate(npcEntity, TrackerModifier.DEFAULT) { tracker ->
                    if (targetPlayer != null) {
                        tracker.markPlayerForSpawn(targetPlayer)
                    }
                }
            }
            .ifPresent { tracker ->
                val success = tracker.animate(
                    animation,
                    AnimationModifier.builder()
                        .start(0)
                        .player(targetPlayer)
                        .type(animType)
                        .build()
                ) {
                    if (targetPlayer != null) {
                        tracker.unmarkPlayerForSpawn(targetPlayer)
                        tracker.registry().remove(targetPlayer)
                        if (tracker.playerCount() == 0) tracker.close()
                    } else {
                        tracker.close()
                    }
                }

                if (!success) {
                    tracker.close()
                    return@ifPresent
                }

                if (targetPlayer != null && !tracker.isSpawned(targetPlayer)) {
                    tracker.markPlayerForSpawn(targetPlayer)
                    tracker.registry().spawnIfNotSpawned(targetPlayer)
                }
            }
    }
}

class ModelCommand {
    @Command(
        aliases = ["npc"],
        usage = "model [model]",
        desc = "",
        modifiers = ["model"],
        min = 1,
        max = 2,
        permission = "citizens.npc.model"
    )
    @Suppress("UNUSED")
    fun model(args: CommandContext, sender: CommandSender, npc: NPC?, @Arg(1, completionsProvider = TabComplete::class) model: String?) {
        if (npc == null) return Messaging.sendTr(sender, CommandMessages.MUST_HAVE_SELECTED)
        npc.getOrAddTrait(ModelTrait::class.java).renderer = model?.let {
            PrimeModel.modelOrNull(it)
        }
        sender.sendMessage("Set ${npc.name}'s model to $model.")
    }

    private class TabComplete : CompletionsProvider {
        override fun getCompletions(p0: CommandContext?, p1: CommandSender?, p2: NPC?): Collection<String> = PrimeModel.modelKeys()
    }
}
