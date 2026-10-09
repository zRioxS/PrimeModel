package kr.rioxs.primemodel.script


import kr.rioxs.primemodel.api.bone.BoneTags
import kr.rioxs.primemodel.api.bone.BoneTags.BoneName
import kr.rioxs.primemodel.api.PrimeModel
import kr.rioxs.primemodel.api.script.Scripts.AnimationScript
import kr.rioxs.primemodel.api.tracker.EntityTracker
import kr.rioxs.primemodel.api.tracker.Tracker
import kr.rioxs.primemodel.api.tracker.TrackerUpdateAction
import kr.rioxs.primemodel.api.util.Utils.BonePredicate
import kr.rioxs.primemodel.util.toPackName
import kr.rioxs.primemodel.util.toSet

class BrightnessScript(
    val predicate: BonePredicate,
    val block: Int,
    val sky: Int
) : AnimationScript {

    override fun accept(tracker: Tracker) {
        tracker.update(
            TrackerUpdateAction.brightness(block, sky),
            predicate
        )
    }

    override fun isSync(): Boolean = false
}

class ChangePartScript(
    val predicate: BonePredicate,
    newModel: String,
    newPart: BoneTags.BoneName
) : AnimationScript {

    private val model by lazy {
        PrimeModel.modelOrNull(newModel)?.groupByTree(newPart)?.itemStack
    }

    override fun accept(tracker: Tracker) {
        model?.let {
            tracker.update(
                TrackerUpdateAction.itemStack(it),
                predicate
            )
        }
    }

    override fun isSync(): Boolean = false
}

class EnchantScript(
    val predicate: BonePredicate,
    val enchant: Boolean
) : AnimationScript {

    override fun accept(tracker: Tracker) {
        tracker.update(
            TrackerUpdateAction.enchant(enchant),
            predicate
        )
    }

    override fun isSync(): Boolean = false
}

class PartVisibilityScript(
    val predicate: BonePredicate,
    val visible: Boolean
) : AnimationScript {

    override fun accept(tracker: Tracker) {
        tracker.update(
            TrackerUpdateAction.togglePart(visible),
            predicate
        )
    }

    override fun isSync(): Boolean = false
}

class RemapScript(
    model: String,
    map: String?
) : AnimationScript {

    private val newModel by lazy {
        model.toPackName().let {
            PrimeModel.modelOrNull(it)
        }
    }
    private val filter by lazy {
        map?.let {
            PrimeModel.modelOrNull(it.toPackName())?.flatten()?.map { group ->
                group.name()
            }?.toSet()
        }
    }

    override fun accept(tracker: Tracker) {
        val f = filter
        newModel?.run {
            tracker.update(TrackerUpdateAction.perBone {
                (if (f == null || f.contains(it.name())) {
                    groupByTree(it.name())?.itemStack?.let { item ->
                        TrackerUpdateAction.itemStack(item)
                    }
                } else null) ?: TrackerUpdateAction.none()
            })
        }
    }

    override fun isSync(): Boolean = false
}

class TintScript(
    val predicate: BonePredicate,
    val color: Int,
    val damageTint: Boolean
) : AnimationScript {

    override fun accept(tracker: Tracker) {
        if (damageTint && tracker is EntityTracker) {
            tracker.damageTintValue(color)
        } else {
            if (tracker is EntityTracker) tracker.cancelDamageTint()
            tracker.update(
                TrackerUpdateAction.tint(color),
                predicate
            )
        }
    }

    override fun isSync(): Boolean = false
}
