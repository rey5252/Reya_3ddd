package com.reya.bossdamage.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.reya.bossdamage.network.BossInfoPacket;
import net.minecraft.Util;

/** Latest boss info received from the server; entries vanish when updates stop. */
public final class ClientBossData {
    /** Server sends every 0.5 s; keep an entry a bit longer so a dead boss's final state stays visible. */
    private static final long TIMEOUT_MS = 2500L;

    private record Entry(BossInfoPacket info, long receivedAt) {
    }

    private static final Map<Integer, Entry> ENTRIES = new LinkedHashMap<>();

    public static void accept(BossInfoPacket packet) {
        ENTRIES.put(packet.entityId(), new Entry(packet, Util.getMillis()));
    }

    public static List<BossInfoPacket> current() {
        long now = Util.getMillis();
        ENTRIES.values().removeIf(e -> now - e.receivedAt() > TIMEOUT_MS);
        List<BossInfoPacket> list = new ArrayList<>(ENTRIES.size());
        for (Entry entry : ENTRIES.values()) list.add(entry.info());
        return list;
    }

    public static void clear() {
        ENTRIES.clear();
    }

    private ClientBossData() {
    }
}
