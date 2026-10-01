package com.angelika.lockerworker.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Supervisor whitelist: all {@link ProcessingMachineIds} plus single-block
 * generators / fuel-burning power blocks.
 *
 * <p>
 * Multiblock controllers intentionally absent for v14 — see SUPERVISOR.md.
 */
public final class SupervisorMachineIds {

    private SupervisorMachineIds() {}

    public static final Set<Integer> IDS;
    public static final Set<Integer> GENERATOR_IDS;

    static {
        final int[] generators = new int[] { 100, 101, 102, 105, 114, 837, 838, 839, 993, 994, 1110, 1111, 1112, 1113,
            1114, 1115, 1116, 1117, 1118, 1119, 1120, 1121, 1122, 1123, 1124, 1125, 1127, 1128, 1129, 1130, 1174, 1175,
            1176, 1188, 1189, 1190, 1191, 1192, 1196, 1197, 1198, 2729, 2733, 2734, 2735, 2736, 2737, 2738, 2739, 2740,
            10752, 10753, 12726, 12727, 12728, 12742, 12793 };

        final Set<Integer> gens = new HashSet<Integer>(generators.length * 2);
        for (int id : generators) {
            gens.add(id);
        }
        GENERATOR_IDS = Collections.unmodifiableSet(gens);

        final Set<Integer> all = new HashSet<Integer>(ProcessingMachineIds.IDS.size() + gens.size());
        all.addAll(ProcessingMachineIds.IDS);
        all.addAll(gens);
        IDS = Collections.unmodifiableSet(all);
    }

    public static boolean contains(int metaTileId) {
        return IDS.contains(metaTileId);
    }

    public static boolean isGenerator(int metaTileId) {
        return GENERATOR_IDS.contains(metaTileId);
    }
}
