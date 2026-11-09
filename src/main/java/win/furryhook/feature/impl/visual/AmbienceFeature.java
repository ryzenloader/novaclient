package win.furryhook.feature.impl.visual;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.util.Mth;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.packet.PacketReceiveEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.Theme;

public final class AmbienceFeature extends Feature {
   private static final String FOG_MODE_VANILLA = "Ванила";
   private static final String FOG_MODE_THEME = "От темы";
   public final ModeSetting timeMode = this.register(
      new ModeSetting("Time of Day", "Day", "Dawn", "Morning", "Day", "Evening", "Sunset", "Night").configKey("render.ambience.time")
   );
   public final BooleanSetting fog = this.register(new BooleanSetting("Туман", true).configKey("render.ambience.fog"));
   public final ModeSetting fogMode = this.register(
      new ModeSetting("Режим тумана", "Ванила", "Ванила", "От темы").configKey("render.ambience.fog.mode").visibleWhen(() -> this.fog.getValue())
   );
   public final NumberSetting fogStart = this.register(
      new NumberSetting("Начало тумана", 8.0, 0.0, 128.0, 1.0, " бл").configKey("render.ambience.fog.start").visibleWhen(() -> this.fog.getValue())
   );
   public final NumberSetting fogEnd = this.register(
      new NumberSetting("Конец тумана", 72.0, 8.0, 256.0, 1.0, " бл").configKey("render.ambience.fog.end").visibleWhen(() -> this.fog.getValue())
   );

   public AmbienceFeature() {
      super("Ambience", "Время суток и туман", FeatureCategory.VISUAL, -1);
   }

   public static AmbienceFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(AmbienceFeature.class);
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE && event.getPacket() instanceof ClientboundSetTimePacket) {
         event.cancel();
      }
   }

   public long getTargetTime() {
      return switch ((String)this.timeMode.getValue()) {
         case "Dawn" -> 23000L;
         case "Morning" -> 1000L;
         case "Evening" -> 12000L;
         case "Sunset" -> 13000L;
         case "Night" -> 18000L;
         default -> 6000L;
      };
   }

   public boolean usesFog() {
      return this.fog.getValue();
   }

   public float resolvedFogStart() {
      return Mth.clamp(this.fogStart.getValue().floatValue(), 0.0F, this.resolvedFogEnd() - 1.0F);
   }

   public float resolvedFogEnd() {
      return Math.max(this.fogStart.getValue().floatValue() + 1.0F, this.fogEnd.getValue().floatValue());
   }

   public void applyFog(FogData data) {
      float start = this.resolvedFogStart();
      float end = this.resolvedFogEnd();
      data.environmentalStart = start;
      data.environmentalEnd = end;
      data.renderDistanceStart = start;
      data.renderDistanceEnd = end;
      data.skyEnd = end * 1.35F;
      data.cloudEnd = end * 1.15F;
      if (this.fogMode.is("От темы")) {
         int color = Theme.getAccent();
         data.color.set(ColorUtil.red(color) / 255.0F, ColorUtil.green(color) / 255.0F, ColorUtil.blue(color) / 255.0F, 1.0F);
      }
   }
}
