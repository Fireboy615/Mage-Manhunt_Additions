package net.fireboy.mageadditions.mixin;

import java.util.Map;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes the five Iron's mage mobs through the exact Manhunt entity loot tables
 * while Loot Changes is enabled. Injecting at dropFromLootTable keeps vanilla/
 * Iron's equipment and custom-death drops intact; only the entity loot-table
 * portion is replaced.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityLootOverrideMixin {
    private static final Map<ResourceLocation, ResourceKey<LootTable>> MANHUNT_ENTITY_TABLES = Map.of(
            ironsEntity("apothecarist"), manhuntTable("apothecarist"),
            ironsEntity("archevoker"), manhuntTable("archevoker"),
            ironsEntity("cryomancer"), manhuntTable("cryomancer"),
            ironsEntity("priest"), manhuntTable("priest"),
            ironsEntity("pyromancer"), manhuntTable("pyromancer")
    );

    @Shadow protected Player lastHurtByPlayer;

    @Inject(method = "dropFromLootTable", at = @At("HEAD"), cancellable = true)
    private void mageadditions$useManhuntEntityLoot(DamageSource source, boolean recentlyHit, CallbackInfo ci) {
        if (!CastTimeOverrides.lootChangesEnabled()) {
            return;
        }

        LivingEntity self = (LivingEntity) (Object) this;
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(self.getType());
        ResourceKey<LootTable> customTableKey = MANHUNT_ENTITY_TABLES.get(entityId);
        if (customTableKey == null || !(self.level() instanceof ServerLevel level)) {
            return;
        }

        LootTable table = level.getServer().reloadableRegistries().getLootTable(customTableKey);
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, self)
                .withParameter(LootContextParams.ORIGIN, self.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity());

        if (recentlyHit && lastHurtByPlayer != null) {
            params = params
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, lastHurtByPlayer)
                    .withLuck(lastHurtByPlayer.getLuck());
        }

        LootParams lootParams = params.create(LootContextParamSets.ENTITY);
        table.getRandomItems(lootParams, self.getLootTableSeed(), stack -> self.spawnAtLocation(stack));
        ci.cancel();
    }

    private static ResourceLocation ironsEntity(String name) {
        return ResourceLocation.fromNamespaceAndPath("irons_spellbooks", name);
    }

    private static ResourceKey<LootTable> manhuntTable(String name) {
        return ResourceKey.create(
                Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath("mageadditions", "entity_overrides/" + name)
        );
    }
}
