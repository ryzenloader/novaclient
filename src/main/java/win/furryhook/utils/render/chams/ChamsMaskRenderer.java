package win.furryhook.utils.render.chams;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import win.furryhook.feature.impl.visual.ChamsFeature;
import win.furryhook.utils.render.EntityEspDispatcherBridge;
import win.furryhook.utils.render.EntityEspStateCache;
import win.furryhook.utils.render.HurtUtil;

public final class ChamsMaskRenderer {
   private static final int HURT_BUCKETS = 3;
   private TextureTarget maskBuffer;
   private FeatureRenderDispatcher isolatedDispatcher;

   public void renderGroups(LevelRenderState levelRenderState, ChamsFeature feature, Consumer<ChamsMaskRenderer.MaskFrame> composite) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level != null && minecraft.player != null && minecraft.gameRenderer != null && levelRenderState != null) {
         List<EntityRenderState> states = EntityEspStateCache.currentStates();
         List<Entity> targets = ChamsTargetMatcher.collectTargets(minecraft, feature);
         if (!states.isEmpty() && !targets.isEmpty()) {
            RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
            if (mainTarget != null) {
               this.ensureResources(minecraft, mainTarget.width, mainTarget.height);
               if (this.maskBuffer != null
                  && this.isolatedDispatcher != null
                  && minecraft.getEntityRenderDispatcher() instanceof EntityEspDispatcherBridge bridge) {
                  ArrayList var15 = new ArrayList(4);

                  for (int poseStack = 0; poseStack <= 3; poseStack++) {
                     var15.add(null);
                  }

                  for (EntityRenderState state : states) {
                     Entity target = ChamsTargetMatcher.matchingTarget(state, targets);
                     if (target != null) {
                        int bucket = Math.round(HurtUtil.easedFactor(target) * 3.0F);
                        List<EntityRenderState> group = (List<EntityRenderState>)var15.get(bucket);
                        if (group == null) {
                           group = new ArrayList<>(4);
                           var15.set(bucket, group);
                        }

                        group.add(state);
                     }
                  }

                  minecraft.getEntityRenderDispatcher()
                     .prepare(
                        minecraft.gameRenderer.mainCamera(), (Entity)(minecraft.crosshairPickEntity != null ? minecraft.crosshairPickEntity : minecraft.player)
                     );
                  PoseStack poseStack = new PoseStack();

                  for (int bucket = 0; bucket <= 3; bucket++) {
                     List<EntityRenderState> group = (List<EntityRenderState>)var15.get(bucket);
                     if (group != null && this.renderMask(levelRenderState, bridge, poseStack, group)) {
                        composite.accept(new ChamsMaskRenderer.MaskFrame(this.maskBuffer, mainTarget, bucket / 3.0F));
                     }
                  }
               }
            }
         }
      }
   }

   private boolean renderMask(LevelRenderState levelRenderState, EntityEspDispatcherBridge bridge, PoseStack poseStack, List<EntityRenderState> group) {
      RenderSystem.getDevice()
         .createCommandEncoder()
         .clearColorAndDepthTextures(this.maskBuffer.getColorTexture(), new Vector4f(0.0F, 0.0F, 0.0F, 0.0F), this.maskBuffer.getDepthTexture(), 0.0);
      SubmitNodeStorage storage = new SubmitNodeStorage();
      double cameraX = levelRenderState.cameraRenderState.pos.x();
      double cameraY = levelRenderState.cameraRenderState.pos.y();
      double cameraZ = levelRenderState.cameraRenderState.pos.z();
      GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
      GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
      Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();

      try {
         RenderSystem.outputColorTextureOverride = this.maskBuffer.getColorTextureView();
         RenderSystem.outputDepthTextureOverride = this.maskBuffer.getDepthTextureView();
         modelViewStack.pushMatrix();
         modelViewStack.mul(levelRenderState.cameraRenderState.viewRotationMatrix);

         for (EntityRenderState state : group) {
            bridge.submitForGlow(state, levelRenderState.cameraRenderState, state.x - cameraX, state.y - cameraY, state.z - cameraZ, poseStack, storage);
         }

         this.isolatedDispatcher.renderAllFeatures(storage);
      } finally {
         modelViewStack.popMatrix();
         RenderSystem.outputColorTextureOverride = previousColor;
         RenderSystem.outputDepthTextureOverride = previousDepth;
      }

      return true;
   }

   private void ensureResources(Minecraft minecraft, int width, int height) {
      if (this.isolatedDispatcher == null) {
         this.isolatedDispatcher = new FeatureRenderDispatcher(
            minecraft.gameRenderer.renderBuffers(),
            minecraft.getModelManager(),
            minecraft.getAtlasManager(),
            minecraft.font,
            minecraft.gameRenderer.gameRenderState()
         );
      }

      if (this.maskBuffer == null || this.maskBuffer.width != width || this.maskBuffer.height != height) {
         if (this.maskBuffer != null) {
            this.maskBuffer.destroyBuffers();
         }

         this.maskBuffer = new TextureTarget("furryhook-chams-mask", width, height, true, GpuFormat.RGBA8_UNORM);
      }
   }

   public void release() {
      if (this.maskBuffer != null) {
         this.maskBuffer.destroyBuffers();
         this.maskBuffer = null;
      }
   }

   public record MaskFrame(RenderTarget mask, RenderTarget output, float hurtFactor) {
   }
}
