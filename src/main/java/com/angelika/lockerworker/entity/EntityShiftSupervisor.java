package com.angelika.lockerworker.entity;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.ai.EntityAIDeliverReport;
import com.angelika.lockerworker.entity.ai.EntityAISuperviseMachines;
import com.angelika.lockerworker.sound.ModSounds;
import com.angelika.lockerworker.util.SupervisorReportMemory;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * Shift Supervisor — inspects machines, remembers faults, auto-reports to nearest player
 * during the shift window. Night/bed/afterwork same as {@link EntityLockerWorker}.
 */
public class EntityShiftSupervisor extends EntityLockerWorker {

    private final SupervisorReportMemory memory = new SupervisorReportMemory();
    private final EntityAISuperviseMachines superviseAI;
    private final EntityAIDeliverReport deliverAI;

    private String chaseMobName;
    private boolean wasChasing;

    public EntityShiftSupervisor(World world) {
        super(world);
        // Swap day wander for supervise AI; add deliver AI above wander priority
        tasks.removeTask(wanderAI);
        superviseAI = new EntityAISuperviseMachines(this);
        deliverAI = new EntityAIDeliverReport(this);
        // Priority: swim0, panic1, attack2, night3, return4, break5, deliver6, supervise7, watch8
        tasks.addTask(6, deliverAI);
        tasks.addTask(7, superviseAI);
    }

    public SupervisorReportMemory getReportMemory() {
        return memory;
    }

    public EntityAIDeliverReport getDeliverAI() {
        return deliverAI;
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
            memory.recordCombat(name, SupervisorReportMemory.CombatOutcome.DEFEATED, worldObj);
        } else if (targetFled) {
            memory.recordCombat(name, SupervisorReportMemory.CombatOutcome.GOT_AWAY, worldObj);
        }
        wasChasing = false;
        chaseMobName = null;
    }

    @Override
    public void onDeath(DamageSource source) {
        if (!worldObj.isRemote && wasChasing && chaseMobName != null && !chaseMobName.isEmpty()) {
            memory.recordCombat(chaseMobName, SupervisorReportMemory.CombatOutcome.DIED_FIGHTING, worldObj);
            wasChasing = false;
        } else if (!worldObj.isRemote && getAttackTarget() != null) {
            String name = getAttackTarget().getCommandSenderName();
            if (name != null && !name.isEmpty()) {
                memory.recordCombat(name, SupervisorReportMemory.CombatOutcome.DIED_OTHER, worldObj);
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
        List<String> lines = memory.formatAllChatLines(worldObj);
        if (lines.isEmpty()) {
            player.addChatMessage(new ChatComponentText("No reports on file, boss."));
        } else {
            for (String line : lines) {
                player.addChatMessage(new ChatComponentText(line));
            }
        }
        playAskReportSound();
        return true;
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
        float vol = Math.max(0.0F, Config.soundVolume) * mul;
        if (vol <= 0.0F) {
            return;
        }
        String name = list.get(getRNG().nextInt(list.size()));
        worldObj.playSoundAtEntity(this, name, vol, 0.95F + getRNG().nextFloat() * 0.1F);
    }

    /** Sound phase from supervise AI for working/free-roaming. */
    @Override
    public com.angelika.lockerworker.entity.ai.EntityAIWanderNearMachines.SoundPhase getDaySoundPhase() {
        return superviseAI.getSoundPhase();
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        NBTTagCompound mem = new NBTTagCompound();
        memory.writeToNBT(mem);
        tag.setTag("SupervisorMemory", mem);
        if (chaseMobName != null) {
            tag.setString("ChaseMob", chaseMobName);
        }
        tag.setBoolean("WasChasing", wasChasing);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey("SupervisorMemory")) {
            memory.readFromNBT(tag.getCompoundTag("SupervisorMemory"));
        }
        if (tag.hasKey("ChaseMob")) {
            chaseMobName = tag.getString("ChaseMob");
        }
        wasChasing = tag.getBoolean("WasChasing");
    }
}
