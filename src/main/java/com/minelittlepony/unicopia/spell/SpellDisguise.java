package com.minelittlepony.unicopia.spell;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.minelittlepony.unicopia.Race;
import com.minelittlepony.unicopia.UClient;
import com.minelittlepony.unicopia.UParticles;
import com.minelittlepony.unicopia.mixin.MixinEntity;
import com.minelittlepony.unicopia.player.IFlyingPredicate;
import com.minelittlepony.unicopia.player.IOwned;
import com.minelittlepony.unicopia.player.IPlayer;
import com.minelittlepony.unicopia.player.IPlayerHeightPredicate;
import com.minelittlepony.unicopia.player.PlayerSpeciesList;
import com.minelittlepony.unicopia.render.DisguiseRenderer;
import com.minelittlepony.util.ProjectileUtil;
import com.mojang.authlib.GameProfile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.monster.EntityShulker;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityShulkerBullet;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntitySkull;

public class SpellDisguise extends AbstractSpell implements IFlyingPredicate, IPlayerHeightPredicate {

    @Nonnull
    private String entityId = "";

    @Nullable
    private Entity entity;

    @Nullable
    private NBTTagCompound entityNbt;

    @Override
    public String getName() {
        return "disguise";
    }

    @Override
    public boolean isCraftable() {
        return false;
    }

    @Override
    public SpellAffinity getAffinity() {
        return SpellAffinity.BAD;
    }

    @Override
    public int getTint() {
        return 0;
    }

    public Entity getDisguise() {
        return entity;
    }

    public SpellDisguise setDisguise(@Nullable Entity entity) {
        if (entity == this.entity) {
            entity = null;
        }
        this.entityNbt = null;
        this.entityId = "";

        removeDisguise();

        if (entity != null) {
            entityNbt = encodeEntityToNBT(entity);
            entityId = entityNbt.getString("id");
        }

        return this;
    }

    protected void removeDisguise() {
        if (entity != null) {
            entity.setDead();
            entity = null;
        }
    }

    protected NBTTagCompound encodeEntityToNBT(Entity entity) {
        NBTTagCompound entityNbt = new NBTTagCompound();

        if (entity instanceof EntityPlayer) {
            GameProfile profile = ((EntityPlayer)entity).getGameProfile();

            entityNbt.setString("id", "player");
            entityNbt.setUniqueId("playerId", profile.getId());
            entityNbt.setString("playerName", profile.getName());
            entityNbt.setTag("playerNbt", entity.writeToNBT(new NBTTagCompound()));
        } else {
            entityNbt = entity.writeToNBT(entityNbt);
            entityNbt.setString("id", EntityList.getKey(entity).toString());
        }

        return entityNbt;
    }

    protected synchronized void createPlayer(NBTTagCompound nbt, GameProfile profile, ICaster<?> source) {
        removeDisguise();

        entity = UClient.instance().createPlayer(source.getEntity(), profile);
        entity.setCustomNameTag(source.getOwner().getName());
        ((EntityPlayer)entity).readFromNBT(nbt.getCompoundTag("playerNbt"));
        entity.setUniqueId(UUID.randomUUID());

        PlayerSpeciesList.instance().getPlayer((EntityPlayer)entity).setEffect(null);

        if (entity != null && source.getWorld().isRemote) {
            source.getWorld().spawnEntity(entity);
        }
    }

