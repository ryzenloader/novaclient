package win.furryhook.utils.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MultipointUtil {
   private static final double DEFAULT_STEP = 0.1;

   private MultipointUtil() {
   }

   public static Vec3 getNearestPoint(Entity entity, double expand) {
      return getNearestPoint(entity, expand, 0.1);
   }

   public static Vec3 getNearestPoint(Entity entity, double expand, double step) {
      LocalPlayer player = Minecraft.getInstance().player;
      AABB box = entity.getBoundingBox();
      if (player == null) {
         return box.getCenter();
      }

      Vec3 eye = player.getEyePosition();
      double x = nearestNode(box.minX + expand, box.maxX - expand, step, eye.x);
      double y = nearestNode(box.minY + expand, box.maxY - expand, step, eye.y);
      double z = nearestNode(box.minZ + expand, box.maxZ - expand, step, eye.z);
      return !Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z) ? new Vec3(x, y, z) : box.getCenter();
   }

   private static double nearestNode(double min, double max, double step, double target) {
      if (!(max < min) && !(step <= 0.0)) {
         long lastIndex = (long)Math.floor((max - min) / step + 1.0E-9);
         long index = Math.round((target - min) / step - 1.0E-9);
         index = Math.max(0L, Math.min(lastIndex, index));
         return min + index * step;
      } else {
         return Double.NaN;
      }
   }
}
