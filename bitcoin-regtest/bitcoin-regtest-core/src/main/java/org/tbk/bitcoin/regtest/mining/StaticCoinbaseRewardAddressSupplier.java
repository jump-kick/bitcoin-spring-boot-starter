package org.tbk.bitcoin.regtest.mining;

import org.bitcoinj.core.Address;

import static java.util.Objects.requireNonNull;

public final class StaticCoinbaseRewardAddressSupplier implements CoinbaseRewardAddressSupplier {

    private final Address address;

    public StaticCoinbaseRewardAddressSupplier(Address address) {
        this.address = requireNonNull(address);
    }

    @Override
    public Address get() {
        return this.address;
    }
}

