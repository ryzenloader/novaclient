package win.furryhook.feature.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.combat.AuraFeature;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class SwingAnimationFeature extends Feature {
   public final ModeSetting mode = this.register(
      new ModeSetting(
         "Mode", "Smooth", "None", "Smooth", "Swipe", "GSmar", "Translate", "Jump Translate", "Smooth Block", "Smooth Old", "Default", "Big", "Fap"
      )
   );
   public final NumberSetting strength = this.register(new NumberSetting("Strength", 1.0, 0.5, 3.0, 0.1, ""));
   public final NumberSetting duration = this.register(new NumberSetting("Duration", 1.0, 0.5, 4.0, 0.1, ""));
   public final NumberSetting angle = this.register(
      new NumberSetting("Angle", 40.0, 0.0, 70.0, 1.0, "°")
         .visibleWhen(() -> this.style() == SwingAnimationFeature.Style.TRANSLATE || this.style() == SwingAnimationFeature.Style.SMOOTH_BLOCK)
   );
   public final BooleanSetting onlySwinging = this.register(new BooleanSetting("Only Swinging", false));
   public final BooleanSetting onlyAura = this.register(new BooleanSetting("Only Aura", false));
   public final BooleanSetting spin360 = this.register(new BooleanSetting("Spin 360", false));
   public final ModeSetting spinHand = this.register(
      new ModeSetting("Spin Hand", "Right Arm", "Both Arms", "Right Arm", "Left Arm").visibleWhen(this.spin360::getValue)
   );
   public final NumberSetting spinSpeed = this.register(new NumberSetting("Spin Speed", 2.0, 1.0, 5.0, 1.0, "").visibleWhen(this.spin360::getValue));
   private boolean swingLatched;

   public SwingAnimationFeature() {
      super("SwingAnimation", "Custom first person swing animation", FeatureCategory.VISUAL, -1);
   }

   public SwingAnimationFeature.Style style() {
      return switch ((String)this.mode.getValue()) {
         case "None" -> SwingAnimationFeature.Style.NONE;
         case "Smooth" -> SwingAnimationFeature.Style.SMOOTH;
         case "Swipe" -> SwingAnimationFeature.Style.SWIPE;
         case "GSmar" -> SwingAnimationFeature.Style.GSMAR;
         case "Translate" -> SwingAnimationFeature.Style.TRANSLATE;
         case "Jump Translate" -> SwingAnimationFeature.Style.JUMP_TRANSLATE;
         case "Smooth Block" -> SwingAnimationFeature.Style.SMOOTH_BLOCK;
         case "Smooth Old" -> SwingAnimationFeature.Style.SMOOTH_OLD;
         case "Default" -> SwingAnimationFeature.Style.DEFAULT;
         case "Big" -> SwingAnimationFeature.Style.BIG;
         case "Fap" -> SwingAnimationFeature.Style.FAP;
         default -> SwingAnimationFeature.Style.NONE;
      };
   }

   public boolean shouldApply(float swingProgress) {
      boolean progressing = swingProgress > 0.001F;
      if (this.isEnabled() && this.style() != SwingAnimationFeature.Style.NONE) {
         if (!progressing) {
            this.swingLatched = false;
         }

         if (this.onlySwinging.getValue() && !progressing) {
            return false;
         }

         if (this.onlyAura.getValue() && !this.isAuraTargeting()) {
            return this.swingLatched && progressing;
         }

         this.swingLatched = progressing;
         return true;
      } else {
         this.swingLatched = false;
         return false;
      }
   }

   public boolean shouldModifyDuration() {
      if (!this.isEnabled()) {
         return false;
      } else {
         return this.onlyAura.getValue() ? this.isAuraTargeting() : true;
      }
   }

   public float durationMultiplier() {
      return this.duration.getValue().floatValue();
   }

   public boolean spinAppliesTo(HumanoidArm arm) {
      if (this.isEnabled() && this.spin360.getValue()) {
         return switch ((String)this.spinHand.getValue()) {
            case "Both Arms" -> true;
            case "Left Arm" -> arm == HumanoidArm.LEFT;
            default -> arm == HumanoidArm.RIGHT;
         };
      } else {
         return false;
      }
   }

   public void applySpin(PoseStack poseStack) {
      int divisor = Math.max(1, 6 - this.spinSpeed.getValue().intValue());
      float angleDeg = (float)(System.currentTimeMillis() % (360L * divisor)) / divisor;
      rotate(poseStack, angleDeg, -1.0F, 0.0F, 0.0F);
   }

   public void apply(PoseStack poseStack, HumanoidArm arm, float rawProgress, float equipProgress) {
      float progress = Mth.clamp(rawProgress, 0.0F, 1.0F);
      int side = arm == HumanoidArm.RIGHT ? 1 : -1;
      float sin1 = Mth.sin(progress * progress * (float) Math.PI);
      float sin2 = Mth.sin(Mth.sqrt(progress) * (float) Math.PI);
      float power = this.strength.getValue().floatValue();
      float sVal = power * 70.0F;
      switch (this.style()) {
         case SMOOTH:
            poseStack.translate(side * 0.56F, -0.42F, -0.72F);
            poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0F + sin1 * -20.0F * power)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * sin2 * -20.0F * power));
            poseStack.mulPose(Axis.XP.rotationDegrees(sin2 * -80.0F * power));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * -45));
            poseStack.translate(0.0, -0.1, 0.0);
            break;
         case SWIPE:
            poseStack.translate(0.56F * side, -0.32F, -0.72F);
            poseStack.mulPose(Axis.YP.rotationDegrees(70 * side));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-20 * side));
            poseStack.mulPose(Axis.YP.rotationDegrees(sin2 * sin1 * -5.0F * power));
            poseStack.mulPose(Axis.XP.rotationDegrees(sin2 * sin1 * -120.0F * power));
            poseStack.mulPose(Axis.XP.rotationDegrees(-70.0F));
            break;
         case GSMAR:
            float thrust = Mth.sin(progress * (float) Math.PI);
            float snap = Mth.sin(progress * (float) (Math.PI * 2)) * thrust;
            poseStack.translate(side * 0.5F, -0.34F + thrust * 0.08F * power, -0.62F - thrust * 0.34F * power);
            poseStack.mulPose(Axis.YP.rotationDegrees(side * (38.0F - thrust * 10.0F * power)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * (-13.0F + snap * 8.0F * power)));
            poseStack.mulPose(Axis.XP.rotationDegrees(-68.0F + snap * 34.0F * power));
            poseStack.translate(0.0F, -0.08F, thrust * 0.1F * power);
            break;
         case TRANSLATE:
            poseStack.translate(0.6F, 0.0F, -0.8F);
            poseStack.translate(0.56F, -1.0F, -0.82F);
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-60.0F));
            float translateAngle = -this.angle.getValue().floatValue() - 40.0F * Mth.sin(progress * (float) Math.PI) * sVal / 50.0F;
            rotate(poseStack, translateAngle, 1.0F, 0.0F, 0.0F);
            rotate(poseStack, -20.0F, 0.01F, 0.0F, 0.0F);
            poseStack.translate(-0.5F, 0.08F, 0.0F);
            break;
         case JUMP_TRANSLATE:
            float offset = -0.2F * Mth.sin(progress * (float) Math.PI);
            float phase = Mth.sqrt(Math.max(0.0F, -offset)) * (float) Math.PI;
            poseStack.translate(0.4F, -0.15F, -0.5F);
            poseStack.translate(0.0F, offset * -sVal / 100.0F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
            rotate(poseStack, Mth.sin(offset * offset * (float) Math.PI) * -34.0F, 0.0F, 1.0F, 0.2F);
            rotate(poseStack, Mth.sin(phase) * -20.7F, 0.2F, 0.1F, 1.0F);
            rotate(poseStack, Mth.sin(phase) * -68.6F, 1.3F, 0.1F, 0.2F);
            poseStack.scale(0.4F, 0.4F, 0.4F);
            poseStack.mulPose(Axis.YP.rotationDegrees(30.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(60.0F));
            break;
         case SMOOTH_BLOCK:
            poseStack.translate(0.6F, 0.0F, -0.8F);
            poseStack.translate(0.56F, -1.0F, -0.82F);
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-60.0F));
            float blockAngle = -this.angle.getValue().floatValue() - 40.0F * Mth.sin(progress * (float) Math.PI) * sVal / 50.0F;
            rotate(poseStack, blockAngle, 1.0F, equipProgress, 0.0F);
            rotate(poseStack, -40.0F, 0.01F, 0.0F, 0.0F);
            poseStack.translate(-0.5F, 0.08F, 0.0F);
            break;
         case SMOOTH_OLD:
            poseStack.translate(0.4F, 0.0F, -0.8F);
            poseStack.translate(0.56F, -0.44F, -0.71999997F);
            poseStack.mulPose(Axis.YP.rotationDegrees(72.0F));
            rotate(poseStack, sin2 * -10.0F, 0.0F, 0.0F, 1.0F);
            rotate(poseStack, sin2 * -sVal, 0.01F, 0.0F, 0.0F);
            rotate(poseStack, -20.0F, 0.01F, 0.0F, 0.0F);
            poseStack.translate(-0.5F, 0.08F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(20.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(20.0F));
            break;
         case DEFAULT:
            float angleDefault = -sVal * Mth.sin(progress * (float) Math.PI) * sVal / 100.0F;
            poseStack.translate(1.2F, -0.1F, -0.71999997F);
            poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
            poseStack.translate(-0.5F, 0.2F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(30.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(60.0F));
            poseStack.translate(0.3F, 0.0F, 0.4F);
            poseStack.translate(0.0F, 0.5F, 0.0F);
            rotate(poseStack, 90.0F, 1.0F, 0.0F, -1.0F);
            poseStack.translate(0.6F, 0.5F, 0.0F);
            rotate(poseStack, -90.0F, 1.0F, 0.0F, -1.0F);
            rotate(poseStack, -10.0F, 1.0F, 0.0F, -1.0F);
            rotate(poseStack, 10.0F, -1.0F, 0.0F, 0.0F);
            poseStack.translate(0.0F, 0.0F, -0.5F);
            rotate(poseStack, angleDefault, 1.0F, 0.0F, 1.0F);
            poseStack.translate(0.0F, 0.0F, 0.5F);
            break;
         case BIG:
            poseStack.translate(0.5F, -0.3F, -0.65F);
            poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
            rotate(poseStack, sin1 * -20.0F, 0.0F, 1.0F, 0.0F);
            rotate(poseStack, sin2 * -20.0F, 0.0F, 0.0F, 1.0F);
            rotate(poseStack, sin2 * -sVal, 1.0F, 0.0F, 0.0F);
            poseStack.scale(0.5F, 0.5F, 0.5F);
            poseStack.translate(-0.5F, 0.2F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(30.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(60.0F));
            break;
         case FAP:
            poseStack.translate(1.2F, -0.1F, -0.71999997F);
            poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
            poseStack.translate(-0.5F, 0.2F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(30.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(60.0F));
            long now = System.currentTimeMillis();
            long remainder = now % 255L;
            int pulse = (int)Math.min(255L, (remainder > 127L ? Math.abs(remainder - 255L) : remainder) * 2L);
            float legacySwingOffset = -0.2F * Mth.sin(progress * (float) Math.PI);
            float folded = legacySwingOffset > 0.5F ? 1.0F - legacySwingOffset : legacySwingOffset;
            poseStack.translate(0.3F, 0.0F, 0.4F);
            poseStack.translate(0.0F, 0.5F, 0.0F);
            rotate(poseStack, 90.0F, 1.0F, 0.0F, -1.0F);
            poseStack.translate(0.6F, 0.5F, 0.0F);
            rotate(poseStack, -90.0F, 1.0F, 0.0F, -1.0F);
            rotate(poseStack, -10.0F, 1.0F, 0.0F, -1.0F);
            rotate(poseStack, -folded * 10.0F, 10.0F, 10.0F, -9.0F);
            rotate(poseStack, 10.0F, -1.0F, 0.0F, 0.0F);
            poseStack.translate(0.0, 0.0, -0.5);
            boolean swinging = progress > 0.001F;
            float fapAngle = swinging ? -pulse / (110.0F - sVal) : 1.0F;
            rotate(poseStack, fapAngle, 1.0F, 0.0F, 1.0F);
            poseStack.translate(0.0, 0.0, 0.5);
      }
   }

   private static void rotate(PoseStack poseStack, float degrees, float axisX, float axisY, float axisZ) {
      float lengthSquared = axisX * axisX + axisY * axisY + axisZ * axisZ;
      if (lengthSquared != 0.0F && degrees != 0.0F) {
         float invLength = Mth.invSqrt(lengthSquared);
         poseStack.mulPose(Axis.of(new Vector3f(axisX * invLength, axisY * invLength, axisZ * invLength)).rotationDegrees(degrees));
      }
   }

   private boolean isAuraTargeting() {
      AuraFeature aura = FeatureManager.INSTANCE.getEnabled(AuraFeature.class);
      return aura != null && aura.getCurrentTarget() != null;
   }

   public enum Style {
      NONE,
      SMOOTH,
      SWIPE,
      GSMAR,
      TRANSLATE,
      JUMP_TRANSLATE,
      SMOOTH_BLOCK,
      SMOOTH_OLD,
      DEFAULT,
      BIG,
      FAP;
   }
}
