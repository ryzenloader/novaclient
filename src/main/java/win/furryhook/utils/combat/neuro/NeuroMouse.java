package win.furryhook.utils.combat.neuro;

public final class NeuroMouse {
   private static int steps;
   private static double moved;
   private static double peak;

   private NeuroMouse() {
   }

   public static void onMouseTurn(double yawDelta, double pitchDelta) {
      double magnitude = Math.hypot(yawDelta, pitchDelta);
      if (!(magnitude <= 0.0)) {
         steps++;
         moved += magnitude;
         if (magnitude > peak) {
            peak = magnitude;
         }
      }
   }

   public static int steps() {
      return steps;
   }

   public static float peak() {
      return (float)peak;
   }

   public static float evenness() {
      return steps > 0 && !(peak <= 0.0) ? (float)Math.min(1.0, moved / steps / peak) : 0.0F;
   }

   public static void consume() {
      steps = 0;
      moved = 0.0;
      peak = 0.0;
   }
}
