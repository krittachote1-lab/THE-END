package com.example.yinyang.entity;

import com.example.yinyang.YinYangMod;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Yogiri Takatou, THE END form. Cannot be damaged, killed, removed, teleported, pushed
 * or affected by potions. Erases whatever it targets, fills the world with eyes, and
 * ends everything in a finale every few seconds.
 */
public class YogiriTakatouEntity extends Monster implements GeoEntity {

    private static final float SCALE = 6.0F;
    private static final double ERASE_RANGE = 48.0D;
    private static final double PUNCH_RANGE = 6.0D;
    private static final int PUNCH_COOLDOWN = 6;
    private static final double TELEPORT_LIMIT = 30_000_000.0D;
    private static final double VOID_LIMIT = -100.0D;
    private static final int INTRO_TICKS = 160;
    private static final double INTRO_RANGE = 128.0D;
    private static final int FINALE_INTERVAL = 240;
    private static final int FINALE_CHARGE = 80;
    private static final double FINALE_RADIUS = 80.0D;
    private static final int MAX_EYES = 60;
    private static final List<Anchor> ANCHORS = new CopyOnWriteArrayList<>();

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.literal("Yogiri Takatou ?/?"),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Level spawnLevel;
    private final List<Eye> eyes = new ArrayList<>();
    private boolean introDone = false;
    private int introTicks = 0;
    private Marker cameraMarker;
    private Anchor anchor;
    private int finaleCooldown = 200;
    private int finaleCharge = -1;
    private int punchCooldown = 0;

