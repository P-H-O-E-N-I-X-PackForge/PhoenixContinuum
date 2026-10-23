package net.phoenix.continuum.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;

import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.api.ContinuumSwarmPower;

import java.util.UUID;

/** The Swarm Receiver Array for GregTech 7.x. {@code SwarmReceiverMachine8} is the GregTech 8 twin of this class. */
public class SwarmReceiverMachine extends MultiblockControllerMachine implements ContinuumSwarmPower.Sink {

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
