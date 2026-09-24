package com.angelika.lockerworker.entity.ai;

import java.util.List;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityShiftSupervisor;
import com.angelika.lockerworker.util.PathToward;

/**
 * During shift: if undelivered reports and a player within reportPlayerRadius,
 * run to them, look, dump ALL pending in one burst, play supervisor_report,
 * follow ~reportFollowTicks, then resume.
 */
public class EntityAIDeliverReport extends EntityAIBase {

    private enum Stage {
        SEEK_PLAYER,
        RUN,
        DUMP,
        FOLLOW
    }

    private final EntityShiftSupervisor supervisor;
    private final PathToward.Tracker pathToward = new PathToward.Tracker();

    private Stage stage = Stage.SEEK_PLAYER;
    private EntityPlayer target;
    private int followLeft;
    private int repathCooldown;

    public EntityAIDeliverReport(EntityShiftSupervisor supervisor) {
        this.supervisor = supervisor;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (!supervisor.isInShiftWindow()) {
            return false;
        }
        if (!supervisor.getReportMemory()
            .hasUndelivered()) {
            return false;
        }
        return findNearestPlayer() != null;
    }

    @Override
    public boolean continueExecuting() {
        if (!supervisor.isInShiftWindow()) {
            return false;
        }
        // Keep going through FOLLOW even if all marked delivered mid-burst
        if (stage == Stage.FOLLOW || stage == Stage.DUMP) {
            return target != null && target.isEntityAlive();
        }
        return supervisor.getReportMemory()
            .hasUndelivered() && target != null
            && target.isEntityAlive();
    }

    @Override
    public void startExecuting() {
        target = findNearestPlayer();
        stage = Stage.RUN;
        followLeft = 0;
        repathCooldown = 0;
        pathToward.reset();
    }

    @Override
    public void resetTask() {
        target = null;
        stage = Stage.SEEK_PLAYER;
        followLeft = 0;
        pathToward.reset();
        supervisor.getNavigator()
            .clearPathEntity();
    }

    @Override
    public void updateTask() {
        if (target == null || !target.isEntityAlive()) {
            target = findNearestPlayer();
            if (target == null) {
                return;
            }
        }

        switch (stage) {
            case RUN:
                tickRun();
                break;
            case DUMP:
                doDump();
                break;
            case FOLLOW:
                tickFollow();
                break;
            default:
                stage = Stage.RUN;
                break;
        }
    }

    private void tickRun() {
        supervisor.getLookHelper()
            .setLookPositionWithEntity(target, 30.0F, 30.0F);
        double distSq = supervisor.getDistanceSqToEntity(target);
        if (distSq <= 9.0D) { // ~3 blocks
            stage = Stage.DUMP;
            return;
        }
        if (repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        repathCooldown = 10;
        PathToward.tryMoveToward(supervisor, pathToward, target.posX, target.posY, target.posZ, Config.getPathSpeed());
    }

    private void doDump() {
        supervisor.getNavigator()
            .clearPathEntity();
        supervisor.getLookHelper()
            .setLookPositionWithEntity(target, 30.0F, 30.0F);
        List<String> lines = supervisor.getReportMemory()
            .formatUndeliveredChatLines(supervisor.worldObj);
        if (lines.isEmpty()) {
            // Fallback: dump all if somehow empty undelivered list
            lines = supervisor.getReportMemory()
                .formatAllChatLines(supervisor.worldObj);
        }
        for (String line : lines) {
            target.addChatMessage(new ChatComponentText(line));
        }
        supervisor.playReportSound();
        supervisor.getReportMemory()
            .markAllDelivered();
        followLeft = Math.max(1, Config.reportFollowTicks);
        stage = Stage.FOLLOW;
    }

    private void tickFollow() {
        supervisor.getLookHelper()
            .setLookPositionWithEntity(target, 30.0F, 30.0F);
        followLeft--;
        if (followLeft <= 0) {
            // Done — resetTask via continueExecuting false next tick
            target = null;
            stage = Stage.SEEK_PLAYER;
            return;
        }
        double distSq = supervisor.getDistanceSqToEntity(target);
        if (distSq > 6.25D) {
            if (repathCooldown <= 0) {
                repathCooldown = 10;
                PathToward.tryMoveToward(
                    supervisor,
                    pathToward,
                    target.posX,
                    target.posY,
                    target.posZ,
                    Config.getPathSpeed());
            } else {
                repathCooldown--;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private EntityPlayer findNearestPlayer() {
        double r = Config.reportPlayerRadius;
        if (r < 1.0D) {
            r = 1.0D;
        }
        AxisAlignedBB box = supervisor.boundingBox.expand(r, r * 0.5, r);
        List<EntityPlayer> list = supervisor.worldObj.getEntitiesWithinAABB(EntityPlayer.class, box);
        EntityPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (EntityPlayer p : list) {
            if (p == null || !p.isEntityAlive()) {
                continue;
            }
            double d = supervisor.getDistanceSqToEntity(p);
            if (d <= r * r && d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }
}
