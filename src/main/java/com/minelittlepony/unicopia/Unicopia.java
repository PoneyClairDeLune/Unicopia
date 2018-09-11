package com.minelittlepony.unicopia;

import net.minecraft.entity.Entity;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.minelittlepony.jumpingcastle.api.IChannel;
import com.minelittlepony.jumpingcastle.api.JumpingCastle;
import com.minelittlepony.unicopia.client.particle.EntityMagicFX;
import com.minelittlepony.unicopia.client.particle.Particles;
import com.minelittlepony.unicopia.command.Commands;
import com.minelittlepony.unicopia.input.Keyboard;
import com.minelittlepony.unicopia.network.MsgPlayerAbility;
import com.minelittlepony.unicopia.network.MsgPlayerCapabilities;
import com.minelittlepony.unicopia.player.PlayerSpeciesList;
import com.minelittlepony.unicopia.power.PowersRegistry;

import come.minelittlepony.unicopia.forgebullshit.FBS;

@Mod(modid = Unicopia.MODID, name = Unicopia.NAME, version = Unicopia.VERSION)
@EventBusSubscriber
public class Unicopia {
    public static final String MODID = "unicopia";
    public static final String NAME = "@NAME@";
    public static final String VERSION = "@VERSION@";

    public static IChannel channel;

    public static int MAGIC_PARTICLE;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {

    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        channel = JumpingCastle.listen(MODID)
        .consume(MsgPlayerCapabilities.class, (msg, channel) -> {
            PlayerSpeciesList.instance().handleSpeciesChange(msg.senderId, msg.newRace);
        })
        .consume(MsgPlayerAbility.class, (msg, channel) -> {
            msg.applyServerAbility();
        });

        MAGIC_PARTICLE = Particles.instance().registerParticle(new EntityMagicFX.Factory());

        PowersRegistry.instance().init();

        FBS.init();
    }

    @SubscribeEvent
    public static void registerItemsStatic(RegistryEvent.Register<Item> event) {
        // Why won't you run!?
        UItems.registerItems();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerLoggedInEvent event) {
        PlayerSpeciesList.instance().sendCapabilities(event.player.getGameProfile().getId());
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onGameTick(TickEvent.ClientTickEvent event) {
        if (event.phase == Phase.END) {
            Keyboard.getKeyHandler().onKeyInput();
        }
    }

    @SubscribeEvent
    public static void onPlyerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == Phase.END) {
            PlayerSpeciesList.instance().getPlayer(event.player).onEntityUpdate();
        }
    }

    @EventHandler
    public void onServerStarted(FMLServerStartingEvent event) {
        Commands.init(event);
    }

    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent.RightClickItem event) {
        // Why won't you run!?
        if (!event.isCanceled()
            && event.getItemStack().getItemUseAction() == EnumAction.EAT) {
            PlayerSpeciesList.instance().getPlayer(event.getEntityPlayer()).onEntityEat();
        }
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        // Why won't you run!?
        FBS.attach(event);
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        FBS.clone(event);
    }
}
