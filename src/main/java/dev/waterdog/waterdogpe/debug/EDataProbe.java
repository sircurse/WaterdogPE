package dev.waterdog.waterdogpe.debug;

import dev.waterdog.waterdogpe.logger.MainLogger;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;

import java.util.Set;

public final class EDataProbe {

    private EDataProbe() {}

    public static void dump(String tag, SetEntityDataPacket p, MainLogger log) {
        EntityDataMap m = p.getMetadata();
        boolean hasF  = m.containsKey(EntityDataTypes.FLAGS);
        boolean hasF2 = m.containsKey(EntityDataTypes.FLAGS_2);
        boolean hasH  = m.containsKey(EntityDataTypes.HEIGHT);

        String keys = String.valueOf(m.keySet());
        String masks = laneMasks(m);

        log.info("[SED {}] eid={} keys={} (FLAGS={}, FLAGS_2={}, HEIGHT={})",
                tag, p.getRuntimeEntityId(), keys, yesNo(hasF), yesNo(hasF2), yesNo(hasH));
        log.info("[SED {}] lane-masks {}", tag, masks);
    }

    private static String yesNo(boolean b) { return b ? "Y" : "N"; }

    @SuppressWarnings("unchecked")
    private static String laneMasks(EntityDataMap m) {
        long low = 0L, high = 0L;

        Object vF  = m.get(EntityDataTypes.FLAGS);
        Object vF2 = m.get(EntityDataTypes.FLAGS_2);

        if (vF instanceof Set<?> s) {
            for (Object o : s) if (o instanceof EntityFlag f) {
                int id = FlagIds.idOf(f);
                if (id >= 0 && id < 64) low |= (1L << id);
                if (id >= 64 && id < 128) high |= (1L << (id - 64));
            }
        }
        if (vF2 instanceof Set<?> s) {
            for (Object o : s) if (o instanceof EntityFlag f) {
                int id = FlagIds.idOf(f);
                if (id >= 0 && id < 64) low |= (1L << id);
                if (id >= 64 && id < 128) high |= (1L << (id - 64));
            }
        }
        return String.format("FLAGS=0x%s, FLAGS_2=0x%s",
                Long.toHexString(low), Long.toHexString(high));
    }
}
