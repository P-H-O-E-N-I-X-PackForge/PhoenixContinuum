package net.phoenix.continuum.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
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
public class SwarmControlMachine extends WorkableElectricMultiblockMachine implements SwarmHolograms.Source {

    public SwarmControlMachine(BlockEntityCreationInfo info) {
        super(info);
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
        if (getLevel() != null && !getLevel().isClientSide) SwarmHolograms.register(getLevel(), getBlockPos(), this);
    }

    private void unregister() {
        if (getLevel() != null && !getLevel().isClientSide) SwarmHolograms.unregister(getLevel(), getBlockPos());
    }

    @Override
    public void formStructure(String substructureName) {
        super.formStructure(substructureName);
        register();
    }

    @Override
    public void invalidateStructure(String substructureName) {
        unregister();
        super.invalidateStructure(substructureName);
    }

    @Override
    public void onUnload() {
        unregister();
        super.onUnload();
    }

    @Override
    public InteractionResult onUse(com.gregtechceu.gtceu.utils.ExtendedUseOnContext context) {
        var player = context.getPlayer();
        if (!isFormed() || player == null || player.isShiftKeyDown()) return super.onUse(context);
        if (player instanceof ServerPlayer serverPlayer && getLevel() != null && getLevel().getServer() != null) {
            register();
            ContinuumServerEvents.engageAndSend(serverPlayer);
            ContinuumNetwork.sendToPlayer(serverPlayer,
                    new S2CSwarmControlPacket(SwarmControlService.view(serverPlayer, getBlockPos()), true));
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
