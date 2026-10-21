package us.timinc.mc.cobblemon.spawnchaining

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.platform.events.PlatformEvents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import org.apache.logging.log4j.LogManager
import us.timinc.mc.cobblemon.spawnchaining.config.ContextConfig
import us.timinc.mc.cobblemon.spawnchaining.handler.CaptureChainer
import us.timinc.mc.cobblemon.spawnchaining.handler.KoChainer
import us.timinc.mc.cobblemon.spawnchaining.handler.ReplacementHandler
import us.timinc.mc.cobblemon.timcore.*
import us.timinc.mc.cobblemon.timcore.pokemonpropertyextractor.CustomPropertiesExtractor

const val MOD_ID: String = "spawn_chaining"

object SpawnChaining : AbstractMod<SpawnChaining.SpawnChainingConfig>(MOD_ID, SpawnChainingConfig::class.java) {
    class SpawnChainingConfig : AbstractConfig() {
        val onCapture: Boolean = true
        val onKo: Boolean = true
        val initialChance: Float = 1.0F
        val points: Map<String, Map<String, Float>> = mapOf()
        val notifyPlayer: Boolean = true
        val breakOnSuccess: Boolean = true
        val chainingBlacklist = mutableSetOf(
            "labels=legendary,mythical,ultra_beast,paradox any_label"
        )
        val chainingWhitelist = mutableSetOf<String>()
        val overrideBlacklist = mutableSetOf(
            "labels=legendary,mythical,ultra_beast,paradox,pseudo_legendary any_label"
        )
        val overrideWhitelist = mutableSetOf<String>()
        val assumePlayerSpawnered: Boolean = true
        val treatActivatedHabitatSpawnsAsPlayerSpawns: Boolean = true
        val levelModRange: IntRange = -5..5
        val levelModMaxRange: IntRange = -15..15
    }

    object TranslationComponents {
        fun chained(): MutableComponent = Component.translatable("spawn_chaining.feedback.chained")
    }

    object DataKeys {
        const val LEVEL_MOD = "spawn_chaining:level_mod"
        const val SPAWN_CHAINING = "spawn_chaining:chaining"

        object Triggers {
            const val CAPTURE = "capture"
            const val KO = "ko"
        }
    }

    val contexts = listOf(TimCore.DataKeys.SpawnerTypes.PLAYER, TimCore.DataKeys.SpawnerTypes.FISHING)
    val triggers = listOf(DataKeys.Triggers.CAPTURE, DataKeys.Triggers.KO)
    val contextConfigs: Map<String, Map<String, ContextConfig>> =
        contexts.fold(mapOf()) { contextConfigs, context ->
            val triggerConfigs = triggers.fold(mapOf<String, ContextConfig>()) { triggerConfigs, trigger ->
                triggerConfigs.plus(
                    trigger to ConfigBuilder.load(
                        ContextConfig::class.java,
                        "$MOD_ID/${trigger}_${context.split(":")[1]}"
                    )
                )
            }
            contextConfigs.plus(context to triggerConfigs)
        }

    fun getContextConfig(context: String, trigger: String) = contextConfigs[context]?.get(trigger)

    object CustomPokemonProperties {
        val LEVEL_MOD = CustomFloatProperty(DataKeys.LEVEL_MOD)
    }

    object FeatureExtractors {
        val CUSTOM_PROPERTY_CHAINING = CustomPropertiesExtractor(DataKeys.SPAWN_CHAINING)
    }

    private fun warnAboutMissingCounter() {
        if (TimCore.isModInstalled("cobbled_counter")) return

        val affectedConfigs = mutableListOf<String>()
        if (config.points.isNotEmpty()) affectedConfigs.add("config/$MOD_ID.json")
        contextConfigs.forEach { (context, triggerConfigs) ->
            triggerConfigs.forEach { (trigger, contextConfig) ->
                if (!contextConfig.points.isNullOrEmpty()) {
                    affectedConfigs.add("config/$MOD_ID/${trigger}_${context.substringAfter(':')}.json")
                }
            }
        }
        if (affectedConfigs.isEmpty()) return

        LogManager.getLogger(MOD_ID).warn(
            "Counter is not installed, but points bonuses are configured in: ${affectedConfigs.joinToString()}. " +
                "Those bonuses will be ignored; base chaining chances still apply."
        )
    }

    override fun registerEventHandlers() {
        super.registerEventHandlers()
        PlatformEvents.SERVER_STARTED.subscribe { warnAboutMissingCounter() }
        RELOAD_CONFIG.subscribe { warnAboutMissingCounter() }
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, CaptureChainer::handle)
        TimCoreEvents.BATTLE_FAINTED_PVW_WILD.subscribe(Priority.NORMAL, KoChainer::handle)
        TimCoreEvents.SPAWNING_SPAWN.subscribe(Priority.HIGHEST, ReplacementHandler::handle)
    }
}
