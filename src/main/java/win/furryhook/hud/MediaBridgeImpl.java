package win.furryhook.hud;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MediaBridgeImpl implements MediaBridge {
   private static final List<String> OWNER_PRIORITY = List.of("SPOTIFY", "CHROME");

   @Override
   public MediaSnapshot poll() {
      try {
         MediaPlayerInfo bridge = MediaPlayerInfo.Instance;
         List<IMediaSession> sessions = bridge != null ? bridge.getMediaSessions() : null;
         IMediaSession session = pickSession(sessions);
         if (session != null && session.getMedia() != null) {
            MediaInfo media = session.getMedia();
            String title = media.getTitle() != null && !media.getTitle().isEmpty() ? media.getTitle() : "Unknown title";
            String artist = media.getArtist() != null && !media.getArtist().isEmpty() ? media.getArtist() : "Unknown artist";
            return new MediaSnapshot(title, artist, media.getDuration(), media.getPosition(), media.getPlaying(), media.getArtworkPng());
         } else {
            return MediaSnapshot.EMPTY;
         }
      } catch (Throwable t) {
         return MediaSnapshot.EMPTY;
      }
   }

   private static IMediaSession pickSession(List<IMediaSession> sessions) {
      return sessions != null && !sessions.isEmpty() ? sessions.stream().filter(s -> {
         MediaInfo m = s.getMedia();
         return m != null && (m.getTitle() != null && !m.getTitle().isEmpty() || m.getArtist() != null && !m.getArtist().isEmpty());
      }).min(Comparator.comparingInt(MediaBridgeImpl::ownerPriorityIndex)).orElse(null) : null;
   }

   private static int ownerPriorityIndex(IMediaSession session) {
      String owner = session.getOwner() != null ? session.getOwner().toUpperCase(Locale.ROOT) : "";

      for (int i = 0; i < OWNER_PRIORITY.size(); i++) {
         if (owner.contains(OWNER_PRIORITY.get(i))) {
            return i;
         }
      }

      return OWNER_PRIORITY.size();
   }
}
