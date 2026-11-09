package win.furryhook.mixin.accessor;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
   @Accessor("destroyDelay")
   void setDestroyDelay(int var1);

   @Invoker("ensureHasSentCarriedItem")
   void invokerEnsureHasSentCarriedItem();

   @Accessor("destroyDelay")
   int getDestroyDelay();

   @Accessor("carriedIndex")
   void setCarriedIndex(int var1);
}
