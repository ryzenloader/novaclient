package win.furryhook.feature.impl.visual;

import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.projectile.Projectile;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.render.Render2DEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ColorMode;
import win.furryhook.feature.setting.ColorSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.MultiSelectSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.combat.HealthUtil;
import win.furryhook.utils.render.HurtUtil;
import win.furryhook.utils.render.Render3DUtil;
import win.furryhook.utils.render.gui.Render2DUtil;

public final class EntityEspFeature extends Feature {
   private static final int BASE_COLOR = -14188801;
   private static final float ESP_ALPHA = 0.92F;
   private static final float FADE_START_DISTANCE = 0.4F;
   private static final float FADE_FULL_DISTANCE = 2.0F;
   private static final float MIN_RENDER_ALPHA = 0.05F;
   private static final float BOX_LINE_WIDTH = 1.0F;
   private final MultiSelectSetting targets = this.register(
      new MultiSelectSetting("Targets", Set.of("Players", "Hostile"), "Players", "Hostile", "Passive", "Items", "Projectiles")
   );
   private final ModeSetting boxMode = this.register(new ModeSetting("Box Mode", "Corners", "Corners", "Box"));
   private final BooleanSetting rounded = this.register(new BooleanSetting("Rounded", false));
   private final BooleanSetting healthBar = this.register(new BooleanSetting("Health Bar", true));
   private final BooleanSetting box = this.register(new BooleanSetting("Box", true));
   private final NumberSetting distance = this.register(new NumberSetting("Distance", 64.0, 5.0, 128.0, 1.0, " blocks"));
   private final ModeSetting colorMode = this.register(ColorMode.setting());
   private final ColorSetting espColor = this.register(
      new ColorSetting("Color", -14188801).configKey("Glow Color").visibleWhen(() -> ColorMode.isCustom(this.colorMode))
   );
   private boolean frameDrawBox;
   private boolean frameCornerMode;
   private boolean frameRounded;
   private boolean frameHealthBar;
   private boolean frameFirstPersonSelf;
   private double frameMaxDistanceSqr;
   private int frameColor;
   private boolean framePlayers;
   private boolean frameItems;
   private boolean frameProjectiles;
   private boolean frameHostile;
   private boolean framePassive;

   public EntityEspFeature() {
      super("EntityESP", "Draws boxes and glow around selected entities.", FeatureCategory.VISUAL, -1);
   }

