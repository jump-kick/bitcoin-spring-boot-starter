package org.tbk.bitcoin.common.bitcoinj.util;

import org.bitcoinj.core.*;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.junit.jupiter.api.Test;
import org.tbk.bitcoin.common.genesis.GenesisTx;
import org.tbk.bitcoin.regtest.common.AddressSupplier;
import org.tbk.bitcoin.regtest.mining.RegtestEaterAddressSupplier;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class MoreScriptsTest {
    private static final NetworkParameters TEST_NETWORK = RegTestParams.get();

    private static final BitcoinSerializer MAINNET_SERIALIZER = new BitcoinSerializer(MainNetParams.get(), false);

    private static final AddressSupplier TEST_ADDRESS_SUPPLIER = new RegtestEaterAddressSupplier();

    @Test
    void itShouldExtractAddressesFromGenesisTx() {
        Transaction tx = MAINNET_SERIALIZER.makeTransaction(GenesisTx.get().toByteArray());
        List<Address> addresses = MoreScripts.extractOutputAddress(tx);

        assertThat(addresses, hasSize(1));

        Address address = addresses.get(0);
        assertThat(address.toString(), is("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"));
    }

    @Test
    void itShouldExtractAddressFromTxOutWithAddress() {
        Address testAddress = TEST_ADDRESS_SUPPLIER.get();

        TransactionOutput output = new TransactionOutput(testAddress.getParameters(), null, Coin.valueOf(1), testAddress);
        Address extractedAddress = MoreScripts.extractOutputAddress(output).orElseThrow();

        assertThat(extractedAddress, is(testAddress));
    }

    @Test
    void itShouldExtractNothingFromTxOutWithoutAddress() {
        TransactionOutput output = new TransactionOutput(TEST_NETWORK, null, Coin.valueOf(1), new byte[0]);
        Optional<Address> extractedAddress = MoreScripts.extractOutputAddress(output);

        assertThat(extractedAddress, is(Optional.empty()));
    }
}