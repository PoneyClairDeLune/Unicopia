package com.minelittlepony.unicopia.block;

import java.util.List;

import com.minelittlepony.unicopia.ability.EarthPonyGrowAbility.Growable;
import com.minelittlepony.unicopia.entity.mob.IgnominiousBulbEntity;
import com.minelittlepony.unicopia.particle.MagicParticleEffect;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.block.BlockState;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.SuspiciousStewIngredient;
import net.minecraft.client.util.ParticleUtil;
import net.minecraft.entity.Dismounting;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class CuringJokeBlock extends FlowerBlock implements Growable {
    private static final MapCodec<CuringJokeBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            STEW_EFFECT_CODEC.forGetter(FlowerBlock::getStewEffects),
            FlowerBlock.createSettingsCodec()
    ).apply(instance, CuringJokeBlock::new));

    public CuringJokeBlock(StatusEffect suspiciousStewEffect, int effectDuration, Settings settings) {
        super(suspiciousStewEffect, effectDuration, settings);
    }

    protected CuringJokeBlock(List<SuspiciousStewIngredient.StewEffect> effects, Settings settings) {
        super(effects, settings);
    }

    @Override
    public MapCodec<? extends FlowerBlock> getCodec() {
        return CODEC;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        for (int i = 0; i < 3; i++) {
            ParticleUtil.spawnParticle(world, pos, random, new MagicParticleEffect(0x3388EE));
        }
    }

    @Override
    public boolean grow(World world, BlockState state, BlockPos pos) {
        var otherFlowers = BlockPos.streamOutwards(pos, 16, 16, 16)
                .filter(p -> world.getBlockState(p).isOf(this))
                .map(BlockPos::toImmutable)
                .toList();

        IgnominiousBulbEntity bulb = new IgnominiousBulbEntity(world);
        bulb.setBaby(true);
        bulb.updatePositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);

        if (Dismounting.canPlaceEntityAt(world, bulb, bulb.getBoundingBox())) {
            otherFlowers.forEach(p -> world.breakBlock(p, false));
            world.spawnEntity(bulb);
            return true;
        }

        return false;
    }
}
