package net.phoenix.continuum.integration.tesla;

import net.phoenix_tesla_network.tesla.common.machine.multiblock.electric.TeslaWirelessRegistry;

import java.math.BigInteger;
import java.util.UUID;

/** The part of {@link TeslaLink} that uses the Tesla Network's classes. */
final class TeslaImpl {

    private TeslaImpl() {}

    static long fill(UUID team, UUID owner, long amount) {
        var bank = team == null ? null : TeslaWirelessRegistry.getBank(team);
        if (bank == null && owner != null) bank = TeslaWirelessRegistry.getBank(owner);
        if (bank == null) return 0;
        return bank.fill(BigInteger.valueOf(amount)).longValue();
    }
}
