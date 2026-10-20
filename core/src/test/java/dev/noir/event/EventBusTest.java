package dev.noir.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusTest {
    record Ping(int n) {}

    static final class Stoppable extends Cancellable {
        @Override public boolean stopsPropagation() { return true; }
    }

    @Test
    void priorityOrder() {
        List<String> order = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        EventBus bus = new EventBus((m, t) -> errors.add(m));
        bus.subscribe(Ping.class, 0, p -> order.add("normal"));
        bus.subscribe(Ping.class, 10, p -> order.add("high"));
        bus.subscribe(Ping.class, -5, p -> order.add("low"));
        bus.post(new Ping(1));
        assertEquals(List.of("high", "normal", "low"), order);
    }

    @Test
    void unsubscribeAndErrorIsolation() {
        List<String> errors = new ArrayList<>();
        EventBus bus = new EventBus((m, t) -> errors.add(m));
        int[] count = {0};
        bus.subscribe(Ping.class, 5, p -> { throw new IllegalStateException("boom"); });
        EventBus.Subscription s = bus.subscribe(Ping.class, p -> count[0]++);
        bus.post(new Ping(1));
        s.unsubscribe();
        bus.post(new Ping(2));
        assertEquals(1, count[0]);
        assertEquals(2, errors.size());
    }

    @Test
    void cancellationStopsWhenRequested() {
        EventBus bus = new EventBus((m, t) -> {});
        int[] after = {0};
        bus.subscribe(Stoppable.class, 1, Cancellable::cancel);
        bus.subscribe(Stoppable.class, 0, e -> after[0]++);
        Stoppable e = bus.post(new Stoppable());
        assertTrue(e.isCancelled());
        assertEquals(0, after[0]);
    }
}
