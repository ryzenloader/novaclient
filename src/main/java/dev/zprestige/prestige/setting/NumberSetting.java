package dev.zprestige.prestige.setting;

public class NumberSetting extends Setting {
	private double value;
	public final double min;
	public final double max;
	public final double step;

	public NumberSetting(String name, double def, double min, double max, double step) {
		super(name);
		this.min = min;
		this.max = max;
		this.step = step;
		set(def);
	}

	public double get() { return value; }
	public int getInt() { return (int) Math.round(value); }
	public float getFloat() { return (float) value; }

	public void set(double v) {
		double snapped = Math.round(v / step) * step;
		value = Math.max(min, Math.min(max, snapped));
	}

	public void increase() { set(value + step); }
	public void decrease() { set(value - step); }

	@Override
	public String display() {
		return step >= 1 ? Integer.toString(getInt()) : String.format("%.1f", value);
	}

	@Override public String serialize() { return Double.toString(value); }

	@Override
	public void deserialize(String text) {
		try { set(Double.parseDouble(text)); } catch (NumberFormatException ignored) {}
	}
}
