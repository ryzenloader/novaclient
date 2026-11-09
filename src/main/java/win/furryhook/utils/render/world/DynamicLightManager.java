package win.furryhook.utils.render.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.phys.Vec3;
import win.furryhook.feature.impl.player.FullBrightFeature;
import win.furryhook.utils.render.Render3DUtil;

public final class DynamicLightManager {
   public static final DynamicLightManager INSTANCE = new DynamicLightManager();
   private static final int MAX_CANDIDATES = 64;
   private static final int MAX_SHADER_LIGHTS = 16;
   private static final double MAX_DISTANCE_SQR = 4096.0;
   private volatile Map<Long, Integer> virtualLuminance = Map.of();
   private List<DynamicLightManager.Candidate> candidates = List.of();
   private ClientLevel engineLevel;

   private DynamicLightManager() {
   }

   public static int virtualLuminance(long blockPos) {
      return INSTANCE.virtualLuminance.getOrDefault(blockPos, 0);
   }

   public void tick(Minecraft minecraft, FullBrightFeature feature) {
      LocalPlayer player = minecraft.player;
      ClientLevel level = minecraft.level;
      if (player != null && level != null) {
         List<DynamicLightManager.Candidate> collected = new ArrayList<>();
         DynamicLightManager.LightSpec localLight = strongest(lightFor(player.getMainHandItem()), lightFor(player.getOffhandItem()));
         if (localLight != null) {
            collected.add(DynamicLightManager.Candidate.entity(player, player.getEyeHeight() * 0.72, localLight));
         }

         for (Entity entity : level.entitiesForRendering()) {
            if (entity != player && !entity.isRemoved() && !(entity.distanceToSqr(player) > 4096.0)) {
               DynamicLightManager.LightSpec source = null;
               double yOffset = entity.getBbHeight() * 0.5;
               if (feature.lightItems.getValue() && entity instanceof ItemEntity itemEntity) {
                  source = lightFor(itemEntity.getItem());
                  yOffset = 0.2;
               }

               if (feature.lightOthers.getValue() && entity instanceof LivingEntity living) {
                  source = strongest(source, strongest(lightFor(living.getMainHandItem()), lightFor(living.getOffhandItem())));
               }

               if (feature.lightOthers.getValue() && entity.displayFireAnimation()) {
                  source = strongest(source, DynamicLightManager.LightSpec.FIRE);
               }

               if (source != null) {
                  collected.add(DynamicLightManager.Candidate.entity(entity, yOffset, source));
               }
            }
         }

         collected.sort(Comparator.comparingDouble(candidate -> candidate.position(1.0F).distanceToSqr(player.position())));
         if (collected.size() > 64) {
            collected = new ArrayList<>(collected.subList(0, 64));
         }

         this.candidates = List.copyOf(collected);
         this.updateEngineLights(level, feature);
      } else {
         this.clear();
      }
   }

   public List<DynamicLightManager.RenderLight> shaderLights(FullBrightFeature feature, float partialTick, Vec3 cameraPosition) {
      if (feature.usesShaderLights() && !this.candidates.isEmpty()) {
         float radiusMultiplier = feature.lightRadius.getValue().floatValue();
         return this.candidates
            .stream()
            .filter(candidate -> !candidate.entity.isRemoved())
            .map(candidate -> candidate.toRenderLight(partialTick, radiusMultiplier))
            .sorted(Comparator.comparingDouble(light -> light.position.distanceToSqr(cameraPosition)))
            .limit(16L)
            .toList();
      } else {
         return List.of();
      }
   }

   public void clear() {
      this.candidates = List.of();
      this.clearEngineLights();
   }

   public void revalidateAfterVanillaUpdates(ClientLevel level) {
      Map<Long, Integer> sources = this.virtualLuminance;
      if (level != null && level == this.engineLevel && !sources.isEmpty()) {
         LayerLightEventListener blockLight = level.getChunkSource().getLightEngine().getLayerListener(LightLayer.BLOCK);

         for (long packedPos : sources.keySet()) {
            blockLight.checkBlock(BlockPos.of(packedPos));
         }

         blockLight.runLightUpdates();
      }
   }

   private void updateEngineLights(ClientLevel level, FullBrightFeature feature) {
      if (this.engineLevel != null && this.engineLevel != level) {
         this.clearEngineLights();
      }

      this.engineLevel = level;
      Map<Long, Integer> next = new HashMap<>();
      if (feature.usesEngineLights()) {
         float radiusMultiplier = feature.lightRadius.getValue().floatValue();
         float intensity = feature.lightIntensity.getValue().floatValue();

         for (DynamicLightManager.Candidate candidate : this.candidates) {
            Vec3 position = candidate.position(1.0F);
            BlockPos blockPos = BlockPos.containing(position);
            if (level.hasChunkAt(blockPos)) {
               int luminance = Math.clamp(Math.round(candidate.spec.luminance * radiusMultiplier * Math.min(1.5F, intensity)), 1, 15);
               next.merge(blockPos.asLong(), luminance, Math::max);
            }
         }
      }

      Map<Long, Integer> previous = this.virtualLuminance;
      this.virtualLuminance = Map.copyOf(next);
      if (!previous.equals(next)) {
         Set<Long> changed = new HashSet<>(previous.keySet());
         changed.addAll(next.keySet());
         LayerLightEventListener lightEngine = level.getChunkSource().getLightEngine().getLayerListener(LightLayer.BLOCK);

         for (long packedPos : changed) {
            if (previous.getOrDefault(packedPos, 0) != next.getOrDefault(packedPos, 0)) {
               lightEngine.checkBlock(BlockPos.of(packedPos));
            }
         }
      }
   }

