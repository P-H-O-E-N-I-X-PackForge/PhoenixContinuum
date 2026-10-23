package net.phoenix.continuum.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;

import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.api.ContinuumSwarmPower;

import java.util.UUID;

public class SwarmReceiverMachine8 extends MultiblockControllerMachine implements ContinuumSwarmPower.Sink {

    private UUID registeredTeam;

    public SwarmReceiverMachine8(BlockEntityCreationInfo info) {
        super(info);
    }

    private void unregister() {
        if (registeredTeam != null) ContinuumSwarmPower.unregister(registeredTeam, this);
        registeredTeam = null;
    }

    @Override
    public void invalidateStructure(@org.jetbrains.annotations.NotNull String substructureName) {
        unregister();
        super.invalidateStructure(substructureName);
    }

    @Override
    public void onUnload() {
        unregister();
        super.onUnload();
    }

    @Override
    public void formStructure(@org.jetbrains.annotations.NotNull String substructureName) {
        super.formStructure(substructureName);
        if (getLevel() == null || getLevel().isClientSide || getOwnerUUID() == null) return;
        unregister();
        registeredTeam = ContinuumHost.teamOf(getOwnerUUID());
        ContinuumSwarmPower.register(registeredTeam, this);
    }

    @Override
    public net.minecraft.world.InteractionResult onUse(
                                                       com.gregtechceu.gtceu.utils.ExtendedUseOnContext context) {
        var player = context.getPlayer();
        if (!isFormed() || player == null || player.isShiftKeyDown()) return super.onUse(context);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && getLevel() != null &&
                getLevel().getServer() != null) {
            var server = getLevel().getServer();
            UUID owner = getOwnerUUID();
            UUID team = ContinuumHost.teamOf(owner != null ? owner : player.getUUID());
            long stored = net.phoenix.continuum.common.SwarmStorage.get(server).get(getLevel(), getBlockPos());
            long capacity = (long) Math.min(9.0E18,
                    Math.max(0.0, net.phoenix.continuum.config.ContinuumConfigs.INSTANCE.swarmArrayCapacitorEU));
            String note = "Fills its output hatches from the capacitor" +
                    (net.phoenix.continuum.integration.tesla.TeslaLink.loaded() ?
                            ", after the Tesla Network storage, which comes first." : ".") +
                    " Sneak and use for the GregTech screen.";
            net.phoenix.continuum.network.ContinuumNetwork.sendToPlayer(serverPlayer,
                    new net.phoenix.continuum.network.S2COpenPowerPacket(
                            net.phoenix.continuum.common.SwarmReport.build(server, team, "Swarm Receiver Array", stored,
                                    capacity, 0, note)));
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public long accept(long amount) {
        if (!isFormed() || getLevel() == null || getLevel().getServer() == null) return 0;
        
        long toTesla = 0;
        if (amount > 0 && net.phoenix.continuum.integration.tesla.TeslaLink.loaded()) {
            UUID owner = getOwnerUUID();
            toTesla = net.phoenix.continuum.integration.tesla.TeslaLink.fill(
                    owner == null ? null : ContinuumHost.teamOf(owner), owner, amount);
        }
        return toTesla + net.phoenix.continuum.common.SwarmStorage.exchange(getLevel().getServer(), getLevel(), getBlockPos(), amount - toTesla,
                this::fillHatches);
    }

    private long fillHatches(long available) {
        long remaining = available;
        for (var part : getParts()) {
            if (remaining <= 0) break;
            if (!(part instanceof EnergyHatchPartMachine hatch)) continue;

            long room = hatch.energyContainer.getEnergyCapacity() - hatch.energyContainer.getEnergyStored();
            long put = Math.min(remaining, room);
            if (put <= 0) continue;
            hatch.energyContainer.changeEnergy(put);
            remaining -= put;
        }
        return available - remaining;
    }
}
