package net.phoenix.continuum.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;

import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.api.ContinuumSwarmPower;

import java.util.UUID;

/** The Swarm Receiver Array for GregTech 7.x. {@code SwarmReceiverMachine8} is the GregTech 8 twin of this class. */
public class SwarmReceiverMachine extends MultiblockControllerMachine implements ContinuumSwarmPower.Sink,
        com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine {

    private UUID registeredTeam;

    public SwarmReceiverMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    private void unregister() {
        if (registeredTeam != null) ContinuumSwarmPower.unregister(registeredTeam, this);
        registeredTeam = null;
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
    public void onStructureFormed() {
        super.onStructureFormed();
        if (getLevel() == null || getLevel().isClientSide || getOwnerUUID() == null) return;
        unregister();
        registeredTeam = ContinuumHost.teamOf(getOwnerUUID());
        ContinuumSwarmPower.register(registeredTeam, this);
    }

    @Override
    public net.minecraft.world.InteractionResult onUse(net.minecraft.world.level.block.state.BlockState state,
                                                       net.minecraft.world.level.Level level,
                                                       net.minecraft.core.BlockPos pos,
                                                       net.minecraft.world.entity.player.Player player,
                                                       net.minecraft.world.InteractionHand hand,
                                                       net.minecraft.world.phys.BlockHitResult hit) {
        if (!isFormed() || player.isShiftKeyDown()) return net.minecraft.world.InteractionResult.PASS;
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && level.getServer() != null) {
            var server = level.getServer();
            UUID owner = getOwnerUUID();
            UUID team = ContinuumHost.teamOf(owner != null ? owner : player.getUUID());
            long stored = net.phoenix.continuum.common.SwarmStorage.get(server).get(level, pos);
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
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public long accept(long amount) {
        if (!isFormed() || getLevel() == null || getLevel().getServer() == null) return 0;
        // with the Tesla Network installed, the swarm fills its tesla storage first; what that cannot take goes on
        long toTesla = 0;
        if (amount > 0 && net.phoenix.continuum.integration.tesla.TeslaLink.loaded()) {
            UUID owner = getOwnerUUID();
            toTesla = net.phoenix.continuum.integration.tesla.TeslaLink.fill(
                    owner == null ? null : ContinuumHost.teamOf(owner), owner, amount);
        }
        return toTesla + net.phoenix.continuum.common.SwarmStorage.exchange(getLevel().getServer(), getLevel(), getPos(), amount - toTesla,
                this::fillHatches);
    }

    /** Puts up to this much into the energy hatches; returns how much they took. */
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
