package com.minelittlepony.unicopia.particle;

import com.minelittlepony.unicopia.Unicopia;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;

public interface UParticles {
    ParticleType<MagicParticleEffect> UNICORN_MAGIC = register("unicorn_magic", FabricParticleTypes.complex(MagicParticleEffect.CODEC, MagicParticleEffect.PACKET_CODEC));
    SimpleParticleType CHANGELING_MAGIC = register("changeling_magic", FabricParticleTypes.simple());
    SimpleParticleType BUBBLE = register("bubble", FabricParticleTypes.simple());
    ParticleType<FootprintParticleEffect> FOOTPRINT = register("footprint", FabricParticleTypes.complex(FootprintParticleEffect.CODEC, FootprintParticleEffect.PACKET_CODEC));
    ParticleType<BlockStateParticleEffect> DUST_CLOUD = register("dust_cloud", FabricParticleTypes.complex(BlockStateParticleEffect::createCodec, BlockStateParticleEffect::createPacketCodec));

    ParticleType<OrientedBillboardParticleEffect> RAINBOOM_RING = register("rainboom_ring", FabricParticleTypes.complex(OrientedBillboardParticleEffect::createCodec, OrientedBillboardParticleEffect::createPacketCodec));
    ParticleType<TargetBoundParticleEffect> RAINBOOM_TRAIL = register("rainboom_trail", FabricParticleTypes.complex(TargetBoundParticleEffect::createCodec, TargetBoundParticleEffect::createPacketCodec));
    ParticleType<TargetBoundParticleEffect> WIND = register("wind", FabricParticleTypes.complex(TargetBoundParticleEffect::createCodec, TargetBoundParticleEffect::createPacketCodec));

    SimpleParticleType RAIN_DROPS = register("rain_drops", FabricParticleTypes.simple());

    ParticleType<SphereParticleEffect> SPHERE = register("sphere", FabricParticleTypes.complex(true, SphereParticleEffect::createCodec, SphereParticleEffect::createPacketCodec));
    ParticleType<SphereParticleEffect> DISK = register("disk", FabricParticleTypes.complex(true, SphereParticleEffect::createCodec, SphereParticleEffect::createPacketCodec));

    ParticleType<FollowingParticleEffect> HEALTH_DRAIN = register("health_drain", FabricParticleTypes.complex(true, FollowingParticleEffect::createCodec, FollowingParticleEffect::createPacketCodec));
    ParticleType<SpiralParticleEffect> SPIRAL = register("spiral", FabricParticleTypes.complex(true, SpiralParticleEffect.CODEC, SpiralParticleEffect.PACKET_CODEC));

    SimpleParticleType GROUND_POUND = register("ground_pound", FabricParticleTypes.simple());
    SimpleParticleType CLOUDS_ESCAPING = register("clouds_escaping", FabricParticleTypes.simple(true));

    ParticleType<LightningBoltParticleEffect> LIGHTNING_BOLT = register("lightning_bolt", FabricParticleTypes.complex(true, LightningBoltParticleEffect.CODEC, LightningBoltParticleEffect.PACKET_CODEC));
    SimpleParticleType SHOCKWAVE = register("shockwave", FabricParticleTypes.simple(true));

    static <T extends ParticleType<?>> T register(String name, T type) {
        return Registry.register(Registries.PARTICLE_TYPE, Unicopia.id(name), type);
    }

    static void bootstrap() {}
}
