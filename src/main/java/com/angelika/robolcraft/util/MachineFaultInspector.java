package com.angelika.robolcraft.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * Reflection-only single-block GT fault checks for the Shift Supervisor.
 * Multiblocks are skipped (not reliable) — see SUPERVISOR.md.
 */
public final class MachineFaultInspector {

    public static final String PHRASE_OUTPUT_BLOCKED = "its output is blocked";
    public static final String PHRASE_NO_POWER = "it has no power while there is work waiting";
    public static final String PHRASE_NO_LUBRICANT = "it is missing lubricant";
    public static final String PHRASE_NO_FUEL = "it has run out of fuel";

    private static final String IGTE_FQCN = "gregtech.api.interfaces.tileentity.IGregTechTileEntity";

    /** v25: resolve IGTE + methods once (no Class.forName per inspect). */
    private static final class GtHandles {

        static final Class<?> IGTE_CLASS;
        static final Method CAN_ACCESS_DATA;
        static final Method GET_META_TILE_ENTITY;
        static final Method GET_META_TILE_ID;
        static final boolean AVAILABLE;

        static {
            Class<?> igt = null;
            Method can = null;
            Method getMte = null;
            Method getId = null;
            boolean ok = false;
            try {
                igt = Class.forName(IGTE_FQCN);
                can = igt.getMethod("canAccessData");
                getMte = igt.getMethod("getMetaTileEntity");
                getId = igt.getMethod("getMetaTileID");
                ok = true;
            } catch (Throwable t) {
                ok = false;
            }
            IGTE_CLASS = igt;
            CAN_ACCESS_DATA = can;
            GET_META_TILE_ENTITY = getMte;
            GET_META_TILE_ID = getId;
            AVAILABLE = ok;
        }
    }

    private static final Map<String, Field> FIELD_CACHE = new HashMap<String, Field>();
    private static final Map<String, Method> METHOD_CACHE = new HashMap<String, Method>();

    private MachineFaultInspector() {}

    public static final class FaultResult {

        public final String machineName;
        public final int x, y, z;
        public final List<String> phrases;
        public final boolean skippedMultiblock;

        public FaultResult(String machineName, int x, int y, int z, List<String> phrases, boolean skippedMultiblock) {
            this.machineName = machineName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.phrases = phrases;
            this.skippedMultiblock = skippedMultiblock;
        }

        public boolean hasFault() {
            return phrases != null && !phrases.isEmpty();
        }

        public String joinedPhrases() {
            if (phrases == null || phrases.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < phrases.size(); i++) {
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(phrases.get(i));
            }
            return sb.toString();
        }
    }

    public static FaultResult inspect(World world, int x, int y, int z) {
        List<String> phrases = new ArrayList<String>();
        String name = "GregTech Machine";
        if (world == null || !world.blockExists(x, y, z)) {
            return new FaultResult(name, x, y, z, phrases, false);
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null || GregTechMachineLookup.isPipeTileEntity(te)) {
            return new FaultResult(name, x, y, z, phrases, false);
        }

        Object mte = null;
        int metaId = -1;
        try {
            if (!GtHandles.AVAILABLE || GtHandles.IGTE_CLASS == null || !GtHandles.IGTE_CLASS.isInstance(te)) {
                return new FaultResult(name, x, y, z, phrases, false);
            }
            Object ok = GtHandles.CAN_ACCESS_DATA.invoke(te);
            if (!(ok instanceof Boolean) || !((Boolean) ok).booleanValue()) {
                return new FaultResult(name, x, y, z, phrases, false);
            }
            mte = GtHandles.GET_META_TILE_ENTITY.invoke(te);
            if (mte == null) {
                return new FaultResult(name, x, y, z, phrases, false);
            }
            Object idObj = GtHandles.GET_META_TILE_ID.invoke(te);
            if (idObj instanceof Integer) {
                metaId = ((Integer) idObj).intValue();
            }
            name = resolveLocalName(mte, te, name);
        } catch (Throwable t) {
            return new FaultResult(name, x, y, z, phrases, false);
        }

        if (isClassNamed(mte, "MTEMultiBlockBase")) {
            return new FaultResult(name, x, y, z, phrases, true);
        }

        boolean isGen = metaId >= 0 && SupervisorMachineIds.isGenerator(metaId);
        if (isGen || isClassNamed(mte, "MTEBasicGenerator") || isClassNamed(mte, "MTEBoiler")) {
            if (isOutOfFuel(mte)) {
                phrases.add(PHRASE_NO_FUEL);
            }
            return new FaultResult(name, x, y, z, phrases, false);
        }

        if (isClassNamed(mte, "MTEBasicMachine")) {
            if (isOutputBlocked(mte)) {
                phrases.add(PHRASE_OUTPUT_BLOCKED);
            }
            if (isNoPowerWhileWaiting(mte)) {
                phrases.add(PHRASE_NO_POWER);
            }
            if (isMissingLubricant(mte)) {
                phrases.add(PHRASE_NO_LUBRICANT);
            }
        }
        return new FaultResult(name, x, y, z, phrases, false);
    }