   @EventTarget
   public void onRender2D(Render2DEvent event) {
      Minecraft mc = event.getClient();
      if (mc != null && mc.level != null && mc.player != null) {
         this.captureFrameState(mc);
         if (this.frameDrawBox || this.frameHealthBar) {
            float tickDelta = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);

            for (Entity entity : mc.level.entitiesForRendering()) {
               if (this.isValidOverlayTarget(mc, entity)) {
                  float alpha = this.overlayAlpha(mc.player, entity);
                  if (!(alpha < 0.05F)) {
                     Render3DUtil.ScreenBounds bounds = this.projectEntityBounds(mc, entity, tickDelta);
                     if (bounds != null && !(bounds.width() < 2.0F) && !(bounds.height() < 2.0F)) {
                        int color = HurtUtil.blend(this.frameColor, entity, alpha);
                        if (this.frameDrawBox) {
                           if (this.frameCornerMode) {
                              if (this.frameRounded) {
                                 this.drawRoundedCornerBox(bounds, color);
                              } else {
                                 this.drawCornerBox(bounds, color);
                              }
                           } else if (this.frameRounded) {
                              this.drawRoundedBox(bounds, color);
                           } else {
                              this.drawBox(bounds, color);
                           }
                        }

                        if (this.frameHealthBar && entity instanceof LivingEntity livingEntity) {
                           this.drawHealthBar(bounds, livingEntity, alpha);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void captureFrameState(Minecraft mc) {
      this.frameDrawBox = this.box.getValue();
      this.frameCornerMode = this.boxMode.is("Corners");
      this.frameRounded = this.rounded.getValue();
      this.frameHealthBar = this.healthBar.getValue();
      this.frameFirstPersonSelf = mc.options.getCameraType().isFirstPerson();
      double maxDistance = this.distance.getValue();
      this.frameMaxDistanceSqr = maxDistance * maxDistance;
      this.frameColor = this.resolveColor();
      this.framePlayers = this.targets.isSelected("Players");
      this.frameItems = this.targets.isSelected("Items");
      this.frameProjectiles = this.targets.isSelected("Projectiles");
      this.frameHostile = this.targets.isSelected("Hostile");
      this.framePassive = this.targets.isSelected("Passive");
   }

   private boolean isValidOverlayTarget(Minecraft mc, Entity entity) {
      if (entity == null || entity.isRemoved() || !entity.isAlive()) {
         return false;
      } else if (entity == mc.player && this.frameFirstPersonSelf) {
         return false;
      } else {
         return !this.isWithinRenderDistance(mc.player, entity) ? false : this.isTarget(entity);
      }
   }

   private boolean isWithinRenderDistance(Player viewer, Entity entity) {
      return viewer != null && viewer.distanceToSqr(entity) <= this.frameMaxDistanceSqr;
   }

   private boolean isTarget(Entity entity) {
      if (entity instanceof Player) {
         return this.framePlayers;
      }

      if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
         return this.frameItems;
      }

      if (!(entity instanceof Projectile) && !(entity instanceof EyeOfEnder) && !(entity instanceof EvokerFangs)) {
         MobCategory category = entity.getType().getCategory();
         if (category == MobCategory.MONSTER) {
            return this.frameHostile;
         } else {
            return category != MobCategory.CREATURE
                  && category != MobCategory.AXOLOTLS
                  && category != MobCategory.AMBIENT
                  && category != MobCategory.UNDERGROUND_WATER_CREATURE
                  && category != MobCategory.WATER_CREATURE
                  && category != MobCategory.WATER_AMBIENT
               ? false
               : this.framePassive;
         }
      } else {
         return this.frameProjectiles;
      }
   }

   private float overlayAlpha(Player viewer, Entity entity) {
      float distance = viewer.distanceTo(entity);
      float progress = (distance - 0.4F) / 1.6F;
      return 0.92F * Mth.clamp(progress, 0.0F, 1.0F);
   }

   private int resolveColor() {
      return ColorMode.resolve(this.colorMode, this.espColor);
   }

   private Render3DUtil.ScreenBounds projectEntityBounds(Minecraft mc, Entity entity, float tickDelta) {
      double x = Render3DUtil.lerpX(entity, tickDelta);
      double y = Render3DUtil.lerpY(entity, tickDelta);
      double z = Render3DUtil.lerpZ(entity, tickDelta);
      double halfWidth = entity.getBbWidth() / 1.5;
      double height = entity.getBbHeight() + 0.1 - (entity.isShiftKeyDown() ? 0.2 : 0.0);
      return Render3DUtil.projectBoxBounds(mc, x - halfWidth, y, z - halfWidth, x + halfWidth, y + height, z + halfWidth);
   }

   private void drawBox(Render3DUtil.ScreenBounds bounds, int color) {
      int minX = Math.round(bounds.minX());
      int minY = Math.round(bounds.minY());
      int maxX = Math.round(bounds.maxX());
      int maxY = Math.round(bounds.maxY());
      int width = maxX - minX;
      int height = maxY - minY;
      if (width > 0 && height > 0) {
         Render2DUtil.rect(minX, minY, width, Math.max(1, Math.round(1.0F))).color(color).draw();
         Render2DUtil.rect(minX, maxY - 1, width, 1.0F).color(color).draw();
         Render2DUtil.rect(minX, minY, 1.0F, height).color(color).draw();
         Render2DUtil.rect(maxX - 1, minY, 1.0F, height).color(color).draw();
      }
   }

   private void drawCornerBox(Render3DUtil.ScreenBounds bounds, int color) {
      int minX = Math.round(bounds.minX());
      int minY = Math.round(bounds.minY());
      int maxX = Math.round(bounds.maxX());
      int maxY = Math.round(bounds.maxY());
      int width = maxX - minX;
      int height = maxY - minY;
      if (width > 0 && height > 0) {
         int cornerWidth = Math.max(2, Math.round(width / 4.0F));
         int cornerHeight = Math.max(2, Math.round(height / 4.0F));
         Render2DUtil.rect(minX, minY, cornerWidth, 1.0F).color(color).draw();
         Render2DUtil.rect(minX, minY, 1.0F, cornerHeight).color(color).draw();
         Render2DUtil.rect(maxX - cornerWidth, minY, cornerWidth, 1.0F).color(color).draw();
         Render2DUtil.rect(maxX - 1, minY, 1.0F, cornerHeight).color(color).draw();
         Render2DUtil.rect(minX, maxY - 1, cornerWidth, 1.0F).color(color).draw();
         Render2DUtil.rect(minX, maxY - cornerHeight, 1.0F, cornerHeight).color(color).draw();
         Render2DUtil.rect(maxX - cornerWidth, maxY - 1, cornerWidth, 1.0F).color(color).draw();
         Render2DUtil.rect(maxX - 1, maxY - cornerHeight, 1.0F, cornerHeight).color(color).draw();
      }
   }

   private void drawRoundedBox(Render3DUtil.ScreenBounds bounds, int color) {
      float minX = Math.round(bounds.minX());
      float minY = Math.round(bounds.minY());
      float width = Math.round(bounds.maxX()) - minX;
      float height = Math.round(bounds.maxY()) - minY;
      if (!(width <= 0.0F) && !(height <= 0.0F)) {
         float radius = Math.min(width, height) * 0.75F / 4.0F;
         Render2DUtil.rect(minX, minY, width, height).color(0).radius(radius).border(1.0F, color).draw();
      }
   }

   private void drawRoundedCornerBox(Render3DUtil.ScreenBounds bounds, int color) {
      float minX = Math.round(bounds.minX());
      float minY = Math.round(bounds.minY());
      float width = Math.round(bounds.maxX()) - minX;
      float height = Math.round(bounds.maxY()) - minY;
      if (!(width <= 0.0F) && !(height <= 0.0F)) {
         float cornerWidth = Math.max(3.0F, width / 4.0F);
         float cornerHeight = Math.max(3.0F, height / 4.0F);
         float radius = Math.min(cornerWidth, cornerHeight) * 0.75F;
         float maxY = minY + height;
         float maxX = minX + width;
         float[][] corners = new float[][]{{minX, minY}, {maxX - cornerWidth, minY}, {minX, maxY - cornerHeight}, {maxX - cornerWidth, maxY - cornerHeight}};

         for (float[] corner : corners) {
            Render2DUtil.pushScissor(corner[0], corner[1], cornerWidth, cornerHeight);
            Render2DUtil.rect(minX, minY, width, height).color(0).radius(radius).border(1.0F, color).draw();
            Render2DUtil.popScissor();
         }
      }
   }

   private void drawHealthBar(Render3DUtil.ScreenBounds bounds, LivingEntity entity, float alpha) {
      float health = Mth.clamp(HealthUtil.getHealth(entity), 0.0F, entity.getMaxHealth());
      float ratio = entity.getMaxHealth() <= 0.0F ? 0.0F : health / entity.getMaxHealth();
      int barHeight = Math.max(1, Math.round(bounds.height()));
      int filledHeight = Math.max(0, Math.round(barHeight * ratio));
      int x = Math.round(bounds.minX()) - 4;
      int y = Math.round(bounds.minY());
      Render2DUtil.rect(x, y, 2.0F, barHeight).color(ColorUtil.multiplyAlpha(Integer.MIN_VALUE, alpha)).draw();
      if (filledHeight > 0) {
         int fillY = y + (barHeight - filledHeight);
         int fillColor = ColorUtil.multiplyAlpha(ColorUtil.lerp(-65536, -16711936, ratio), alpha);
         Render2DUtil.rect(x, fillY, 2.0F, filledHeight).color(fillColor).draw();
      }
   }
}
