package com.minelittlepony.unicopia.ability;

import org.lwjgl.glfw.GLFW;

import net.minecraft.util.Identifier;
import net.minecraft.util.registry.MutableRegistry;
import net.minecraft.util.registry.SimpleRegistry;

public interface Abilities {
    MutableRegistry<Integer> KEYS_CODES = new SimpleRegistry<>();
    MutableRegistry<Ability<?>> REGISTRY = new SimpleRegistry<>();

    // unicorn
    Ability<?> TELEPORT = register(new UnicornTeleportAbility(), "teleport", GLFW.GLFW_KEY_O);
    Ability<?> CAST = register(new UnicornCastingAbility(), "cast", GLFW.GLFW_KEY_P);

    // earth
    Ability<?> GROW = register(new EarthPonyGrowAbility(), "grow", GLFW.GLFW_KEY_N);
    Ability<?> STOMP = register(new EarthPonyStompAbility(), "stomp", GLFW.GLFW_KEY_M);

    // pegasus
    Ability<?> CARRY = register(new PegasusCarryAbility(), "carry", GLFW.GLFW_KEY_K);
    Ability<?> CLOUD = register(new PegasusCloudInteractionAbility(), "cloud", GLFW.GLFW_KEY_J);

    // changeling
    Ability<?> FEED = register(new ChangelingFeedAbility(), "feed", GLFW.GLFW_KEY_O);
    Ability<?> TRAP = register(new ChangelingTrapAbility(), "trap", GLFW.GLFW_KEY_L);

    Ability<?> DISGUISE = register(new ChangelingDisguiseAbility(), "disguise", GLFW.GLFW_KEY_P);

    static <T extends Ability<?>> T register(T power, String name, int keyCode) {
        Identifier id = new Identifier("unicopia", name);
        KEYS_CODES.add(id, keyCode);
        return REGISTRY.add(id, power);
    }
}
