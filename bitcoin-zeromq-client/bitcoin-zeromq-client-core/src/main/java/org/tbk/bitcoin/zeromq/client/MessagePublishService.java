package org.tbk.bitcoin.zeromq.client;

import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static java.util.Objects.requireNonNull;

@Slf4j
public final class MessagePublishService<T> extends AbstractIdleService implements Publisher<T> {

    private final String serviceId = Integer.toHexString(System.identityHashCode(this));

    private final ExecutorService publisherExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
            .setNameFormat("zmq-msg-pub-" + serviceId + "-%d")
            .setDaemon(false)
            .build());

    private final SubmissionPublisher<T> publisher = new SubmissionPublisher<>(publisherExecutor, Flow.defaultBufferSize());

    private final Scheduler subscribeOnScheduler = Schedulers.newSingle("zmq-msg-sub-" + serviceId);

    private final MessagePublisherFactory<T> bitcoinMessagePublisher;

    private Disposable subscription;

    public MessagePublishService(MessagePublisherFactory<T> bitcoinMessagePublisher) {
        this.bitcoinMessagePublisher = requireNonNull(bitcoinMessagePublisher);
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        publisher.subscribe(FlowAdapters.toFlowSubscriber(s));
    }

    @Override
    protected String serviceName() {
        return String.format("%s-%s-%s", super.serviceName(), bitcoinMessagePublisher.getTopicName(), serviceId);
    }

    @Override
    protected void startUp() {
        log.info("starting..");

        this.subscription = bitcoinMessagePublisher.create()
                .subscribeOn(subscribeOnScheduler)
                .subscribe(publisher::submit);

        log.info("started successfully");
    }

    @Override
    protected void shutDown() {
        log.info("terminating..");

        this.subscription.dispose();
        this.subscribeOnScheduler.dispose();

        this.publisher.close();

        boolean executorShutdownSuccessful = shutdownAndAwaitTermination(publisherExecutor, Duration.ofSeconds(10));
        if (!executorShutdownSuccessful) {
            log.warn("unclean shutdown of executor service");
        }

        log.info("terminated");
    }
}
