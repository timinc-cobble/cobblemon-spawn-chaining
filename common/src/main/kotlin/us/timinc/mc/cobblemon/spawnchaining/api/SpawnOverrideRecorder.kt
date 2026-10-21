package us.timinc.mc.cobblemon.spawnchaining.api

import com.cobblemon.mod.common.api.pokemon.PokemonPropertyExtractor
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import net.minecraft.server.level.ServerPlayer
import us.timinc.mc.cobblemon.spawnchaining.MOD_ID
import us.timinc.mc.cobblemon.spawnchaining.SpawnChaining
import us.timinc.mc.cobblemon.spawnchaining.extension.constrain
import us.timinc.mc.cobblemon.spawnchaining.store.SpawnOverride
import us.timinc.mc.cobblemon.timcore.*

interface SpawnOverrideRecorder {
    fun record(
        player: ServerPlayer,
        pokemon: Pokemon,
        trigger: String,
        debugger: Debugger.Case<SpawnChaining.SpawnChainingConfig>,
    ): Boolean {
        if (!LimitedList.PokemonMatcherList.matchesList(
                pokemon,
                SpawnChaining.config.chainingWhitelist.map(PokemonMatcher::parse).toSet(),
                SpawnChaining.config.chainingBlacklist.map(PokemonMatcher::parse).toSet()
            )
        ) {
            debugger.debug("${pokemon.getIdentifier()} is prohibited by the blacklist/whitelist; not recording.")
            return false
        }

        var spawnCause = pokemon.getSpawnCause()
        if (spawnCause == null) {
            debugger.debug("No spawn cause recorded.")
            if (SpawnChaining.config.assumePlayerSpawnered) {
                debugger.debug("Assuming player spawnered.")
                spawnCause = TimCore.DataKeys.SpawnerTypes.PLAYER
            } else {
                debugger.debug("It hurt itself in its confusion (returning).")
                return false
            }
        }
        if (spawnCause == TimCore.DataKeys.SpawnerTypes.HABITAT) {
            if (!SpawnChaining.config.treatActivatedHabitatSpawnsAsPlayerSpawns) {
                debugger.debug("Activated habitat spawns are ignored for chaining.")
                return false
            }
            debugger.debug("Treating activated habitat spawn as player spawnered.")
            spawnCause = TimCore.DataKeys.SpawnerTypes.PLAYER
        }
        val spawnCauseId = spawnCause.asIdentifierDefaultingNamespace(MOD_ID)

        val props = pokemon.createPokemonProperties(
            PokemonPropertyExtractor.SPECIES,
            PokemonPropertyExtractor.LEVEL,
            SpawnChaining.FeatureExtractors.CUSTOM_PROPERTY_CHAINING
        )

        val previousLevelMod = (SpawnChaining.CustomPokemonProperties.LEVEL_MOD.getValue(pokemon)?.toInt() ?: 0)
        debugger.debug("Previous level mod was $previousLevelMod.")
        val contextConfig = SpawnChaining.getContextConfig(spawnCauseId.toString(), trigger)
        val levelModRoll = (contextConfig?.levelModRange ?: SpawnChaining.config.levelModRange).random()
        debugger.debug("Rolled a new level mod of $levelModRoll.")
        val levelMod = (contextConfig?.levelModMaxRange ?: SpawnChaining.config.levelModMaxRange).constrain(levelModRoll + previousLevelMod)
        props.level = pokemon.level + levelMod - previousLevelMod
        debugger.debug("Updated props level to ${props.level}.")

        SpawnOverride.record(player, props, spawnCause.asIdentifierDefaultingNamespace(MOD_ID), levelMod, trigger)
        debugger.debug("Recorded props against player.")

        return true
    }
}
