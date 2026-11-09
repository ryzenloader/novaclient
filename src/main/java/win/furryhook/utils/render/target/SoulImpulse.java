package win.furryhook.utils.render.target;

import net.minecraft.world.entity.Entity;

public final class SoulImpulse {
   private static volatile int entityId = Integer.MIN_VALUE;
   private static volatile long stamp;
   private static long consumed;

   private SoulImpulse() {
   }

   public static void hit(Entity entity) {
      if (entity != null) {
         entityId = entity.getId();
         stamp = System.nanoTime();
      }
   }

   static boolean consume(int id) {
      long current = stamp;
      if (current != consumed && entityId == id) {
         consumed = current;
         return true;
      } else {
         return false;
      }
   }

   static void clear() {
      consumed = stamp;
   }
}
