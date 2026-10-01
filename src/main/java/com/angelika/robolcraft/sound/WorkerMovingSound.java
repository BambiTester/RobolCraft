package com.angelika.robolcraft.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import com.angelika.robolcraft.client.ClientConfig;
import com.angelika.robolcraft.entity.EntityRobolCraft;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * One-shot (non-repeating) {@link MovingSound} that follows a locker worker.
 * Call {@link #forceStop()} to end immediately on mode change / interrupt.
 *
 * <p>
 * Uses {@link AttenuationType#NONE} and fades {@link #getVolume()} by distance to
 * the local player out to {@link ClientConfig#soundHearDistance}, multiplied by
 * {@link ClientConfig#soundVolume} (client-local; vanilla LINEAR ignores custom hear distance).
 */
@SideOnly(Side.CLIENT)
public class WorkerMovingSound extends MovingSound {

    private final EntityRobolCraft worker;
    private final float baseVolume;
    private boolean forceStop;

    public EntityRobolCraft getWorker() {
        return worker;
    }

    public WorkerMovingSound(EntityRobolCraft worker, ResourceLocation location, float volume, float pitch) {
        super(location);
        this.worker = worker;
        this.baseVolume = volume;
        this.repeat = false;
        this.field_147665_h = 0;
        this.volume = volume;
        this.field_147663_c = pitch;
        this.field_147666_i = ISound.AttenuationType.NONE;
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

    @Override
    public float getVolume() {
        float master = ClientConfig.soundVolume;
        if (master <= 0.0F || baseVolume <= 0.0F) {
            return 0.0F;
        }
        float hear = ClientConfig.soundHearDistance;
        if (hear < 4.0F) {
            hear = 4.0F;
        }
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return baseVolume * master;
        }
        double dx = player.posX - xPosF;
        double dy = player.posY - yPosF;
        double dz = player.posZ - zPosF;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist >= hear) {
            return 0.0F;
        }
        float fade = 1.0F - (dist / hear);
        return baseVolume * master * fade;
    }

    public void forceStop() {
        forceStop = true;
        donePlaying = true;
    }

    public boolean wasForceStopped() {
        return forceStop;
    }
}
