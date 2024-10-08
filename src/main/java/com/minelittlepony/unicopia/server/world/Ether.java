package com.minelittlepony.unicopia.server.world;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.minelittlepony.unicopia.Unicopia;
import com.minelittlepony.unicopia.ability.magic.Caster;
import com.minelittlepony.unicopia.ability.magic.spell.Spell;
import com.minelittlepony.unicopia.ability.magic.spell.effect.SpellType;
import com.minelittlepony.unicopia.entity.EntityReference;
import com.minelittlepony.unicopia.server.world.chunk.PositionalDataMap;
import com.minelittlepony.unicopia.util.NbtSerialisable;
import com.minelittlepony.unicopia.util.Tickable;

import net.minecraft.nbt.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

public class Ether extends PersistentState implements Tickable {
    private static final Identifier ID = Unicopia.id("ether");

    public static Ether get(World world) {
        return WorldOverlay.getPersistableStorage(world, ID, Ether::new, Ether::new);
    }

    private final Map<Identifier, Map<UUID, Map<UUID, Entry<?>>>> endpoints;
    private final PositionalDataMap<Entry<?>> positionData = new PositionalDataMap<>();

    private final Object locker = new Object();

    private final World world;

    Ether(World world, NbtCompound compound) {
        this.world = world;
        this.endpoints = NbtSerialisable.readMap(compound.getCompound("endpoints"), Identifier::tryParse, typeNbt -> {
            return NbtSerialisable.readMap((NbtCompound)typeNbt, UUID::fromString, entityNbt -> {
                return NbtSerialisable.readMap((NbtCompound)entityNbt, UUID::fromString, nbt -> new Entry<>(nbt));
            });
        });
    }

