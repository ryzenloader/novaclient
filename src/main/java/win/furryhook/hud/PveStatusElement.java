package win.furryhook.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.pve.AutoMineFeature;
import win.furryhook.feature.impl.pve.PveManagerFeature;
import win.furryhook.feature.impl.visual.HudFeature;
import win.furryhook.pve.PveFeature;
import win.furryhook.utils.render.Textures;
import win.furryhook.utils.render.gui.UiFontStyle;

public final class PveStatusElement extends HudElement {
   private static final float PADDING = 6.0F;
   private static final float ROW_SIZE = 7.0F;
   private static final float ROW_HEIGHT = 13.0F;
   private final List<PveStatusElement.Row> rows = new ArrayList<>();

   public PveStatusElement() {
      super("pve_status", "PvE State");
   }

   @Override
   protected float defaultY(float unit) {
      return 170.0F * unit;
   }

   @Override
   protected boolean preservePositionOnContentResize() {
      return true;
   }

   @Override
   protected void layout(Minecraft mc, float unit) {
      this.rebuildRows();
      float rowSize = 7.0F * unit;
      float contentWidth = 26.0F * unit
         + glassChevronWidth(unit)
         + glassTextWidth("PvE State", 7.5F * unit, UiFontStyle.SEMIBOLD)
         + glassTextWidth(Integer.toString(this.rows.size()), 6.0F * unit, UiFontStyle.MEDIUM);

      for (PveStatusElement.Row row : this.rows) {
         float measured = glassTextWidth(row.label(), rowSize, UiFontStyle.MEDIUM) + 6.0F * unit + glassBadgeWidth(row.value(), rowSize, unit);
         contentWidth = Math.max(contentWidth, measured + 12.0F * unit);
      }

      this.width = contentWidth;
      this.height = (19.0F + this.rows.size() * 13.0F + 5.0F) * unit;
   }

   @Override
   protected void draw(Minecraft mc, float unit) {
      float alpha = this.appearAlpha();
      this.drawGlassListHeader("PvE State", Textures.Icons.OPTION, this.rows.size(), unit, alpha);
      float cursorY = this.y + 19.0F * unit;
      float rowSize = 7.0F * unit;

      for (PveStatusElement.Row row : this.rows) {
         float centerY = cursorY + 13.0F * unit / 2.0F;
         drawGlassText(row.label(), this.x + 6.0F * unit, centerY, rowSize, UiFontStyle.MEDIUM, GLASS_TEXT_COLOR, alpha);
         this.drawGlassBadge(
            row.value(), this.x + this.width - 6.0F * unit, centerY, rowSize, unit, row.accent() ? HudFeature.hudAccent() : glassMutedColor(), alpha
         );
         cursorY += 13.0F * unit;
      }
   }

   private void rebuildRows() {
      this.rows.clear();
      AutoMineFeature autoMine = FeatureManager.INSTANCE.getFeature(AutoMineFeature.class);
      if (autoMine == null) {
         this.rows.add(new PveStatusElement.Row("AutoMine", "In development", false));
      } else {
         AutoMineFeature.Status status = autoMine.currentStatus();
         this.rows.add(new PveStatusElement.Row("AutoMine", status.active() ? displayPhase(status.phase()) : "Disabled", status.active()));
         this.rows.add(new PveStatusElement.Row("Mined ores", Integer.toString(status.minedOres()), false));
         this.rows.add(new PveStatusElement.Row("Known targets", Integer.toString(status.knownTargets()), false));
         this.rows.add(new PveStatusElement.Row("Elapsed", formatDuration(status.elapsedMillis()), false));
         this.rows.add(new PveStatusElement.Row("Mine profile", status.profile(), false));
         this.rows.add(new PveStatusElement.Row("Tool profile", status.toolProfile(), false));
      }

      for (Feature feature : FeatureManager.INSTANCE.getFeatures()) {
         if (feature instanceof PveFeature && feature != autoMine && feature != PveManagerFeature.INSTANCE && feature.isEnabled()) {
            this.rows.add(new PveStatusElement.Row(feature.getName(), "In development", false));
         }
      }
   }

   private static String formatDuration(long millis) {
      long totalSeconds = Math.max(0L, millis / 1000L);
      long hours = totalSeconds / 3600L;
      long minutes = totalSeconds % 3600L / 60L;
      long seconds = totalSeconds % 60L;
      return hours > 0L ? String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds) : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
   }

   private static String displayPhase(String phase) {
      if (phase != null && phase.startsWith("Paused:")) {
         String reason = phase.substring("Paused:".length()).trim();
         return "Paused: " + reason;
      } else {
         return phase;
      }
   }

   private record Row(String label, String value, boolean accent) {
   }
}
