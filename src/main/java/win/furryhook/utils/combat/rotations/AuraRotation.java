package win.furryhook.utils.combat.rotations;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public interface AuraRotation {
   void tick(LocalPlayer var1, LivingEntity var2, Vec3 var3, boolean var4);

   default void onAttack() {
   }

   default void reset() {
   }
}
