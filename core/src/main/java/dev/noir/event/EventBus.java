package dev.noir.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Minimal typed event bus. Listeners run in descending priority order; a listener
 * that throws is reported and skipped so one bad module can't break the tick loop.
 * Dispatch is exact-type (no superclass walking) which keeps posting cheap.
 */
public final class EventBus {
    private final Map<Class<?>, CopyOnWriteArrayList<Listener<?>>> listeners = new ConcurrentHashMap<>();
    private final BiConsumer<String, Throwable> errorSink;

    public EventBus(BiConsumer<String, Throwable> errorSink) {
        this.errorSink = errorSink;
    }

    public <E> Subscription subscribe(Class<E> type, Consumer<? super E> handler) {
        return subscribe(type, 0, handler);
    }

    public <E> Subscription subscribe(Class<E> type, int priority, Consumer<? super E> handler) {
        Listener<E> listener = new Listener<>(priority, handler);
        CopyOnWriteArrayList<Listener<?>> list = listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());
        synchronized (list) {
            int i = 0;
            while (i < list.size() && list.get(i).priority >= priority) i++;
            list.add(i, listener);
        }
        return () -> list.remove(listener);
    }

    @SuppressWarnings("unchecked")
    public <E> E post(E event) {
        List<Listener<?>> list = listeners.get(event.getClass());
        if (list == null) return event;
        for (Listener<?> l : list) {
            try {
                ((Listener<E>) l).handler.accept(event);
            } catch (Throwable t) {
                errorSink.accept("Event listener failed for " + event.getClass().getSimpleName(), t);
            }
            if (event instanceof Cancellable c && c.isCancelled() && c.stopsPropagation()) break;
        }
        return event;
    }

    public boolean hasListeners(Class<?> type) {
        List<Listener<?>> list = listeners.get(type);
        return list != null && !list.isEmpty();
    }

    @FunctionalInterface
    public interface Subscription {
        void unsubscribe();
    }

    private record Listener<E>(int priority, Consumer<? super E> handler) {}
}