    protected void checkAndCreateDisguiseEntity(ICaster<?> source) {
        if (entity == null && entityNbt != null) {
            if ("player".equals(entityId)) {

                NBTTagCompound nbt = entityNbt;
                entityNbt = null;

                createPlayer(nbt, new GameProfile(
                        nbt.getUniqueId("playerId"),
                        nbt.getString("playerName")
                    ), source);
                new Thread(() -> createPlayer(nbt, TileEntitySkull.updateGameProfile(new GameProfile(
                    null,
                    nbt.getString("playerName")
                )), source)).start();
            } else {
                entity = EntityList.createEntityFromNBT(entityNbt, source.getWorld());
            }

            if (entity != null && source.getWorld().isRemote) {
                source.getWorld().spawnEntity(entity);
            }

            entityNbt = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean update(ICaster<?> source) {
        checkAndCreateDisguiseEntity(source);

        EntityLivingBase owner = source.getOwner();

        if (owner == null) {
            return true;
        }

        if (entity != null) {
            entity.onGround = owner.onGround;

            if (!(entity instanceof EntityFallingBlock || entity instanceof EntityPlayer)) {
                entity.onUpdate();
            }

            entity.copyLocationAndAnglesFrom(owner);

            entity.setNoGravity(true);

            entity.getEntityData().setBoolean("disguise", true);

            entity.lastTickPosX = owner.lastTickPosX;
            entity.lastTickPosY = owner.lastTickPosY;
            entity.lastTickPosZ = owner.lastTickPosZ;

            entity.prevPosX = owner.prevPosX;
            entity.prevPosY = owner.prevPosY;
            entity.prevPosZ = owner.prevPosZ;

            entity.motionX = owner.motionX;
            entity.motionY = owner.motionY;
            entity.motionZ = owner.motionZ;

            entity.prevRotationPitch = owner.prevRotationPitch;
            entity.prevRotationYaw = owner.prevRotationYaw;

            entity.distanceWalkedOnStepModified = owner.distanceWalkedOnStepModified;
            entity.distanceWalkedModified = owner.distanceWalkedModified;
            entity.prevDistanceWalkedModified = owner.prevDistanceWalkedModified;

            if (entity instanceof EntityLivingBase) {
                EntityLivingBase l = (EntityLivingBase)entity;

                l.rotationYawHead = owner.rotationYawHead;
                l.prevRotationYawHead = owner.prevRotationYawHead;
                l.renderYawOffset = owner.renderYawOffset;
                l.prevRenderYawOffset = owner.prevRenderYawOffset;

                l.limbSwing = owner.limbSwing;
                l.limbSwingAmount = owner.limbSwingAmount;
                l.prevLimbSwingAmount = owner.prevLimbSwingAmount;

                l.swingingHand = owner.swingingHand;
                l.swingProgress = owner.swingProgress;
                l.swingProgressInt = owner.swingProgressInt;
                l.isSwingInProgress = owner.isSwingInProgress;

                l.hurtTime = owner.hurtTime;
                l.deathTime = owner.deathTime;
                l.setHealth(owner.getHealth());

                for (EntityEquipmentSlot i : EntityEquipmentSlot.values()) {
                    ItemStack neu = owner.getItemStackFromSlot(i);
                    ItemStack old = l.getItemStackFromSlot(i);
                    if (old != neu) {
                        l.setItemStackToSlot(i, neu);
                    }
                }

                if (l instanceof EntityShulker) {
                    l.rotationYaw = 0;

                    l.renderYawOffset = 0;
                    l.prevRenderYawOffset = 0;
                }
            }

            if (isAttachedEntity(entity)) {

                entity.posX = Math.floor(owner.posX) + 0.5;
                entity.posY = Math.floor(owner.posY + 0.2);
                entity.posZ = Math.floor(owner.posZ) + 0.5;

                entity.lastTickPosX = entity.posX;
                entity.lastTickPosY = entity.posY;
                entity.lastTickPosZ = entity.posZ;

                entity.prevPosX = entity.posX;
                entity.prevPosY = entity.posY;
                entity.prevPosZ = entity.posZ;
            }

            if (entity instanceof EntityShulker) {
                EntityShulker shulker = ((EntityShulker)entity);

                shulker.setAttachmentPos(null);

                if (source.getWorld().isRemote && source instanceof IPlayer) {
                    IPlayer player = (IPlayer)source;


                    float peekAmount = 0.3F;

                    if (!owner.isSneaking()) {
                        float speed = (float)Math.sqrt(Math.pow(owner.motionX, 2) + Math.pow(owner.motionZ, 2));

                        peekAmount = speed * 30;
                        if (peekAmount > 1) {
                            peekAmount = 1;
                        }
                    }

                    peekAmount = player.getInterpolator().interpolate("peek", peekAmount, 5);

                    MixinEntity.Shulker.setPeek(shulker, peekAmount);
                }
            }

            if (entity instanceof EntityLiving) {
                EntityLiving l = (EntityLiving)entity;

                l.setNoAI(true);
            }

            if (entity instanceof EntityMinecart) {
                entity.rotationYaw += 90;
                entity.rotationPitch = 0;
            }

            if (entity instanceof EntityPlayer) {
                EntityPlayer l = (EntityPlayer)entity;

                l.chasingPosX = l.posX;
                l.chasingPosY = l.posY;
                l.chasingPosZ = l.posZ;
            }

            if (owner.isBurning()) {
                entity.setFire(1);
            } else {
                entity.extinguish();
            }

            entity.noClip = true;
            entity.updateBlocked = true;

            entity.setSneaking(owner.isSneaking());
            entity.setInvisible(false);

            if (source instanceof IPlayer) {
                ((IPlayer) source).setInvisible(true);
            }

            owner.setInvisible(true);

            if (entity instanceof EntityTameable) {
                ((EntityTameable)entity).setSitting(owner.isSneaking());
            }

            if (owner instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer)owner;

                if (entity instanceof IOwned) {
                    IOwned.cast(entity).setOwner(player);
                }

                if (entity instanceof EntityPlayer) {
                    entity.getDataManager().set(MixinEntity.Player.getModelFlag(), owner.getDataManager().get(MixinEntity.Player.getModelFlag()));
                }

                if (UClient.instance().isClientPlayer(player)) {
                    entity.setAlwaysRenderNameTag(false);
                    entity.setCustomNameTag("");

                    if (UClient.instance().getViewMode() == 0) {
                        entity.setInvisible(true);
                        entity.posY = -Integer.MIN_VALUE;
                    }
                } else {
                    entity.setAlwaysRenderNameTag(true);
                    entity.setCustomNameTag(player.getName());
                }
            }

            if (!(source instanceof IPlayer) || ((IPlayer) source).getPlayerSpecies() == Race.CHANGELING) {
                return true;
            }
        }

        owner.setInvisible(false);

        if (source instanceof IPlayer) {
            ((IPlayer) source).setInvisible(false);
        }


        return false;
    }

    @Override
    public void setDead() {
        super.setDead();
        removeDisguise();
    }

    @Override
    public void render(ICaster<?> source) {
        if (source.getWorld().rand.nextInt(30) == 0) {
            source.spawnParticles(UParticles.CHANGELING_MAGIC, 2);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);

        compound.setString("entityId", entityId);
        compound.setBoolean("dead", getDead());

        if (entityNbt != null) {
            compound.setTag("entity", entityNbt);
        } else if (entity != null) {
            compound.setTag("entity", encodeEntityToNBT(entity));
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);

        String newId = compound.getString("entityId");

        if (!newId.contentEquals(entityId)) {
            entityNbt = null;
            removeDisguise();
        }

        if (compound.hasKey("entity")) {
            entityId = newId;

            entityNbt = compound.getCompoundTag("entity");

            if (entity != null) {
                entity.readFromNBT(entityNbt);
            }
        }
    }

    @Override
    public boolean checkCanFly(IPlayer player) {
        if (entity == null || !player.getPlayerSpecies().canFly()) {
            return false;
        }

        if (entity instanceof IOwned) {
            IPlayer iplayer = PlayerSpeciesList.instance().getPlayer(IOwned.<EntityPlayer>cast(entity).getOwner());

            return iplayer != null && iplayer.getPlayerSpecies().canFly();
        }

        return entity instanceof EntityFlying
                || entity instanceof net.minecraft.entity.passive.EntityFlying
                || entity instanceof EntityDragon
                || entity instanceof EntityAmbientCreature
                || entity instanceof EntityShulkerBullet
                || ProjectileUtil.isProjectile(entity);
    }

    @Override
    public float getTargetEyeHeight(IPlayer player) {
        if (entity != null) {
            if (entity instanceof EntityFallingBlock) {
                return 0.5F;
            }
            return entity.getEyeHeight();
        }
        return -1;
    }

    @Override
    public float getTargetBodyHeight(IPlayer player) {
        if (entity != null) {
            if (entity instanceof EntityFallingBlock) {
                return 0.9F;
            }
            return entity.height - 0.1F;
        }
        return -1;
    }

    public static boolean isAttachedEntity(Entity entity) {
        return entity instanceof EntityShulker
            || entity instanceof EntityFallingBlock;
    }
}
