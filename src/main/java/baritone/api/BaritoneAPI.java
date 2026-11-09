package baritone.api;
public final class BaritoneAPI {
    private static final IBaritoneProvider provider = new IBaritoneProvider() {
        @Override
        public IBaritone getPrimaryBaritone() { return null; }
    };
    private static final Settings settings = new Settings();
    public static IBaritoneProvider getProvider() { return provider; }
    public static Settings getSettings() { return settings; }
}
