package dev.noir.event;

/** Base for events whose default action can be cancelled by a listener. */
public abstract class Cancellable {
    private boolean cancelled;

    public void cancel() { cancelled = true; }

    public boolean isCancelled() { return cancelled; }

    /** Whether remaining listeners are skipped once cancelled. Defaults to false so observers still see it. */
    public boolean stopsPropagation() { return false; }
}