    Ether(World world) {
        this.world = world;
        this.endpoints = new HashMap<>();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound compound) {
        synchronized (locker) {
            pruneNodes();
            compound.put("endpoints", NbtSerialisable.writeMap(endpoints, Identifier::toString, entities -> {
                return NbtSerialisable.writeMap(entities, UUID::toString, spells -> {
                    return NbtSerialisable.writeMap(spells, UUID::toString, Entry::toNBT);
                });
            }));
            return compound;
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Spell> Entry<T> getOrCreate(T spell, Caster<?> caster) {
        synchronized (locker) {
            Entry<T> entry = (Entry<T>)endpoints
                    .computeIfAbsent(spell.getTypeAndTraits().type().getId(), typeId -> new HashMap<>())
                    .computeIfAbsent(caster.asEntity().getUuid(), entityId -> new HashMap<>())
                    .computeIfAbsent(spell.getUuid(), spellid -> {
                        markDirty();
                        return new Entry<>(spell, caster);
                    });

            if (entry.spell.get() != spell) {
                entry.spell = new WeakReference<>(spell);
                markDirty();
            }
            if (entry.removed) {
                entry.removed = false;
                positionData.update(entry);
                markDirty();
            }
            return entry;
        }
    }

    @Override
    public void tick() {
        endpoints.values().forEach(byType -> {
            byType.values().forEach(entries -> {
                entries.values().forEach(Entry::update);
            });
        });
    }

    public <T extends Spell> void remove(SpellType<T> spellType, UUID entityId) {
        synchronized (locker) {
            endpoints.computeIfPresent(spellType.getId(), (typeId, entries) -> {
                Map<UUID, Entry<?>> data = entries.remove(entityId);
                if (data != null) {
                    markDirty();
                    data.values().forEach(positionData::remove);
                }
                return entries.isEmpty() ? null : entries;
            });
        }
    }

    public void remove(SpellType<?> spellType, Caster<?> caster) {
        remove(spellType, caster.asEntity().getUuid());
    }

    public <T extends Spell> void remove(T spell, Caster<?> caster) {
        Entry<T> entry = get(spell, caster);
        if (entry != null) {
            entry.markDead();
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Spell> Entry<T> get(T spell, Caster<?> caster) {
        return get((SpellType<T>)spell.getTypeAndTraits().type(), caster.asEntity().getUuid(), spell.getUuid());
    }

    public <T extends Spell> Entry<T> get(SpellType<T> spell, EntityReference.EntityValues<?> entityId, @Nullable UUID spellId) {
        return get(spell, entityId.uuid(), spellId);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends Spell> Entry<T> get(SpellType<T> spell, UUID entityId, @Nullable UUID spellId) {
        if (spellId == null) {
            return null;
        }
        synchronized (locker) {
            Entry<?> entry = endpoints
                    .getOrDefault(spell.getId(), Map.of())
                    .getOrDefault(entityId, Map.of())
                    .get(spellId);
            return entry == null || entry.isDead() ? null : (Entry<T>)entry;
        }
    }

    public <T extends Spell> boolean anyMatch(SpellType<T> spellType, BiPredicate<T, Caster<?>> condition) {
        return anyMatch(spellType, entry -> {
            var spell = entry.getSpell();
            var caster = entry.getCaster();
            return spell != null && caster != null && condition.test(spell, caster);
        });
    }

    @SuppressWarnings("unchecked")
    public <T extends Spell> boolean anyMatch(SpellType<T> spellType, Predicate<Entry<T>> condition) {
        synchronized (locker) {
            for (var entries : endpoints.getOrDefault(spellType.getId(), Map.of()).values()) {
                for (var entry : entries.values()) {
                    if (!entry.isDead() && condition.test((Entry<T>)entry)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Set<Entry<?>> getAtPosition(BlockPos pos) {
        return world.isClient() ? Set.of() : positionData.getState(pos);
    }

    private void pruneNodes() {
        this.endpoints.values().removeIf(entities -> {
            entities.values().removeIf(spells -> {
                spells.values().removeIf(Entry::isDead);
                return spells.isEmpty();
            });
            return entities.isEmpty();
        });
    }

    public class Entry<T extends Spell> implements PositionalDataMap.Hotspot, NbtSerialisable {
        public final EntityReference<?> entity;

        @Nullable
        private UUID spellId;
        private WeakReference<T> spell;

        private boolean removed;

        private float pitch;
        private final AtomicBoolean changed = new AtomicBoolean(true);
        private float yaw;
        private float radius;

        private final Set<UUID> claimants = new HashSet<>();

        private BlockPos currentPos = BlockPos.ORIGIN;
        private BlockPos previousPos = BlockPos.ORIGIN;

        private Entry(NbtElement nbt) {
            this.entity = new EntityReference<>();
            this.spell = new WeakReference<>(null);
            this.fromNBT((NbtCompound)nbt);
        }

        public Entry(T spell, Caster<?> caster) {
            this.entity = new EntityReference<>(caster.asEntity());
            this.spell = new WeakReference<>(spell);
            spellId = spell.getUuid();
            update();
        }

        void update() {
            previousPos = currentPos;
            currentPos = entity.getTarget().map(t -> BlockPos.ofFloored(t.pos())).orElse(BlockPos.ORIGIN);
            if (!currentPos.equals(previousPos)) {
                positionData.update(this);
            }
        }

        public boolean hasChanged() {
            return changed.getAndSet(false);
        }

        public float getPitch() {
            return pitch;
        }

        public void setPitch(float pitch) {
            if (!MathHelper.approximatelyEquals(this.pitch, pitch)) {
                this.pitch = pitch;
                changed.set(true);
            }
            markDirty();
        }

        public float getYaw() {
            return yaw;
        }

        public void setYaw(float yaw) {
            if (!MathHelper.approximatelyEquals(this.yaw, yaw)) {
                this.yaw = yaw;
                changed.set(true);
            }
            markDirty();
        }


        @Override
        public BlockPos getCenter() {
            return currentPos;
        }

        @Override
        public float getRadius() {
            return radius;
        }

        public void setRadius(float radius) {
            if (!MathHelper.approximatelyEquals(this.radius, radius)) {
                this.radius = radius;
                if ((int)radius != (int)this.radius) {
                    positionData.update(this);
                }
                changed.set(true);
            }
            markDirty();
        }

        public boolean isAlive() {
            return !isDead();
        }

        boolean isDead() {
            if (!removed) {
                getSpell();
            }
            return removed;
        }

        @Nullable
        public UUID getSpellId() {
            return spellId;
        }

        public void markDead() {
            Unicopia.LOGGER.debug("Marking " + entity.getTarget().orElse(null) + " as dead");
            removed = true;
            positionData.remove(this);
            claimants.clear();
            markDirty();
        }

        public boolean entityMatches(UUID uuid) {
            return entity.getTarget().filter(target -> uuid.equals(target.uuid())).isPresent();
        }

        public void claim(UUID claimant) {
            claimants.add(claimant);
            markDirty();
        }

        public void release(UUID claimant) {
            claimants.remove(claimant);
            markDirty();
        }

        public boolean isClaimedBy(UUID claimant) {
            return claimants.contains(claimant);
        }

        public boolean hasClaimant() {
            return !claimants.isEmpty();
        }

        @Nullable
        public T getSpell() {
            if (removed) {
                return null;
            }
            T spell = this.spell.get();
            if (spell == null) {
                if (spellId != null) {
                    spell = entity
                            .getOrEmpty(world)
                            .flatMap(Caster::of)
                            .flatMap(caster -> caster.getSpellSlot().<T>get(s -> s.getUuid().equals(spellId)))
                            .orElse(null);

                    if (spell != null) {
                        this.spell = new WeakReference<>(spell);
                    }
                }
            }

            if (spell != null && spell.isDead()) {
                spellId = null;
                spell = null;
                markDead();
            }

            return spell;
        }

        @Nullable
        public Caster<?> getCaster() {
            if (removed) {
                return null;
            }
            return Caster.of(this.entity.get(world)).orElse(null);
        }

        @Override
        public void toNBT(NbtCompound compound) {
            entity.toNBT(compound);
            compound.putBoolean("removed", removed);
            compound.putFloat("pitch", pitch);
            compound.putFloat("yaw", yaw);
            compound.putFloat("radius", radius);
            if (spellId != null) {
                compound.putUuid("spellId", spellId);
            }
            NbtList list = new NbtList();
            claimants.forEach(claimant -> {
                list.add(NbtHelper.fromUuid(claimant));
            });
            compound.put("claimants", list);
        }

        @Override
        public void fromNBT(NbtCompound compound) {
            entity.fromNBT(compound);
            removed = compound.getBoolean("removed");
            pitch = compound.getFloat("pitch");
            yaw = compound.getFloat("yaw");
            radius = compound.getFloat("radius");
            spellId = compound.containsUuid("spellid") ? compound.getUuid("spellId") : null;

            claimants.clear();
            if (compound.contains("claimants", NbtElement.LIST_TYPE)) {
                compound.getList("claimants", NbtElement.INT_ARRAY_TYPE).forEach(el -> {
                    claimants.add(NbtHelper.toUuid(el));
                });
            }
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Entry<?> e
                    && e.entity.referenceEquals(entity)
                    && Objects.equals(e.spell.get(), spell.get());
        }

        public boolean equals(UUID entityId, UUID spellId) {
            return entity.referenceEquals(entityId) && spellId.equals(this.spellId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entity, spell.get());
        }
    }
}
