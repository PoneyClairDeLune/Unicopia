package com.minelittlepony.unicopia.block.cloud;

import org.jetbrains.annotations.Nullable;

import com.minelittlepony.unicopia.EquineContext;
import com.minelittlepony.unicopia.InteractionManager;
import com.minelittlepony.unicopia.entity.player.Pony;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

public class CloudBlock extends Block implements CloudLike {
    private static final MapCodec<CloudBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.fieldOf("meltable").forGetter(b -> b.meltable),
            BedBlock.createSettingsCodec()
    ).apply(instance, CloudBlock::new));

    protected final boolean meltable;

    public CloudBlock(boolean meltable, Settings settings) {
        super(CloudLike.applyCloudProperties(meltable ? settings.ticksRandomly() : settings));
        this.meltable = meltable;
    }

    @Override
    public MapCodec<? extends CloudBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        return 0.9F;
    }

    @Override
    protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }

    @Override
    public void onEntityLand(BlockView world, Entity entity) {
        boolean bounce = Math.abs(entity.getVelocity().y) > 0.3;
        super.onEntityLand(world, entity);
        if (bounce) {
            entity.addVelocity(0, 0.2F, 0);
        }
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (world.random.nextInt(150) == 0) {
            generateSurfaceParticles(world, state, pos, ShapeContext.absent(), 1);
        }
    }

    @Override
    public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.handleFallDamage(fallDistance, 0, world.getDamageSources().fall());
        generateSurfaceParticles(world, state, pos, ShapeContext.absent(), 9);

        if (!world.isClient && fallDistance > 7) {
            world.breakBlock(pos, true);
        }
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (world.random.nextInt(15) == 0) {
            generateSurfaceParticles(world, state, pos, ShapeContext.absent(), 1);
        }
    }

    protected void generateSurfaceParticles(World world, BlockState state, BlockPos pos, ShapeContext context, int count) {
        VoxelShape shape = state.getCullingShape(world, pos);
        Random rng = world.random;
        Box box = shape.getBoundingBox();

        for (int i = 0; i < count; i++) {
            world.addParticle(ParticleTypes.CLOUD,
                    pos.getX() + MathHelper.lerp(rng.nextFloat(), box.minX, box.maxX),
                    pos.getY() + box.maxY,
                    pos.getZ() + MathHelper.lerp(rng.nextFloat(), box.minZ, box.maxZ), 0, 0, 0);

            world.addParticle(ParticleTypes.CLOUD,
                    pos.getX() + (rng.nextBoolean() ? box.minX : box.maxX),
                    pos.getY() + MathHelper.lerp(rng.nextFloat(), box.minY, box.maxY),
                    pos.getZ() + MathHelper.lerp(rng.nextFloat(), box.minZ, box.maxZ), 0, 0, 0);

            world.addParticle(ParticleTypes.CLOUD,
                    pos.getX() + MathHelper.lerp(rng.nextFloat(), box.minX, box.maxX),
                    pos.getY() + MathHelper.lerp(rng.nextFloat(), box.minY, box.maxY),
                    pos.getZ() + (rng.nextBoolean() ? box.minZ : box.maxZ), 0, 0, 0);
        }
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {

        if (entity instanceof PlayerEntity player && (player.getAbilities().flying || Pony.of(player).getPhysics().isFlying())) {
            return;
        }

        if (entity.getVelocity().y < 0) {
            float cloudWalking = EquineContext.of(entity).getCloudWalkingStrength();
            if (cloudWalking > 0) {
                entity.setVelocity(entity.getVelocity().multiply(1, 1 - cloudWalking, 1));
                entity.addVelocity(0, 0.07, 0);
                entity.setOnGround(true);
            }
        }

        entity.setVelocity(entity.getVelocity().multiply(0.9F, 1, 0.9F));
    }

    @Override
    protected final VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        EquineContext equineContext = EquineContext.of(context);
        if (!canInteract(state, world, pos, equineContext)) {
            return VoxelShapes.empty();
        }
        return getOutlineShape(state, world, pos, context, equineContext);
    }

    @Override
    protected final VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return getOutlineShape(state, world, pos, ShapeContext.absent(), EquineContext.ABSENT);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return this.collidable ? state.getOutlineShape(world, pos, context) : VoxelShapes.empty();
    }

    @Override
    @Nullable
    public final BlockState getPlacementState(ItemPlacementContext context) {
        EquineContext equineContext = EquineContext.of(context);
        if (!canInteract(getDefaultState(), context.getWorld(), context.getBlockPos(), equineContext)) {
            return null;
        }
        return getPlacementState(context, equineContext);
    }

    @Override
    protected final boolean canReplace(BlockState state, ItemPlacementContext context) {
        EquineContext equineContext = EquineContext.of(context);
        if (canInteract(state, context.getWorld(), context.getBlockPos(), equineContext)) {
            return canReplace(state, context, equineContext);
        }
        return true;
    }

    @Override
    protected boolean isSideInvisible(BlockState state, BlockState stateFrom, Direction direction) {
        VoxelShape shape = state.getCullingShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        VoxelShape shapeFrom = stateFrom.getCullingShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        return !shape.isEmpty() && !shapeFrom.isEmpty() && VoxelShapes.isSideCovered(shape, shapeFrom, direction);
    }

    @Override
    protected boolean canPathfindThrough(BlockState state, NavigationType type) {
        return type != NavigationType.LAND || !InteractionManager.getInstance().getPathingEquineContext().collidesWithClouds();
    }

    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context, EquineContext equineContext) {
        return VoxelShapes.fullCube();
    }

    protected boolean canInteract(BlockState state, BlockView world, BlockPos pos, EquineContext context) {
        return context.collidesWithClouds() || context.hasFeatherTouch();
    }

    protected boolean canReplace(BlockState state, ItemPlacementContext context, EquineContext equineContext) {
        return super.canReplace(state, context);
    }

    @Nullable
    protected BlockState getPlacementState(ItemPlacementContext placementContext, EquineContext equineContext) {
        return super.getPlacementState(placementContext);
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (meltable) {
            if (world.getLightLevel(LightType.BLOCK, pos) > 11) {
                dropStacks(state, world, pos);
                world.removeBlock(pos, false);
            }
        }
    }
}
