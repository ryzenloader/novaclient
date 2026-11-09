package win.furryhook.feature.impl.combat;

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.DiscardPolicy;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.LineEvent.Type;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.EntityAttackEvent;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.ButtonSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.utils.ConfigIO;

public final class HitSoundFeature extends Feature {
   private static final Logger LOGGER = LoggerFactory.getLogger(HitSoundFeature.class);
   private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("wav", "wave", "aiff", "aif", "au", "ogg");
   private static final String NONE = "None";
   private static final int RESCAN_INTERVAL_TICKS = 100;
   private static final long MAX_PLAYBACK_MILLIS = 10000L;
   private static final ExecutorService PLAYBACK_EXECUTOR = new ThreadPoolExecutor(1, 4, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), runnable -> {
      Thread thread = new Thread(runnable, "HitSound Playback");
      thread.setDaemon(true);
      return thread;
   }, new DiscardPolicy());
   public final NumberSetting volume = this.register(new NumberSetting("Volume", 100.0, 0.0, 200.0, 1.0, "%"));
   public final ModeSetting sound = this.register(new ModeSetting("Sound", "None", "None"));
   public final ButtonSetting openFolder = this.register(new ButtonSetting("Sounds Folder", "Open Folder", this::openSoundsFolder));
   private volatile Map<String, Path> loadedSounds = Map.of();
   private int tickCounter;

   public HitSoundFeature() {
      super("HitSound", "Plays your own sound files when you attack an entity", FeatureCategory.COMBAT, -1);
   }

   private Path soundsDir() {
      return ConfigIO.resolve("hitsounds");
   }

   @Override
   protected void onEnable() {
      this.rescan();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (++this.tickCounter >= 100) {
         this.tickCounter = 0;
         this.rescan();
      }
   }

   @EventTarget
   public void onAttack(EntityAttackEvent event) {
      Path path = this.loadedSounds.get(this.sound.getValue());
      if (path != null) {
         this.playAsync(path);
      }
   }

   private void openSoundsFolder() {
      PLAYBACK_EXECUTOR.execute(() -> {
         Path dir = this.soundsDir();

         try {
            Files.createDirectories(dir);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.OPEN)) {
               Desktop.getDesktop().open(dir.toFile());
            } else {
               new ProcessBuilder("explorer.exe", dir.toString()).start();
            }
         } catch (IOException | UnsupportedOperationException exception) {
            LOGGER.warn("Failed to open hitsounds folder", exception);
            return;
         }

         this.rescan();
      });
   }

   private void rescan() {
      Path dir = this.soundsDir();
      Map<String, Path> found = new LinkedHashMap<>();
      if (Files.isDirectory(dir)) {
         try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : stream.sorted().toList()) {
               if (Files.isRegularFile(path) && SUPPORTED_EXTENSIONS.contains(extensionOf(path))) {
                  found.put(path.getFileName().toString(), path);
               }
            }
         } catch (IOException exception) {
            LOGGER.warn("Failed to scan hitsounds folder", exception);
         }
      }

      this.loadedSounds = Map.copyOf(found);
      List<String> options = found.isEmpty() ? List.of("None") : List.copyOf(found.keySet());
      this.sound.updateModes(options);
   }

   private static String extensionOf(Path path) {
      String fileName = path.getFileName().toString();
      int dot = fileName.lastIndexOf(46);
      return dot >= 0 && dot != fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
   }

   private void playAsync(Path path) {
      double percent = this.volume.getValue();
      if (!(percent <= 0.0)) {
         PLAYBACK_EXECUTOR.execute(() -> this.play(path, percent));
      }
   }

   private void play(Path path, double percent) {
      Clip clip = null;

      try (AudioInputStream stream = this.openAudioStream(path)) {
         clip = AudioSystem.getClip();
         CountDownLatch finished = new CountDownLatch(1);
         clip.addLineListener(lineEvent -> {
            if (lineEvent.getType() == Type.STOP) {
               finished.countDown();
            }
         });
         clip.open(stream);
         this.applyVolume(clip, percent);
         clip.start();
         long micros = clip.getMicrosecondLength();
         long waitMillis = micros > 0L ? Math.min(10000L, micros / 1000L + 1000L) : 10000L;
         finished.await(waitMillis, TimeUnit.MILLISECONDS);
      } catch (InterruptedException exception) {
         Thread.currentThread().interrupt();
      } catch (UnsupportedAudioFileException | IOException | LineUnavailableException exception) {
         LOGGER.warn("Failed to play hitsound {}", path.getFileName(), exception);
      } finally {
         if (clip != null) {
            clip.stop();
            clip.close();
         }
      }
   }

   private AudioInputStream openAudioStream(Path path) throws IOException, UnsupportedAudioFileException {
      return "ogg".equals(extensionOf(path)) ? this.decodeOgg(path) : AudioSystem.getAudioInputStream(path.toFile());
   }

   private AudioInputStream decodeOgg(Path path) throws IOException {
      byte[] bytes = Files.readAllBytes(path);
      ByteBuffer encoded = BufferUtils.createByteBuffer(bytes.length);
      encoded.put(bytes).flip();
      MemoryStack stack = MemoryStack.stackPush();

      AudioInputStream var32;
      try {
         IntBuffer error = stack.mallocInt(1);
         long decoder = STBVorbis.stb_vorbis_open_memory(encoded, error, null);
         if (decoder == 0L) {
            throw new IOException("stb_vorbis_open_memory failed with error " + error.get(0));
         }

         try {
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(decoder, info);
            int channels = info.channels();
            int sampleRate = info.sample_rate();
            int lengthSamples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);
            ShortBuffer pcm = MemoryUtil.memAllocShort(lengthSamples * channels);

            try {
               int decodedSamples = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
               pcm.position(0).limit(decodedSamples * channels);
               byte[] pcmBytes = new byte[pcm.remaining() * 2];

               for (int i = 0; pcm.hasRemaining(); i++) {
                  short sample = pcm.get();
                  pcmBytes[i * 2] = (byte)(sample & 0xFF);
                  pcmBytes[i * 2 + 1] = (byte)(sample >> 8 & 0xFF);
               }

               AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
               var32 = new AudioInputStream(new ByteArrayInputStream(pcmBytes), format, pcmBytes.length / format.getFrameSize());
            } finally {
               MemoryUtil.memFree(pcm);
            }
         } finally {
            STBVorbis.stb_vorbis_close(decoder);
         }
      } catch (Throwable var30) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var27) {
               var30.addSuppressed(var27);
            }
         }

         throw var30;
      }

      if (stack != null) {
         stack.close();
      }

      return var32;
   }

   private void applyVolume(Clip clip, double percent) {
      if (clip.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
         FloatControl gainControl = (FloatControl)clip.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
         float gain = (float)(20.0 * Math.log10(Math.max(1.0E-4, percent / 100.0)));
         gain = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), gain));
         gainControl.setValue(gain);
      }
   }
}
