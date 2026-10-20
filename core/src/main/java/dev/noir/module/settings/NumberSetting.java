package dev.noir.module.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.noir.util.MathUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** A number with a slider range and step. Values are clamped and snapped to the step. */
public final class NumberSetting extends Setting<Double, NumberSetting> {
    private final double min;
    private final double max;
    private final double step;
    private final int decimals;
    private String unit = "";

    public NumberSetting(String name, double defaultValue, double min, double max, double step, String description) {
        super(name, description, defaultValue);
        if (min > max) throw new IllegalArgumentException(name + ": min > max");
        if (defaultValue < min || defaultValue > max) throw new IllegalArgumentException(name + ": default out of range");
        this.min = min;
        this.max = max;
        this.step = step;
        this.decimals = MathUtil.decimalsFor(step);
    }

    public NumberSetting unit(String unit) {
        this.unit = unit;
        return this;
    }

    /** Blocks values above {@code limit} in Strict mode. */
    public NumberSetting strictMax(double limit, String reason) {
        return strict(v -> v > limit + 1e-9, reason);
    }

    public double min() { return min; }
    public double max() { return max; }
    public double step() { return step; }
    public String unit() { return unit; }

    public double getDouble() { return get(); }
    public float getFloat() { return get().floatValue(); }
    public int getInt() { return (int) Math.round(get()); }

    /** 0..1 position of the raw value within the range. */
    public double fraction() {
        return max == min ? 0 : (rawValue() - min) / (max - min);
    }

    public double valueAtFraction(double f) {
        return min + MathUtil.clamp(f, 0, 1) * (max - min);
    }

    @Override
    protected Double sanitize(Double v) {
        if (v.isNaN() || v.isInfinite()) return rawValue();
        double s = MathUtil.snap(MathUtil.clamp(v, min, max), min, step);
        s = MathUtil.clamp(s, min, max);
        return BigDecimal.valueOf(s).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(rawValue());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) set(json.getAsDouble());
    }

    @Override
    public Double preview(String text) {
        try {
            String t = text.trim();
            if (!unit.isEmpty() && t.toLowerCase(Locale.ROOT).endsWith(unit.toLowerCase(Locale.ROOT))) {
                t = t.substring(0, t.length() - unit.length()).trim();
            }
            double v = Double.parseDouble(t);
            if (v < min || v > max) return null;
            return sanitize(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String format(double v) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v) + unit;
    }

    @Override
    public String displayValue(Double v) {
        return format(v);
    }

    @Override
    public String typeName() {
        return format(min) + ".." + format(max);
    }
}
