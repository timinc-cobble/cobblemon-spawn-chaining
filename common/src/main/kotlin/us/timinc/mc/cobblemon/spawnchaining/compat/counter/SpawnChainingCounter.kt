package us.timinc.mc.cobblemon.spawnchaining.compat.counter

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import net.minecraft.server.level.ServerPlayer
import us.timinc.mc.cobblemon.counter.CounterMod
import us.timinc.mc.cobblemon.counter.api.CounterTypeRegistry
import us.timinc.mc.cobblemon.counter.extension.getCounterManager
import us.timinc.mc.cobblemon.spawnchaining.SpawnChaining
import us.timinc.mc.cobblemon.timcore.Debugger
import us.timinc.mc.cobblemon.timcore.PokemonRepresentation

object SpawnChainingCounter {
    init {
        SpawnChaining.debugger.debug("Loading Counter compat", true)
    }

    fun getBoost(
        player: ServerPlayer,
        pokemonRep: PokemonRepresentation<PokemonProperties>,
        points: Map<String, Map<String, Float>>,
        debugger: Debugger.Case<SpawnChaining.SpawnChainingConfig>?,
    ): Float {
        val manager = player.getCounterManager()
        var boost = 0F
        for ((counterTypeName, scoreTypeList) in points) {
            for ((scoreTypeName, value) in scoreTypeList) {
                try {
                    val counterType = CounterTypeRegistry.findByType(counterTypeName)
                    val species = pokemonRep.species!!
                    val form = pokemonRep.form!!
                    val statScore = if (scoreTypeName == CounterMod.ScoreTypes.STREAK.type) manager.getStreakScore(
                        counterType, species.resourceIdentifier, form.name
                    ) else manager.getCountScore(counterType, species.resourceIdentifier, form.name)
                    boost += statScore * value
                } catch (e: Exception) {
                    e.message?.let { debugger?.debug(it) }
                }
            }
        }
        return boost
    }
}
