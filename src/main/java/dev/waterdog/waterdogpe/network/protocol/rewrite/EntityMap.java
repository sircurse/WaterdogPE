/*
 * Copyright 2022 WaterdogTEAM
 * Licensed under the GNU General Public License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.waterdog.waterdogpe.network.protocol.rewrite;

import it.unimi.dsi.fastutil.longs.LongListIterator;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.packet.*;

import dev.waterdog.waterdogpe.logger.MainLogger;
import dev.waterdog.waterdogpe.network.protocol.rewrite.types.RewriteData;
import dev.waterdog.waterdogpe.network.protocol.user.PlayerRewriteUtils;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import org.cloudburstmc.protocol.common.PacketSignal;

import java.util.Arrays;
import java.util.Collection;
import java.util.ListIterator;
import java.util.function.LongConsumer;

import static dev.waterdog.waterdogpe.network.protocol.Signals.mergeSignals;

/**
 * Class to map the proper entityIds to entity-related packets.
 */
public class EntityMap implements BedrockPacketHandler {
    private static final Collection<EntityDataType<Long>> ENTITY_DATA_FIELDS = Arrays.asList(
            EntityDataTypes.OWNER_EID,
            EntityDataTypes.TARGET_EID,
            EntityDataTypes.LEASH_HOLDER,
            EntityDataTypes.WITHER_TARGET_A,
            EntityDataTypes.WITHER_TARGET_B,
            EntityDataTypes.WITHER_TARGET_C,
            EntityDataTypes.TRADE_TARGET_EID,
            EntityDataTypes.BALLOON_ANCHOR_EID,
            EntityDataTypes.AGENT_EID
    );

    private final ProxiedPlayer player;
    private final RewriteData rewrite;

    public EntityMap(ProxiedPlayer player) {
        this.player = player;
        this.rewrite = player.getRewriteData();
    }

    public PacketSignal doRewrite(BedrockPacket packet) {
        return this.player.canRewrite() ? packet.handle(this) : PacketSignal.UNHANDLED;
    }

