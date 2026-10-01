package com.angelika.robolcraft.entity;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.angelika.robolcraft.Config;
import com.angelika.robolcraft.entity.ai.EntityAIDeliverReport;
import com.angelika.robolcraft.entity.ai.EntityAISuperviseMachines;
import com.angelika.robolcraft.sound.ModSounds;
import com.angelika.robolcraft.tileentity.TileEntityLocker;
import com.angelika.robolcraft.tileentity.TileEntitySupervisorLocker;
import com.angelika.robolcraft.util.SupervisorReportMemory;
import com.angelika.robolcraft.util.WorkerSchedule;

/**
 * Shift Supervisor — inspects machines, remembers faults, auto-reports to nearest player
 * during the shift window. Night/bed/afterwork same as {@link EntityRobolCraft}.
 */
public class EntityShiftSupervisor extends EntityRobolCraft {

    /** Legacy entity-side memory; migrated into home locker TE on load. */
    private SupervisorReportMemory legacyMigratedMemory;
    private final EntityAISuperviseMachines superviseAI;
    private final EntityAIDeliverReport deliverAI;

    private String chaseMobName;
    private boolean wasChasing;

    public EntityShiftSupervisor(World world) {
        super(world);
        // Swap day wander for supervise AI; deliver above break so reports interrupt break
        tasks.removeTask(wanderAI);
        tasks.removeTask(breakAI);
        superviseAI = new EntityAISuperviseMachines(this);
        deliverAI = new EntityAIDeliverReport(this);
        // Priority: swim0, panic1, medkit2, attack3, night4, return5, deliver6, break7, supervise8, watch9
        // (medkit/attack/night/return already from parent; re-add deliver/break/supervise)
        tasks.addTask(6, deliverAI);
        tasks.addTask(7, breakAI);
        tasks.addTask(8, superviseAI);
    }

    public SupervisorReportMemory getReportMemory() {
        TileEntityLocker te = getHomeLockerTE();
        if (te instanceof TileEntitySupervisorLocker) {
            return ((TileEntitySupervisorLocker) te).getReportMemory();
        }
        if (legacyMigratedMemory == null) {
            legacyMigratedMemory = new SupervisorReportMemory();
        }
        return legacyMigratedMemory;
    }

    public EntityAIDeliverReport getDeliverAI() {
        return deliverAI;
    }

    /** Persist and broadcast supervisor report-memory changes to tracking clients. */
    public void onReportMemoryChanged() {
        TileEntityLocker te = getHomeLockerTE();
        if (te instanceof TileEntitySupervisorLocker) {
            te.markDirty();
            te.syncToClients();
        }
    }

    /** True during WORK+BREAK (shift reporting window). */
    public boolean isInShiftWindow() {
        return WorkerSchedule.isShift(worldObj) && !isForcedStayAtLocker() && !isLyingInBed();
    }

    @Override
    public void onHostileChaseStarted(EntityLivingBase target) {
        if (target == null) {
            return;
        }
        chaseMobName = target.getCommandSenderName();
        if (chaseMobName == null || chaseMobName.isEmpty()) {
            chaseMobName = target.getClass()
                .getSimpleName();
        }
        wasChasing = true;
    }

    @Override
    public void onHostileChaseEnded(EntityLivingBase target, boolean defeated, boolean targetFled) {
        if (!wasChasing) {
            return;
        }
        String name = chaseMobName;
        if (name == null && target != null) {
            name = target.getCommandSenderName();
        }
        if (name == null || name.isEmpty()) {
            wasChasing = false;
            chaseMobName = null;
            return;
        }
        if (defeated) {
            getReportMemory().recordCombat(name, SupervisorReportMemory.CombatOutcome.DEFEATED, worldObj);
            onReportMemoryChanged();
        } else if (targetFled) {
            getReportMemory().recordCombat(name, SupervisorReportMemory.CombatOutcome.GOT_AWAY, worldObj);
            onReportMemoryChanged();
        }
        wasChasing = false;
        chaseMobName = null;
    }

