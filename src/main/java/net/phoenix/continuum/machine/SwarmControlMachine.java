package net.phoenix.continuum.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.phoenix.continuum.common.ContinuumServerEvents;
import net.phoenix.continuum.common.SwarmControlService;
import net.phoenix.continuum.common.SwarmHolograms;
import net.phoenix.continuum.network.ContinuumNetwork;
import net.phoenix.continuum.network.S2CSwarmControlPacket;

import java.util.UUID;

/**
 * The GregTech Swarm Control Array. It is online while it is formed and running its recipe (power and coolant); then
 * it shows the same screen and hologram as the plain block.
 */
public class SwarmControlMachine extends WorkableElectricMultiblockMachine implements SwarmHolograms.Source,
        IInteractedMachine {

    public SwarmControlMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public boolean active() {
        return isFormed() && getRecipeLogic().isWorking();
    }

    @Override
    public UUID owner() {
        return getOwnerUUID();
    }

    /** The structure reaches back from the controller, so its middle is a block behind it. */
    @Override
    public int[] offset() {
        var back = getFrontFacing().getOpposite();
        return new int[] { back.getStepX(), back.getStepZ() };
    }

    private void register() {
        if (getLevel() != null && !getLevel().isClientSide) SwarmHolograms.register(getLevel(), getPos(), this);
    }

    private void unregister() {
        if (getLevel() != null && !getLevel().isClientSide) SwarmHolograms.unregister(getLevel(), getPos());
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        register();
    }

    @Override
    public void onStructureInvalid() {
        unregister();
        super.onStructureInvalid();
    }

    @Override
    public void onUnload() {
        unregister();
        super.onUnload();
    }

    @Override
    public InteractionResult onUse(net.minecraft.world.level.block.state.BlockState state,
                                   net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                   net.minecraft.world.entity.player.Player player,
                                   net.minecraft.world.InteractionHand hand,
                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (!isFormed() || player.isShiftKeyDown()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer && level.getServer() != null) {
            register();
            ContinuumServerEvents.engageAndSend(serverPlayer);
            ContinuumNetwork.sendToPlayer(serverPlayer,
                    new S2CSwarmControlPacket(SwarmControlService.view(serverPlayer, pos), true));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
