package org.tbk.bitcoin.regtest.example;

import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Block;
import org.consensusj.bitcoin.jsonrpc.BitcoinClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.tbk.bitcoin.zeromq.client.MessagePublishService;
import org.tbk.electrum.ElectrumClient;
import org.tbk.electrum.command.DaemonLoadWalletRequest;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.tbk.bitcoin.regtest.common.BitcoindStatusLogging.logBitcoinStatusOnNewBlock;
import static org.tbk.bitcoin.regtest.electrum.common.ElectrumdStatusLogging.logElectrumStatusOnNewBlock;

@Slf4j
@Configuration
@Profile("!test")
public class BitcoinRegtestExampleApplicationConfig {

    @Bean
    public CommandLineRunner logZmqRawBlocksMessages(MessagePublishService<Block> bitcoinjBlockPublishService) {
        return args -> {
            AtomicLong zeromqBlockCounter = new AtomicLong();
            Disposable subscription = Flux.from(bitcoinjBlockPublishService).subscribe(arg -> {
                log.info("Received zeromq message: {} - {}", zeromqBlockCounter.incrementAndGet(), arg.getHash());
            });
            Runtime.getRuntime().addShutdownHook(new Thread(subscription::dispose));

            bitcoinjBlockPublishService.awaitRunning(Duration.ofSeconds(10));
        };
    }

    @Bean
    public CommandLineRunner logBitcoinStatus(MessagePublishService<Block> bitcoinjBlockPublishService,
                                              BitcoinClient bitcoinClient) {
        return args -> logBitcoinStatusOnNewBlock(bitcoinjBlockPublishService, bitcoinClient);
    }

    @Bean
    public CommandLineRunner logElectrumStatus(MessagePublishService<Block> bitcoinjBlockPublishService,
                                               ElectrumClient electrumClient) {
        return args -> logElectrumStatusOnNewBlock(bitcoinjBlockPublishService, electrumClient);
    }

    @Bean
    public CommandLineRunner loadElectrumWallet(ElectrumClient electrumClient) {
        return args -> {
            boolean daemonConnected = electrumClient.isDaemonConnected();
            log.info("electrum daemon connected: {}", daemonConnected);

            Boolean loadWalletResult = electrumClient.loadWallet(DaemonLoadWalletRequest.builder()
                    .walletPath("/home/electrum/.electrum/regtest/wallets/default_wallet")
                    .build());
            log.info("electrum load wallet result: {}", loadWalletResult);

            boolean walletSynchronized = electrumClient.isWalletSynchronized();
            log.info("electrum wallet synchronized: {}", walletSynchronized);

            if (!walletSynchronized) {
                log.info("Will wait max. 30s for electrum wallet to synchronize..");
                Stopwatch sw = Stopwatch.createStarted();
                Boolean walletSynchronizedAfterWaiting = Flux.interval(Duration.ofMillis(100))
                        .map(it -> electrumClient.isWalletSynchronized())
                        .filter(it -> it)
                        .blockFirst(Duration.ofSeconds(30));

                log.info("Electrum wallet synchronized after {}: {}", sw.stop(), walletSynchronizedAfterWaiting);

                if (!Boolean.TRUE.equals(walletSynchronizedAfterWaiting)) {
                    throw new IllegalStateException("Could not synchronized electrum wallet");
                }
            }
        };
    }
}