    public YogiriTakatouEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.spawnLevel = level;
        this.moveControl = new FlyingMoveControl(this, 10, true);
        this.navigation = new FlyingPathNavigation(this, level);
        this.xpReward = 0;
        ScaleTypes.BASE.getScaleData(this).setScale(SCALE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 4096.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 96.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MoveTowardsTargetGoal(this, 1.0D, 96.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true,
                target -> target.isAlive() && !(target instanceof YogiriTakatouEntity)));
    }

    // ---------------------------------------------------------------
    // Save data
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

    /** No potion effect can stick. */
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    /** Death is refused: health is restored at once. */
    @Override
    public void die(DamageSource source) {
        System.out.println("[Yogiri Takatou] death refused");
        super.setHealth(this.getMaxHealth());
    }

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

    /** Every removal route is refused: kill, discard, unload to a chunk, changing dimension. */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED
                || reason == Entity.RemovalReason.DISCARDED
                || reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            System.out.println("[Yogiri Takatou] removal refused: " + reason);
            return;
        }
        super.remove(reason);
    }

    @Override
    public void setPos(double x, double y, double z) {
        if (isFarAway(x, z)) {
            System.out.println("[Yogiri Takatou] teleport refused");
            return;
        }
        super.setPos(x, y, z);
    }

    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        if (isFarAway(x, z)) {
            System.out.println("[Yogiri Takatou] teleport refused");
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

    /** No knockback, no push, no fling. */
    public void knockback(double strength, double x, double z) {
    }

    public void push(double x, double y, double z) {
    }

    public void push(Entity entity) {
    }

    @Override
    public void setDeltaMovement(Vec3 motion) {
        if (motion.lengthSqr() > 9.0D) {
            return;
        }
        super.setDeltaMovement(motion);
    }

    // ---------------------------------------------------------------
    // Watchdog: brings the boss back if it is ever lost
    // ---------------------------------------------------------------

    public static void watchdog(ServerLevel server) {
        for (Anchor a : ANCHORS) {
            if (a.level != server) {
                continue;
            }
            Entity found = server.getEntity(a.uuid);
            if (found instanceof YogiriTakatouEntity boss && !boss.isRemoved()) {
                if (isFarAway(boss.getX(), boss.getZ()) || boss.getY() < VOID_LIMIT) {
                    boss.restoreTo(a.pos);
                }
                continue;
            }
            BlockPos at = new BlockPos((int) Math.floor(a.pos.x), (int) Math.floor(a.pos.y), (int) Math.floor(a.pos.z));
            if (!server.isLoaded(at)) {
                continue;
            }
            ANCHORS.remove(a);
            YogiriTakatouEntity copy = YinYangMod.YOGIRI.get().create(server);
            if (copy == null) {
                continue;
            }
            copy.introDone = true;
            copy.moveTo(a.pos.x, a.pos.y, a.pos.z, 0.0F, 0.0F);
            server.addFreshEntity(copy);
            System.out.println("[Yogiri Takatou] missing boss restored");
        }
    }

    private void updateAnchor(ServerLevel server) {
        if (this.anchor == null) {
            this.anchor = new Anchor(server, this.getUUID(), this.position());
            ANCHORS.add(this.anchor);
        }
        this.anchor.uuid = this.getUUID();
        if (isFarAway(this.getX(), this.getZ()) || this.getY() < VOID_LIMIT) {
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

        for (ServerPlayer player : server.players()) {
            this.bossEvent.addPlayer(player);
        }

        updateAnchor(server);

        if (!introDone) {
            runIntro(server);
            return;
        }

        // Sweep objects with no hitbox or no life (markers, displays, projectiles, items) in range.
        if (server.getGameTime() % 10 == 0) {
            sweepObjects(server, ERASE_RANGE);
        }

        // Ambient eyes: the world keeps filling with them.
        if (server.getGameTime() % 20 == 0) {
            spawnEye(server, 20.0D, 60.0D, 200);
        }
        drawEyes(server);

        LivingEntity target = this.getTarget();
        boolean fighting = target != null && target.isAlive();
        this.setNoGravity(fighting);

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

        // Finale: charge, then end everything nearby at once.
        if (finaleCharge >= 0) {
            finaleChargeEffect(server, finaleCharge);
            finaleCharge++;
            if (finaleCharge >= FINALE_CHARGE) {
                finale(server);
                finaleCharge = -1;
                finaleCooldown = FINALE_INTERVAL;
            }
        } else if (finaleCooldown > 0) {
            finaleCooldown--;
        } else if (fighting) {
            finaleCharge = 0;
        }
    }

    // ---------------------------------------------------------------
    // Cinematic intro: the world fills with eyes, a vortex rises, and the camera orbits
    // ---------------------------------------------------------------

    private void runIntro(ServerLevel server) {
        if (introTicks == 0) {
            beginIntro(server);
        }
        vortex(server, introTicks);
        if (introTicks % 2 == 0 && eyes.size() < 30) {
            spawnEye(server, 12.0D, 50.0D, 200);
        }
        drawEyes(server);
        orbitCamera(introTicks);
        introTicks++;
        if (introTicks >= INTRO_TICKS) {
            endIntro(server);
        }
    }

    private void beginIntro(ServerLevel server) {
        cameraMarker = EntityType.MARKER.create(server);
        if (cameraMarker != null) {
            cameraMarker.moveTo(this.getX(), this.getY() + 4.0D, this.getZ() + 14.0D, 0.0F, 0.0F);
            server.addFreshEntity(cameraMarker);
        }
        for (ServerPlayer player : server.players()) {
            if (player.distanceToSqr(this) > INTRO_RANGE * INTRO_RANGE) {
                continue;
            }
            if (cameraMarker != null) {
                player.setCamera(cameraMarker);
            }
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
            player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 100, 40));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Yogiri Takatou")));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("THE END")));
        }
        for (int i = 0; i < 12; i++) {
            spawnEye(server, 12.0D, 50.0D, 200);
        }
        server.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 5.0F, 0.5F);
    }

    private void orbitCamera(int t) {
        if (cameraMarker == null) {
            return;
        }
        double progress = t / (double) INTRO_TICKS;
        double angle = progress * Math.PI * 2.0D;
        double radius = 16.0D - 8.0D * progress;
        double cx = this.getX();
        double cy = this.getY() + 4.5D;
        double cz = this.getZ();
        double camX = cx + Math.cos(angle) * radius;
        double camY = cy + 2.0D + Math.sin(progress * Math.PI) * 3.0D;
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
    // Eyes: each one is an almond outline with a red pupil, facing the boss
    // ---------------------------------------------------------------

    private static final class Eye {
        final Vec3 center;
        final Vec3 u;
        final Vec3 v;
        final double w;
        final double h;
        int life;

        Eye(Vec3 center, Vec3 u, Vec3 v, double w, double h, int life) {
            this.center = center;
            this.u = u;
            this.v = v;
            this.w = w;
            this.h = h;
            this.life = life;
        }
    }

    private void spawnEye(ServerLevel server, double minR, double maxR, int life) {
        double theta = this.random.nextDouble() * Math.PI * 2.0D;
        double cosPhi = this.random.nextDouble() * 2.0D - 1.0D;
        double sinPhi = Math.sqrt(1.0D - cosPhi * cosPhi);
        double r = minR + this.random.nextDouble() * (maxR - minR);
        Vec3 focus = this.position().add(0, 4.5D, 0);
        Vec3 center = focus.add(sinPhi * Math.cos(theta) * r, cosPhi * r * 0.5D, sinPhi * Math.sin(theta) * r);
        addEye(center, focus, 0.8D + this.random.nextDouble() * 0.8D, life);
    }

    private void addEye(Vec3 center, Vec3 facing, double size, int life) {
        Vec3 n = facing.subtract(center);
        if (n.lengthSqr() < 1.0E-6D) {
            n = new Vec3(0.0D, 0.0D, 1.0D);
        }
        n = n.normalize();
        Vec3 u = n.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (u.lengthSqr() < 1.0E-4D) {
            u = n.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        u = u.normalize();
        Vec3 v = u.cross(n).normalize();
        if (eyes.size() >= MAX_EYES) {
            eyes.remove(0);
        }
        eyes.add(new Eye(center, u, v, size * 1.6D, size * 0.9D, life));
    }

    private void drawEyes(ServerLevel server) {
        for (Eye eye : eyes) {
            eye.life--;
            if (eye.life % 3 != 0) {
                continue;
            }
            // Blinks now and then: the outline closes almost completely for a few ticks.
            double open = (eye.life % 60 < 4) ? 0.1D : 1.0D;
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3.0D;
                Vec3 p = eye.center.add(eye.u.scale(Math.cos(a) * eye.w))
                        .add(eye.v.scale(Math.sin(a) * eye.h * open));
                server.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            server.sendParticles(ParticleTypes.FLAME, eye.center.x, eye.center.y, eye.center.z, 1, 0, 0, 0, 0);
        }
        eyes.removeIf(e -> e.life <= 0);
    }

    // ---------------------------------------------------------------
    // Vortex: a spiral of portal light rising around the boss
    // ---------------------------------------------------------------

    private void vortex(ServerLevel server, int t) {
        double cx = this.getX();
        double cz = this.getZ();
        double base = this.getY();
        for (int i = 0; i < 16; i++) {
            double phase = (i / 16.0D + t * 0.02D) % 1.0D;
            double a = phase * Math.PI * 4.0D + t * 0.15D;
            double r = 2.0D + phase * 6.0D;
            double y = base + phase * 14.0D;
            server.sendParticles(ParticleTypes.PORTAL,
                    cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, 2, 0.1D, 0.1D, 0.1D, 0.3D);
        }
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, base + 4.5D, cz, 6, 0.6D, 1.2D, 0.6D, 0.2D);
    }

    // ---------------------------------------------------------------
    // Punch and erase
    // ---------------------------------------------------------------

    private void punch(ServerLevel server, LivingEntity target) {
        this.swing(InteractionHand.MAIN_HAND);
        Vec3 hit = target.position().add(0, target.getBbHeight() / 2.0D, 0);
        server.sendParticles(ParticleTypes.EXPLOSION, hit.x, hit.y, hit.z, 2, 0.3D, 0.3D, 0.3D, 0.0D);
        server.sendParticles(ParticleTypes.SWEEP_ATTACK, hit.x, hit.y, hit.z, 3, 0.4D, 0.4D, 0.4D, 0.0D);
        server.playSound(null, hit.x, hit.y, hit.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    private void erase(ServerLevel server, LivingEntity victim) {
        Vec3 center = victim.position().add(0, victim.getBbHeight() / 2.0D, 0);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 30, 0.5D, 0.8D, 0.5D, 0.2D);
        server.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 20, 0.5D, 0.8D, 0.5D, 0.1D);
        server.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.5F, 0.8F);
        if (this.tickCount % 10 == 0) {
            addEye(center, this.position().add(0, 4.5D, 0), 0.6D, 40);
        }

        if (victim instanceof Player player) {
            player.setHealth(0.0F);
            player.die(this.damageSources().mobAttack(this));
        } else {
            eraseMob(victim);
        }
    }

    /**
     * Removes an entity by the most direct route first. setRemoved() sets the removal flag
     * itself, so it bypasses any mod that only changes remove(). If that is not enough, it
     * tries remove(), then throws the entity far below the world as a last resort.
     */
    private static void purge(Entity e) {
        e.discard();
        if (!e.isRemoved()) {
            e.setRemoved(Entity.RemovalReason.DISCARDED);
        }
        if (!e.isRemoved()) {
            e.remove(Entity.RemovalReason.KILLED);
        }
        if (!e.isRemoved()) {
            e.setPos(e.getX(), -10_000.0D, e.getZ());
            e.discard();
        }
    }

    /** Living mobs: purge first, then kill outright if they are still standing. */
    private void eraseMob(LivingEntity victim) {
        purge(victim);
        if (!victim.isRemoved()) {
            victim.setHealth(0.0F);
            victim.die(this.damageSources().mobAttack(this));
            purge(victim);
        }
        System.out.println("[Yogiri Takatou] erase " + EntityType.getKey(victim.getType())
                + " removed=" + victim.isRemoved());
    }

    // ---------------------------------------------------------------
    // Finale
    // ---------------------------------------------------------------

    /**
     * Removes every entity that is not alive and not a boss: markers, display entities,
     * projectiles, boats, items and anything else with no hitbox. Players are not touched here.
     */
    private void sweepObjects(ServerLevel server, double radius) {
        AABB area = this.getBoundingBox().inflate(radius);
        List<Entity> objects = server.getEntitiesOfClass(Entity.class, area,
                e -> e != this
                        && !(e instanceof LivingEntity)
                        && !(e instanceof YogiriTakatouEntity)
                        && !(e instanceof GodOfYinyangEntity)
                        && e != cameraMarker);
        for (Entity obj : objects) {
            purge(obj);
        }
    }

    private void finaleChargeEffect(ServerLevel server, int t) {
        vortex(server, t + 100);
        if (t % 10 == 0) {
            spawnEye(server, 8.0D, 30.0D, 80);
        }
        if (t % 8 == 0) {
            server.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.4F + t * 0.01F);
        }
        drawEyes(server);
    }

    private void finale(ServerLevel server) {
        double cx = this.getX();
        double cy = this.getY() + 4.5D;
        double cz = this.getZ();

        for (int i = 0; i < 20; i++) {
            spawnEye(server, 10.0D, FINALE_RADIUS * 0.8D, 160);
        }
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 10, 4.0D, 4.0D, 4.0D, 0.0D);
        server.playSound(null, cx, cy, cz, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 5.0F, 0.5F);

        for (int ring = 1; ring <= 30; ring++) {
            double radius = ring * 2.6D;
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24;
                server.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        cx + Math.cos(a) * radius, this.getY() + 0.2D, cz + Math.sin(a) * radius,
                        1, 0, 0, 0, 0);
            }
        }

        for (ServerPlayer player : server.players()) {
            if (player.distanceToSqr(this) <= FINALE_RADIUS * FINALE_RADIUS) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
            }
        }

        sweepObjects(server, FINALE_RADIUS);

        AABB area = this.getBoundingBox().inflate(FINALE_RADIUS);
        List<LivingEntity> victims = server.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != this && e.isAlive() && !(e instanceof YogiriTakatouEntity));
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
