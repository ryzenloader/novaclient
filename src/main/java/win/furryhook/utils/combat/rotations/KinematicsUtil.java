package win.furryhook.utils.combat.rotations;

import net.minecraft.util.Mth;

public class KinematicsUtil {
   public static long calculateFittsLawTime(double distance, double width, double a, double b) {
      if (width <= 0.0) {
         width = 0.1;
      }

      double indexOfDifficulty = Math.log(distance / width + 1.0) / Math.log(2.0);
      if (indexOfDifficulty < 0.0) {
         indexOfDifficulty = 0.0;
      }

      return (long)(a + b * indexOfDifficulty);
   }

   public static double getMinimumJerk(double start, double end, double t) {
      t = Mth.clamp(t, 0.0, 1.0);
      double t3 = t * t * t;
      double t4 = t3 * t;
      double t5 = t4 * t;
      double factor = 10.0 * t3 - 15.0 * t4 + 6.0 * t5;
      return start + (end - start) * factor;
   }
}
