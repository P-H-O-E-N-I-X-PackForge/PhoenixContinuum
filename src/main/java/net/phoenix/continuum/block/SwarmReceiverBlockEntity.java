package net.phoenix.continuum.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.phoenix.continuum.ContinuumRegistry;
import net.phoenix.continuum.api.ContinuumHost;
import net.phoenix.continuum.api.ContinuumSwarmPower;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.machine.PadTiers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class SwarmReceiverBlockEntity extends BlockEntity implements ContinuumSwarmPower.Sink,
        net.phoenix.continuum.common.SwarmReceiver, net.phoenix.continuum.common.SwarmHolograms.Source {

    private long stored;
    private UUID owner;
    private UUID registeredTeam;
    private final LazyOptional<IEnergyStorage> output = LazyOptional.of(Output::new);

    public SwarmReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ContinuumRegistry.SWARM_RECEIVER_ENTITY.get(), pos, state);
    }

    private final class Output implements IEnergyStorage {

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            int taken = (int) Math.min(Math.min(amount, stored), maxOut());
            if (taken > 0 && !simulate) {
                stored -= taken;
                setChanged();
            }
            return taken;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, stored);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, capacityFE());
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }

    private static int maxOut() {
        return Math.max(1, ContinuumConfigs.INSTANCE.swarmReceiverMaxFEPerTick);
    }

    private static long capacityFE() {
        return (long) Math.min(9.0E18, Math.max(1000.0, ContinuumConfigs.INSTANCE.swarmReceiverCapacityFE));
    }

    private boolean isActive() {
        return PadTiers.basic();
    }

    @Override
    public long stored() {
        return stored;
    }

    @Override
    public long capacity() {
        return capacityFE();
    }

    private long receivedAt = Long.MIN_VALUE / 2;

    /** The uplink shows while the swarms have been feeding it in the last few seconds. */
    @Override
    public boolean active() {
        return isActive() && owner != null && level != null && level.getGameTime() - receivedAt < 60L;
    }

    @Override
    public @Nullable UUID owner() {
        return owner;
    }

    @Override
    public int kind() {
        return net.phoenix.continuum.network.S2CHologramPacket.State.RECEIVER;
    }

    @Override
    public int fill() {
        return (int) Math.min(100L, stored * 100L / Math.max(1L, capacity()));
    }

    @Override
    public net.phoenix.continuum.common.SwarmReport report(net.minecraft.server.MinecraftServer server) {
        UUID team = owner == null ? null : ContinuumHost.teamOf(owner);
        String note = isActive() ? "Pass it on to machines that touch it, up to the rate shown. Several receivers share" +
                " a swarm's power, and what nothing takes is lost." :
                "GregTech is installed, so this block does nothing. Build a Swarm Receiver Array instead.";
        if (team == null) {
            return new net.phoenix.continuum.common.SwarmReport("Swarm Receiver", "FE", stored, capacityFE(), maxOut(), 0,
                    "This receiver has no owner yet: place it yourself.", java.util.List.of());
        }
        return net.phoenix.continuum.common.SwarmReport.build(server, team, "Swarm Receiver", stored, capacityFE(),
                maxOut(), note);
    }

    public void setOwner(@Nullable UUID player) {
        if (player == null || level == null || level.isClientSide) return;
        owner = player;
        setChanged();
        register();
    }

    private void register() {
        if (!isActive() || owner == null || level == null || level.isClientSide) return;
        UUID team = ContinuumHost.teamOf(owner);
        if (team.equals(registeredTeam)) return;
        unregister();
        registeredTeam = team;
        ContinuumSwarmPower.register(team, this);
    }

    private void unregister() {
        if (registeredTeam != null) ContinuumSwarmPower.unregister(registeredTeam, this);
        registeredTeam = null;
    }

    @Override
    public long accept(long amount) {
        long room = capacityFE() - stored;
        long taken = Math.max(0L, Math.min(amount, room));
        if (taken > 0) {
            stored += taken;
            if (level != null) receivedAt = level.getGameTime();
            setChanged();
        }
        return taken;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SwarmReceiverBlockEntity self) {
        if (level.isClientSide) return;
        if (self.isActive()) net.phoenix.continuum.common.SwarmHolograms.register(level, pos, self);
        if (self.stored <= 0 || !self.isActive()) return;
        int budget = (int) Math.min(self.stored, maxOut());
        for (Direction side : Direction.values()) {
            if (budget <= 0) break;
            BlockEntity neighbour = level.getBlockEntity(pos.relative(side));
            if (neighbour == null) continue;
            var target = neighbour.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).resolve().orElse(null);
            if (target == null || !target.canReceive()) continue;
            int moved = target.receiveEnergy(budget, false);
            if (moved > 0) {
                budget -= moved;
                self.stored -= moved;
                self.setChanged();
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        register();
    }

    @Override
    public void setRemoved() {
        if (level != null) net.phoenix.continuum.common.SwarmHolograms.unregister(level, worldPosition);
        unregister();
        output.invalidate();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null) net.phoenix.continuum.common.SwarmHolograms.unregister(level, worldPosition);
        unregister();
        super.onChunkUnloaded();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY && isActive()) return output.cast();
        return super.getCapability(cap, side);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Stored", stored);
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stored = tag.getLong("Stored");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
}
