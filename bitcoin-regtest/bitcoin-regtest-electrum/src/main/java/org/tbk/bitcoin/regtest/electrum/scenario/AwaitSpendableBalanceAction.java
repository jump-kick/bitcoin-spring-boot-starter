package org.tbk.bitcoin.regtest.electrum.scenario;

import com.google.common.base.Stopwatch;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.reactivestreams.Subscriber;
import org.tbk.bitcoin.regtest.scenario.RegtestAction;
import org.tbk.electrum.bitcoinj.BitcoinjElectrumClient;
import org.tbk.electrum.bitcoinj.model.BitcoinjBalance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

@Slf4j
public final class AwaitSpendableBalanceAction implements RegtestAction<Coin> {
    private static final Duration defaultTimeout = Duration.ofSeconds(30);
    private static final Duration defaultCheckInterval = Duration.ofMillis(100);

    private final BitcoinjElectrumClient client;
    private final Coin expectedAmount;
    private final Duration timeout;
    private final Duration checkInterval;

    public AwaitSpendableBalanceAction(BitcoinjElectrumClient client,
                                       Coin expectedAmount) {
        this(client, expectedAmount, defaultTimeout, defaultCheckInterval);
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "false positive")
    public AwaitSpendableBalanceAction(BitcoinjElectrumClient client,
                                       Coin expectedAmount,
                                       Duration timeout,
                                       Duration checkInterval) {
        this.client = requireNonNull(client);
        this.expectedAmount = requireNonNull(expectedAmount);
        this.timeout = requireNonNull(timeout);
        this.checkInterval = requireNonNull(checkInterval);

        checkArgument(expectedAmount.isPositive(), "'expectedAmount' must be positive");
        checkArgument(!checkInterval.isNegative(), "'checkInterval' must be positive");

        // user made a mistake when 'timeout' is smaller than or equal to 'checkInterval'
        checkArgument(timeout.compareTo(checkInterval) > 0, "'timeout' must be greater than 'checkInterval");
    }

    @Override
    public void subscribe(Subscriber<? super Coin> s) {
        create().subscribe(s);
    }

    private Mono<Coin> create() {
        return Mono.fromCallable(() -> {
            Stopwatch sw = Stopwatch.createStarted();

            log.debug("Poll electrum every {} till balance reaches {} for {}",
                    this.checkInterval, this.expectedAmount.toFriendlyString(), this.timeout);

            Coin coin = Flux.interval(this.checkInterval)
                    .doOnNext(it -> log.trace("Waiting balance of {} by electrum.. ({} attempt)",
                            this.expectedAmount.toFriendlyString(), it))
                    .map(it -> this.client.getBalance())
                    .doOnNext(balance -> {
                        log.trace("Balance: {} total", balance.getTotal().toFriendlyString());
                        log.trace("         {} confirmed", balance.getConfirmed().toFriendlyString());
                        log.trace("         {} unconfirmed", balance.getUnconfirmed().toFriendlyString());
                        log.trace("         {} spendable", balance.getSpendable().toFriendlyString());
                        log.trace("         {} unmatured", balance.getUnmatured().toFriendlyString());
                    })
                    .map(BitcoinjBalance::getSpendable)
                    .filter(it -> !it.isLessThan(this.expectedAmount))
                    .blockFirst(this.timeout);

            requireNonNull(coin, "electrum could not find balance in time");

            log.debug("Found balance {} after {}.. ", coin.toFriendlyString(), sw.stop());

            return coin;
        });
    }
}
