package win.furryhook.utils.render.target;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class SoulSpirit {
   static final int HISTORY = 96;
   private static final float REST_FREQUENCY = 1.6F;
   private static final float REST_DAMPING = 1.0F;
   private static final float LAUNCH_FREQUENCY = 3.2F;
   private static final float LAUNCH_DAMPING = 0.45F;
   final int index;
   double x;
   double y;
   double z;
   double vx;
   double vy;
   double vz;
   final float phase;
   final float orbitFrequency;
   final float bobFrequency;
   final float orbitRadius;
   final float orbitHeight;
   final float pulsePhase;
   final float jitterSeed;
   float launch;
   float delay;
   float flash;
   private final float[] history = new float[288];
   private int historyCount;
   private int historyHead;
   private static final float[] TEMP_A = new float[3];
   private static final float[] TEMP_B = new float[3];
   private static final float[] TEMP_C = new float[3];
   private static final float[] TEMP_D = new float[3];

   SoulSpirit(int index, int count) {
      this.index = index;
      float golden = 2.399963F;
      this.phase = index * golden;
      this.orbitFrequency = 0.55F + hash(index, 0) * 0.35F;
      this.bobFrequency = 0.8F + hash(index, 1) * 0.7F;
      this.orbitRadius = 0.75F + hash(index, 2) * 0.45F;
      this.orbitHeight = 0.28F + hash(index, 3) * 0.4F;
      this.pulsePhase = hash(index, 4) * (float) (Math.PI * 2);
      this.jitterSeed = hash(index, 5) * 12.9F;
      this.delay = index * 0.06F;
      if (count <= 1) {
         this.delay = 0.0F;
      }
   }

   void placeAt(Vec3 position) {
      this.x = position.x;
      this.y = position.y;
      this.z = position.z;
      this.vx = 0.0;
      this.vy = 0.0;
      this.vz = 0.0;
      this.historyCount = 0;
      this.historyHead = 0;
   }

   void launchTo(Vec3 anchor, Vec3 lateral) {
      this.launch = 0.42F;
      this.delay = this.index * 0.06F;
      double dx = anchor.x - this.x;
      double dy = anchor.y - this.y;
      double dz = anchor.z - this.z;
      double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (!(length < 1.0E-4)) {
         float sign = (this.index & 1) == 0 ? 1.0F : -1.0F;
         double push = Math.min(6.0, length) * 0.55 * sign;
         this.vx = this.vx + lateral.x * push;
         this.vy = this.vy + (lateral.y * push + 0.35);
         this.vz = this.vz + lateral.z * push;
      }
   }

   void impact() {
      this.flash = 1.0F;
   }

   void step(float dt, Vec3 anchor, float clock, float speedScale) {
      if (this.delay > 0.0F) {
         this.delay -= dt;
      }

      boolean launching = this.launch > 0.0F && this.delay <= 0.0F;
      float frequency = (launching ? 3.2F : 1.6F) * speedScale;
      float damping = launching ? 0.45F : 1.0F;
      if (this.launch > 0.0F && this.delay <= 0.0F) {
         this.launch = Math.max(0.0F, this.launch - dt);
      }

      double targetX = anchor.x;
      double targetY = anchor.y;
      double targetZ = anchor.z;
      if (this.delay > 0.0F) {
         targetX = this.x;
         targetY = this.y;
         targetZ = this.z;
      }

      float omega = (float) (Math.PI * 2) * frequency;
      double ax = (targetX - this.x) * omega * omega - this.vx * 2.0 * damping * omega;
      double ay = (targetY - this.y) * omega * omega - this.vy * 2.0 * damping * omega;
      double az = (targetZ - this.z) * omega * omega - this.vz * 2.0 * damping * omega;
      this.vx += ax * dt;
      this.vy += ay * dt;
      this.vz += az * dt;
      float wander = 0.9F * dt;
      this.vx = this.vx + Math.sin(clock * 0.83F + this.jitterSeed) * wander;
      this.vy = this.vy + Math.sin(clock * 1.11F + this.jitterSeed * 1.7F) * wander * 0.6F;
      this.vz = this.vz + Math.cos(clock * 0.97F + this.jitterSeed * 2.3F) * wander;
      this.x = this.x + this.vx * dt;
      this.y = this.y + this.vy * dt;
      this.z = this.z + this.vz * dt;
      if (this.flash > 0.0F) {
         this.flash = Math.max(0.0F, this.flash - dt * 6.5F);
      }

      this.pushHistory();
   }

   float speed() {
      return (float)Math.sqrt(this.vx * this.vx + this.vy * this.vy + this.vz * this.vz);
   }

   int historyCount() {
      return this.historyCount;
   }

   void sample(float t, float[] out) {
      int count = this.historyCount;
      if (count == 0) {
         out[0] = (float)this.x;
         out[1] = (float)this.y;
         out[2] = (float)this.z;
      } else if (count < 4) {
         this.read(Math.round(Mth.clamp(t, 0.0F, count - 1)), out);
      } else {
         float clamped = Mth.clamp(t, 0.0F, count - 1);
         int i1 = (int)clamped;
         float f = clamped - i1;
         int i0 = Math.max(0, i1 - 1);
         int i2 = Math.min(count - 1, i1 + 1);
         int i3 = Math.min(count - 1, i1 + 2);
         float[] p0 = TEMP_A;
         float[] p1 = TEMP_B;
         float[] p2 = TEMP_C;
         float[] p3 = TEMP_D;
         this.read(i0, p0);
         this.read(i1, p1);
         this.read(i2, p2);
         this.read(i3, p3);

         for (int axis = 0; axis < 3; axis++) {
            out[axis] = catmullRom(p0[axis], p1[axis], p2[axis], p3[axis], f);
         }
      }
   }

   private void read(int age, float[] out) {
      int slot = this.historyHead - 1 - age;

      while (slot < 0) {
         slot += 96;
      }

      slot %= 96;
      int base = slot * 3;
      out[0] = this.history[base];
      out[1] = this.history[base + 1];
      out[2] = this.history[base + 2];
   }

   private void pushHistory() {
      int base = this.historyHead * 3;
      this.history[base] = (float)this.x;
      this.history[base + 1] = (float)this.y;
      this.history[base + 2] = (float)this.z;
      this.historyHead = (this.historyHead + 1) % 96;
      if (this.historyCount < 96) {
         this.historyCount++;
      }
   }

   private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
      float t2 = t * t;
      float t3 = t2 * t;
      return 0.5F * (2.0F * p1 + (-p0 + p2) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2 + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
   }

   private static float hash(int seed, int salt) {
      int hash = seed * -1640531527 + salt * -2048144789;
      hash ^= hash >>> 15;
      hash *= 739982445;
      hash ^= hash >>> 12;
      return (hash >>> 8 & 65535) / 65535.0F;
   }
}
