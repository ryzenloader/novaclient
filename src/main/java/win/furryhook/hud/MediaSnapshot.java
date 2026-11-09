package win.furryhook.hud;

public record MediaSnapshot(String title, String artist, long durationMs, long positionMs, boolean playing, byte[] artworkPng) {
   public static final MediaSnapshot EMPTY = new MediaSnapshot("Not playing", "", 0L, 0L, false, null);

   public MediaSnapshot {
      if (title == null || title.isEmpty()) {
         title = durationMs > 0L ? "Unknown title" : "Not playing";
      }

      if (artist == null) {
         artist = "";
      }
   }
}
