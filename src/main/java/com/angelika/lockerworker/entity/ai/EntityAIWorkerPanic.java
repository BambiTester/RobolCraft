package com.angelika.lockerworker.entity.ai;

import net.minecraft.entity.ai.EntityAIPanic;

import com.angelika.lockerworker.entity.EntityLockerWorker;

/**
 * Vanilla panic with a flag so {@link EntityLockerWorker#isPanicking()} is readable
 * without reflecting into private {@code EntityAITasks.executingTaskEntries}.
 * Used by workers and shift supervisors (same combat/flee sound parity).
 */
public class EntityAIWorkerPanic extends EntityAIPanic {

    private final EntityLockerWorker worker;

    public EntityAIWorkerPanic(EntityLockerWorker worker, double speed) {
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
