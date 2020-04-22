package com.minelittlepony.unicopia.entity;

import java.util.Map;

import javax.annotation.Nullable;

import com.minelittlepony.unicopia.EquinePredicates;
import com.minelittlepony.unicopia.Race;
import com.minelittlepony.unicopia.ability.PegasusCloudInteractionAbility.ICloudEntity;
import com.minelittlepony.unicopia.block.UBlocks;
import com.minelittlepony.unicopia.entity.player.Pony;
import com.minelittlepony.unicopia.item.UItems;
import com.minelittlepony.unicopia.particles.ParticleEmitter;
import com.minelittlepony.unicopia.particles.UParticles;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.SpawnType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.FlyingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.IWorld;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

public class CloudEntity extends FlyingEntity implements ICloudEntity, InAnimate {

    private static final TrackedData<Integer> RAINTIMER = DataTracker.registerData(CloudEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> THUNDERING = DataTracker.registerData(CloudEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> SCALE = DataTracker.registerData(CloudEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Boolean> STATIONARY = DataTracker.registerData(CloudEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    protected double targetAltitude;

    protected int directionX;
    protected int directionZ;

    public CloudEntity(EntityType<? extends CloudEntity> type, World world) {
        super(type, world);
        ignoreCameraFrustum = true;
        targetAltitude = getRandomFlyingHeight();
        // TODO: drops cloud_matter x1
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(RAINTIMER, 0);
        dataTracker.startTracking(THUNDERING, false);
        dataTracker.startTracking(STATIONARY, false);
        dataTracker.startTracking(SCALE, 1);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.BLOCK_WOOL_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLOCK_WOOL_BREAK;
    }

    @Override
    public boolean doesRenderOnFire() {
        return false;
    }

    @Override
    public float getBrightnessAtEyes() {
        return 0xF000F0;
    }

    @Override
    public boolean cannotDespawn() {
        return hasCustomName() || getStationary() || getOpaque();
    }

    @Override
    public int getLimitPerChunk() {
        return 6;
    }

    @Override
    public boolean canInteract(Race race) {
        return race.canInteractWithClouds();
    }

    @Override
    public void onStruckByLightning(LightningEntity lightningBolt) {

    }

    @Override
    public EntityData initialize(IWorld world, LocalDifficulty difficulty, SpawnType type, @Nullable EntityData data, @Nullable CompoundTag tag) {
        if (random.nextInt(20) == 0 && canRainHere()) {
            setRaining();
            if (random.nextInt(20) == 0) {
                setIsThundering(true);
            }
        }

        setCloudSize(1 + random.nextInt(4));

        return super.initialize(world, difficulty, type, data, tag);
    }

    @Override
    protected void pushAway(Entity other) {
        if (other instanceof CloudEntity || other instanceof PlayerEntity) {
            if (other.getY() > getY()) {
                return;
            }

            super.pushAway(other);
        }
    }

    @Override
    public void pushAwayFrom(Entity other) {
        if (other instanceof PlayerEntity) {
            if (EquinePredicates.INTERACT_WITH_CLOUDS.test((PlayerEntity)other)) {
                super.pushAwayFrom(other);
            }
        } else if (other instanceof CloudEntity) {
            super.pushAwayFrom(other);
        }
    }

    @Override
    public void tick() {
        Box boundingbox = getBoundingBox();

        if (getIsRaining()) {
            if (world.isClient) {
                for (int i = 0; i < 30 * getCloudSize(); i++) {
                    double x = MathHelper.nextDouble(random, boundingbox.x1, boundingbox.x2);
                    double y = getBoundingBox().y1 + getHeight()/2;
                    double z = MathHelper.nextDouble(random, boundingbox.z1, boundingbox.z2);

                    ParticleEffect particleId = canSnowHere(new BlockPos(x, y, z)) ? ParticleTypes.ITEM_SNOWBALL : UParticles.RAIN_DROPS;

                    world.addParticle(particleId, x, y, z, 0, 0, 0);
                }

                Box rainedArea = boundingbox
                        .expand(1, 0, 1)
                        .expand(0, -(getY() - getGroundPosition(getBlockPos()).getY()), 0);


                for (PlayerEntity j : world.getEntities(PlayerEntity.class, rainedArea, j -> canSnowHere(j.getBlockPos()))) {
                    j.world.playSound(j, j.getBlockPos(), SoundEvents.WEATHER_RAIN, SoundCategory.AMBIENT, 0.1F, 0.6F);
                }
            }

            double width = getDimensions(getPose()).width;
            BlockPos pos = getGroundPosition(new BlockPos(
                getX() + random.nextFloat() * width,
                getY(),
                getZ() + random.nextFloat() * width
            ));

            if (getIsThundering()) {
                if (random.nextInt(3000) == 0) {
                    spawnThunderbolt(pos);
                }

                if (random.nextInt(200) == 0) {
                    setIsThundering(false);
                }
            }

            BlockState state = world.getBlockState(pos);

            if (state.getBlock() instanceof FireBlock) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
            }

            if (random.nextInt(20) == 0) {
                BlockPos below = pos.down();
                state = world.getBlockState(below);
                if (state.getBlock() != null) {
                    Biome biome = world.getBiome(below);

                    if (biome.canSetIce(world, below)) {
                        world.setBlockState(below, Blocks.ICE.getDefaultState());
                    }

                    if (biome.canSetSnow(world, pos)) {
                        world.setBlockState(pos, Blocks.SNOW.getDefaultState());
                    }

                    if (state.getBlock() instanceof FarmlandBlock) {
                        int moisture = state.get(FarmlandBlock.MOISTURE);

                        if (moisture < 7) {
                            world.setBlockState(below, state.with(FarmlandBlock.MOISTURE, moisture + 1));
                        }
                    } else if (state.getBlock() instanceof CropBlock) {
                        int age = state.get(CropBlock.AGE);

                        if (age < 7) {
                            world.setBlockState(below, state.with(CropBlock.AGE, age + 1), 2);
                        }
                    }

                    state.getBlock().rainTick(world, below);
                }
            }

            if (setRainTimer(getRainTimer() - 1) == 0) {
                if (!getStationary()) {
                    spawnHurtParticles();

                    if (getCloudSize() > 1) {
                        setIsRaining(false);
                        setCloudSize(getCloudSize() - 1);
                    } else {
                        remove();
                    }
                }
            }
        } else {
            if (random.nextInt(8000) == 0 && canRainHere()) {
                setRaining();
                if (random.nextInt(7000) == 0) {
                    setIsThundering(true);
                }
            }
        }

        pitch = 0;
        headYaw = 0;
        yaw = 0;

        for (Entity i : world.getEntities(this, boundingbox
                .expand(1 / (1 + getCloudSize())), EquinePredicates.ENTITY_INTERACT_WITH_CLOUDS)) {
            if (i.getY() > getY() + 0.5) {
                applyGravityCompensation(i);
            }
        }

        if (isOnFire() && !dead) {
            for (int i = 0; i < 5; i++) {
                world.addParticle(ParticleTypes.CLOUD,
                        MathHelper.nextDouble(random, boundingbox.x1, boundingbox.x2),
                        MathHelper.nextDouble(random, boundingbox.y1, boundingbox.y2),
                        MathHelper.nextDouble(random, boundingbox.z1, boundingbox.z2), 0, 0.25, 0);
            }
        }

        if (getStationary()) {
            setVelocity(0, 0, 0);
        }

        super.tick();

        double motionFactor = (1 + getCloudSize() / 4);

        Vec3d vel = this.getVelocity();
        this.setVelocity(vel.x / motionFactor, vel.y, vel.z / motionFactor);


        hurtTime = 0;
    }

    @Override
    public double getMountedHeightOffset() {
        return getBoundingBox().y2 - getBoundingBox().y1 - 0.25;
    }

    @Override
    public void travel(Vec3d motion) {
        if (!getStationary()) {
            super.travel(motion);
        }
    }

    @Override
    public void onPlayerCollision(PlayerEntity player) {
        if (player.getY() >= getY()) {
            if (applyGravityCompensation(player)) {
                double difX = player.getX() - player.prevX;
                double difZ = player.getZ() - player.prevZ;
                double difY = player.getY() - player.prevY;

                player.horizontalSpeed = (float)(player.horizontalSpeed + MathHelper.sqrt(difX * difX + difZ * difZ) * 0.6);
                player.distanceTraveled = (float)(player.distanceTraveled + MathHelper.sqrt(difX * difX + difY * difY + difZ * difZ) * 0.6);

                if (Pony.of(player).stepOnCloud()) {
                    BlockSoundGroup soundtype = BlockSoundGroup.WOOL;
                    player.playSound(soundtype.getStepSound(), soundtype.getVolume() * 0.15F, soundtype.getPitch());
                }
            }
        }

        super.onPlayerCollision(player);
    }

    @Override
    protected void mobTick() {
        if (!getStationary()) {
            if (!hasVehicle()) {
                double distance = targetAltitude - getY();

                if (targetAltitude < getY() && !world.isAir(getBlockPos())) {
                    distance = 0;
                }

                if (Math.abs(distance) < 1 && random.nextInt(7000) == 0) {
                    targetAltitude = getRandomFlyingHeight();
                    distance = targetAltitude - getY();
                }

                if (Math.abs(distance) < 1) {
                    distance = 0;
                }

                Vec3d vel = getVelocity();

                setVelocity(vel.x, vel.y - 0.002 + (Math.signum(distance) * 0.699999988079071D - vel.y) * 0.10000000149011612D, vel.z);
            }
        }
    }

    protected float getRandomFlyingHeight() {
        float a = getMaximumFlyingHeight();
        float b = getMinimumFlyingHeight();

        float min = Math.min(a, b);
        float max = Math.max(a, b);

        return min + world.random.nextFloat() * (max - min);
    }

    protected float getMinimumFlyingHeight() {
        float ground = world.getBiome(getBlockPos()).getDepth();
        float cloud = world.getDimension().getCloudHeight();

        float min = Math.min(ground, cloud);
        float max = Math.max(ground, cloud);

        return min + (max - min)/2;
    }

    protected float getMaximumFlyingHeight() {
        return world.getDimension().getCloudHeight() - 5;
    }

    @Override
    public void handleStatus(byte type) {
        if (type == 2 && !isOnFire()) {
            spawnHurtParticles();
        }
        super.handleStatus(type);
    }

    @Override
    public void handlePegasusInteration(int interationType) {
        if (!world.isClient) {
            switch (interationType) {
                case 1:
                    setIsRaining(!getIsRaining());
                    break;
                case 2:
                    spawnThunderbolt();
                    break;
            }
        }

        spawnHurtParticles();
    }

    public void spawnHurtParticles() {
        for (int i = 0; i < 50 * getCloudSize(); i++) {
            ParticleEmitter.instance().emitDiggingParticles(this, UBlocks.normal_cloud);
        }
        playHurtSound(DamageSource.GENERIC);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        Entity attacker = source.getAttacker();

        if (attacker instanceof PlayerEntity) {
            return damage(source, amount, (PlayerEntity)attacker);
        }

        return source == DamageSource.IN_WALL || super.damage(source, amount);
    }

    private boolean damage(DamageSource source, float amount, PlayerEntity player) {

        ItemStack stack = player.getMainHandStack();

        boolean canFly = EnchantmentHelper.getEnchantments(stack).containsKey(Enchantments.FEATHER_FALLING)
                || EquinePredicates.INTERACT_WITH_CLOUDS.test(player);
        boolean stat = getStationary();

        if (stat || canFly) {
            if (!isOnFire()) {
                spawnHurtParticles();
            }

            if (stack != null && stack.getItem() instanceof SwordItem) {
                return super.damage(source, amount);
            } else if (stack != null && stack.getItem() instanceof ShovelItem) {
                return super.damage(source, amount * 1.5f);
            } else if (canFly) {
                if (player.getY() < getY() || !world.isAir(getBlockPos())) {
                    targetAltitude = getY() + 5;
                } else if (player.getY() > getY()) {
                    targetAltitude = getY() - 5;
                }
            }
        }
        return false;
    }

    @Override
    public void onDeath(DamageSource s) {
        if (s == DamageSource.GENERIC || (s.getSource() != null && s.getSource() instanceof PlayerEntity)) {
            remove();
        }

        super.onDeath(s);
        clearItemFloatingState();
    }

    @Override
    public void remove() {
        super.remove();
        clearItemFloatingState();
    }

    //@FUF(reason = "There is no TickEvent.EntityTickEvent. Waiting on mixins...")
    protected void clearItemFloatingState() {
        Box bounds = getBoundingBox().expand(1 / (1 + getCloudSize())).expand(5);

        for (Entity i : world.getEntities(this, bounds, this::entityIsFloatingItem)) {
            i.setNoGravity(false);
        }
    }

    private boolean entityIsFloatingItem(Entity e) {
        return e instanceof ItemEntity
                && EquinePredicates.ITEM_INTERACT_WITH_CLOUDS.test((ItemEntity)e);
    }

    @Override
    protected void dropEquipment(DamageSource source, int looting, boolean hitByPlayer) {
        if (hitByPlayer) {
            int amount = 13 + world.random.nextInt(3);

            dropItem(UItems.cloud_matter, amount * (1 + looting));

            if (world.random.nextBoolean()) {
                dropItem(UItems.dew_drop, 3 + looting);
            }
        }
    }

    @Override
    public ItemEntity dropItem(ItemConvertible stack, int amount) {
        ItemEntity item = super.dropItem(stack, amount);

        Ponylike.of(item).setSpecies(Race.PEGASUS);
        item.setNoGravity(true);
        item.setVelocity(0, 0, 0);

        return item;
    }

    @Override
    public void readCustomDataFromTag(CompoundTag tag) {
        super.readCustomDataFromTag(tag);

        setRainTimer(tag.getInt("RainTimer"));
        setIsThundering(tag.getBoolean("IsThundering"));
        setCloudSize(tag.getByte("CloudSize"));
        setStationary(tag.getBoolean("IsStationary"));
    }

    @Override
    public void writeCustomDataToTag(CompoundTag tag) {
        super.writeCustomDataToTag(tag);

        tag.putInt("RainTimer", getRainTimer());
        tag.putBoolean("IsThundering", getIsThundering());
        tag.putByte("CloudSize", (byte)getCloudSize());
        tag.putBoolean("IsStationary", getStationary());
    }

    protected boolean applyGravityCompensation(Entity entity) {
        int floatStrength = getFloatStrength(entity);

        if (!isConnectedThroughVehicle(entity) && floatStrength > 0) {

            double boundModifier = entity.fallDistance > 80 ? 80 : MathHelper.floor(entity.fallDistance * 10) / 10;

            entity.onGround = true;

            Vec3d motion = entity.getVelocity();
            double motionX = motion.x;
            double motionY = motion.y;
            double motionZ = motion.z;

            motionY += (((floatStrength > 2 ? 1 : floatStrength/2) * 0.699999998079071D) - motionY + boundModifier * 0.7) * 0.10000000149011612D;
            if (!getStationary()) {
                motionX += ((motionX - motionX) / getCloudSize()) - 0.002F;
            }

            if (!getStationary() && motionY > 0.4 && world.random.nextInt(900) == 0) {
                spawnThunderbolt(getBlockPos());
            }

            // @FUF(reason = "There is no TickEvents.EntityTickEvent. Waiting on mixins...")
            if (getStationary() && entity instanceof ItemEntity) {
                motionX /= 8;
                motionZ /= 8;
                motionY /= 16;
                entity.setNoGravity(true);
            }
            entity.setVelocity(motionX, motionY, motionZ);

            return true;
        }

        return false;
    }

    @Override
    public void move(MovementType type, Vec3d delta) {
        setBoundingBox(getBoundingBox().offset(delta));
        moveToBoundingBoxCenter();
    }

    public int getFloatStrength(Entity entity) {
        if (EquinePredicates.ENTITY_INTERACT_WITH_CLOUDS.test(entity)) {
            return 3;
        }

        if (entity instanceof PlayerEntity) {
            return getFeatherEnchantStrength((PlayerEntity)entity);
        }

        return 0;
    }

    public static int getFeatherEnchantStrength(PlayerEntity player) {
        for (ItemStack stack : player.getArmorItems()) {
            if (stack != null) {
                Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
                if (enchantments.containsKey(Enchantments.FEATHER_FALLING)) {
                    return enchantments.get(Enchantments.FEATHER_FALLING);
                }
            }
        }
        return 0;
    }

    private boolean canRainHere() {
        return world.getBiome(getBlockPos()).getRainfall() > 0;
    }

    private boolean canSnowHere(BlockPos pos) {
        return world.getBiome(pos).canSetSnow(world, pos);
    }

    public void spawnThunderbolt() {
        spawnThunderbolt(getGroundPosition(getBlockPos()));
    }

    public void spawnThunderbolt(BlockPos pos) {
        if (world instanceof ServerWorld) {
            ((ServerWorld)world).addLightning(new LightningEntity(world, pos.getX(), pos.getY(), pos.getZ(), false));
        }
    }

    private BlockPos getGroundPosition(BlockPos inPos) {
        BlockPos pos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, inPos);

        if (pos.getY() >= getY()) {
            while (World.isValid(pos)) {
                pos = pos.down();
                if (world.getBlockState(pos).hasSolidTopSurface(world, pos, this)) {
                    return pos.up();
                }
            }

        }
        return pos;
    }

    public int getRainTimer() {
        return dataTracker.get(RAINTIMER);
    }

    public int setRainTimer(int val) {
        val = Math.max(0, val);
        dataTracker.set(RAINTIMER, val);
        return val;
    }

    private void setRaining() {
        setRainTimer(700 + random.nextInt(20));
    }

    public void setIsRaining(boolean val) {
        if (val) {
            setRaining();
        } else {
            setRainTimer(0);
        }
    }

    public boolean getIsRaining() {
        return getRainTimer() > 0;
    }

    public boolean getIsThundering() {
        return dataTracker.get(THUNDERING);
    }

    public void setIsThundering(boolean val) {
        dataTracker.set(THUNDERING, val);
    }

    public boolean getStationary() {
        return dataTracker.get(STATIONARY);
    }

    public void setStationary(boolean val) {
        dataTracker.set(STATIONARY, val);
    }

    public boolean getOpaque() {
        return false;
    }

    public int getCloudSize() {
        return dataTracker.get(SCALE);
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        return super.getDimensions(pose).scaled(getCloudSize());
    }

    public void setCloudSize(int val) {
        val = Math.max(1, val);
        dataTracker.set(SCALE, val);
        calculateDimensions();
    }
}
