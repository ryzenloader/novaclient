package win.furryhook.feature.impl.movement;

import net.minecraft.world.phys.Vec3;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;

public final class ElytraBoostFeature extends Feature {
   private static final long BOOST_WINDOW_MS = 150L;
   public final ModeSetting mode = this.register(new ModeSetting("Boost Mode", "BravoGrief", "Custom", "Bravo", "BravoGrief", "ReallyWorld"));
   public final BooleanSetting nearBoost = this.register(new BooleanSetting("Near Player Boost", false));
   private final NumberSetting cXZ00 = this.register(this.slider("XZ 0-5", 1.52));
   private final NumberSetting cXZ05 = this.register(this.slider("XZ 5-10", 1.53));
   private final NumberSetting cXZ10 = this.register(this.slider("XZ 10-15", 1.54));
   private final NumberSetting cXZ15 = this.register(this.slider("XZ 15-20", 1.55));
   private final NumberSetting cXZ20 = this.register(this.slider("XZ 20-25", 1.56));
   private final NumberSetting cXZ25 = this.register(this.slider("XZ 25-30", 1.57));
   private final NumberSetting cXZ30 = this.register(this.slider("XZ 30-35", 1.58));
   private final NumberSetting cXZ35 = this.register(this.slider("XZ 35-40", 1.59));
   private final NumberSetting cXZ40 = this.register(this.slider("XZ 40-45", 1.6));
   private final NumberSetting cY00 = this.register(this.slider("Y 0-5", 1.51));
   private final NumberSetting cY05 = this.register(this.slider("Y 5-10", 1.52));
   private final NumberSetting cY10 = this.register(this.slider("Y 10-15", 1.53));
   private final NumberSetting cY15 = this.register(this.slider("Y 15-20", 1.54));
   private final NumberSetting cY20 = this.register(this.slider("Y 20-25", 1.55));
   private final NumberSetting cY25 = this.register(this.slider("Y 25-30", 1.56));
   private final NumberSetting cY30 = this.register(this.slider("Y 30-35", 1.57));
   private final NumberSetting cY35 = this.register(this.slider("Y 35-40", 1.58));
   private final NumberSetting cY40 = this.register(this.slider("Y 40-45", 1.59));
   private static final double[] BRAVOGRIEF_XZ = new double[]{1.67, 1.68, 1.69, 1.72, 1.72, 1.73, 1.74, 1.75, 1.76};
   private static final double[] BRAVOGRIEF_Y = new double[]{1.72, 1.72, 1.72, 1.72, 1.72, 1.73, 1.74, 1.76, 1.8};
   private static final double[] REALLYWORLD_XZ = new double[]{1.6, 1.63, 1.66, 1.71, 1.73, 1.81, 1.81, 1.81, 1.81};
   private static final double[] REALLYWORLD_Y = new double[]{1.6, 1.62, 1.6, 1.62, 1.64, 1.68, 1.75, 1.9, 2.16};
   private static long lastBoostAppliedMs = Long.MIN_VALUE;

   private NumberSetting slider(String name, double def) {
      return new NumberSetting(name, def, 1.5, 3.0, 0.01, "").visibleWhen(() -> this.mode.is("Custom"));
   }

   public ElytraBoostFeature() {
      super("ElytraBoost", "Boosts elytra firework velocity beyond vanilla", FeatureCategory.MOVEMENT, -1);
   }

