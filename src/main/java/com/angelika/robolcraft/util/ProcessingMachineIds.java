package com.angelika.robolcraft.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Explicit whitelist of GregTech5 Unofficial (GTNH) single-block processing machine
 * meta-tile entity IDs for Locker Worker AI targeting.
 *
 * <p>
 * <b>Scope:</b> main processing machines only (assemblers, lathes, cutters, furnaces, etc.).
 * Pipes, cables/wires, hatches, hulls, generators, multiblock controllers, and automation
 * buffers are intentionally absent.
 *
 * <p>
 * <b>Sources (fetched 2026-09-22):</b>
 * <ul>
 * <li>https://raw.githubusercontent.com/GTNewHorizons/GT5-Unofficial/master/src/main/java/gregtech/api/enums/MetaTileEntityIDs.java</li>
 * <li>https://raw.githubusercontent.com/GTNewHorizons/GT5-Unofficial/master/src/main/java/gregtech/loaders/preload/LoaderMetaTileEntities.java</li>
 * </ul>
 *
 * <p>
 * Pipe/cable ID ranges documented in MetaTileEntityIDs (do not whitelist):
 * frames 4096–5095, pipes 5096–6099.
 *
 * <p>
 * <b>TODO:</b> re-verify numeric IDs against the exact GT5 jar shipped in GTNH 2.8.4
 * (mod currently depends on a moving CoreMod Maven coordinate).
 *
 * <p>
 * Full name → id table is also in WHITELIST.md at the repo root.
 */
public final class ProcessingMachineIds {

    private ProcessingMachineIds() {}

    /**
     * Unmodifiable set of whitelisted meta-tile IDs (506 entries).
     * Lookup via {@link #contains(int)}.
     */
    public static final Set<Integer> IDS;

    static {
        final int[] raw = new int[] { 103, 104, 106, 107, 109, 110, 112, 113, 115, 116, 118, 119, 201, 202, 203, 204,
            205, 211, 212, 213, 214, 215, 221, 222, 223, 224, 225, 231, 232, 233, 234, 235, 241, 242, 243, 244, 245,
            251, 252, 253, 254, 255, 261, 262, 263, 264, 265, 271, 272, 273, 274, 275, 281, 282, 283, 284, 285, 291,
            292, 293, 294, 295, 301, 302, 303, 304, 305, 311, 312, 313, 314, 315, 321, 322, 323, 324, 325, 326, 327,
            328, 331, 332, 333, 334, 335, 341, 342, 343, 344, 345, 351, 352, 353, 354, 355, 361, 362, 363, 364, 365,
            371, 372, 373, 374, 375, 381, 382, 383, 384, 385, 391, 392, 393, 394, 395, 401, 402, 403, 404, 405, 406,
            407, 408, 411, 412, 413, 414, 415, 416, 417, 418, 421, 422, 423, 424, 425, 431, 432, 433, 434, 435, 491,
            492, 493, 494, 495, 501, 502, 503, 504, 505, 511, 512, 513, 514, 515, 521, 522, 523, 524, 525, 531, 532,
            533, 534, 535, 541, 542, 543, 544, 545, 551, 552, 553, 554, 555, 561, 562, 563, 564, 565, 571, 572, 573,
            574, 575, 581, 582, 583, 584, 585, 591, 592, 593, 594, 595, 601, 602, 603, 604, 605, 611, 612, 613, 614,
            615, 621, 622, 623, 624, 625, 641, 642, 643, 644, 645, 651, 652, 653, 654, 655, 661, 662, 663, 664, 665,
            1180, 1181, 1182, 1183, 1184, 1185, 1186, 1187, 10760, 10761, 10762, 10763, 10764, 10765, 10766, 10780,
            10781, 10782, 10783, 10784, 10785, 10786, 10790, 10791, 10792, 10793, 10794, 10795, 10796, 10800, 10801,
            10802, 10803, 10804, 10805, 10806, 10810, 10811, 10812, 10813, 10814, 10815, 10816, 10820, 10821, 10822,
            10823, 10824, 10825, 10826, 10830, 10831, 10832, 10833, 10834, 10835, 10836, 10840, 10841, 10842, 10843,
            10844, 10845, 10846, 10850, 10851, 10852, 10853, 10854, 10855, 10856, 10860, 10861, 10862, 10863, 10864,
            10865, 10866, 10870, 10871, 10872, 10873, 10874, 10875, 10876, 10880, 10881, 10882, 10883, 10884, 10885,
            10886, 10890, 10891, 10892, 10893, 10894, 10895, 10896, 10900, 10901, 10902, 10903, 10904, 10905, 10906,
            10910, 10911, 10912, 10913, 10914, 10915, 10916, 10920, 10921, 10922, 10923, 10924, 10925, 10926, 10930,
            10931, 10932, 10933, 10934, 10935, 10936, 10940, 10941, 10942, 10943, 10944, 10945, 10946, 10960, 10961,
            10962, 10963, 10964, 10965, 10966, 10970, 10971, 10972, 10973, 10974, 10975, 10976, 10980, 10981, 10982,
            10983, 10984, 10985, 10986, 10990, 10991, 10992, 10993, 10994, 10995, 10996, 11010, 11011, 11012, 11013,
            11014, 11015, 11016, 11020, 11021, 11022, 11023, 11024, 11025, 11026, 11040, 11041, 11042, 11043, 11044,
            11045, 11046, 11050, 11051, 11052, 11053, 11054, 11055, 11056, 11070, 11071, 11072, 11073, 11074, 11075,
            11076, 11080, 11081, 11082, 11083, 11084, 11085, 11086, 11090, 11091, 11092, 11093, 11094, 11095, 11096,
            11120, 11121, 11122, 11123, 11124, 11125, 11126, 11130, 11131, 11132, 11133, 11134, 11135, 11136, 11140,
            11141, 11142, 11143, 11144, 11145, 11146, 11150, 11151, 11152, 11153, 11154, 11155, 11156, 11170, 11171,
            11172, 11173, 11174, 11175, 11176, 11180, 11181, 11182, 11183, 11184, 11185, 11186, 11190, 11191, 11192,
            11193, 11194, 11195, 11196, 11200, 11201, 11202, 11203, 11204, 11205, 11206, 11210, 11211, 11212, 11213,
            11214, 11215, 11216, 12090, 12091, 12092, 12093, 12094, 12096 };
        final Set<Integer> set = new HashSet<>(raw.length * 2);
        for (int id : raw) {
            set.add(id);
        }
        IDS = Collections.unmodifiableSet(set);
    }

    /** GT pipe meta-tile ID range start (inclusive). See MetaTileEntityIDs javadoc. */
    public static final int PIPE_ID_MIN = 5096;
    /** GT pipe meta-tile ID range end (inclusive). */
    public static final int PIPE_ID_MAX = 6099;
    /** GT frame meta-tile ID range start (inclusive). */
    public static final int FRAME_ID_MIN = 4096;
    /** GT frame meta-tile ID range end (inclusive). */
    public static final int FRAME_ID_MAX = 5095;

    public static boolean contains(int metaTileId) {
        return IDS.contains(metaTileId);
    }

    public static boolean isPipeOrFrameId(int metaTileId) {
        return (metaTileId >= FRAME_ID_MIN && metaTileId <= FRAME_ID_MAX)
            || (metaTileId >= PIPE_ID_MIN && metaTileId <= PIPE_ID_MAX);
    }
}
