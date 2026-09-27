package com.heavenscollapse.util;

import com.heavenscollapse.HeavensCollapsePlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays the "divine lightning strike" cinematic: sounds, particles and an
 * optional lightning bolt. Everything here is purely presentational - it
 * never touches health or damage.
 *
 * <p>Every world interaction takes explicit {@link Location}/{@link World}
 * parameters captured by the caller <em>before</em> the killing blow is
 * applied, so this class never depends on the target entity still being
 * alive or even still existing.</p>
 */
public class EffectsUtil {

    /** Directions the outward warden-particle burst travels in (a full sphere). */
    private static final int WARDEN_BURST_POINTS = 48;

    /** How many expanding steps the burst takes before it finishes. */
    private static final int WARDEN_BURST_STEPS = 14;

    /** Final radius, in blocks, the burst reaches. */
    private static final double WARDEN_BURST_MAX_RADIUS = 8.0;

    /** Delays (in ticks) of the echoing Sonic Boom pulses after the first. */
    private static final long[] ECHO_BOOM_DELAYS_TICKS = {5L, 10L, 16L};

    /** How long the storm cloud hangs and charges before the bolt actually strikes. */
    private static final long STRIKE_WINDUP_TICKS = 6L;

    private static final Color WHITE = Color.fromRGB(255, 255, 255);
    private static final Color LIGHTNING_YELLOW = Color.fromRGB(255, 221, 64);

    /** Blocks the ground can scorch into - mostly dark stone, with some coal for speckle. */
    private static final Material[] SCORCH_MATERIALS = {
            Material.BLACKSTONE, Material.BLACKSTONE, Material.BASALT, Material.COAL_BLOCK
    };

