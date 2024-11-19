package com.minelittlepony.unicopia.entity.effect;

import org.jetbrains.annotations.Nullable;

import com.minelittlepony.unicopia.USounds;
import com.minelittlepony.unicopia.util.TypedActionResult;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;

public class FoodPoisoningStatusEffect extends StatusEffect {

    FoodPoisoningStatusEffect(int color) {
        super(StatusEffectCategory.HARMFUL, color);
    }

    @Override
    public boolean applyUpdateEffect(ServerWorld world, LivingEntity entity, int amplifier) {
        boolean showParticles = entity.getStatusEffect(entity.getRegistryManager().getOrThrow(RegistryKeys.STATUS_EFFECT).getEntry(this)).shouldShowParticles();

        if (!entity.hasStatusEffect(StatusEffects.NAUSEA) && entity.getRandom().nextInt(12) == 0) {
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 1, true, showParticles, false));
        }

        if (entity instanceof PlayerEntity player) {
            player.getHungerManager().addExhaustion(0.5F);
        }

        if (EffectUtils.isPoisoned(entity) && entity.getRandom().nextInt(12) == 0 && !entity.hasStatusEffect(StatusEffects.POISON)) {
            StatusEffects.POISON.value().applyUpdateEffect(world, entity, 1);
        }

        return true;
    }

    @Override
    public void applyInstantEffect(ServerWorld world, @Nullable Entity source, @Nullable Entity attacker, LivingEntity target, int amplifier, double proximity) {
        applyUpdateEffect(world, target, amplifier);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        int i = 40 >> amplifier;
        return i <= 0 || duration % i == 0;
    }

    public static TypedActionResult<ItemStack> apply(ItemStack stack, PlayerEntity user) {
        @Nullable
        FoodComponent food = stack.get(DataComponentTypes.FOOD);

        if (food == null || !user.canConsume(food.canAlwaysEat()) || !user.hasStatusEffect(UEffects.FOOD_POISONING)) {
            return TypedActionResult.pass(stack);
        }

        user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(), USounds.Vanilla.ENTITY_PLAYER_BURP, SoundCategory.NEUTRAL,
                1,
                1 + (user.getWorld().random.nextFloat() - user.getWorld().random.nextFloat()) * 0.4f);
        if (!user.getWorld().isClient) {
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 1, true, false, false));
        }
        return TypedActionResult.fail(stack);
    }
}