   public static ElytraBoostFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(ElytraBoostFeature.class);
   }

   public static void markBoostApplied() {
      lastBoostAppliedMs = System.currentTimeMillis();
   }

   public static boolean isBoostActiveNow() {
      return System.currentTimeMillis() - lastBoostAppliedMs < 150L;
   }

   public Vec3 computeBoost(float yaw, float pitch) {
      float wrappedYaw = wrapDegrees(yaw);
      double xz;
      double y;
      switch ((String)this.mode.getValue()) {
         case "Custom":
            xz = this.customXZ(wrappedYaw);
            y = this.customY(pitch);
            if (y > xz) {
               xz = y;
            }
            break;
         case "Bravo":
            xz = this.bravoXZ(pitch, wrappedYaw);
            y = this.bravoY(pitch);
            break;
         case "BravoGrief":
            xz = arrayLookup(BRAVOGRIEF_XZ, fold(wrappedYaw));
            y = arrayLookup(BRAVOGRIEF_Y, fold(pitch));
            break;
         default:
            xz = arrayLookup(REALLYWORLD_XZ, fold(wrappedYaw));
            y = arrayLookup(REALLYWORLD_Y, fold(pitch));
            if (y > xz) {
               xz = y;
            }
      }

      return new Vec3(xz, y, xz);
   }

   private double customXZ(float yaw) {
      NumberSetting[] xz = new NumberSetting[]{this.cXZ00, this.cXZ05, this.cXZ10, this.cXZ15, this.cXZ20, this.cXZ25, this.cXZ30, this.cXZ35, this.cXZ40};
      int i = (int)(fold(yaw) / 5.0F);
      if (i < 0) {
         i = 0;
      }

      if (i >= xz.length) {
         i = xz.length - 1;
      }

      return xz[i].getValue();
   }

   private double customY(float pitch) {
      NumberSetting[] y = new NumberSetting[]{this.cY00, this.cY05, this.cY10, this.cY15, this.cY20, this.cY25, this.cY30, this.cY35, this.cY40};
      int i = (int)(fold(pitch) / 5.0F);
      if (i < 0) {
         i = 0;
      }

      if (i >= y.length) {
         i = y.length - 1;
      }

      return y[i].getValue();
   }

   private double bravoXZ(float pitch, float yaw) {
      float p = Math.abs(pitch);
      float y = Math.abs(wrapDegrees(yaw) % 90.0F);
      double v;
      if (p >= 38.0F && p <= 52.0F) {
         v = 2.0;
      } else if (p >= 32.0F && p <= 58.0F) {
         v = 1.96;
      } else if (p >= 28.0F && p <= 62.0F) {
         v = 1.95;
      } else if ((!(y >= 29.0F) || !(y <= 61.0F)) && (!(p >= 29.0F) || !(p <= 61.0F))) {
         if ((!(y >= 28.0F) || !(y <= 60.0F)) && (!(p >= 28.0F) || !(p <= 60.0F))) {
            if ((!(y >= 26.0F) || !(y <= 64.0F)) && (!(p >= 26.0F) || !(p <= 64.0F))) {
               if ((!(y >= 24.0F) || !(y <= 66.0F)) && (!(p >= 24.0F) || !(p <= 66.0F))) {
                  if ((!(y >= 15.0F) || !(y <= 75.0F)) && (!(p >= 15.0F) || !(p <= 75.0F))) {
                     if ((!(y >= 13.0F) || !(y <= 77.0F)) && (!(p >= 13.0F) || !(p <= 77.0F))) {
                        if ((!(y >= 12.0F) || !(y <= 78.0F)) && (!(p >= 12.0F) || !(p <= 78.0F))) {
                           if ((!(y >= 8.0F) || !(y <= 82.0F)) && (!(p >= 11.0F) || !(p <= 79.0F))) {
                              if ((!(y >= 5.0F) || !(y <= 85.0F)) && (!(p >= 8.0F) || !(p <= 82.0F))) {
                                 if (!(y <= 90.0F) && !(p <= 90.0F)) {
                                    v = 1.66;
                                 } else {
                                    v = 1.67;
                                 }
                              } else {
                                 v = 1.67;
                              }
                           } else {
                              v = 1.75;
                           }
                        } else {
                           v = 1.75;
                        }
                     } else {
                        v = 1.75;
                     }
                  } else {
                     v = 1.75;
                  }
               } else {
                  v = 1.75;
               }
            } else {
               v = 1.874;
            }
         } else {
            v = 1.954;
         }
      } else {
         v = 1.963;
      }

      return pitch > 15.0F ? v - 0.068 : v;
   }

   private double bravoY(float pitch) {
      float p = Math.abs(pitch);
      if (p >= 37.0F && p <= 38.0F) {
         return 2.03;
      } else if (p >= 25.0F && p <= 30.0F) {
         return 2.0;
      } else if (p >= 35.0F && p <= 45.0F) {
         return 1.99;
      } else if (p >= 40.0F && p <= 50.0F) {
         return 1.97;
      } else if (p >= 50.0F && p <= 60.0F) {
         return 1.96;
      } else if (p >= 51.0F && p <= 61.0F) {
         return 1.85;
      } else {
         return p >= 52.0F && p <= 65.0F ? 1.8 : 1.59;
      }
   }

   private static double arrayLookup(double[] table, float folded) {
      int i = (int)(folded / 5.0F);
      if (i < 0) {
         i = 0;
      }

      if (i >= table.length) {
         i = table.length - 1;
      }

      return table[i];
   }

   private static float fold(float angle) {
      float v = Math.abs(angle);
      if (v > 90.0F) {
         v = 180.0F - v;
      }

      if (v > 45.0F) {
         v = 90.0F - v;
      }

      return v;
   }

   private static float wrapDegrees(float v) {
      float x = v % 360.0F;
      if (x >= 180.0F) {
         x -= 360.0F;
      }

      if (x < -180.0F) {
         x += 360.0F;
      }

      return x;
   }
}
