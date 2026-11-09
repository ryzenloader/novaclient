package win.furryhook.hud;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.math.AnimatedValue;
import win.furryhook.utils.render.gui.Render2DUtil;
import win.furryhook.utils.render.gui.UiFontStyle;

public final class MediaPlayerElement extends HudElement {
   private static final float CARD_HEIGHT = 32.0F;
   private static final float CARD_PAD = 6.0F;
   private static final float ART_SIZE = 20.0F;
   private static final float ART_RADIUS = 3.0F;
   private static final float TEXT_GAP = 4.0F;
   private static final float TITLE_SIZE = 7.5F;
   private static final float ARTIST_SIZE = 6.5F;
   private static final float PROGRESS_HEIGHT = 1.5F;
   private static final float PROGRESS_RADIUS = 0.75F;
   private static final float MAX_TEXT_WIDTH = 112.0F;
   private static final Identifier NO_ART_ID = Identifier.parse("furryhook:textures/hud/mediaplayer_noart.png");
   private final ExecutorService mediaExecutor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "furryhook-mediaplayer");
      t.setDaemon(true);
      return t;
   });
   private final AnimatedValue widthAnim = new AnimatedValue();
   private final AnimatedValue progressAnim = new AnimatedValue();
   private String displayTitle = "";
   private String displayArtist = "";
   private volatile String title = "Not playing";
   private volatile String artist = "";
   private volatile long durationMs;
   private volatile long positionMs;
   private volatile boolean playing;
   private volatile Identifier artworkId;
   private byte[] lastArtworkBytes;
   private String lastArtworkTrack;
   private boolean noArtUploaded;
   private volatile MediaBridge bridge;
   private volatile boolean bridgeResolved;

   public MediaPlayerElement() {
      super("mediaplayer", "Media Player");
   }

   @Override
   protected void layout(Minecraft mc, float unit) {
      long now = System.nanoTime();
      this.pollMedia(mc);
      float target = this.measureCardWidth(unit);
      float width = this.widthAnim.update(target, 0.13F, now);
      this.width = width;
      this.height = width > 0.5F ? 32.0F * unit : 0.0F;
      if (width < target) {
         float textBudget = Math.max(0.0F, width - 36.0F * unit);
         this.displayTitle = glassEllipsize(this.displayTitle, 7.5F * unit, UiFontStyle.SEMIBOLD, textBudget);
         this.displayArtist = glassEllipsize(this.displayArtist, 6.5F * unit, UiFontStyle.MEDIUM, textBudget);
      }
   }

   private float measureCardWidth(float unit) {
      this.displayTitle = glassEllipsize(this.title, 7.5F * unit, UiFontStyle.SEMIBOLD, 112.0F * unit);
      this.displayArtist = glassEllipsize(this.artist, 6.5F * unit, UiFontStyle.MEDIUM, 112.0F * unit);
      float titleW = glassTextWidth(this.displayTitle, 7.5F * unit, UiFontStyle.SEMIBOLD);
      float artistW = glassTextWidth(this.displayArtist, 6.5F * unit, UiFontStyle.MEDIUM);
      return 36.0F * unit + Math.max(titleW, artistW);
   }

   @Override
   protected void draw(Minecraft mc, float unit) {
      if (!(this.width <= 0.5F)) {
         float alpha = this.appearAlpha();
         float cardHeight = 32.0F * unit;
         int accent = HudFeature.hudAccent();
         this.drawWatermarkPanel(this.x, this.y, this.width, cardHeight, 5.0F * unit, unit, alpha);
         if (!(this.width <= 36.0F * unit)) {
            float artSize = 20.0F * unit;
            float artX = this.x + 6.0F * unit;
            float artY = this.y + (cardHeight - artSize) / 2.0F;
            Identifier art = this.artworkId != null ? this.artworkId : this.ensureNoArt();
            Render2DUtil.texture(artX, artY, artSize, artSize, art).radius(3.0F * unit).color(ColorUtil.multiplyAlpha(-1, alpha)).draw();
            float textX = artX + artSize + 4.0F * unit;
            drawGlassText(this.displayTitle, textX, this.y + 10.0F * unit, 7.5F * unit, UiFontStyle.SEMIBOLD, GLASS_TEXT_COLOR, alpha);
            if (!this.displayArtist.isEmpty()) {
               drawGlassText(this.displayArtist, textX, this.y + 20.0F * unit, 6.5F * unit, UiFontStyle.MEDIUM, glassMutedColor(), alpha);
            }

            if (this.durationMs > 0L) {
               float barX = textX;
               float barY = this.y + cardHeight - 4.5F * unit;
               float barW = this.x + this.width - 6.0F * unit - barX;
               float progress = Math.min(1.0F, (float)this.positionMs / (float)this.durationMs);
               float eased = this.progressAnim.update(progress, 0.3F, System.nanoTime());
               Render2DUtil.rect(barX, barY, barW, 1.5F * unit)
                  .color(ColorUtil.multiplyAlpha(ColorUtil.rgba(255, 255, 255, 18), alpha))
                  .radius(0.75F * unit)
                  .draw();
               if (eased > 0.001F) {
                  Render2DUtil.rect(barX, barY, barW * eased, 1.5F * unit).color(ColorUtil.multiplyAlpha(accent, alpha)).radius(0.75F * unit).draw();
               }
            }
         }
      }
   }

   private void pollMedia(Minecraft mc) {
      this.mediaExecutor.submit(() -> {
         try {
            MediaBridge b = this.ensureBridge();
            if (b == null) {
               return;
            }

            MediaSnapshot snap = b.poll();
            if (snap == null) {
               snap = MediaSnapshot.EMPTY;
            }

            this.title = snap.title();
            this.artist = snap.artist();
            this.durationMs = snap.durationMs();
            this.positionMs = snap.positionMs();
            this.playing = snap.playing();
            byte[] artwork = snap.artworkPng();
            String trackKey = this.title + " — " + this.artist;
            if (artwork != null && artwork.length > 0 && !trackKey.equals(this.lastArtworkTrack)) {
               Identifier id = uploadArtwork(mc, artwork, trackKey);
               if (id != null) {
                  this.artworkId = id;
                  this.lastArtworkTrack = trackKey;
                  this.lastArtworkBytes = artwork;
               }
            } else if (artwork == null || artwork.length == 0) {
               this.artworkId = null;
               this.lastArtworkTrack = trackKey;
            }
         } catch (Throwable ignored) {
            this.title = "Not playing";
            this.artist = "";
            this.durationMs = 0L;
            this.positionMs = 0L;
            this.playing = false;
            this.artworkId = null;
         }
      });
   }

   private MediaBridge ensureBridge() {
      if (this.bridgeResolved) {
         return this.bridge;
      }

      this.bridgeResolved = true;

      try {
         Class<?> impl = Class.forName("win.furryhook.hud.MediaBridgeImpl");
         this.bridge = (MediaBridge)impl.getDeclaredConstructor().newInstance();
      } catch (Throwable var2) {
      }

      return this.bridge;
   }

   private static Identifier uploadArtwork(Minecraft mc, byte[] pngBytes, String trackKey) {
      Identifier id = Identifier.parse("furryhook:dynamic/mediaplayer/" + Integer.toHexString(trackKey.hashCode()));

      try (InputStream in = new ByteArrayInputStream(pngBytes)) {
         NativeImage image = NativeImage.read(in);
         DynamicTexture texture = new DynamicTexture(id::toString, image);
         texture.upload();
         mc.getTextureManager().register(id, texture);
         return id;
      } catch (IOException e) {
         return null;
      }
   }

   private Identifier ensureNoArt() {
      if (this.noArtUploaded) {
         return NO_ART_ID;
      }

      Minecraft mc = Minecraft.getInstance();

      try (InputStream in = mc.getResourceManager().open(NO_ART_ID)) {
         NativeImage image = NativeImage.read(in);
         DynamicTexture texture = new DynamicTexture(NO_ART_ID::toString, image);
         texture.upload();
         mc.getTextureManager().register(NO_ART_ID, texture);
         this.noArtUploaded = true;
      } catch (Throwable var7) {
      }

      return NO_ART_ID;
   }
}
