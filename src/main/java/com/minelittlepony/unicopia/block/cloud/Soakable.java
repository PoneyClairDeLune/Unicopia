package com.minelittlepony.unicopia.block.cloud;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import com.minelittlepony.unicopia.USounds;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

public interface Soakable {
    IntProperty MOISTURE = IntProperty.of("moisture", 1, 7);
    Direction[] DIRECTIONS = Arrays.stream(Direction.values()).filter(d -> d != Direction.UP).toArray(Direction[]::new);

    @Nullable
    BlockState getStateWithMoisture(BlockState state, int moisture);

    default int getMoisture(BlockState state) {
        return state.getOrEmpty(MOISTURE).orElse(0);
    }

    static void addMoistureParticles(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(5) == 0) {
            world.addParticle(ParticleTypes.DRIPPING_WATER,
                    pos.getX() + random.nextFloat(),
                    pos.getY(),
                    pos.getZ() + random.nextFloat(),
                    0, 0, 0
            );
        }
    }

    static ActionResult tryCollectMoisture(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (state.getBlock() instanceof Soakable soakable) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(Items.GLASS_BOTTLE)) {
                player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, Items.POTION.getDefaultStack(), false));
                world.playSound(player, player.getX(), player.getY(), player.getZ(), USounds.Vanilla.ITEM_BOTTLE_FILL, SoundCategory.NEUTRAL, 1, 1);
                world.emitGameEvent(player, GameEvent.FLUID_PICKUP, pos);
                updateMoisture(soakable, state, world, pos, soakable.getMoisture(state) - 1);

                return ActionResult.SUCCESS;
            }
        }

        return tryDepositMoisture(state, world, pos, player, hand, hit);
    }

    static ActionResult tryDepositMoisture(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (state.getBlock() instanceof Soakable soakable) {
            ItemStack stack = player.getStackInHand(hand);
            if (soakable.getMoisture(state) < 7
                    && stack.isOf(Items.POTION)
                    && PotionUtil.getPotion(stack) == Potions.WATER) {
                player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, Items.GLASS_BOTTLE.getDefaultStack(), false));
                world.playSound(player, player.getX(), player.getY(), player.getZ(), USounds.Vanilla.ITEM_BUCKET_EMPTY, SoundCategory.NEUTRAL, 1, 1);
                world.emitGameEvent(player, GameEvent.FLUID_PLACE, pos);
                updateMoisture(soakable, state, world, pos, soakable.getMoisture(state) + 1);

                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.PASS;
    }

    static void tickMoisture(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (state.getBlock() instanceof Soakable soakable) {
            int moisture = soakable.getMoisture(state);

            if (world.hasRain(pos) && world.isAir(pos.up())) {
                if (moisture < 7) {
                    world.setBlockState(pos, soakable.getStateWithMoisture(state, moisture + 1));
                }
            } else if (!world.isAir(pos.up())) {
                if (moisture > 1) {
                    BlockPos neighborPos = pos.offset(Util.getRandom(Soakable.DIRECTIONS, random));
                    BlockState neighborState = world.getBlockState(neighborPos);

                    if (neighborState.getBlock() instanceof Soakable neighborSoakable && neighborSoakable.getMoisture(neighborState) < moisture) {
                        int half = Math.max(1, moisture / 2);
                        @Nullable
                        BlockState newNeighborState = neighborSoakable.getStateWithMoisture(neighborState, half);
                        if (newNeighborState != null) {
                            updateMoisture(soakable, state, world, pos, moisture - half);
                            world.setBlockState(neighborPos, soakable.getStateWithMoisture(state, half));
                            world.emitGameEvent(null, GameEvent.BLOCK_CHANGE, neighborPos);
                            return;
                        }
                    }
                }
                updateMoisture(soakable, state, world, pos, moisture - 1);
            }
        }
    }

    private static void updateMoisture(Soakable soakable, BlockState state, World world, BlockPos pos, int newMoisture) {
        world.setBlockState(pos, soakable.getStateWithMoisture(state, newMoisture));
        world.playSound(null, pos, SoundEvents.ENTITY_SALMON_FLOP, SoundCategory.BLOCKS, 1, (float)world.random.nextTriangular(0.5, 0.3F));
    }
}