   private void clearEngineLights() {
      Map<Long, Integer> previous = this.virtualLuminance;
      this.virtualLuminance = Map.of();
      ClientLevel level = this.engineLevel;
      this.engineLevel = null;
      if (level != null && !previous.isEmpty()) {
         LayerLightEventListener lightEngine = level.getChunkSource().getLightEngine().getLayerListener(LightLayer.BLOCK);

         for (long packedPos : previous.keySet()) {
            lightEngine.checkBlock(BlockPos.of(packedPos));
         }
      }
   }

   private static DynamicLightManager.LightSpec lightFor(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return null;
      }

      if (stack.is(Items.SOUL_TORCH) || stack.is(Items.SOUL_LANTERN) || stack.is(Items.SOUL_CAMPFIRE)) {
         return new DynamicLightManager.LightSpec(7002623, 12, 0.08F);
      }

      if (stack.is(Items.REDSTONE_TORCH)) {
         return new DynamicLightManager.LightSpec(16727332, 9, 0.04F);
      }

      if (stack.is(Items.SEA_LANTERN) || stack.is(Items.END_ROD)) {
         return new DynamicLightManager.LightSpec(14219519, 14, 0.02F);
      }

      if (stack.is(Items.OCHRE_FROGLIGHT)) {
         return new DynamicLightManager.LightSpec(16765802, 15, 0.01F);
      }

      if (stack.is(Items.VERDANT_FROGLIGHT)) {
         return new DynamicLightManager.LightSpec(9306049, 15, 0.01F);
      }

      if (stack.is(Items.PEARLESCENT_FROGLIGHT)) {
         return new DynamicLightManager.LightSpec(14919935, 15, 0.01F);
      }

      if (stack.is(Items.LAVA_BUCKET)
         || stack.is(Items.FIRE_CHARGE)
         || stack.is(Items.BLAZE_ROD)
         || stack.is(Items.BLAZE_POWDER)
         || stack.is(Items.MAGMA_CREAM)) {
         return new DynamicLightManager.LightSpec(16738852, 15, 0.18F);
      }

      if (stack.is(Items.GLOW_BERRIES) || stack.is(Items.GLOW_INK_SAC)) {
         return new DynamicLightManager.LightSpec(9306032, 10, 0.04F);
      }

      if (stack.is(Items.NETHER_STAR)) {
         return new DynamicLightManager.LightSpec(13101311, 15, 0.08F);
      }

      if (stack.getItem() instanceof BlockItem blockItem) {
         int luminance = blockItem.getBlock().defaultBlockState().getLightEmission();
         if (luminance > 0) {
            return new DynamicLightManager.LightSpec(16765072, luminance, luminance >= 14 ? 0.07F : 0.03F);
         }
      }

      return null;
   }

   private static DynamicLightManager.LightSpec strongest(DynamicLightManager.LightSpec first, DynamicLightManager.LightSpec second) {
      if (first == null) {
         return second;
      } else if (second == null) {
         return first;
      } else {
         return first.luminance >= second.luminance ? first : second;
      }
   }

   private record Candidate(Entity entity, double yOffset, DynamicLightManager.LightSpec spec) {
      private static DynamicLightManager.Candidate entity(Entity entity, double yOffset, DynamicLightManager.LightSpec spec) {
         return new DynamicLightManager.Candidate(entity, yOffset, spec);
      }

      private Vec3 position(float partialTick) {
         return Render3DUtil.interpolatedPosition(this.entity, partialTick).add(0.0, this.yOffset, 0.0);
      }

      private DynamicLightManager.RenderLight toRenderLight(float partialTick, float radiusMultiplier) {
         return new DynamicLightManager.RenderLight(
            this.position(partialTick), this.spec.rgb, (3.0F + this.spec.luminance * 0.62F) * radiusMultiplier, this.spec.flicker, this.entity.getId() * 0.731F
         );
      }
   }

   private record LightSpec(int rgb, int luminance, float flicker) {
      private static final DynamicLightManager.LightSpec FIRE = new DynamicLightManager.LightSpec(16738852, 15, 0.22F);
   }

   public record RenderLight(Vec3 position, int rgb, float radius, float flicker, float phase) {
   }
}
