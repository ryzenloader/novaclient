package win.furryhook.utils.render;

import java.util.UUID;
import net.minecraft.core.ClientAsset.Texture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import win.furryhook.context.MinecraftContext;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.visual.CapeFeature;

public final class ClientCape {
   private ClientCape() {
   }

   public static boolean shouldForceCape(UUID playerUuid) {
      if (MinecraftContext.mc.player == null) {
         return false;
      }

      CapeFeature feature = getFeature();
      return feature != null && feature.isEnabled() ? MinecraftContext.mc.player.getUUID().equals(playerUuid) : false;
   }

   public static PlayerSkin apply(PlayerSkin skin) {
      CapeFeature feature = getFeature();
      String fileName = feature != null ? feature.currentStyle().getFileName() : "pmcboykisser.png";
      final Identifier dynamicTextureId = Identifier.fromNamespaceAndPath("furryhook", "textures/cape/" + fileName);
      Texture dynamicTexture = new Texture() {
         public Identifier id() {
            return dynamicTextureId;
         }

         public Identifier texturePath() {
            return dynamicTextureId;
         }
      };
      return new PlayerSkin(skin.body(), dynamicTexture, skin.elytra(), skin.model(), skin.secure());
   }

   private static CapeFeature getFeature() {
      return FeatureManager.INSTANCE.getFeature(CapeFeature.class);
   }
}
