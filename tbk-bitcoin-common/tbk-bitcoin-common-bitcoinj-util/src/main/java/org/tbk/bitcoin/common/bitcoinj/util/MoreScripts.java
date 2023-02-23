package org.tbk.bitcoin.common.bitcoinj.util;

import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.script.ScriptPattern;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
public final class MoreScripts {

    private MoreScripts() {
        throw new UnsupportedOperationException();
    }

    public static List<Address> extractOutputAddress(Transaction tx) {
        return Stream.ofNullable(tx)
                .flatMap(it -> it.getOutputs().stream())
                .map(it -> extractAddressOrNull(tx.getParams(), it.getScriptPubKey()))
                .filter(Objects::nonNull)
                .toList();
    }

    public static Optional<Address> extractOutputAddress(TransactionOutput txOut) {
        return MoreScripts.extractAddress(txOut.getParams(), txOut.getScriptPubKey());
    }

    public static Optional<Address> extractAddress(NetworkParameters networkParameters, Script script) {
        return Optional.ofNullable(extractAddressOrNull(networkParameters, script));
    }

    private static Address extractAddressOrNull(NetworkParameters networkParameters, Script script) {
        if (script == null) {
            return null;
        }

        if (ScriptPattern.isOpReturn(script)) {
            return null;
        }

        try {
            boolean forcePayToPubKey = true; // force public key to address - assume caller wants to avoid nulls
            return script.getToAddress(networkParameters, forcePayToPubKey);
        } catch (ScriptException se) {
            log.debug("Cannot extract address from script: {}: {}", script, se.getMessage());
            return null;
        }
    }
}
