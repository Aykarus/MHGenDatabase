package com.ghstudios.android.features.monsters.detail

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import com.ghstudios.android.data.classes.*
import com.ghstudios.android.data.database.DataManager
import com.ghstudios.android.loggedThread
import com.ghstudios.android.toList

enum class WeaknessRating {
    IMMUNE,
    RESISTS,
    REGULAR,
    WEAK,
    VERY_WEAK
}

data class MonsterWeaknessValue<T>(val type: T, val value: Int) {
    val rating = when(value) {
        0, 1, 2, 3 -> WeaknessRating.RESISTS
        4 -> WeaknessRating.REGULAR
        5 -> WeaknessRating.WEAK
        6, 7 -> WeaknessRating.VERY_WEAK
        else -> throw IllegalArgumentException("Invalid weakness value $value")
    }
}

data class MonsterWeaknessResult(
        var state: String, // editable since under certain conditions it becomes "all states"
        val element: List<MonsterWeaknessValue<ElementStatus>>,
        val status: List<MonsterWeaknessValue<ElementStatus>>,
        val items: List<WeaknessType>
)

/**
 * Represents basic initialization information regarding a monster.
 */
data class MonsterDetailMetadata(
        val id: Long,
        val name: String,
        val hasDamage : Boolean
)

/**
 * A viewmodel for the entirety of monster detail data.
 * This should be attached to the activty or fragment owning the viewpager.
 */
class MonsterDetailViewModel(app : Application) : AndroidViewModel(app) {
    private val dataManager = DataManager.get(app.applicationContext)

    val monsterMetadata = MutableLiveData<MonsterDetailMetadata>()

    val monsterData = MutableLiveData<Monster>()
    val weaknessData = MutableLiveData<List<MonsterWeaknessResult>>()
    val ailmentData = MutableLiveData<List<MonsterAilment>>()
    val habitatData = MutableLiveData<List<Habitat>>()
    val damageData = MutableLiveData<List<MonsterDamage>>()
    val statusData = MutableLiveData<List<MonsterStatus>>()

    var monsterId = -1L

    /**
     * Sets the viewmodel to represent a monster, and loads the viewmodels
     * Does nothing if the data is already loaded.
     */
    fun setMonster(monsterId : Long) {
        if (this.monsterId == monsterId) {
            return
        }

        this.monsterId = monsterId

        loggedThread(name="Monster Loading") {
            val monster = dataManager.getMonster(monsterId)
            val damageList = dataManager.queryMonsterDamageArray(monsterId)
            val statusList = dataManager.queryMonsterStatus(monsterId)

            // load and post metadata and monster first (high priority)
            monsterMetadata.postValue(MonsterDetailMetadata(
                    id=monster.id,
                    name=monster.name,
                    hasDamage = damageList.isNotEmpty() || statusList.isNotEmpty()
            ))
            monsterData.postValue(monster)

            // then load the rest
            ailmentData.postValue(dataManager.queryAilmentsFromId(monsterId).toList { it.ailment })
            habitatData.postValue(dataManager.queryHabitatMonster(monsterId).toList { it.habitat })
            damageData.postValue(damageList)
            statusData.postValue(statusList)

            val weaknessRaw = dataManager.queryMonsterWeaknessArray(monsterId)
            weaknessData.postValue(processWeaknessData(weaknessRaw))
        }
    }

    private fun processWeaknessData(weaknessList: List<MonsterWeakness>): List<MonsterWeaknessResult> {
        if (weaknessList.isEmpty()) {
            return emptyList()
        }

        // internal helper function to calculate top weaknesses
        fun <T> calculateTopWeaknesses(weaknessMap: Map<T, Int>, count: Int): List<MonsterWeaknessValue<T>> {
            val weaknesses = weaknessMap
                    .map { MonsterWeaknessValue(it.key, it.value) }
                    .filter { it.rating != WeaknessRating.RESISTS }
                    .sortedByDescending { it.value }

            // take the top two weaknesses
            val results = weaknesses.take(count).toMutableList()

            if (weaknesses.size > count) {
                // add all weaknesses equivalent to the last taken weakness as well
                results += weaknesses.drop(count).takeWhile { it.value == results.last().value }
            }

            return results
        }

        val stateResults = weaknessList.map {
            val topElement = calculateTopWeaknesses(it.elementWeaknesses, 2)
            val topStatus = calculateTopWeaknesses(it.statusWeaknesses, 1)
            MonsterWeaknessResult(
                    state = it.state ?: "",
                    element = topElement,
                    status = topStatus,
                    items = it.vulnerableTraps + it.vulnerableBombs)
        }

        // detect if any categories are equivalent, so that we don't show them multiple times
        val stateSequence = stateResults.asSequence()
        val elementsDiffer = stateSequence.map { it.element }.distinct().count() > 1
        val statusesDiffer = stateSequence.map { it.status }.distinct().count() > 1
        val itemsDiffer = stateSequence.map { it.items }.distinct().count() > 1

        // If all categories are equivalent, we need to remove alternative states
        // if there is more than one state, we'll also have to rename the first one
        val anyDiffer = elementsDiffer || statusesDiffer || itemsDiffer
        if (!anyDiffer && stateResults.size > 1) {
            // todo: translate
            stateResults.first().state = "All States"
        }

        return when(anyDiffer) {
            true -> stateResults
            false -> stateResults.take(1)
        }
    }
}