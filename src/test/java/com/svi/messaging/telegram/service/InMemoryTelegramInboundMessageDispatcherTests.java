package com.svi.messaging.telegram.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.svi.messaging.telegram.config.TelegramProperties;
import com.svi.messaging.telegram.model.TelegramDispatchResult;
import com.svi.messaging.telegram.model.TelegramInboundMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InMemoryTelegramInboundMessageDispatcherTests {
	private final TelegramMessageProcessingService processingService = mock(TelegramMessageProcessingService.class);
	private final List<CountDownLatch> releases = new ArrayList<>();
	private InMemoryTelegramInboundMessageDispatcher dispatcher;

	@AfterEach
	void tearDown() {
		releases.forEach(CountDownLatch::countDown);
		if (dispatcher != null) {
			dispatcher.shutdown();
		}
	}

	@Test
	void reservesOnlyOneCopyUnderConcurrentDelivery() throws Exception {
		var started = new CountDownLatch(1);
		var release = releaseLatch();
		doAnswer(invocation -> {
			started.countDown();
			assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
			return null;
		}).when(processingService).process(any());
		dispatcher = dispatcher(100, Clock.systemUTC());
		try (var executor = Executors.newFixedThreadPool(8)) {
			List<Future<TelegramDispatchResult>> futures = new ArrayList<>();
			for (int index = 0; index < 20; index++) {
				futures.add(executor.submit(() -> dispatcher.dispatch(message(1))));
			}
			List<TelegramDispatchResult> results = new ArrayList<>();
			for (var future : futures) {
				results.add(future.get(2, TimeUnit.SECONDS));
			}
			assertThat(results).filteredOn(result -> result == TelegramDispatchResult.ACCEPTED).hasSize(1);
			assertThat(results).filteredOn(result -> result == TelegramDispatchResult.DUPLICATE).hasSize(19);
			assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
		}
	}

	@Test
	void preservesAcceptedOrderAndReleasesRejectedUpdateReservations() throws Exception {
		var firstStarted = new CountDownLatch(1);
		var secondStarted = new CountDownLatch(1);
		var thirdFinished = new CountDownLatch(1);
		var firstRelease = releaseLatch();
		var secondRelease = releaseLatch();
		List<Long> processed = java.util.Collections.synchronizedList(new ArrayList<>());
		doAnswer(invocation -> {
			TelegramInboundMessage message = invocation.getArgument(0);
			processed.add(message.updateId());
			if (message.updateId() == 1) {
				firstStarted.countDown();
				assertThat(firstRelease.await(5, TimeUnit.SECONDS)).isTrue();
			}
			else if (message.updateId() == 2) {
				secondStarted.countDown();
				assertThat(secondRelease.await(5, TimeUnit.SECONDS)).isTrue();
			}
			else {
				thirdFinished.countDown();
			}
			return null;
		}).when(processingService).process(any());
		dispatcher = dispatcher(100, Clock.systemUTC());
		assertThat(dispatcher.dispatch(message(1))).isEqualTo(TelegramDispatchResult.ACCEPTED);
		assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
		assertThat(dispatcher.dispatch(message(2))).isEqualTo(TelegramDispatchResult.ACCEPTED);
		assertThat(dispatcher.dispatch(message(3))).isEqualTo(TelegramDispatchResult.OVERLOADED);
		firstRelease.countDown();
		assertThat(secondStarted.await(2, TimeUnit.SECONDS)).isTrue();
		assertThat(dispatcher.dispatch(message(3))).isEqualTo(TelegramDispatchResult.ACCEPTED);
		secondRelease.countDown();
		assertThat(thirdFinished.await(2, TimeUnit.SECONDS)).isTrue();
		assertThat(processed).containsExactly(1L, 2L, 3L);
	}

	@Test
	void protectsInFlightUpdatesFromExpiryAndRejectsFullDeduplicationStorage() throws Exception {
		var clock = new MutableClock();
		var started = new CountDownLatch(1);
		var release = releaseLatch();
		doAnswer(invocation -> {
			started.countDown();
			assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
			return null;
		}).when(processingService).process(any());
		dispatcher = dispatcher(1, clock);
		assertThat(dispatcher.dispatch(message(1))).isEqualTo(TelegramDispatchResult.ACCEPTED);
		assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
		clock.advance(Duration.ofHours(2));
		assertThat(dispatcher.dispatch(message(1))).isEqualTo(TelegramDispatchResult.DUPLICATE);
		assertThat(dispatcher.dispatch(message(2))).isEqualTo(TelegramDispatchResult.OVERLOADED);
		assertThat(dispatcher.dispatch(message(1))).isEqualTo(TelegramDispatchResult.DUPLICATE);
	}

	@Test
	void expiresCompletedUpdatesAfterTheirRetentionPeriod() throws Exception {
		var clock = new MutableClock();
		var secondStarted = new CountDownLatch(1);
		var secondRelease = releaseLatch();
		doAnswer(invocation -> {
			TelegramInboundMessage message = invocation.getArgument(0);
			if (message.updateId() == 2) {
				secondStarted.countDown();
				assertThat(secondRelease.await(5, TimeUnit.SECONDS)).isTrue();
			}
			return null;
		}).when(processingService).process(any());
		dispatcher = dispatcher(100, clock);
		assertThat(dispatcher.dispatch(message(1))).isEqualTo(TelegramDispatchResult.ACCEPTED);
		assertThat(dispatcher.dispatch(message(2))).isEqualTo(TelegramDispatchResult.ACCEPTED);
		assertThat(secondStarted.await(2, TimeUnit.SECONDS)).isTrue();
		assertThat(dispatcher.dispatch(message(1))).isEqualTo(TelegramDispatchResult.DUPLICATE);
		clock.advance(Duration.ofHours(2));
		assertThat(dispatcher.dispatch(message(1))).isEqualTo(TelegramDispatchResult.ACCEPTED);
		assertThat(dispatcher.dispatch(message(2))).isEqualTo(TelegramDispatchResult.DUPLICATE);
	}

	private InMemoryTelegramInboundMessageDispatcher dispatcher(int capacity, Clock clock) {
		var properties = new TelegramProperties("123:test-token", "test-webhook-secret",
				Duration.ofSeconds(3), Duration.ofSeconds(10), 1024, 1, 1, capacity,
				Duration.ofHours(1), Duration.ofSeconds(1));
		return new InMemoryTelegramInboundMessageDispatcher(
				processingService, properties, new SimpleMeterRegistry(), clock);
	}

	private TelegramInboundMessage message(long updateId) {
		return new TelegramInboundMessage(updateId, 42, updateId, null, "Synthetic test text");
	}

	private CountDownLatch releaseLatch() {
		var latch = new CountDownLatch(1);
		releases.add(latch);
		return latch;
	}

	private static final class MutableClock extends Clock {
		private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

		void advance(Duration duration) {
			now.updateAndGet(value -> value.plus(duration));
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now.get();
		}
	}
}
