package win.furryhook.context;

import net.minecraft.client.multiplayer.ClientLevel;

public interface WorldContext extends MinecraftContext {
   default ClientLevel world() {
      return this.level();
   }

   default boolean hasWorld() {
      return this.world() != null;
   }
}
