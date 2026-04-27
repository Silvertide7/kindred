package net.silvertide.petsummon.client.data;

import net.silvertide.petsummon.network.BondView;

import java.util.Collections;
import java.util.List;

/**
 * Client-side cache of the player's bond roster, populated by S2CRosterSync.
 * Read by the roster screen.
 */
public final class ClientRosterData {
    private static List<BondView> bonds = Collections.emptyList();

    public static void update(List<BondView> newBonds) {
        bonds = List.copyOf(newBonds);
    }

    public static List<BondView> bonds() {
        return bonds;
    }

    public static void clear() {
        bonds = Collections.emptyList();
    }

    private ClientRosterData() {}
}
