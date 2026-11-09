package win.furryhook.feature.impl.combat;

import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.gizmos.Gizmos.TemporaryCollection;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.render.Render3DEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class HitBoxesFeature extends Feature implements MinecraftContext {
   private static final double MAX_RENDER_DISTANCE_SQR = 4096.0;
   private static final int HITBOX_COLOR = -16711809;
   private static final int EYE_COLOR = -43691;
   private static final float LINE_WIDTH = 1.5F;
   public final NumberSetting size = this.register(new NumberSetting("Size", 3.0, 1.0, 10.0, 0.5, ""));
   public final BooleanSetting withAura = this.register(new BooleanSetting("With Aura", false));
   public final BooleanSetting showSize = this.register(new BooleanSetting("Show Size", false));
   public final BooleanSetting yModification = this.register(new BooleanSetting("Y Size", false));

   public HitBoxesFeature() {
      super("HitBoxes", "Expands the collision size of surrounding entities for easier hits", FeatureCategory.COMBAT, -1);
   }

   public static HitBoxesFeature getInstance() {
      return FeatureManager.INSTANCE.getFeature(HitBoxesFeature.class);
   }

   public static HitBoxesFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(HitBoxesFeature.class);
   }

   public double getHorizontalExpansion() {
      return this.size.getValue() / 10.0;
   }

   public double getVerticalExpansion() {
      return this.yModification.getValue() ? this.getHorizontalExpansion() : 0.0;
   }

   public double getAuraExpansion() {
      return this.isEnabled() && this.withAura.getValue() ? this.getHorizontalExpansion() : 0.0;
   }

   public AABB expandBoundingBox(AABB boundingBox) {
      double horizontal = this.getHorizontalExpansion();
      return boundingBox.inflate(horizontal, this.getVerticalExpansion(), horizontal);
   }

   public boolean appliesTo(Entity entity) {
      return this.isEnabled() && entity instanceof LivingEntity livingEntity && livingEntity.isAlive();
   }

   @EventTarget
   public void onRender3D(Render3DEvent event) {
      if (this.isEnabled() && this.showSize.getValue() && mc.player != null && mc.level != null && event.getClient().levelRenderer != null) {
         float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
         SimpleGizmoCollector collector = new SimpleGizmoCollector();
         TemporaryCollection ignored = Gizmos.withCollector(collector);

         try {
            for (Entity entity : mc.level.entitiesForRendering()) {
               if (entity instanceof LivingEntity livingEntity && livingEntity.isAlive() && entity != mc.player && !(mc.player.distanceToSqr(entity) > 4096.0)) {
                  AABB box = this.interpolatedBox(livingEntity, partialTick);
                  Gizmos.addGizmo(new HitBoxesFeature.BoundingBoxGizmo(box, -16711809, 1.5F)).setAlwaysOnTop();
                  double eyeY = Mth.lerp(partialTick, livingEntity.yOld, livingEntity.getY()) + livingEntity.getEyeHeight();
                  AABB eyeBox = new AABB(box.minX, eyeY - 0.01, box.minZ, box.maxX, eyeY + 0.01, box.maxZ);
                  Gizmos.addGizmo(new HitBoxesFeature.BoundingBoxGizmo(eyeBox, -43691, 1.5F)).setAlwaysOnTop();
               }
            }
         } catch (Throwable var13) {
            if (ignored != null) {
               try {
                  ignored.close();
               } catch (Throwable var12) {
                  var13.addSuppressed(var12);
               }
            }

            throw var13;
         }

         if (ignored != null) {
            ignored.close();
         }

         event.getClient().levelRenderer.addMainThreadGizmos(collector.drainGizmos());
      }
   }

   private AABB interpolatedBox(LivingEntity entity, float partialTick) {
      AABB box = this.expandBoundingBox(entity.getBoundingBox());
      double renderX = Mth.lerp(partialTick, entity.xOld, entity.getX());
      double renderY = Mth.lerp(partialTick, entity.yOld, entity.getY());
      double renderZ = Mth.lerp(partialTick, entity.zOld, entity.getZ());
      return box.move(renderX - entity.getX(), renderY - entity.getY(), renderZ - entity.getZ());
   }

   private record BoundingBoxGizmo(AABB box, int color, float lineWidth) implements Gizmo {
      public void emit(GizmoPrimitives primitives, float alpha) {
         Vec3 minMinMin = new Vec3(this.box.minX, this.box.minY, this.box.minZ);
         Vec3 minMinMax = new Vec3(this.box.minX, this.box.minY, this.box.maxZ);
         Vec3 minMaxMin = new Vec3(this.box.minX, this.box.maxY, this.box.minZ);
         Vec3 minMaxMax = new Vec3(this.box.minX, this.box.maxY, this.box.maxZ);
         Vec3 maxMinMin = new Vec3(this.box.maxX, this.box.minY, this.box.minZ);
         Vec3 maxMinMax = new Vec3(this.box.maxX, this.box.minY, this.box.maxZ);
         Vec3 maxMaxMin = new Vec3(this.box.maxX, this.box.maxY, this.box.minZ);
         Vec3 maxMaxMax = new Vec3(this.box.maxX, this.box.maxY, this.box.maxZ);
         primitives.addLine(minMinMin, minMinMax, this.color, this.lineWidth);
         primitives.addLine(minMinMin, minMaxMin, this.color, this.lineWidth);
         primitives.addLine(minMinMin, maxMinMin, this.color, this.lineWidth);
         primitives.addLine(minMinMax, minMaxMax, this.color, this.lineWidth);
         primitives.addLine(minMinMax, maxMinMax, this.color, this.lineWidth);
         primitives.addLine(minMaxMin, minMaxMax, this.color, this.lineWidth);
         primitives.addLine(minMaxMin, maxMaxMin, this.color, this.lineWidth);
         primitives.addLine(maxMinMin, maxMinMax, this.color, this.lineWidth);
         primitives.addLine(maxMinMin, maxMaxMin, this.color, this.lineWidth);
         primitives.addLine(maxMinMax, maxMaxMax, this.color, this.lineWidth);
         primitives.addLine(minMaxMax, maxMaxMax, this.color, this.lineWidth);
         primitives.addLine(maxMaxMin, maxMaxMax, this.color, this.lineWidth);
      }
   }
}
