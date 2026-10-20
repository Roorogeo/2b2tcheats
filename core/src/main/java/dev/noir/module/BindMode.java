package dev.noir.module;

public enum BindMode {
    TOGGLE("Toggle"),
    HOLD("Hold");

    private final String label;

    BindMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
