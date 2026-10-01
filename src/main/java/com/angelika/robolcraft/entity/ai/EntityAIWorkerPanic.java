package com.angelika.robolcraft.entity.ai;

import net.minecraft.entity.ai.EntityAIPanic;

import com.angelika.robolcraft.entity.EntityRobolCraft;

/**
 * Vanilla panic with a flag so {@link EntityRobolCraft#isPanicking()} is readable
 * without reflecting into private {@code EntityAITasks.executingTaskEntries}.
 * Used by workers and shift supervisors (same combat/flee sound parity).
 */
public class EntityAIWorkerPanic extends EntityAIPanic {

    private final EntityRobolCraft worker;

    public EntityAIWorkerPanic(EntityRobolCraft worker, double speed) {
        super(worker, speed);
        this.worker = worker;
    }

    @Override
    public void startExecuting() {
        super.startExecuting();
        worker.setPanickingFlag(true);
    }

    @Override
    public void resetTask() {
        super.resetTask();
        worker.setPanickingFlag(false);
    }
}
