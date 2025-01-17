package com.minelittlepony.unicopia.projectile;

import org.jetbrains.annotations.Nullable;

import com.minelittlepony.unicopia.util.SoundEmitter;

import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.ProjectileDispenserBehavior;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ProjectileItem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Unit;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Position;
import net.minecraft.world.World;

public interface Projectile extends ItemConvertible, ProjectileItem {
    ProjectileItem.Settings SETTINGS = ProjectileItem.Settings
            .builder()
            .uncertainty(0)
            .build();

    static void makeDispensable(Projectile projectile) {
        DispenserBlock.registerBehavior(projectile.asItem(), new ProjectileDispenserBehavior(projectile.asItem()));
    }

    @Override
    default ProjectileEntity createEntity(World world, Position pos, ItemStack stack, Direction direction) {
        ProjectileEntity entity = createProjectile(stack.copyWithCount(1), world, null);
        entity.setPosition(pos.getX(), pos.getY(), pos.getZ());
        if (entity instanceof PersistentProjectileEntity p) {
            p.pickupType = PersistentProjectileEntity.PickupPermission.ALLOWED;
        }
        return entity;
    }

    @Override
    default ProjectileItem.Settings getProjectileSettings() {
        return SETTINGS;
    }

    default TypedActionResult<ItemStack> triggerThrow(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        if (!world.isClient) {
            SoundEmitter.playSoundAt(player,
                    getThrowSound(stack), SoundCategory.NEUTRAL,
                    0.5F,
                    0.4F / (world.random.nextFloat() * 0.4F + 0.8F));

            ItemStack projectileStack = stack.splitUnlessCreative(1, player);
            if (player.isCreative()) {
                projectileStack.set(DataComponentTypes.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
            }

            world.spawnEntity(createProjectile(projectileStack, world, player));
        }

        player.incrementStat(Stats.USED.getOrCreateStat(asItem()));

        if (!player.isCreative()) {
            stack.decrement(1);
        }

        return TypedActionResult.success(stack, world.isClient());
    }

    default ProjectileEntity createProjectile(ItemStack stack, World world, @Nullable PlayerEntity player) {
        MagicProjectileEntity projectile = player == null ? new MagicProjectileEntity(world) : new MagicProjectileEntity(world, player);
        projectile.setItem(stack);
        projectile.setThrowDamage(getProjectileDamage(stack));
        projectile.setMaxAge(-1);
        if (player != null) {
            projectile.setVelocity(player, player.getPitch(), player.getYaw(), 0, 1.5F, 1);
        }
        return projectile;
    }

    SoundEvent getThrowSound(ItemStack stack);

    float getProjectileDamage(ItemStack stack);
}
