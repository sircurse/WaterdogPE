package dev.waterdog.waterdogpe.debug;

import dev.waterdog.waterdogpe.logger.MainLogger;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.common.util.TypeMap;

import java.lang.reflect.Field;

public final class FlagDump {

    private FlagDump() {}

    public static void dumpAll(MainLogger log) {
        dumpFor("org.cloudburstmc.protocol.bedrock.codec.v844.Bedrock_v844", log);
        dumpFor("org.cloudburstmc.protocol.bedrock.codec.v827.Bedrock_v827", log);
        dumpFor("org.cloudburstmc.protocol.bedrock.codec.v776.Bedrock_v776", log);
    }


    @SuppressWarnings("unchecked")
    private static void dumpFor(String className, MainLogger log) {
        try {
            Class<?> cls = Class.forName(className);
            var f = cls.getDeclaredField("ENTITY_FLAGS");
            f.setAccessible(true);
            TypeMap<EntityFlag> map = (TypeMap<EntityFlag>) f.get(null);

            log.info("========== EntityFlag ID table [{}] ==========",
                    className.substring(className.lastIndexOf('.') + 1));

            for (EntityFlag flag : EntityFlag.values()) {
                int id = map.getId(flag);
                String bucket = (id >= 64) ? "FLAGS_2" : "FLAGS";
                log.info("{} = {} ({})", flag.name(), id, bucket);
            }

            log.info("=====================================================");
        } catch (Throwable t) {
            log.warning("FlagDump failed for {}: {}", className, t.toString());
        }
    }

}
