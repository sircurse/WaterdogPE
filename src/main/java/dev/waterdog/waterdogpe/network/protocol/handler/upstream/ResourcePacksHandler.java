package dev.waterdog.waterdogpe.network.protocol.handler.upstream;

import dev.waterdog.waterdogpe.event.defaults.PlayerResourcePackApplyEvent;
import dev.waterdog.waterdogpe.packs.PackManager;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.PacketSignal;

import java.util.LinkedList;
import java.util.Queue;

public class ResourcePacksHandler extends AbstractUpstreamHandler {

    private static final int CHUNK_SEND_DELAY_TICKS = 4;

    private final Queue<ResourcePackDataInfoPacket> pendingPacks = new LinkedList<>();
    private final Queue<ResourcePackChunkRequestPacket> chunkRequestQueue = new LinkedList<>();
    private ResourcePackDataInfoPacket sendingPack;
    private boolean sendingChunks = false;

    public ResourcePacksHandler(ProxiedPlayer player) {
        super(player);
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        PackManager packManager = this.player.getProxy().getPackManager();

        switch (packet.getStatus()) {
            case REFUSED:
                this.player.disconnect("disconnectionScreen.noReason");
                break;

            case SEND_PACKS:
                for (String packIdVer : packet.getPackIds()) {
                    ResourcePackDataInfoPacket response = packManager.packInfoFromIdVer(packIdVer);
                    if (response == null) {
                        this.player.disconnect("disconnectionScreen.resourcePack");
                        break;
                    }
                    this.pendingPacks.offer(response);
                }
                this.sendNextPacket();
                break;

            case HAVE_ALL_PACKS:
                PlayerResourcePackApplyEvent event = new PlayerResourcePackApplyEvent(this.player, packManager.getStackPacket());
                this.player.getProxy().getEventManager().callEvent(event);
                this.player.getConnection().sendPacket(event.getStackPacket());
                break;

            case COMPLETED:
                if (!this.player.hasUpstreamBridge()) {
                    this.player.initialConnect();
                }
                break;
        }

        return this.cancel();
    }

    @Override
    public PacketSignal handle(ResourcePackChunkRequestPacket packet) {
        this.chunkRequestQueue.add(packet);
        if (!sendingChunks) {
            sendingChunks = true;
            sendNextChunk();
        }
        return this.cancel();
    }

    private void sendNextPacket() {
        ResourcePackDataInfoPacket infoPacket = this.pendingPacks.poll();
        if (infoPacket != null && this.player.isConnected()) {
            this.sendingPack = infoPacket;
            this.player.sendPacket(infoPacket);
        }
    }

    private void sendNextChunk() {
        ResourcePackChunkRequestPacket request = this.chunkRequestQueue.poll();
        if (request == null) {
            this.sendingChunks = false;
            return;
        }

        PackManager packManager = this.player.getProxy().getPackManager();
        String key = request.getPackId() + "_" + request.getPackVersion();
        ResourcePackChunkDataPacket response = packManager.packChunkDataPacket(key, request);

        if (response == null) {
            this.player.disconnect("Unknown resource pack!");
            this.sendingChunks = false;
            return;
        }

        this.player.sendPacket(response);

        if (this.sendingPack != null && (request.getChunkIndex() + 1) >= this.sendingPack.getChunkCount()) {
            this.sendNextPacket();
        }

        // Schedule next chunk send
        this.player.getProxy().getScheduler().scheduleDelayed(() -> {
            if (this.player.isConnected()) {
                sendNextChunk();
            } else {
                this.sendingChunks = false;
            }
        }, CHUNK_SEND_DELAY_TICKS);
    }
}
