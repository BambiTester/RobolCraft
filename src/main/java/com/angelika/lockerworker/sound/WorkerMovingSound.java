package com.angelika.lockerworker.sound;

import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.ResourceLocation;

import com.angelika.lockerworker.entity.EntityLockerWorker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * One-shot (non-repeating) {@link MovingSound} that follows a locker worker.
 * Call {@link #forceStop()} to end immediately on mode change / interrupt.
 */
@SideOnly(Side.CLIENT)
public class WorkerMovingSound extends MovingSound {

    private final EntityLockerWorker worker;
    private boolean forceStop;

    public WorkerMovingSound(EntityLockerWorker worker, ResourceLocation location, float volume, float pitch) {
        super(location);
        this.worker = worker;
        this.repeat = false;
        this.field_147665_h = 0;
        this.volume = volume;
        this.field_147663_c = pitch;
        this.xPosF = (float) worker.posX;
        this.yPosF = (float) worker.posY;
        this.zPosF = (float) worker.posZ;
    }

    @Override
    public void update() {
        if (forceStop || worker == null || worker.isDead || worker.worldObj == null) {
            donePlaying = true;
            return;
        }
        xPosF = (float) worker.posX;
        yPosF = (float) worker.posY;
        zPosF = (float) worker.posZ;
    }

    public void forceStop() {
        forceStop = true;
        donePlaying = true;
    }

    public boolean wasForceStopped() {
        return forceStop;
    }
}