    @Override
    public void onDeath(DamageSource source) {
        if (!worldObj.isRemote && wasChasing && chaseMobName != null && !chaseMobName.isEmpty()) {
            getReportMemory().recordCombat(chaseMobName, SupervisorReportMemory.CombatOutcome.DIED_FIGHTING, worldObj);
            onReportMemoryChanged();
            wasChasing = false;
        } else if (!worldObj.isRemote && getAttackTarget() != null) {
            String name = getAttackTarget().getCommandSenderName();
            if (name != null && !name.isEmpty()) {
                getReportMemory().recordCombat(name, SupervisorReportMemory.CombatOutcome.DIED_OTHER, worldObj);
                onReportMemoryChanged();
            }
        }
        super.onDeath(source);
    }

    @Override
    public boolean interact(EntityPlayer player) {
        if (worldObj.isRemote) {
            return true;
        }
        // Right-click: locker ID FIRST, then memory dump + ask sound ONLY (no generic interaction)
        getLookHelper().setLookPositionWithEntity(player, 30.0F, 30.0F);
        player.addChatMessage(new ChatComponentText(getLinkedLockerIdChat()));
        List<IChatComponent> lines = getReportMemory().formatAllChatComponents(worldObj, null);
        if (lines.isEmpty()) {
            player.addChatMessage(new ChatComponentText("No reports on file, boss."));
        } else {
            for (IChatComponent line : lines) {
                player.addChatMessage(line);
            }
        }
        playAskReportSound();
        return true;
    }

    @Override
    public String getCommandSenderName() {
        if (hasCustomNameTag()) {
            return getCustomNameTag();
        }
        return StatCollector.translateToLocal("entity.ShiftSupervisor.name");
    }

    public void playAskReportSound() {
        playSupervisorCategory(ModSounds.supervisorAskReport(), 1.0F);
    }

    public void playReportSound() {
        playSupervisorCategory(ModSounds.supervisorReport(), 1.0F);
    }

    private void playSupervisorCategory(List<String> list, float mul) {
        if (list == null || list.isEmpty() || worldObj == null || worldObj.isRemote) {
            return;
        }
        float vol = Math.max(0.0F, Config.getBroadcastSoundVolume()) * mul;
        if (vol <= 0.0F) {
            return;
        }
        String name = list.get(getRNG().nextInt(list.size()));
        worldObj.playSoundAtEntity(this, name, vol, 0.95F + getRNG().nextFloat() * 0.1F);
    }

    /** Sound phase from supervise AI for working/free-roaming. */
    @Override
    public com.angelika.robolcraft.entity.ai.EntityAIWanderNearMachines.SoundPhase getDaySoundPhase() {
        return superviseAI.getSoundPhase();
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        // Memory persists on home locker TE — do not duplicate on entity
        if (chaseMobName != null) {
            tag.setString("ChaseMob", chaseMobName);
        }
        tag.setBoolean("WasChasing", wasChasing);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey("SupervisorMemory")) {
            // Migrate legacy entity memory into locker TE once
            SupervisorReportMemory tmp = new SupervisorReportMemory();
            tmp.readFromNBT(tag.getCompoundTag("SupervisorMemory"));
            TileEntityLocker te = getHomeLockerTE();
            if (te instanceof TileEntitySupervisorLocker) {
                SupervisorReportMemory dest = ((TileEntitySupervisorLocker) te).getReportMemory();
                // Only fill if locker memory empty
                if (!dest.hasUndelivered() && dest.getCombatReport() == null
                    && dest.getMachineReports()
                        .isEmpty()) {
                    NBTTagCompound copy = new NBTTagCompound();
                    tmp.writeToNBT(copy);
                    dest.readFromNBT(copy);
                    te.markDirty();
                    te.syncToClients();
                }
            } else {
                legacyMigratedMemory = tmp;
            }
        }
        if (tag.hasKey("ChaseMob")) {
            chaseMobName = tag.getString("ChaseMob");
        }
        wasChasing = tag.getBoolean("WasChasing");
    }
}
