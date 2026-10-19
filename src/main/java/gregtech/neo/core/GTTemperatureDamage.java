package gregtech.neo.core;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class GTTemperatureDamage {
    private GTTemperatureDamage() {
    }

    // GT6 1.7.10: gregapi/util/UT.java:2978-2986.
    public static boolean apply(LivingEntity entity, long temperature, float multiplier) {
        return apply(entity, temperature, multiplier, Float.MAX_VALUE);
    }

    public static boolean apply(
        LivingEntity entity,
        long temperature,
        float multiplier,
        float cap
    ) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        Level level = entity.level();
        if (temperature > 320) {
            float damage = Math.max(
                1.0F,
                Math.min(cap, multiplier * (temperature - 300) / 50.0F)
            );
            entity.hurt(level.damageSources().onFire(), damage);
            return true;
        }
        if (temperature < 260) {
            float damage = Math.max(
                1.0F,
                Math.min(cap, multiplier * (270 - temperature) / 25.0F)
            );
            entity.hurt(level.damageSources().freeze(), damage);
            return true;
        }
        return false;
    }
}
