package win.furryhook.utils.render.gui;

import win.furryhook.utils.math.MathUtil;

public final class LiquidGlass {
   private static final float BEVEL_FROM_RADIUS = 1.15F;
   private static final float MIN_BEVEL = 5.0F;
   private static final float MAX_BEVEL = 22.0F;
   private static boolean enabled = true;
   private static float intensity = 1.0F;
   private static float refraction = 1.0F;
   private static float specular = 0.55F;
   private static float dispersion = 0.55F;
   private static float frost = 0.3F;
   private static float pointerX;
   private static float pointerY;
   private static boolean pointerValid;

   private LiquidGlass() {
   }

   public static boolean enabled() {
      return enabled;
   }

   public static void setEnabled(boolean value) {
      enabled = value;
   }

   public static float intensity() {
      return intensity;
   }

   public static void setIntensity(float value) {
      intensity = MathUtil.clamp(value, 0.0F, 1.0F);
   }

   public static float specular() {
      return specular;
   }

   public static void setSpecular(float value) {
      specular = MathUtil.clamp(value, 0.0F, 1.0F);
   }

   public static float dispersion() {
      return dispersion;
   }

   public static void setDispersion(float value) {
      dispersion = MathUtil.clamp(value, 0.0F, 1.0F);
   }

   public static float frost() {
      return frost;
   }

   public static void setFrost(float value) {
      frost = MathUtil.clamp(value, 0.0F, 1.0F);
   }

   public static float refraction() {
      return refraction;
   }

   public static void setRefraction(float value) {
      refraction = MathUtil.clamp(value, 0.0F, 2.0F);
   }

   public static void setPointer(float x, float y) {
      pointerX = x;
      pointerY = y;
      pointerValid = true;
   }

   public static void clearPointer() {
      pointerValid = false;
   }

   public static float bevelFor(float cornerRadius) {
      float bevel = Math.max(cornerRadius, 0.0F) * 1.15F;
      return MathUtil.clamp(bevel, 5.0F, 22.0F) * refraction;
   }

   public static float lightX(float centerX, float centerY) {
      return component(centerX, centerY, true);
   }

   public static float lightY(float centerX, float centerY) {
      return component(centerX, centerY, false);
   }

   private static float component(float centerX, float centerY, boolean horizontal) {
      if (!pointerValid) {
         return 0.0F;
      }

      float dx = pointerX - centerX;
      float dy = pointerY - centerY;
      float length = (float)Math.sqrt(dx * dx + dy * dy);
      return length < 0.001F ? 0.0F : (horizontal ? dx : dy) / length;
   }
}
