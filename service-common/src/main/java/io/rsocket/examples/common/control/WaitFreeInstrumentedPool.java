package io.rsocket.examples.common.control;

import java.time.Duration;
import java.util.function.Function;

import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.pool.InstrumentedPool;
import reactor.pool.PooledRef;
import reactor.util.context.Context;

@RequiredArgsConstructor
public class WaitFreeInstrumentedPool<POOLABLE> implements InstrumentedPool<POOLABLE> {

	final InstrumentedPool<POOLABLE> origin;
	final OverflowStrategy           overflowStrategy;

	@Override
	public PoolMetrics metrics() {
		return origin.metrics();
	}

	public <T> Flux<T> withPoolable(Function<POOLABLE, Publisher<T>> scopeFunction) {
		return origin.withPoolable(scopeFunction)
				.transform(f -> new Flux<T>() {
					@Override
					public void subscribe(CoreSubscriber<? super T> actual) {
						WaitFreeSubscriber waitFreeSubscriber = new WaitFreeSubscriber<>(actual);
						f.subscribe(waitFreeSubscriber);
						if (!waitFreeSubscriber.done) {
							waitFreeSubscriber.onComplete();
						}
					}
				})
				.doOnError(__ -> {
					if (overflowStrategy == OverflowStrategy.TERMINATE) {
						dispose();
					}
				});
	}

	@Override
	public Mono<Integer> warmup() {
		return origin.warmup();
	}

	@Override
	public Mono<PooledRef<POOLABLE>> acquire() {
		return origin.acquire();
	}

	@Override
	public Mono<PooledRef<POOLABLE>> acquire(Duration timeout) {
		return origin.acquire(timeout);
	}

	@Override
	public Mono<Void> disposeLater() {
		return origin.disposeLater();
	}

	@Override
	public boolean isDisposed() {
		return origin.isDisposed();
	}

	@RequiredArgsConstructor
	static class WaitFreeSubscriber<T> implements CoreSubscriber<T>, Subscription {

		final CoreSubscriber<? extends T> actual;

		Subscription s;
		boolean      done;

		@Override
		public Context currentContext() {
			return actual.currentContext();
		}

		@Override
		public void onSubscribe(Subscription s) {
			if (Operators.validate(this.s, s)) {
				this.s = s;
				actual.onSubscribe(this);
			}
		}

		@Override
		public void onNext(T t) {

		}

		@Override
		public void onError(Throwable t) {
			done = true;
			actual.onError(t);
		}

		@Override
		public void onComplete() {
			done = true;
			actual.onComplete();
		}

		@Override
		public void request(long n) {
			s.request(n);
		}

		@Override
		public void cancel() {
			s.cancel();
		}
	}
}
