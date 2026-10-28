package net.phoenix.continuum.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;

import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.api.ContinuumSwarmPower;

import java.util.UUID;

public class SwarmReceiverMachine extends MultiblockControllerMachine implements ContinuumSwarmPower.Sink,
        com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine, net.phoenix.continuum.common.SwarmReceiver,
        net.phoenix.continuum.common.SwarmHolograms.Source {

    private UUID registeredTeam;
    private long receivedAt = Long.MIN_VALUE / 2;

    public SwarmReceiverMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public boolean active() {
        return isFormed() && getOwnerUUID() != null && getLevel() != null &&
                getLevel().getGameTime() - receivedAt < 60L;
    }

    @Override
    public UUID owner() {
        return getOwnerUUID();
    }

    @Override
    public int kind() {
        return net.phoenix.continuum.network.S2CHologramPacket.State.RECEIVER;
    }

    @Override
    public int fill() {
        return (int) Math.min(100L, stored() * 100L / Math.max(1L, capacity()));
    }

    @Override
    public long stored() {
        return getLevel() == null || getLevel().getServer() == null ? 0L :
                net.phoenix.continuum.common.SwarmStorage.get(getLevel().getServer()).get(getLevel(), getPos());
    }

    @Override
    public long capacity() {
        return (long) Math.min(9.0E18,
                Math.max(0.0, net.phoenix.continuum.config.ContinuumConfigs.INSTANCE.swarmArrayCapacitorEU));
    }

    @Override
    public net.phoenix.continuum.common.SwarmReport report(net.minecraft.server.MinecraftServer server) {
        UUID owner = getOwnerUUID();
        UUID team = ContinuumHost.teamOf(owner != null ? owner : new UUID(0L, 0L));
        String note = "Fills its output hatches from the capacitor" +
                (net.phoenix.continuum.integration.tesla.TeslaLink.loaded() ?
                        ", after the Tesla Network storage, which comes first." : ".");
        return net.phoenix.continuum.common.SwarmReport.build(server, team, "Swarm Receiver Array", stored(),
                capacity(), 0, note);
    }

    private void unregister() {
        if (registeredTeam != null) ContinuumSwarmPower.unregister(registeredTeam, this);
        registeredTeam = null;
        if (getLevel() != null) net.phoenix.continuum.common.SwarmHolograms.unregister(getLevel(), getPos());
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
        net.phoenix.continuum.common.SwarmHolograms.register(getLevel(), getPos(), this);
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
            net.phoenix.continuum.network.ContinuumNetwork.sendToPlayer(serverPlayer,
                    new net.phoenix.continuum.network.S2COpenPowerPacket(report(level.getServer()), pos, true));
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public long accept(long amount) {
        if (!isFormed() || getLevel() == null || getLevel().getServer() == null) return 0;
        receivedAt = getLevel().getGameTime();

        long toTesla = 0;
        if (amount > 0 && net.phoenix.continuum.integration.tesla.TeslaLink.loaded()) {
            UUID owner = getOwnerUUID();
            toTesla = net.phoenix.continuum.integration.tesla.TeslaLink.fill(
                    owner == null ? null : ContinuumHost.teamOf(owner), owner, amount);
        }
        return toTesla + net.phoenix.continuum.common.SwarmStorage.exchange(getLevel().getServer(), getLevel(), getPos(), amount - toTesla,
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
