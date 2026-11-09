package win.furryhook.mixin.accessor;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
   @Accessor("rightClickDelay")
   void setRightClickDelay(int var1);

   @Accessor("user")
   @Mutable
   void setUser(User var1);

   @Accessor("profileFuture")
   @Mutable
   void setProfileFuture(CompletableFuture<ProfileResult> var1);

   @Accessor("userApiService")
   @Mutable
   void setUserApiService(UserApiService var1);

   @Accessor("profileKeyPairManager")
   @Mutable
   void setProfileKeyPairManager(ProfileKeyPairManager var1);

   @Accessor("services")
   @Mutable
   void setServices(Services var1);
}