    private static String resolveLocalName(Object mte, TileEntity te, String fallback) {
        try {
            Method m = findMethod(mte.getClass(), "getLocalName");
            if (m != null) {
                m.setAccessible(true);
                Object n = m.invoke(mte);
                if (n instanceof String && !((String) n).isEmpty()) {
                    return (String) n;
                }
            }
        } catch (Throwable ignored) {}
        try {
            Method m = findMethod(mte.getClass(), "getMetaName");
            if (m != null) {
                m.setAccessible(true);
                Object n = m.invoke(mte);
                if (n instanceof String && !((String) n).isEmpty()) {
                    return (String) n;
                }
            }
        } catch (Throwable ignored) {}
        try {
            if (te.getWorldObj() != null) {
                return te.getBlockType()
                    .getLocalizedName();
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    private static boolean isClassNamed(Object obj, String simpleName) {
        if (obj == null) {
            return false;
        }
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            if (simpleName.equals(c.getSimpleName())) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    private static boolean isOutputBlocked(Object mte) {
        try {
            Field f = findField(mte.getClass(), "mOutputBlocked");
            if (f == null) {
                return false;
            }
            f.setAccessible(true);
            Object v = f.get(mte);
            return v instanceof Integer && ((Integer) v).intValue() > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isNoPowerWhileWaiting(Object mte) {
        try {
            Field stutter = findField(mte.getClass(), "mStuttering");
            if (stutter != null) {
                stutter.setAccessible(true);
                Object v = stutter.get(mte);
                if (v instanceof Boolean && ((Boolean) v).booleanValue()) {
                    return true;
                }
            }
            Field maxProg = findField(mte.getClass(), "mMaxProgresstime");
            int maxP = 0;
            if (maxProg != null) {
                maxProg.setAccessible(true);
                Object v = maxProg.get(mte);
                if (v instanceof Integer) {
                    maxP = ((Integer) v).intValue();
                }
            }
            Method hasEnergy = findMethod(mte.getClass(), "hasEnoughEnergyToCheckRecipe");
            boolean enough = true;
            if (hasEnergy != null) {
                hasEnergy.setAccessible(true);
                Object v = hasEnergy.invoke(mte);
                if (v instanceof Boolean) {
                    enough = ((Boolean) v).booleanValue();
                }
            }
            if (!enough && (maxP > 0 || hasItemInputs(mte))) {
                return true;
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    private static boolean hasItemInputs(Object mte) {
        try {
            Field inv = findField(mte.getClass(), "mInventory");
            if (inv == null) {
                return false;
            }
            inv.setAccessible(true);
            Object raw = inv.get(mte);
            if (!(raw instanceof ItemStack[])) {
                return false;
            }
            ItemStack[] stacks = (ItemStack[]) raw;
            int lim = Math.min(stacks.length, 4);
            for (int i = 0; i < lim; i++) {
                if (stacks[i] != null) {
                    return true;
                }
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    private static boolean isMissingLubricant(Object mte) {
        try {
            Method getRecipeMap = findMethod(mte.getClass(), "getRecipeMap");
            if (getRecipeMap == null) {
                return false;
            }
            getRecipeMap.setAccessible(true);
            Object recipeMap = getRecipeMap.invoke(mte);
            if (recipeMap == null) {
                return false;
            }
            Fluid lube = FluidRegistry.getFluid("lubricant");
            if (lube == null) {
                return false;
            }
            Method containsInput = findMethod(recipeMap.getClass(), "containsInput", FluidStack.class);
            boolean needsLube = false;
            if (containsInput != null) {
                containsInput.setAccessible(true);
                Object r = containsInput.invoke(recipeMap, new FluidStack(lube, 1));
                needsLube = r instanceof Boolean && ((Boolean) r).booleanValue();
            }
            if (!needsLube) {
                return false;
            }
            Method getFillable = findMethod(mte.getClass(), "getFillableStack");
            FluidStack fillable = null;
            if (getFillable != null) {
                getFillable.setAccessible(true);
                Object f = getFillable.invoke(mte);
                if (f instanceof FluidStack) {
                    fillable = (FluidStack) f;
                }
            }
            if (fillable == null || fillable.amount <= 0) {
                return hasItemInputs(mte);
            }
            Fluid fl = fillable.getFluid();
            if (fl != lube && (fl == null || fl.getName() == null
                || !fl.getName()
                    .toLowerCase()
                    .contains("lubricant"))) {
                return hasItemInputs(mte);
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    private static boolean isOutOfFuel(Object mte) {
        try {
            if (isClassNamed(mte, "MTEBoiler")) {
                Field pe = findField(mte.getClass(), "mProcessingEnergy");
                int energy = 0;
                if (pe != null) {
                    pe.setAccessible(true);
                    Object v = pe.get(mte);
                    if (v instanceof Integer) {
                        energy = ((Integer) v).intValue();
                    }
                }
                if (energy > 0) {
                    return false;
                }
                return !hasAnyInventoryItem(mte);
            }
            String sn = mte.getClass()
                .getSimpleName();
            if (sn.contains("Solar") || sn.contains("Lightning") || sn.contains("Magic")) {
                return false;
            }
            Field mFluid = findField(mte.getClass(), "mFluid");
            if (mFluid != null) {
                mFluid.setAccessible(true);
                Object v = mFluid.get(mte);
                if (v instanceof FluidStack && ((FluidStack) v).amount > 0) {
                    Method gfv = findMethod(mte.getClass(), "getFuelValue", FluidStack.class);
                    if (gfv != null) {
                        gfv.setAccessible(true);
                        Object fv = gfv.invoke(mte, v);
                        if (fv instanceof Number && ((Number) fv).longValue() > 0) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
            if (hasGeneratorItemFuel(mte)) {
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasAnyInventoryItem(Object mte) {
        try {
            Field inv = findField(mte.getClass(), "mInventory");
            if (inv == null) {
                return false;
            }
            inv.setAccessible(true);
            Object raw = inv.get(mte);
            if (!(raw instanceof ItemStack[])) {
                return false;
            }
            for (ItemStack s : (ItemStack[]) raw) {
                if (s != null) {
                    return true;
                }
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    private static boolean hasGeneratorItemFuel(Object mte) {
        try {
            Field inv = findField(mte.getClass(), "mInventory");
            if (inv == null) {
                return false;
            }
            inv.setAccessible(true);
            Object raw = inv.get(mte);
            if (!(raw instanceof ItemStack[])) {
                return false;
            }
            ItemStack[] stacks = (ItemStack[]) raw;
            Method getInputSlot = findMethod(mte.getClass(), "getInputSlot");
            int slot = 0;
            if (getInputSlot != null) {
                getInputSlot.setAccessible(true);
                Object s = getInputSlot.invoke(mte);
                if (s instanceof Integer) {
                    slot = ((Integer) s).intValue();
                }
            }
            if (slot >= 0 && slot < stacks.length && stacks[slot] != null) {
                Method gfv = findMethod(mte.getClass(), "getFuelValue", ItemStack.class);
                if (gfv != null) {
                    gfv.setAccessible(true);
                    Object fv = gfv.invoke(mte, stacks[slot]);
                    return fv instanceof Number && ((Number) fv).longValue() > 0;
                }
                return true;
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    private static Field findField(Class<?> clazz, String name) {
        if (clazz == null) {
            return null;
        }
        String key = clazz.getName() + "#" + name;
        if (FIELD_CACHE.containsKey(key)) {
            return FIELD_CACHE.get(key);
        }
        Field found = null;
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                found = c.getDeclaredField(name);
                break;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        FIELD_CACHE.put(key, found);
        return found;
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        if (clazz == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(clazz.getName());
        sb.append('#')
            .append(name);
        if (params != null) {
            for (Class<?> p : params) {
                sb.append('/')
                    .append(p == null ? "null" : p.getName());
            }
        }
        String key = sb.toString();
        if (METHOD_CACHE.containsKey(key)) {
            return METHOD_CACHE.get(key);
        }
        Method found = null;
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                found = c.getDeclaredMethod(name, params);
                break;
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        METHOD_CACHE.put(key, found);
        return found;
    }
}
