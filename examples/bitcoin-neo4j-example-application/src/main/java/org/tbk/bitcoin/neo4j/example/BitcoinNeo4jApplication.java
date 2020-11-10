package org.tbk.bitcoin.neo4j.example;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.boot.web.context.WebServerPortFileWriter;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.support.TransactionTemplate;
import org.tbk.bitcoin.common.bitcoinj.util.MoreScripts;
import org.tbk.bitcoin.common.util.ShutdownHooks;
import org.tbk.bitcoin.jsonrpc.cache.CacheFacade;
import org.tbk.bitcoin.zeromq.client.MessagePublishService;
import org.tbk.spring.bitcoin.neo4j.model.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@SpringBootApplication
public class BitcoinNeo4jApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder()
                .sources(BitcoinNeo4jApplication.class)
                .listeners(applicationPidFileWriter(), webServerPortFileWriter())
                .web(WebApplicationType.SERVLET)
                .profiles("development", "local", "demo")
                .run(args);
    }

    public static ApplicationListener<?> applicationPidFileWriter() {
        return new ApplicationPidFileWriter("application.pid");
    }

    public static ApplicationListener<?> webServerPortFileWriter() {
        return new WebServerPortFileWriter("application.port");
    }

    @Bean
    @Profile("demo")
    public CommandLineRunner insertBlockToNeo4j(NetworkParameters networkParameters,
                                                MessagePublishService<Block> bitcoinjBlockPublishService,
                                                MessagePublishService<Transaction> bitcoinjTranscationPublishService,
                                                BlockNeoRepository blockRepository,
                                                TxNeoRepository transactionRepository,
                                                TxOutputNeoRepository txOutputRepository,
                                                AddressNeoRepository addressRepository,
                                                TransactionTemplate transactionTemplate,
                                                CacheFacade caches) {
        return args -> {
            log.info("Starting example application insertBlockToNeo4j");

            ExecutorService executorService = Executors.newFixedThreadPool(10, new ThreadFactoryBuilder()
                    .setNameFormat("load-init-block-%d")
                    .setDaemon(false)
                    .build());

            Runtime.getRuntime().addShutdownHook(ShutdownHooks.shutdownHook(executorService, Duration.ofSeconds(10)));

            // a block with comparatively low amount of total inputs and tx.. (tx count: 318; i nput count: 875)
            Sha256Hash randomBlockHash = Sha256Hash.wrap("000000000000000000341c2bcc0e2eadb0a4b1453a44ac31cab893080f967a85");
            Block randomBlock = caches.block().getUnchecked(randomBlockHash);

            BlockNeoEntity savedNeoBlock = transactionTemplate.execute(status -> {
                String blockHash = randomBlock.getHash().toString();
                String prevBlockHash = randomBlock.getPrevBlockHash().toString();

                log.info("inserting new block {}", blockHash);

                BlockNeoEntity neoBlock = new BlockNeoEntity();
                neoBlock.setHash(blockHash);

                blockRepository.findById(prevBlockHash)
                        .ifPresent(neoBlock::setPrevblock);

                return blockRepository.save(neoBlock);
            });


            AtomicLong txCounter = new AtomicLong();
            randomBlock.getTransactions().forEach(tx -> {
                transactionTemplate.executeWithoutResult(status -> {
                    String txId = tx.getTxId().toString();

                    log.info("{} - inserting new transaction {}", txCounter.incrementAndGet(), txId);

                    TxNeoEntity neoTx = new TxNeoEntity();
                    neoTx.setTxid(txId);
                    neoTx.setBlock(savedNeoBlock);

                    List<TxOutputNeoEntity> neoSpentOutputs = Lists.newArrayList();
                    tx.getInputs().forEach(input -> {
                        if (input.isCoinBase()) {
                            // coinbase inputs cannot be fetched
                            // via `getrawtransaction`
                            return;
                        }

                        TransactionOutPoint outpoint = input.getOutpoint();

                        String neoTxoId = outpoint.getHash().toString() + ":" + outpoint.getIndex();
                        TxOutputNeoEntity TxOutputNeoEntitySpent = txOutputRepository.findById(neoTxoId).orElseGet(() -> {
                            Transaction txFromInput = caches.tx().getUnchecked(outpoint.getHash());
                            TransactionOutput fromOutput = txFromInput.getOutput(outpoint.getIndex());

                            TxOutputNeoEntity neoTxo = new TxOutputNeoEntity();
                            neoTxo.setId(neoTxoId);
                            neoTxo.setIndex(outpoint.getIndex());
                            neoTxo.setValue(fromOutput.getValue().getValue());
                            neoTxo.setSize(fromOutput.getScriptBytes().length);

                            Optional<Address> addressOrEmpty = MoreScripts.extractAddress(networkParameters, fromOutput.getScriptPubKey());
                            addressOrEmpty.ifPresent(address -> {
                                AddressNeoEntity AddressNeoEntity = addressRepository.findById(address.toString()).orElseGet(() -> {
                                    AddressNeoEntity newAddressNeoEntity = new AddressNeoEntity();
                                    newAddressNeoEntity.setAddress(address.toString());
                                    return addressRepository.save(newAddressNeoEntity);
                                });

                                neoTxo.setAddress(AddressNeoEntity);
                            });

                            return txOutputRepository.save(neoTxo);
                        });

                        neoSpentOutputs.add(TxOutputNeoEntitySpent);
                    });

                    List<TxOutputNeoEntity> neoCreatedOutputs = Lists.newArrayList();
                    tx.getOutputs().forEach(output -> {
                        TxOutputNeoEntity neoTxo = new TxOutputNeoEntity();
                        neoTxo.setId(txId + ":" + output.getIndex());
                        neoTxo.setIndex(output.getIndex());
                        neoTxo.setValue(output.getValue().getValue());
                        neoTxo.setCreatedIn(neoTx);
                        neoTxo.setSize(output.getScriptBytes().length);

                        Optional<Address> addressOrEmpty = MoreScripts.extractAddress(networkParameters, output.getScriptPubKey());
                        addressOrEmpty.ifPresent(address -> {
                            AddressNeoEntity AddressNeoEntity = addressRepository.findById(address.toString()).orElseGet(() -> {
                                AddressNeoEntity newAddressNeoEntity = new AddressNeoEntity();
                                newAddressNeoEntity.setAddress(address.toString());
                                return addressRepository.save(newAddressNeoEntity);
                            });

                            neoTxo.setAddress(AddressNeoEntity);
                        });

                        neoCreatedOutputs.add(txOutputRepository.save(neoTxo));
                    });

                    neoTx.setInputs(neoSpentOutputs);
                    neoTx.setOutputs(neoCreatedOutputs);

                    transactionRepository.save(neoTx);
                });
            });
        };
    }

    @Bean
    @Profile("disabled-for-now")
    public CommandLineRunner mainRunner(NetworkParameters networkParameters,
                                        MessagePublishService<Block> bitcoinjBlockPublishService,
                                        MessagePublishService<Transaction> bitcoinjTranscationPublishService,
                                        BlockNeoRepository blockRepository,
                                        TxNeoRepository transactionRepository,
                                        TxOutputNeoRepository txOutputRepository,
                                        AddressNeoRepository addressRepository,
                                        TransactionTemplate transactionTemplate,
                                        CacheFacade caches) {
        return args -> {
            log.info("Starting example application mainRunner");


            ExecutorService executorService = Executors.newFixedThreadPool(10, new ThreadFactoryBuilder()
                    .setNameFormat("load-tx-%d")
                    .setDaemon(false)
                    .build());

            Runtime.getRuntime().addShutdownHook(ShutdownHooks.shutdownHook(executorService, Duration.ofSeconds(10)));

            Flux.from(bitcoinjBlockPublishService)
                    .subscribe(block -> {
                        transactionTemplate.executeWithoutResult(status -> {
                            String blockHash = block.getHash().toString();
                            String prevBlockHash = block.getPrevBlockHash().toString();

                            log.info("inserting new block {}", blockHash);

                            BlockNeoEntity neoBlock = new BlockNeoEntity();
                            neoBlock.setHash(blockHash);

                            blockRepository.findById(prevBlockHash)
                                    .ifPresent(neoBlock::setPrevblock);

                            blockRepository.save(neoBlock);
                        });
                    });

            Flux.from(bitcoinjTranscationPublishService)
                    /*.parallel()
                    .runOn(Schedulers.fromExecutorService(executorService))
                    .doOnNext(tx -> {
                        Stopwatch stopwatch = Stopwatch.createStarted();
                        log.info("loading data for tx {}", tx.getTxId());

                        for (TransactionInput input : tx.getInputs()) {
                            if (input.isCoinBase()) {
                                // coinbase inputs cannot be fetched
                                // via `getrawtransaction`
                                continue;
                            }
                            TransactionOutPoint outpoint = input.getOutpoint();
                            Transaction txFromInput = caches.tx().getUnchecked(outpoint.getHash());

                            RawTransactionInfo txFromInputInfo = caches.txInfo().getUnchecked(txFromInput.getTxId());

                            Optional.ofNullable(txFromInputInfo.getBlockhash())
                                    .map(caches.blockInfo()::getUnchecked);
                        }
                        log.info("loading data took {} for tx {}", stopwatch.stop(), tx.getTxId());
                    })
                    .sequential()*/
                    .onErrorContinue((e, val) -> {
                        log.error("error on val: " + val, e);
                    })
                    .subscribe(tx -> {
                        transactionTemplate.executeWithoutResult(status -> {
                            String txId = tx.getTxId().toString();

                            log.info("inserting new transaction {}", txId);

                            TxNeoEntity neoTx = new TxNeoEntity();
                            neoTx.setTxid(txId);

                            List<TxOutputNeoEntity> neoSpentOutputs = Lists.newArrayList();
                            tx.getInputs().forEach(input -> {
                                if (input.isCoinBase()) {
                                    // coinbase inputs cannot be fetched
                                    // via `getrawtransaction`
                                    return;
                                }

                                TransactionOutPoint outpoint = input.getOutpoint();

                                String neoTxoId = outpoint.getHash().toString() + ":" + outpoint.getIndex();
                                TxOutputNeoEntity TxOutputNeoEntitySpent = txOutputRepository.findById(neoTxoId).orElseGet(() -> {
                                    Transaction txFromInput = caches.tx().getUnchecked(outpoint.getHash());
                                    TransactionOutput fromOutput = txFromInput.getOutput(outpoint.getIndex());

                                    TxOutputNeoEntity neoTxo = new TxOutputNeoEntity();
                                    neoTxo.setId(neoTxoId);
                                    neoTxo.setIndex(outpoint.getIndex());
                                    neoTxo.setValue(fromOutput.getValue().getValue());
                                    neoTxo.setSize(fromOutput.getScriptBytes().length);

                                    Optional<Address> addressOrEmpty = MoreScripts.extractAddress(networkParameters, fromOutput.getScriptPubKey());
                                    addressOrEmpty.ifPresent(address -> {
                                        AddressNeoEntity AddressNeoEntity = addressRepository.findById(address.toString()).orElseGet(() -> {
                                            AddressNeoEntity newAddressNeoEntity = new AddressNeoEntity();
                                            newAddressNeoEntity.setAddress(address.toString());
                                            return addressRepository.save(newAddressNeoEntity);
                                        });

                                        neoTxo.setAddress(AddressNeoEntity);
                                    });

                                    return txOutputRepository.save(neoTxo);
                                });

                                neoSpentOutputs.add(TxOutputNeoEntitySpent);
                            });

                            List<TxOutputNeoEntity> neoCreatedOutputs = Lists.newArrayList();
                            tx.getOutputs().forEach(output -> {
                                TxOutputNeoEntity neoTxo = new TxOutputNeoEntity();
                                neoTxo.setId(txId + ":" + output.getIndex());
                                neoTxo.setIndex(output.getIndex());
                                neoTxo.setValue(output.getValue().getValue());
                                neoTxo.setCreatedIn(neoTx);
                                neoTxo.setSize(output.getScriptBytes().length);

                                Optional<Address> addressOrEmpty = MoreScripts.extractAddress(networkParameters, output.getScriptPubKey());
                                addressOrEmpty.ifPresent(address -> {
                                    AddressNeoEntity AddressNeoEntity = addressRepository.findById(address.toString()).orElseGet(() -> {
                                        AddressNeoEntity newAddressNeoEntity = new AddressNeoEntity();
                                        newAddressNeoEntity.setAddress(address.toString());
                                        return addressRepository.save(newAddressNeoEntity);
                                    });

                                    neoTxo.setAddress(AddressNeoEntity);
                                });

                                neoCreatedOutputs.add(txOutputRepository.save(neoTxo));
                            });

                            neoTx.setInputs(neoSpentOutputs);
                            neoTx.setOutputs(neoCreatedOutputs);

                            transactionRepository.save(neoTx);
                        });
                    });

            bitcoinjBlockPublishService.awaitRunning(Duration.ofSeconds(10));
            bitcoinjTranscationPublishService.awaitRunning(Duration.ofSeconds(10));
        };
    }

}