    private PacketSignal rewriteId(long from, LongConsumer setter) {
        long rewriteId = PlayerRewriteUtils.rewriteId(from, this.rewrite.getEntityId(), this.rewrite.getOriginalEntityId());
        if (rewriteId == from) {
            return PacketSignal.UNHANDLED;
        }
        setter.accept(rewriteId);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(MoveEntityAbsolutePacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(EntityEventPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(MobEffectPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(UpdateAttributesPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(MobEquipmentPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(MobArmorEquipmentPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(PlayerActionPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    //@Override
    //public PacketSignal handle(SetEntityDataPacket packet) {
    //    PacketSignal signal = rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    //    PacketSignal metaSignal = this.rewriteMetadata(packet.getMetadata());
    //    return mergeSignals(signal, metaSignal);
    //}
@Override
public PacketSignal handle(SetEntityDataPacket packet) {
    // --- probe (keep if useful) ---
    // dev.waterdog.waterdogpe.debug.EDataProbe.dump("IN", packet, this.player.getLogger());

    // Always rewrite the eid first
    PacketSignal idSig = rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);

    EntityDataMap meta = packet.getMetadata();
    boolean hasFlags  = meta.containsKey(EntityDataTypes.FLAGS);
    boolean hasFlags2 = meta.containsKey(EntityDataTypes.FLAGS_2);

    if (hasFlags && hasFlags2) {
        // Split into TWO packets to mimic BDS exactly: (FLAGS [+ HEIGHT]) then (FLAGS_2)
        SetEntityDataPacket a = new SetEntityDataPacket();
        a.setRuntimeEntityId(packet.getRuntimeEntityId());
        a.setMetadata(onlyFlags(meta));
        a.setProperties(packet.getProperties());
        a.setTick(packet.getTick());

        SetEntityDataPacket b = new SetEntityDataPacket();
        b.setRuntimeEntityId(packet.getRuntimeEntityId());
        b.setMetadata(onlyFlags2(meta));
        b.setProperties(packet.getProperties());
        b.setTick(packet.getTick());

        // Rewrite IDs again (no-op if unchanged), then forward both in order
        rewriteId(a.getRuntimeEntityId(), a::setRuntimeEntityId);
        forwardSetEntityData(a);
        // dev.waterdog.waterdogpe.debug.EDataProbe.dump("OUT(SPLIT-A)", a, this.player.getLogger());

        rewriteId(b.getRuntimeEntityId(), b::setRuntimeEntityId);
        forwardSetEntityData(b);
        // dev.waterdog.waterdogpe.debug.EDataProbe.dump("OUT(SPLIT-B)", b, this.player.getLogger());

        return PacketSignal.HANDLED; // stop default handling; we already forwarded
    }

    // If only one lane present, ensure the other is not accidentally present
    if (hasFlags)  meta.remove(EntityDataTypes.FLAGS_2);
    if (hasFlags2) meta.remove(EntityDataTypes.FLAGS);

    // Rewrite metadata fields that are entity IDs (OWNER_EID etc.)
    PacketSignal metaSig = this.rewriteMetadata(meta);

    // dev.waterdog.waterdogpe.debug.EDataProbe.dump("OUT", packet, this.player.getLogger());
    return mergeSignals(idSig, metaSig);
}







// add this helper inside EntityMap:
private void forwardSetEntityData(SetEntityDataPacket pkt) {
    // reuse WDPE's standard outgoing path to the client
    this.player.sendPacket(pkt);
}

// optional: keep HEIGHT with FLAGS lane (matches BDS behavior)
private static EntityDataMap onlyFlags(EntityDataMap src) {
    EntityDataMap m = new EntityDataMap();
    if (src.containsKey(EntityDataTypes.FLAGS)) {
        m.put(EntityDataTypes.FLAGS, src.get(EntityDataTypes.FLAGS));
    }
    if (src.containsKey(EntityDataTypes.HEIGHT)) {
        m.put(EntityDataTypes.HEIGHT, src.get(EntityDataTypes.HEIGHT));
    }
    return m;
}

private static EntityDataMap onlyFlags2(EntityDataMap src) {
    EntityDataMap m = new EntityDataMap();
    if (src.containsKey(EntityDataTypes.FLAGS_2)) {
        m.put(EntityDataTypes.FLAGS_2, src.get(EntityDataTypes.FLAGS_2));
    }
    return m;
}















    @Override
    public PacketSignal handle(SetEntityMotionPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(MoveEntityDeltaPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(SetLocalPlayerAsInitializedPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(AddPlayerPacket packet) {
        PacketSignal signal0 = rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
        PacketSignal signal1 = rewriteId(packet.getUniqueEntityId(), packet::setUniqueEntityId);

        PacketSignal signal2 = PacketSignal.UNHANDLED;

        ListIterator<EntityLinkData> iterator = packet.getEntityLinks().listIterator();
        while (iterator.hasNext()) {
            EntityLinkData entityLink = iterator.next();
            long from = PlayerRewriteUtils.rewriteId(entityLink.getFrom(), this.rewrite.getEntityId(), this.rewrite.getOriginalEntityId());
            long to = PlayerRewriteUtils.rewriteId(entityLink.getTo(), this.rewrite.getEntityId(), this.rewrite.getOriginalEntityId());
            if (entityLink.getFrom() != from || entityLink.getTo() != to) {
                iterator.set(new EntityLinkData(from, to, entityLink.getType(), entityLink.isImmediate(), entityLink.isRiderInitiated()));
                signal2 = PacketSignal.HANDLED;
            }
        }

        PacketSignal signal3 = this.rewriteMetadata(packet.getMetadata());
        return (signal0 == PacketSignal.HANDLED || signal1 == PacketSignal.HANDLED || signal2 == PacketSignal.HANDLED || signal3 == PacketSignal.HANDLED) ?
                PacketSignal.HANDLED : PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(AddEntityPacket packet) {
        PacketSignal signal0 = rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
        PacketSignal signal1 = rewriteId(packet.getUniqueEntityId(), packet::setUniqueEntityId);

        PacketSignal signal2 = PacketSignal.UNHANDLED;

        ListIterator<EntityLinkData> iterator = packet.getEntityLinks().listIterator();
        while (iterator.hasNext()) {
            EntityLinkData entityLink = iterator.next();
            long from = PlayerRewriteUtils.rewriteId(entityLink.getFrom(), this.rewrite.getEntityId(), this.rewrite.getOriginalEntityId());
            long to = PlayerRewriteUtils.rewriteId(entityLink.getTo(), this.rewrite.getEntityId(), this.rewrite.getOriginalEntityId());
            if (entityLink.getFrom() != from || entityLink.getTo() != to) {
                iterator.set(new EntityLinkData(from, to, entityLink.getType(), entityLink.isImmediate(), entityLink.isRiderInitiated()));
                signal2 = PacketSignal.HANDLED;
            }
        }

        PacketSignal signal4 = this.rewriteMetadata(packet.getMetadata());
        return (signal0 == PacketSignal.HANDLED || signal1 == PacketSignal.HANDLED || signal2 == PacketSignal.HANDLED || signal4 == PacketSignal.HANDLED) ?
                PacketSignal.HANDLED : PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(AddItemEntityPacket packet) {
        PacketSignal signal0 = rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
        PacketSignal signal1 = rewriteId(packet.getUniqueEntityId(), packet::setUniqueEntityId);
        PacketSignal signal2 = this.rewriteMetadata(packet.getMetadata());
        return (signal0 == PacketSignal.HANDLED || signal1 == PacketSignal.HANDLED || signal2 == PacketSignal.HANDLED) ?
                PacketSignal.HANDLED : PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(AddPaintingPacket packet) {
        PacketSignal signal0 = rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
        PacketSignal signal1 = rewriteId(packet.getUniqueEntityId(), packet::setUniqueEntityId);
        return mergeSignals(signal0, signal1);
    }

    @Override
    public PacketSignal handle(RemoveEntityPacket packet) {
        return rewriteId(packet.getUniqueEntityId(), packet::setUniqueEntityId);
    }

    @Override
    public PacketSignal handle(BossEventPacket packet) {
        PacketSignal signal0 = rewriteId(packet.getBossUniqueEntityId(), packet::setBossUniqueEntityId);
        PacketSignal signal1 = rewriteId(packet.getPlayerUniqueEntityId(), packet::setPlayerUniqueEntityId);
        return mergeSignals(signal0, signal1);
    }

    @Override
    public PacketSignal handle(TakeItemEntityPacket packet) {
        PacketSignal signal0 = rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
        PacketSignal signal1 = rewriteId(packet.getItemRuntimeEntityId(), packet::setItemRuntimeEntityId);
        return mergeSignals(signal0, signal1);
    }

    @Override
    public PacketSignal handle(MovePlayerPacket packet) {
        PacketSignal signal0 = rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
        PacketSignal signal1 = rewriteId(packet.getRidingRuntimeEntityId(), packet::setRidingRuntimeEntityId);
        return mergeSignals(signal0, signal1);
    }

    @Override
    public PacketSignal handle(InteractPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(PlayerLocationPacket packet) {
        return rewriteId(packet.getTargetEntityId(), packet::setTargetEntityId);
    }

    @Override
    public PacketSignal handle(SetEntityLinkPacket packet) {
        EntityLinkData entityLink = packet.getEntityLink();
        long from = PlayerRewriteUtils.rewriteId(entityLink.getFrom(), this.rewrite.getEntityId(), this.rewrite.getOriginalEntityId());
        long to = PlayerRewriteUtils.rewriteId(entityLink.getTo(), this.rewrite.getEntityId(), this.rewrite.getOriginalEntityId());

        if (from != entityLink.getFrom() || to != entityLink.getTo()) {
            packet.setEntityLink(new EntityLinkData(from, to, entityLink.getType(), entityLink.isImmediate(), entityLink.isRiderInitiated()));
            return PacketSignal.HANDLED;
        }
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(AnimatePacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(AdventureSettingsPacket packet) {
        return rewriteId(packet.getUniqueEntityId(), packet::setUniqueEntityId);
    }

    @Override
    public PacketSignal handle(PlayerListPacket packet) {
        if (packet.getAction() != PlayerListPacket.Action.ADD) {
            return PacketSignal.UNHANDLED;
        }

        PacketSignal signal = PacketSignal.UNHANDLED;

        for (PlayerListPacket.Entry entry : packet.getEntries()) {
            long rewriteId = PlayerRewriteUtils.rewriteId(entry.getEntityId(), this.rewrite.getEntityId(), this.rewrite.getOriginalEntityId());
            if (rewriteId != entry.getEntityId()) {
                signal = PacketSignal.HANDLED;
                entry.setEntityId(rewriteId);
            }
        }
        return signal;
    }

    @Override
    public PacketSignal handle(UpdateTradePacket packet) {
        PacketSignal signal0 = rewriteId(packet.getPlayerUniqueEntityId(), packet::setPlayerUniqueEntityId);
        PacketSignal signal1 = rewriteId(packet.getTraderUniqueEntityId(), packet::setTraderUniqueEntityId);
        return mergeSignals(signal0, signal1);
    }

    @Override
    public PacketSignal handle(RespawnPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(EmoteListPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    public PacketSignal handle(NpcDialoguePacket packet) {
        return rewriteId(packet.getUniqueEntityId(), packet::setUniqueEntityId);
    }

    public PacketSignal handle(NpcRequestPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(EmotePacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(SpawnParticleEffectPacket packet) {
        return rewriteId(packet.getUniqueEntityId(), packet::setUniqueEntityId);
    }

    @Override
    public PacketSignal handle(EntityPickRequestPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(EventPacket packet) {
        return rewriteId(packet.getUniqueEntityId(), packet::setUniqueEntityId);
    }

    @Override
    public PacketSignal handle(UpdatePlayerGameTypePacket packet) {
        return rewriteId(packet.getEntityId(), packet::setEntityId);
    }

    @Override
    public PacketSignal handle(UpdateAbilitiesPacket packet) {
        return rewriteId(packet.getUniqueEntityId(), packet::setUniqueEntityId);
    }

    @Override
    public PacketSignal handle(ClientCheatAbilityPacket packet) {
        return rewriteId(packet.getUniqueEntityId(), packet::setUniqueEntityId);
    }

    @Override
    public PacketSignal handle(PlayerUpdateEntityOverridesPacket packet) {
        return rewriteId(packet.getEntityUniqueId(), packet::setEntityUniqueId);
    }

    @Override
    public PacketSignal handle(LevelSoundEventPacket packet) {
        return rewriteId(packet.getEntityUniqueId(), packet::setEntityUniqueId);
    }

    @Override
    public PacketSignal handle(AnimateEntityPacket packet) {
        PacketSignal signal = PacketSignal.UNHANDLED;
        LongListIterator iterator = packet.getRuntimeEntityIds().listIterator();
        while (iterator.hasNext()) {
            PacketSignal returnedSignal = rewriteId(iterator.nextLong(), iterator::set);
            signal = mergeSignals(signal, returnedSignal);
        }
        return signal;
    }

    @Override
    public PacketSignal handle(MovementEffectPacket packet) {
        return rewriteId(packet.getEntityRuntimeId(), packet::setEntityRuntimeId);
    }

    @Override
    public PacketSignal handle(MovementPredictionSyncPacket packet) {
        return rewriteId(packet.getRuntimeEntityId(), packet::setRuntimeEntityId);
    }

    @Override
    public PacketSignal handle(UpdateEquipPacket packet) {
        return rewriteId(packet.getUniqueEntityId(), packet::setUniqueEntityId);
    }

    private PacketSignal rewriteMetadata(EntityDataMap metadata) {
        PacketSignal signal = PacketSignal.UNHANDLED;
        for (EntityDataType<Long> data : ENTITY_DATA_FIELDS) {
            Long id = metadata.get(data);
            if (id != null && id > 0L) { // IDs start at 1, so this is safe
                long rewriteId = PlayerRewriteUtils.rewriteId(id, this.rewrite.getEntityId(), this.rewrite.getOriginalEntityId());
                if (rewriteId != id) {
                    metadata.put(data, rewriteId);
                    signal = PacketSignal.HANDLED;
                }
            }
        }
        return signal;
    }
}
