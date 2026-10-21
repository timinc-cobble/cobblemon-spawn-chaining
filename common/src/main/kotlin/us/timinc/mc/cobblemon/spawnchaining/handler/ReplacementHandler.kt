package us.timinc.mc.cobblemon.spawnchaining.handler

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnAction
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import kotlin.random.Random.Default.nextFloat
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import us.timinc.mc.cobblemon.counter.api.CounterTypeRegistry
import us.timinc.mc.cobblemon.counter.CounterMod
import us.timinc.mc.cobblemon.counter.extension.getCounterManager
import us.timinc.mc.cobblemon.spawnchaining.MOD_ID
import us.timinc.mc.cobblemon.spawnchaining.SpawnChaining
import us.timinc.mc.cobblemon.spawnchaining.store.SpawnOverride
import us.timinc.mc.cobblemon.timcore.AbstractHandler
import us.timinc.mc.cobblemon.timcore.Debugger
import us.timinc.mc.cobblemon.timcore.event.SpawningInfluenceEvents
import us.timinc.mc.cobblemon.timcore.getType
import us.timinc.mc.cobblemon.timcore.LimitedList
import us.timinc.mc.cobblemon.timcore.PokemonMatcher
import us.timinc.mc.cobblemon.timcore.PokemonRepresentation
import us.timinc.mc.cobblemon.timcore.TimCore

object ReplacementHandler : AbstractHandler<SpawningInfluenceEvents.Spawn>() {
    override fun handle(evt: SpawningInfluenceEvents.Spawn) {
        val action = evt.action
        val entity = evt.entity
        if (action !is PokemonSpawnAction || entity !is PokemonEntity) return

        val contextType = when (val type = evt.position.spawner.getType()) {
            TimCore.DataKeys.SpawnerTypes.PLAYER, TimCore.DataKeys.SpawnerTypes.FISHING -> type
            TimCore.DataKeys.SpawnerTypes.HABITAT -> {
                if (!SpawnChaining.config.treatActivatedHabitatSpawnsAsPlayerSpawns) return
                TimCore.DataKeys.SpawnerTypes.PLAYER
            }
            else -> return
        }
        val context = contextType.asIdentifierDefaultingNamespace(MOD_ID)

        val debugger = SpawnChaining.debugger.getCaseDebugger()

        val overriddenPokemonRep = PokemonRepresentation.FromProperties(action.props)
        if (!LimitedList.PokemonMatcherList.matchesList(
                overriddenPokemonRep.getPokemon(),
                SpawnChaining.config.overrideWhitelist.map(PokemonMatcher::parse).toSet(),
                SpawnChaining.config.overrideBlacklist.map(PokemonMatcher::parse).toSet()
            )
        ) {
            debugger.debug("Invalid Pokemon to override, per the whitelist/blacklist.")
            return
        }

        val player = action.spawnablePosition.cause.entity as? ServerPlayer ?: return
        debugger.debug("Attempting a replacement influence for ${player.name.string} on the $context context.")

        val override = SpawnOverride.find(player, context)

        if (override == null) {
            debugger.debug("No override found.")
            return
        }
        debugger.debug("Found an override of ${override.properties.asString()} with a level mod of ${override.levelMod}.")

        val overrideChance =
            getOverrideChance(
                player,
                PokemonRepresentation.FromProperties(override.properties),
                context,
                override.trigger,
                debugger
            )
        debugger.debug("Has a chance of $overrideChance.")
        val overrideRoll = nextFloat()
        debugger.debug("Rolled a $overrideRoll.")
        if (overrideRoll > overrideChance) {
            debugger.debug("Failed the roll.")
            return
        }

        entity.pokemon = override.properties.create()
        debugger.debug("Switched entity's Pokemon to override's.")

        if (SpawnChaining.config.notifyPlayer) {
            player.sendSystemMessage(SpawnChaining.TranslationComponents.chained())
        }

        val breakOnSuccess = SpawnChaining.getContextConfig(override.context.toString(), override.trigger)?.breakOnSuccess
            ?: SpawnChaining.config.breakOnSuccess
        if (breakOnSuccess) {
            SpawnOverride.remove(player)
            debugger.debug("Broke the spawn override.")
        }

        SpawnChaining.CustomPokemonProperties.LEVEL_MOD.entityApplicator(entity, override.levelMod.toFloat())
        debugger.debug("Saved the level mod onto the Pokemon.")
    }

    private fun getOverrideChance(
        player: ServerPlayer,
        pokemonRep: PokemonRepresentation<PokemonProperties>,
        context: ResourceLocation,
        trigger: String,
        debugger: Debugger.Case<SpawnChaining.SpawnChainingConfig>?,
    ): Float {
        val manager = player.getCounterManager()

        var boost = 0F
        val points = SpawnChaining.getContextConfig(context.toString(), trigger)?.points ?: SpawnChaining.config.points
        for ((counterTypeName, scoreTypeList) in points) {
            for ((scoreTypeName, value) in scoreTypeList) {
                try {
                    val counterType = CounterTypeRegistry.findByType(counterTypeName)
                    val species = pokemonRep.species!!
                    val form = pokemonRep.form!!
                    val statScore = if (scoreTypeName == CounterMod.ScoreTypes.STREAK.type) manager.getStreakScore(
                        counterType,
                        species.resourceIdentifier,
                        form.name
                    ) else manager.getCountScore(counterType, species.resourceIdentifier, form.name)
                    boost += statScore * value
                } catch (e: Exception) {
                    e.message?.let { debugger?.debug(it) }
                }
            }
        }

        val initialChance =
            SpawnChaining.getContextConfig(context.toString(), trigger)?.initialChance ?: SpawnChaining.config.initialChance
        return initialChance + boost
    }
}
