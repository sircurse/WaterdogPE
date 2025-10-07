package dev.waterdog.waterdogpe.debug;

import dev.waterdog.waterdogpe.logger.MainLogger;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;

public final class EDataProbe {

    private EDataProbe() {}

    public static void dump(String tag, SetEntityDataPacket p, MainLogger log) {
        EntityDataMap m = p.getMetadata();
        String hasFlags  = m.containsKey(EntityDataTypes.FLAGS)   ? "Y" : "N";
        String hasFlags2 = m.containsKey(EntityDataTypes.FLAGS_2) ? "Y" : "N";
        String hasHeight = m.containsKey(EntityDataTypes.HEIGHT)  ? "Y" : "N";

        // keys present in THIS packet
        log.info("[SED {}] eid={} keys={} (FLAGS={}, FLAGS_2={}, HEIGHT={})",
                tag, p.getRuntimeEntityId(), m.keySet(), hasFlags, hasFlags2, hasHeight);

        // optional: raw packed longs for each lane (if present)
        log.info("[SED {}] masks: FLAGS={}, FLAGS_2={}",
                tag, hexOrDash(m, EntityDataTypes.FLAGS), hexOrDash(m, EntityDataTypes.FLAGS_2));
    }

    private static String hexOrDash(EntityDataMap m, EntityDataType<?> t) {
        Object v = m.get(t);
        if (v instanceof Number) {
            long bits = ((Number) v).longValue();
            return "0x" + Long.toHexString(bits);
        }
        return "-";
    }
}
