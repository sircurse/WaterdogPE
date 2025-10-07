package dev.waterdog.waterdogpe.debug;

import dev.waterdog.waterdogpe.logger.MainLogger;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.common.util.TypeMap;

public final class FlagIds {
    private static volatile TypeMap<EntityFlag> MAP;

    private FlagIds() {}

    @SuppressWarnings("unchecked")
    public static void init(MainLogger log) {
        if (MAP != null) return;
        try {
            Class<?> v844 = Class.forName("org.cloudburstmc.protocol.bedrock.codec.v844.Bedrock_v844");
            var f = v844.getDeclaredField("ENTITY_FLAGS");
            f.setAccessible(true);
            MAP = (TypeMap<EntityFlag>) f.get(null);

            // TypeMap has no size(); count mapped entries instead
            int mapped = 0;
            for (EntityFlag flag : EntityFlag.values()) {
                int id = MAP.getId(flag);
                if (id >= 0) mapped++;
            }
            log.info("[FlagIds] Loaded Cloudburst ENTITY_FLAGS (mapped {} of {})",
                    mapped, EntityFlag.values().length);
        } catch (Throwable t) {
            MAP = null;
            log.warning("[FlagIds] Failed to load ENTITY_FLAGS: {}", t.toString());
        }
    }

    public static int idOf(EntityFlag f) {
        TypeMap<EntityFlag> m = MAP;
        return (m == null) ? -1 : m.getId(f);
    }
}