    /** Only natural terrain blocks scorch - player builds and anything else are left alone. */
    private static final Set<Material> SCORCHABLE_MATERIALS = EnumSet.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.PODZOL,
            Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.STONE,
            Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.MYCELIUM,
            Material.MOSS_BLOCK, Material.SNOW_BLOCK, Material.SNOW
    );

    private final HeavensCollapsePlugin plugin;

    public EffectsUtil(HeavensCollapsePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Plays the full special-attack cinematic. A white-and-yellow storm
     * cloud gathers above the target first; a moment later the lightning
     * actually strikes, bursting white and yellow particles outward and
     * scorching the ground nearby, while the much larger shockwave erupts
     * outward from the WIELDER - this is the player's own divine power
     * surging out, not just something that happens to the victim.
     *
     * <p>All locations are captured up front, before any delay, so this
     * never depends on the target entity still being alive by the time the
     * delayed parts of the effect run.</p>
     */
    public void playSpecialAttack(Player attacker, LivingEntity target) {
        World world = target.getWorld();
        Location targetLoc = target.getLocation().add(0, 1.0, 0);
        Location attackerLoc = attacker.getLocation().add(0, 1.0, 0);

        spawnStormCloud(world, targetLoc);

        new BukkitRunnable() {
            @Override
            public void run() {
                playSounds(world, targetLoc, attackerLoc);
                strikeLightning(world, targetLoc);
                playParticles(world, targetLoc, attackerLoc);
                scorchGround(world, targetLoc);
            }
        }.runTaskLater(plugin, STRIKE_WINDUP_TICKS);
    }

    /**
     * A hovering cloud of white and yellow dust (plus a few vanilla Cloud
     * particles for puffiness) above the target - the lightning visibly
     * "charges up" here for {@value #STRIKE_WINDUP_TICKS} ticks before it
     * actually strikes.
     */
    private void spawnStormCloud(World world, Location targetLoc) {
        if (!plugin.isParticlesEnabled()) {
            return;
        }
        Location cloudLoc = targetLoc.clone().add(0, 4.5, 0);
        world.spawnParticle(Particle.DUST, cloudLoc, 70, 1.3, 0.7, 1.3, 0,
                new Particle.DustOptions(WHITE, 1.7f));
        world.spawnParticle(Particle.DUST, cloudLoc, 70, 1.3, 0.7, 1.3, 0,
                new Particle.DustOptions(LIGHTNING_YELLOW, 1.7f));
        world.spawnParticle(Particle.CLOUD, cloudLoc, 25, 1.0, 0.5, 1.0, 0.01);
    }

    private void playSounds(World world, Location targetLoc, Location attackerLoc) {
        if (!plugin.isSoundsEnabled()) {
            return;
        }
        world.playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 2.5f, 0.9f);
        world.playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 2.0f, 1.0f);
        world.playSound(targetLoc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 1.6f, 0.8f);
        world.playSound(targetLoc, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.7f);
        // The Trident's Channeling-strike thunder - distinct from the plain
        // lightning-bolt thunder above - layers in a second, slightly
        // different rumble for a fuller storm sound.
        world.playSound(targetLoc, Sound.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 1.8f, 1.0f);
        // The Warden's sonic boom is a rare, instantly recognizable vanilla
        // sound. Played from the WIELDER's location as the "source" of the
        // shockwave, at higher volume since the burst is now much bigger.
        world.playSound(attackerLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3.0f, 1.0f);
    }

    private void strikeLightning(World world, Location targetLoc) {
        if (!plugin.isLightningEnabled()) {
            return;
        }
        if (plugin.isLightningDamage()) {
            world.strikeLightning(targetLoc);
            if (!plugin.isLightningFire()) {
                extinguishNearbyFire(targetLoc);
            }
        } else {
            // Visual-only strike: no block damage, no fire, no extra
            // entity damage - just the bolt, flash and thunder.
            world.strikeLightningEffect(targetLoc);
        }
    }

    private void playParticles(World world, Location targetLoc, Location attackerLoc) {
        if (!plugin.isParticlesEnabled()) {
            return;
        }

        // Impact flourish right on the target - the moment judgment lands.
        world.spawnParticle(Particle.FLASH, targetLoc, 2, 0, 0, 0, 0);
        world.spawnParticle(Particle.END_ROD, targetLoc, 45, 0.4, 0.9, 0.4, 0.05);
        world.spawnParticle(Particle.ELECTRIC_SPARK, targetLoc, 35, 0.5, 1.0, 0.5, 0.15);
        world.spawnParticle(Particle.CLOUD, targetLoc, 18, 0.3, 0.3, 0.3, 0.02);
        world.spawnParticle(Particle.SCULK_CHARGE_POP, targetLoc, 25, 0.5, 0.5, 0.5, 0.05);
        world.spawnParticle(Particle.SONIC_BOOM, targetLoc, 1, 0, 0, 0, 0);
        // The storm cloud's white and yellow motes now burst outward from
        // the impact point, as if the cloud discharged straight into the
        // target.
        world.spawnParticle(Particle.DUST, targetLoc, 60, 0.7, 0.7, 0.7, 0,
                new Particle.DustOptions(WHITE, 1.4f));
        world.spawnParticle(Particle.DUST, targetLoc, 60, 0.7, 0.7, 0.7, 0,
                new Particle.DustOptions(LIGHTNING_YELLOW, 1.4f));

        spawnBeam(world, attackerLoc, targetLoc);

        // The big shockwave now erupts from the WIELDER outward in every
        // direction (a full sphere, not just a flat ring) - much larger
        // and longer-lived than the impact flourish above.
        spawnWardenBurst(world, attackerLoc);
        scheduleEchoBooms(world, attackerLoc);
    }

    /**
     * Spawns a short trail of spark particles from the attacker toward the
     * target so the strike reads as connected to the player rather than
     * appearing out of nowhere.
     */
    private void spawnBeam(World world, Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length < 0.5) {
            return;
        }
        direction.normalize();

        int points = (int) Math.min(20, Math.max(4, length * 2));
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            Location point = from.clone().add(direction.clone().multiply(length * t));
            world.spawnParticle(Particle.ELECTRIC_SPARK, point, 2, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /**
     * Animates a genuinely spherical shockwave of Warden-themed particles
     * (Sculk Soul wisps alternating with Sculk Charge Pop motes) shooting
     * outward from the wielder in every direction at once, growing larger
     * over {@value #WARDEN_BURST_STEPS} ticks. Directions are distributed
     * with a Fibonacci sphere so the burst looks like an even, expanding
     * globe rather than a flat ring. Runs as a repeating task so the
     * particles visibly travel outward instead of appearing already
     * spread out.
     */
    private void spawnWardenBurst(World world, Location center) {
        List<Vector> directions = fibonacciSphereDirections(WARDEN_BURST_POINTS);

        new BukkitRunnable() {
            int step = 1;

            @Override
            public void run() {
                if (step > WARDEN_BURST_STEPS) {
                    cancel();
                    return;
                }

                double radius = (WARDEN_BURST_MAX_RADIUS / WARDEN_BURST_STEPS) * step;
                // Alternate shells between the two particle types so the
                // burst reads as more than a single repeating layer.
                Particle shellParticle = (step % 2 == 0) ? Particle.SCULK_SOUL : Particle.SCULK_CHARGE_POP;

                for (Vector direction : directions) {
                    Location point = center.clone().add(direction.clone().multiply(radius));
                    world.spawnParticle(shellParticle, point, 1, 0, 0, 0, 0);
                }

                step++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Evenly distributes {@code count} unit-length direction vectors over
     * a sphere using the Fibonacci sphere method, so a burst built from
     * them expands as a uniform globe instead of clustering at the poles.
     */
    private List<Vector> fibonacciSphereDirections(int count) {
        List<Vector> directions = new ArrayList<>(count);
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));

        for (int i = 0; i < count; i++) {
            double y = 1.0 - (i / (double) (count - 1)) * 2.0;
            double radiusAtY = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = goldenAngle * i;
            double x = Math.cos(theta) * radiusAtY;
            double z = Math.sin(theta) * radiusAtY;
            directions.add(new Vector(x, y, z));
        }

        return directions;
    }

    /**
     * A handful of echoing Sonic Boom pulses, spaced out after the first,
     * from the wielder's location - a "rolling thunder" of booms rather
     * than one flat pulse, extending how long the whole effect reads.
     */
    private void scheduleEchoBooms(World world, Location center) {
        for (long delay : ECHO_BOOM_DELAYS_TICKS) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    world.spawnParticle(Particle.SONIC_BOOM, center, 1, 0, 0, 0, 0);
                }
            }.runTaskLater(plugin, delay);
        }
    }

    /**
     * Turns the natural terrain within {@code scorch-radius} blocks of the
     * target's feet into dark, burned-looking blocks (Blackstone/Basalt/
     * Coal Block), as if the lightning charred the ground. Only affects a
     * small allow-list of natural terrain materials - player-placed
     * structures, containers, ores, logs etc. are left untouched. If
     * {@code scorch-duration-seconds} is greater than 0, every changed
     * block is restored to what it was after that many seconds; set it to
     * 0 to leave the scorch marks permanent.
     */
    private void scorchGround(World world, Location center) {
        if (!plugin.isScorchGroundEnabled()) {
            return;
        }
        int radius = plugin.getScorchRadius();
        if (radius <= 0) {
            return;
        }

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        Map<Block, BlockData> changed = new HashMap<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                Block ground = findGroundBlock(world, centerX + dx, centerY, centerZ + dz);
                if (ground == null || !SCORCHABLE_MATERIALS.contains(ground.getType())) {
                    continue;
                }

                changed.put(ground, ground.getBlockData().clone());
                Material scorch = SCORCH_MATERIALS[random.nextInt(SCORCH_MATERIALS.length)];
                ground.setType(scorch, false);
            }
        }

        if (changed.isEmpty()) {
            return;
        }

        long durationTicks = plugin.getScorchDurationSeconds() * 20L;
        if (durationTicks <= 0) {
            // 0 (or negative) means permanent - leave the scorch marks.
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<Block, BlockData> entry : changed.entrySet()) {
                    entry.getKey().setBlockData(entry.getValue(), false);
                }
            }
        }.runTaskLater(plugin, durationTicks);
    }

    /**
     * Finds the topmost solid, non-liquid block at or just below the given
     * feet-level Y, scanning a few blocks down to tolerate slightly uneven
     * terrain (slabs, a shallow dip, etc.).
     */
    private Block findGroundBlock(World world, int x, int feetY, int z) {
        for (int offset = 0; offset <= 3; offset++) {
            Block candidate = world.getBlockAt(x, feetY - 1 - offset, z);
            if (candidate.getType().isSolid() && !candidate.isLiquid()) {
                return candidate;
            }
        }
        return null;
    }

    private void extinguishNearbyFire(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int radius = 2;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.clone().add(x, y, z).getBlock();
                    Material type = block.getType();
                    if (type == Material.FIRE || type == Material.SOUL_FIRE) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }
}
