package dev.waterdog.waterdogpe.debug;

import dev.waterdog.waterdogpe.logger.MainLogger;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.common.util.TypeMap;

import java.lang.reflect.Field;

public final class FlagDump {

    private FlagDump() {}

    public static void dumpAll(MainLogger log) {
        // dump whichever versions you actually ship; 844 is the latest in your tree
        dumpFor("org.cloudburstmc.protocol.bedrock.codec.v844.Bedrock_v844", log);
        dumpFor("org.cloudburstmc.protocol.bedrock.codec.v827.Bedrock_v827", log);
        dumpFor("org.cloudburstmc.protocol.bedrock.codec.v776.Bedrock_v776", log);
        // add more if you want (v766, v729, etc.)
    }

    @SuppressWarnings("unchecked")
    private static void dumpFor(String className, MainLogger log) {
        try {
            Class<?> cls = Class.forName(className);
            Field f = cls.getDeclaredField("ENTITY_FLAGS"); // protected static final TypeMap<EntityFlag>
            f.setAccessible(true);
            TypeMap<EntityFlag> map = (TypeMap<EntityFlag>) f.get(null);

            log.info("========== EntityFlag ID table [{}] ==========", className.substring(className.lastIndexOf('.') + 1));
            // iterate by ID so you can see holes/shifts; 0..160 is enough, bump if you need
            for (int i = 0; i < 160; i++) {
                EntityFlag flag = map.getType(i);
                if (flag != null) {
                    log.info("ID {:>3} -> {}", i, flag.name());
                }
            }
            // sanity checks for the usual suspects
            log.info("Check: SNEAKING={} CAN_CLIMB={} GLIDING={} HAS_GRAVITY={}",
                    map.getId(EntityFlag.SNEAKING),
                    map.getId(EntityFlag.CAN_CLIMB),
                    map.getId(EntityFlag.GLIDING),
                    map.getId(EntityFlag.HAS_GRAVITY));
            log.info("=====================================================");
        } catch (Throwable t) {
            log.warning("FlagDump failed for {}: {}", className, t.toString());
        }
    }
}
