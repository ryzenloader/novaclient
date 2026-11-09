package win.furryhook.utils.combat.rotations;

public class MouseProfile {
   private final double fittsA;
   private final double fittsB;
   private final double jitterAmount;
   private final double overshootProbability;
   public static MouseProfile DEFAULT = new MouseProfile(100.0, 150.0, 1.0, 0.3);

   public MouseProfile(double fittsA, double fittsB, double jitterAmount, double overshootProbability) {
      this.fittsA = fittsA;
      this.fittsB = fittsB;
      this.jitterAmount = jitterAmount;
      this.overshootProbability = overshootProbability;
   }

   public double getFittsA() {
      return this.fittsA;
   }

   public double getFittsB() {
      return this.fittsB;
   }

   public double getJitterAmount() {
      return this.jitterAmount;
   }

   public double getOvershootProbability() {
      return this.overshootProbability;
   }
}
