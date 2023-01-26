package org.tbk.electrum.model;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BtcUtxoValuesTest {

    @Test
    void toBtc() {
        TxoValue value = SimpleTxoValue.of(1337L);

        assertThat(value.isZero(), is(false));

        String valueAsBtcString = BtcTxoValues.toBtc(value).toPlainString();

        assertThat(valueAsBtcString, is("0.00001337"));

        assertThat("verify equality to `fromBtcString` factory method", value, is(BtcTxoValues.fromBtcString(valueAsBtcString)));
    }

    @Test
    void fromBtcString() {
        TxoValue value = BtcTxoValues.fromBtcString("0.00000042");

        assertThat(value.getValue(), is(42L));
        assertThat(value.isZero(), is(false));
        String valueAsBtcString = BtcTxoValues.toBtc(value).toPlainString();

        assertThat(valueAsBtcString, is("0.00000042"));

        assertThat("verify equality to `toBtc` factory method", value, is(SimpleTxoValue.of(42L)));
    }

    @Test
    void fromBtcStringWithTrailingDot() {
        TxoValue value = BtcTxoValues.fromBtcString("0.");

        assertThat(value, is(SimpleTxoValue.zero()));
        assertThat(value.getValue(), is(0L));
        assertThat(value.isZero(), is(true));
        String valueAsBtcString = BtcTxoValues.toBtc(value).toPlainString();

        assertThat(valueAsBtcString, is("0.00000000"));
    }
}
