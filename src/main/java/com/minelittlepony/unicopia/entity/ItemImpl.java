package com.minelittlepony.unicopia.entity;

import com.minelittlepony.unicopia.*;
import com.minelittlepony.unicopia.item.ItemDuck;
import com.minelittlepony.unicopia.item.enchantment.EnchantmentUtil;
import com.minelittlepony.unicopia.item.enchantment.UEnchantments;
import com.minelittlepony.unicopia.network.track.DataTracker;
import com.minelittlepony.unicopia.network.track.DataTrackerManager;
import com.minelittlepony.unicopia.network.track.Trackable;
import com.minelittlepony.unicopia.network.track.TrackableDataType;
import com.minelittlepony.unicopia.particle.FollowingParticleEffect;
import com.minelittlepony.unicopia.particle.ParticleUtils;
import com.minelittlepony.unicopia.particle.UParticles;
import com.minelittlepony.unicopia.util.VecHelper;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

public class ItemImpl implements Equine<ItemEntity> {
    private final ItemEntity entity;

    private final ItemPhysics physics;

    private final DataTrackerManager trackers;
    protected final DataTracker tracker;

    private final DataTracker.Entry<Race> race;

    public ItemImpl(ItemEntity owner) {
        this.entity = owner;
        this.trackers = Trackable.of(entity).getDataTrackers();
        this.tracker = trackers.getPrimaryTracker();
        this.physics = new ItemPhysics(owner);

        race = tracker.startTracking(TrackableDataType.RACE, Race.HUMAN);
    }

    @Override
    public boolean onProjectileImpact(ProjectileEntity projectile) {
        return false;
    }

    @Override
    public boolean beforeUpdate() {

        if (!entity.getWorld().isClient) {
            if (EnchantmentUtil.getWantItNeedItLevel(entity) > 0) {
                var random = entity.getWorld().random;

                if (random.nextInt(15) == 0) {
                    ParticleUtils.spawnParticles(new FollowingParticleEffect(UParticles.HEALTH_DRAIN, entity.getPos().add(
                            VecHelper.sphere(random).get().add(0, 1, 0)
                    ), 0.2F), entity, 1);
                }
            }
        }

        ItemStack stack = entity.getStack();
        IItemEntity i = (IItemEntity)entity;
        ItemDuck duck = ItemDuck.of(stack);

        if (!stack.isEmpty()) {

            if (duck.isClingy(stack)) {
                Random rng = entity.getWorld().random;

                entity.getWorld().addParticle(duck.getParticleEffect(i),
                        entity.getX() + rng.nextFloat() - 0.5,
                        entity.getY() + rng.nextFloat() - 0.5,
                        entity.getZ() + rng.nextFloat() - 0.5,
                        0, 0, 0
                );

                Vec3d position = entity.getPos();
                VecHelper.findInRange(entity, entity.getWorld(), entity.getPos(), duck.getFollowDistance(i), e -> e instanceof PlayerEntity)
                    .stream()
                    .sorted((a, b) -> (int)(a.getPos().distanceTo(position) - b.getPos().distanceTo(position)))
                    .findFirst()
                    .ifPresent(player -> {
                        double distance = player.getPos().distanceTo(entity.getPos());

                        entity.move(MovementType.SELF,  player.getPos().subtract(entity.getPos()).multiply(distance < 0.3 ? 1 : duck.getFollowSpeed(i)));
                        if (entity.horizontalCollision) {
                            entity.move(MovementType.SELF, new Vec3d(0, entity.verticalCollision ? -0.3 : 0.3, 0));
                        }

                        duck.interactWithPlayer(i, (PlayerEntity)player);
                    });
            }

            if (stack.isIn(UTags.Items.FALLS_SLOWLY)) {
                if (!entity.isOnGround() && Math.signum(entity.getVelocity().y) != getPhysics().getGravitySignum()) {
                    double ticks = ((Entity)entity).age;
                    double shift = Math.sin(ticks / 9D) / 9D;
                    double rise = -Math.cos(ticks / 9D) * getPhysics().getGravitySignum();

                    entity.prevYaw = entity.prevYaw;
                    entity.setYaw(entity.getYaw() + 0.3F);

                    entity.setVelocity(
                            entity.getVelocity()
                                .multiply(0.25, 0, 0.25)
                                .add(0, rise, 0)
                                .add(entity.getRotationVec(1)).normalize().multiply(shift)
                    );
                }
            }

            return duck.onGroundTick(i).isAccepted();
        }


        return false;
    }

    @Override
    public void tick() {
        physics.tick();
    }

    @Override
    public Physics getPhysics() {
        return physics;
    }

    @Override
    public Race getSpecies() {
        return race.get();
    }

    @Override
    public void setSpecies(Race race) {
        this.race.set(race);
    }

    @Override
    public boolean collidesWithClouds() {
        return entity.getStack().isIn(UTags.Items.FLOATS_ON_CLOUDS) || getSpecies().hasPersistentWeatherMagic();
    }

    @Override
    public void toNBT(NbtCompound compound, WrapperLookup lookup) {
        compound.putString("owner_race", getSpecies().getId().toString());
        physics.toNBT(compound, lookup);
    }

    @Override
    public void fromNBT(NbtCompound compound, WrapperLookup lookup) {
        if (compound.contains("owner_race", NbtElement.STRING_TYPE)) {
            setSpecies(Race.fromName(compound.getString("owner_race"), Race.HUMAN));
        }
        physics.fromNBT(compound, lookup);
    }

    @Override
    public ItemEntity asEntity() {
        return entity;
    }

    public interface ClingyItem {
        default boolean isClingy(ItemStack stack) {
            return EnchantmentUtil.getLevel(UEnchantments.CLINGY, stack) > 0;
        }

        default ParticleEffect getParticleEffect(IItemEntity entity) {
            // TODO: was AMBIENT_ENTITY_EFFECT
            return ParticleTypes.EFFECT;
        }

        default float getFollowDistance(IItemEntity entity) {
            return 6 * (1 + EnchantmentUtil.getLevel(UEnchantments.CLINGY, entity.get().asEntity().getStack()));
        }

        default float getFollowSpeed(IItemEntity entity) {
            return Math.min(1, 0.02F * (1 + EnchantmentUtil.getLevel(UEnchantments.CLINGY, entity.get().asEntity().getStack())));
        }

        default void interactWithPlayer(IItemEntity entity, PlayerEntity player) {

        }
    }
}
