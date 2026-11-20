package com.breakerch.yogiri.entity;

import com.breakerch.yogiri.compat.PehkuiCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.util.List;

public class TheEndEntity extends Mob {
    private int erasureCooldown = 0;

    public TheEndEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setCustomName(Component.literal("YOGIRI — THE END"));
        setCustomNameVisible(true);
        setPersistenceRequired();
        setInvulnerable(true);
        setNoAi(true);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 2048.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 2048.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 2048.0D);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        // Persistent and immune to ordinary damage/status effects.
        setInvulnerable(true);
        setNoAi(true);
        if (tickCount % 20 == 0) {
            PehkuiCompat.applyScale(this, 1.35F);
        }

        if (--erasureCooldown <= 0 && level() instanceof ServerLevel serverLevel) {
            erasureCooldown = 10;
            List<LivingEntity> nearby = serverLevel.getEntitiesOfClass(
                    LivingEntity.class,
                    getBoundingBox().inflate(48.0D),
                    target -> target != this && target.isAlive() && !(target instanceof net.minecraft.world.entity.player.Player)
            );
            for (LivingEntity target : nearby) {
                // Discard bypasses enormous health pools and normal damage immunity.
                // Player entities are deliberately excluded.
                target.discard();
            }
        }
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) {
        return false;
    }
}
