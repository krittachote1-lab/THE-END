package com.example.yinyang.entity;

import com.example.yinyang.YinYangMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import virtuoel.pehkui.api.ScaleTypes;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class GodOfYinyangEntity extends Monster implements GeoEntity {

    /** Size multiplier relative to a normal player. */
    private static final float SCALE = 5.0F;
    /** Range at which the boss erases its current target every tick. */
    private static final double ERASE_RANGE = 24.0D;
    /** Any position beyond this (for example x=1,000,000,000) is ignored. */
    private static final double TELEPORT_LIMIT = 30_000_000.0D;
    /** Judgement (ultimate): ticks between judgements, ticks of charging, erase radius. */
    private static final int JUDGEMENT_INTERVAL = 240;
    private static final int JUDGEMENT_CHARGE = 60;
    private static final double JUDGEMENT_RADIUS = 48.0D;
    /** Punch: range and ticks between punches (visual and impact). */
    private static final double PUNCH_RANGE = 5.0D;
    private static final int PUNCH_COOLDOWN = 8;
    /** Cinematic intro: length in ticks and how far away players get the camera. */
    private static final int INTRO_TICKS = 100;
    private static final double INTRO_RANGE = 96.0D;
    /** Every living boss is registered here so a watchdog can bring it back if it vanishes. */
    private static final List<Anchor> ANCHORS = new CopyOnWriteArrayList<>();

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.literal("God Of Yinyang ?/?"),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Level spawnLevel;
    private int judgementCooldown = 200;
    private int chargeTicks = -1;
    private int punchCooldown = 0;
    private boolean introDone = false;
    private int introTicks = 0;
    private Marker cameraMarker;
    private Anchor anchor;

    public GodOfYinyangEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.spawnLevel = level;
        this.moveControl = new FlyingMoveControl(this, 10, true);
        this.navigation = new FlyingPathNavigation(this, level);
        this.xpReward = 0;
        ScaleTypes.BASE.getScaleData(this).setScale(SCALE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1024.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MoveTowardsTargetGoal(this, 1.0D, 64.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true,
                target -> target.isAlive() && !(target instanceof GodOfYinyangEntity)));
    }

    // ---------------------------------------------------------------
    // Save data: remembers whether the intro has already played
    // ---------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IntroDone", this.introDone);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.introDone = tag.getBoolean("IntroDone");
    }

    // ---------------------------------------------------------------
    // Invulnerability
    // ---------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    /** Death sequence is blocked, so the boss never dies. */
    @Override
    public void die(DamageSource source) {
        System.out.println("[God Of Yinyang] die() blocked");
        super.setHealth(this.getMaxHealth());
    }

    /** Health is always full, whatever writes to it. */
    @Override
    public void setHealth(float health) {
        super.setHealth(this.getMaxHealth());
    }

    @Override
    public float getHealth() {
        return this.getMaxHealth();
    }

    @Override
    public void checkDespawn() {
        // never despawns
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** Kill and discard requests are refused, so the boss stays in the world. */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            System.out.println("[God Of Yinyang] removal blocked: " + reason);
            return;
        }
        super.remove(reason);
    }

    /** Teleports far outside the world are ignored. Normal walking and /summon still work. */
    @Override
    public void setPos(double x, double y, double z) {
        if (isFarAway(x, z)) {
            System.out.println("[God Of Yinyang] teleport blocked");
            return;
        }
        super.setPos(x, y, z);
    }

    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        if (isFarAway(x, z)) {
            System.out.println("[God Of Yinyang] teleport blocked");
            return;
        }
        super.moveTo(x, y, z, yRot, xRot);
    }

    private static boolean isFarAway(double x, double z) {
        return Math.abs(x) > TELEPORT_LIMIT || Math.abs(z) > TELEPORT_LIMIT;
    }

    private void restoreTo(Vec3 p) {
        super.setPos(p.x, p.y, p.z);
    }

    /** No knockback from any source. */
    public void knockback(double strength, double x, double z) {
    }

    /** Not pushed by anything. */
    public void push(double x, double y, double z) {
    }

    public void push(Entity entity) {
    }

    /** Huge velocity values (fling attempts) are dropped. Normal flight is unaffected. */
    @Override
    public void setDeltaMovement(Vec3 motion) {
        if (motion.lengthSqr() > 9.0D) {
            return;
        }
        super.setDeltaMovement(motion);
    }

    // ---------------------------------------------------------------
    // Watchdog: brings the boss back if it vanishes by any route
    // ---------------------------------------------------------------

    public static void watchdog(ServerLevel server) {
        for (Anchor a : ANCHORS) {
            if (a.level != server) {
                continue;
            }
            Entity found = server.getEntity(a.uuid);
            if (found instanceof GodOfYinyangEntity boss && !boss.isRemoved()) {
                if (isFarAway(boss.getX(), boss.getZ())) {
                    boss.restoreTo(a.pos);
                }
                continue;
            }
            // Missing. Only restore if the area is loaded, otherwise it may just be unloaded.
            BlockPos at = new BlockPos((int) Math.floor(a.pos.x), (int) Math.floor(a.pos.y), (int) Math.floor(a.pos.z));
            if (!server.isLoaded(at)) {
                continue;
            }
            ANCHORS.remove(a);
            GodOfYinyangEntity copy = YinYangMod.GOD_OF_YINYANG.get().create(server);
            if (copy == null) {
                continue;
            }
            copy.introDone = true;
            copy.moveTo(a.pos.x, a.pos.y, a.pos.z, 0.0F, 0.0F);
            server.addFreshEntity(copy);
            System.out.println("[God Of Yinyang] missing boss restored");
        }
    }

    private void updateAnchor(ServerLevel server) {
        if (this.anchor == null) {
            this.anchor = new Anchor(server, this.getUUID(), this.position());
            ANCHORS.add(this.anchor);
        }
        this.anchor.uuid = this.getUUID();
        if (isFarAway(this.getX(), this.getZ())) {
            // Position was changed without going through setPos(): move it back.
            this.restoreTo(this.anchor.pos);
        } else {
            this.anchor.pos = this.position();
        }
    }

    private static final class Anchor {
        final ServerLevel level;
        UUID uuid;
        Vec3 pos;

        Anchor(ServerLevel level, UUID uuid, Vec3 pos) {
            this.level = level;
            this.uuid = uuid;
            this.pos = pos;
        }
    }

    // ---------------------------------------------------------------
    // Behaviour
    // ---------------------------------------------------------------

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.spawnLevel instanceof ServerLevel server)) {
            return;
        }

        // Boss bar is visible to everyone in this dimension from the moment it spawns.
        for (ServerPlayer player : server.players()) {
            this.bossEvent.addPlayer(player);
        }

        updateAnchor(server);

        // The cinematic intro runs first; nothing else happens until it ends.
        if (!introDone) {
            runIntro(server);
            return;
        }

        // Flies only while in combat, otherwise it falls to the ground.
        LivingEntity target = this.getTarget();
        boolean fighting = target != null && target.isAlive();
        this.setNoGravity(fighting);

        // Punch when the target is close.
        if (punchCooldown > 0) {
            punchCooldown--;
        }
        if (fighting && punchCooldown == 0 && this.distanceToSqr(target) <= PUNCH_RANGE * PUNCH_RANGE) {
            punch(server, target);
            punchCooldown = PUNCH_COOLDOWN;
        }

        // No cooldown: erases the current target every tick while it is in range.
        if (fighting && this.distanceToSqr(target) <= ERASE_RANGE * ERASE_RANGE) {
            erase(server, target);
        }

        // Judgement (ultimate attack): charge, then erase everything nearby at once.
        if (chargeTicks >= 0) {
            chargeEffect(server, chargeTicks);
            chargeTicks++;
            if (chargeTicks >= JUDGEMENT_CHARGE) {
                judgement(server);
                chargeTicks = -1;
                judgementCooldown = JUDGEMENT_INTERVAL;
            }
        } else if (judgementCooldown > 0) {
            judgementCooldown--;
        } else if (fighting) {
            chargeTicks = 0;
        }
    }

    // ---------------------------------------------------------------
    // Cinematic intro: a camera orbits the boss while the yin-yang forms
    // ---------------------------------------------------------------

    private void runIntro(ServerLevel server) {
        if (introTicks == 0) {
            beginIntro(server);
        }
        playSpawnRitual(server, introTicks);
        orbitCamera(introTicks);
        introTicks++;
        if (introTicks >= INTRO_TICKS) {
            endIntro(server);
        }
    }

    private void beginIntro(ServerLevel server) {
        cameraMarker = EntityType.MARKER.create(server);
        if (cameraMarker != null) {
            cameraMarker.moveTo(this.getX(), this.getY() + 6.0D, this.getZ() + 12.0D, 0.0F, 0.0F);
            server.addFreshEntity(cameraMarker);
        }
        for (ServerPlayer player : server.players()) {
            if (player.distanceToSqr(this) > INTRO_RANGE * INTRO_RANGE) {
                continue;
            }
            if (cameraMarker != null) {
                player.setCamera(cameraMarker);
            }
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 30));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("God Of Yinyang")));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("The Yin-Yang Descends")));
        }
        server.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0F, 0.6F);
    }

    private void orbitCamera(int t) {
        if (cameraMarker == null) {
            return;
        }
        double progress = t / (double) INTRO_TICKS;
        double angle = progress * Math.PI * 1.5D;
        double radius = 12.0D - 4.0D * progress;
        double cx = this.getX();
        double cy = this.getY() + 4.5D;
        double cz = this.getZ();
        double camX = cx + Math.cos(angle) * radius;
        double camY = cy + 1.5D;
        double camZ = cz + Math.sin(angle) * radius;
        double dx = cx - camX;
        double dy = cy - camY;
        double dz = cz - camZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        cameraMarker.moveTo(camX, camY, camZ, yaw, pitch);
    }

    private void endIntro(ServerLevel server) {
        for (ServerPlayer player : server.players()) {
            player.setCamera(player);
        }
        if (cameraMarker != null) {
            cameraMarker.discard();
            cameraMarker = null;
        }
        introDone = true;
    }

    // ---------------------------------------------------------------
    // Erasing
    // ---------------------------------------------------------------

    /** Punch: arm swing and impact burst on the target. The erase itself happens in erase(). */
    private void punch(ServerLevel server, LivingEntity target) {
        this.swing(InteractionHand.MAIN_HAND);
        Vec3 hit = target.position().add(0, target.getBbHeight() / 2.0D, 0);
        server.sendParticles(ParticleTypes.EXPLOSION, hit.x, hit.y, hit.z, 2, 0.3D, 0.3D, 0.3D, 0.0D);
        server.sendParticles(ParticleTypes.SWEEP_ATTACK, hit.x, hit.y, hit.z, 3, 0.4D, 0.4D, 0.4D, 0.0D);
        server.playSound(null, hit.x, hit.y, hit.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    private void erase(ServerLevel server, LivingEntity victim) {
        Vec3 center = victim.position().add(0, victim.getBbHeight() / 2.0D, 0);
        server.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 30, 0.5D, 0.8D, 0.5D, 0.15D);
        server.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 30, 0.5D, 0.8D, 0.5D, 0.1D);
        server.playSound(null, center.x, center.y, center.z,
                SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.5F, 1.2F);

        if (victim instanceof Player player) {
            // Setting health to 0 and calling die() directly skips totems.
            player.setHealth(0.0F);
            player.die(this.damageSources().mobAttack(this));
        } else {
            eraseMob(victim);
        }
    }

    /**
     * Removes a mob by every route in turn: discard, remove, die, then throw it into the void
     * (the game's own way of deleting things that fall out of the world). Each step is logged.
     */
    private void eraseMob(LivingEntity victim) {
        victim.discard();
        if (!victim.isRemoved()) {
            victim.remove(Entity.RemovalReason.KILLED);
        }
        if (!victim.isRemoved()) {
            victim.setHealth(0.0F);
            victim.die(this.damageSources().mobAttack(this));
        }
        if (!victim.isRemoved()) {
            victim.setPos(victim.getX(), -10_000.0D, victim.getZ());
            victim.discard();
        }
        System.out.println("[God Of Yinyang] erase " + EntityType.getKey(victim.getType())
                + " removed=" + victim.isRemoved());
    }

    // ---------------------------------------------------------------
    // Judgement (ultimate attack)
    // ---------------------------------------------------------------

    /** Charging: a yin-yang ring closes in on the boss and the sound builds. */
    private void chargeEffect(ServerLevel server, int t) {
        double cx = this.getX();
        double cy = this.getY() + 4.5D;
        double cz = this.getZ();
        double progress = t / (double) JUDGEMENT_CHARGE;
        double r = 14.0D * (1.0D - progress) + 2.0D;
        double spin = t * 0.5D;

        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2 / 32;
            ParticleOptions particle = (i % 2 == 0) ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK;
            spawnRotated(server, particle, cx, cy, cz, Math.cos(a) * r, Math.sin(a) * r, spin);
        }
        if (t % 6 == 0) {
            server.playSound(null, cx, cy, cz, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE,
                    2.0F, 0.5F + (float) progress);
        }
    }

    /** Release: shockwave, lightning on every living target in range, and all of them erased at once. */
    private void judgement(ServerLevel server) {
        Vec3 core = this.position().add(0, 4.5D, 0);
        server.playSound(null, core.x, core.y, core.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4.0F, 0.6F);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, core.x, core.y, core.z, 8, 3.0D, 3.0D, 3.0D, 0.0D);

        for (int ring = 1; ring <= 24; ring++) {
            double radius = ring * 2.0D;
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24;
                server.sendParticles(ParticleTypes.END_ROD,
                        core.x + Math.cos(a) * radius, this.getY() + 0.2D, core.z + Math.sin(a) * radius,
                        1, 0, 0, 0, 0);
            }
        }

        AABB area = this.getBoundingBox().inflate(JUDGEMENT_RADIUS);
        List<LivingEntity> victims = server.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != this && e.isAlive() && !(e instanceof GodOfYinyangEntity));

        for (LivingEntity victim : victims) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
            if (bolt != null) {
                bolt.moveTo(victim.getX(), victim.getY(), victim.getZ());
                bolt.setVisualOnly(true);
                server.addFreshEntity(bolt);
            }
            erase(server, victim);
        }
    }

    // ---------------------------------------------------------------
    // Spawn ritual: a rotating yin-yang made of particles
    // ---------------------------------------------------------------

    private void playSpawnRitual(ServerLevel server, int tick) {
        double cx = this.getX();
        double cy = this.getY() + 4.5D;
        double cz = this.getZ();

        if (tick == 0) {
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 1, 0, 0, 0, 0);
        }

        double spin = tick * 0.2D;
        double r = 3.5D;

        int ringPoints = 40;
        for (int i = 0; i < ringPoints; i++) {
            double a = i * Math.PI * 2 / ringPoints;
            double x = Math.cos(a) * r;
            double y = Math.sin(a) * r;
            ParticleOptions particle = x >= 0 ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK;
            spawnRotated(server, particle, cx, cy, cz, x, y, spin);
        }

        int dotPoints = 12;
        for (int i = 0; i < dotPoints; i++) {
            double a = i * Math.PI * 2 / dotPoints;
            double dx = Math.cos(a) * r / 6.0D;
            double dy = Math.sin(a) * r / 6.0D;
            spawnRotated(server, ParticleTypes.END_ROD, cx, cy, cz, dx, dy + r / 2.0D, spin);
            spawnRotated(server, ParticleTypes.SQUID_INK, cx, cy, cz, dx, dy - r / 2.0D, spin);
        }

        int flairPoints = 16;
        for (int i = 0; i < flairPoints; i++) {
            double a = i * Math.PI * 2 / flairPoints;
            spawnRotated(server, ParticleTypes.ENCHANT, cx, cy, cz,
                    Math.cos(a) * r * 1.3D, Math.sin(a) * r * 1.3D, -spin);
        }
    }

    /** Places one particle at a point on the yin-yang plane, which is rotated around the vertical axis. */
    private static void spawnRotated(ServerLevel server, ParticleOptions particle,
                                     double cx, double cy, double cz,
                                     double localX, double localY, double spin) {
        double wx = cx + localX * Math.cos(spin);
        double wz = cz - localX * Math.sin(spin);
        server.sendParticles(particle, wx, cy + localY, wz, 1, 0, 0, 0, 0);
    }

    // ---------------------------------------------------------------
    // GeckoLib animation
    // ---------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state ->
                state.setAndContinue(this.getAttackAnim(0.0F) > 0.0F
                        ? RawAnimation.begin().thenLoop("punch")
                        : this.getTarget() != null
                                ? RawAnimation.begin().thenLoop("attack")
                                : RawAnimation.begin().thenLoop("idle"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
