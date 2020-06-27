package io.rsocket.examples.common.control;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.rsocket.Payload;
import io.rsocket.lease.Lease;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ReplayProcessor;

/**
 * Requester-side Lease listener.<br>
 * In the nutshell this class implements mechanism to listen (and do appropriate actions as
 * needed) to incoming leases issued by the Responder
 */
public class DefaultLeaseReceiver extends BaseSubscriber<Lease> implements LeaseReceiver {

  static final Logger logger = LoggerFactory.getLogger(DefaultLeaseReceiver.class);

  final UUID uuid;
  final ReplayProcessor<Lease> lastLeaseReplay;

  volatile int allowedRequests;
  static final AtomicIntegerFieldUpdater<DefaultLeaseReceiver> ALLOWED_REQUESTS =
          AtomicIntegerFieldUpdater.newUpdater(DefaultLeaseReceiver.class, "allowedRequests");

  Lease currentLease;

  public DefaultLeaseReceiver(UUID uuid) {
    this.uuid = uuid;
    this.lastLeaseReplay = ReplayProcessor.cacheLast();
  }

  @Override
  public void accept(Flux<Lease> receivedLeases) {
    receivedLeases.subscribe(this);
  }

  @Override
  protected void hookOnNext(Lease l) {
    currentLease = l;
    logger.debug(
            "From Connection[" + uuid + "]: Received leases - ttl: {}, requests: {}",
            l.getTimeToLiveMillis(),
            l.getAllowedRequests());
    ALLOWED_REQUESTS.getAndSet(this, l.getAllowedRequests());
    lastLeaseReplay.onNext(l);
  }

  public Flux<Payload> acquireLease(Flux<Payload> source) {
    return lastLeaseReplay.filter(l -> ALLOWED_REQUESTS.getAndDecrement(this) > 0 && l.isValid())
                          .next()
                          .flatMapMany(l -> source);
  }

  public <T> Mono<T> acquireLease(Mono<T> source) {
    return lastLeaseReplay.filter(l -> ALLOWED_REQUESTS.getAndDecrement(this) > 0 && l.isValid())
                          .next()
                          .flatMap(l -> source);
  }
}