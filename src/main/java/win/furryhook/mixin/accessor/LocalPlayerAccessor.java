package win.furryhook.mixin.accessor;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
   @Accessor("yRotLast")
   float getYRotLast();

   @Accessor("xRotLast")
   float getXRotLast();
}
