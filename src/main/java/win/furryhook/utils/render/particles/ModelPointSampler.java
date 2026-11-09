package win.furryhook.utils.render.particles;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState;
import net.minecraft.client.renderer.entity.state.EntityRenderState.ShadowPiece;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives.Group;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import win.furryhook.utils.render.EntityEspDispatcherBridge;

public final class ModelPointSampler implements SubmitNodeCollector {
   private final List<Vector3f[]> quads = new ArrayList<>();
   private final ModelPointSampler.CapturingConsumer consumer = new ModelPointSampler.CapturingConsumer();

   private ModelPointSampler() {
   }

   public static List<Vector3f> sample(EntityRenderState state, CameraRenderState cameraState, int budget, Random random) {
      Minecraft mc = Minecraft.getInstance();
      if (cameraState != null && mc.player != null && mc.getEntityRenderDispatcher() instanceof EntityEspDispatcherBridge bridge) {
         mc.getEntityRenderDispatcher().prepare(mc.gameRenderer.mainCamera(), (Entity)(mc.crosshairPickEntity != null ? mc.crosshairPickEntity : mc.player));
         ModelPointSampler sampler = new ModelPointSampler();

         try {
            bridge.submitForGlow(state, cameraState, 0.0, 0.0, 0.0, new PoseStack(), sampler);
         } catch (Exception ignored) {
            return List.of();
         }

         return sampler.scatterPoints(budget, random);
      } else {
         return List.of();
      }
   }

   private List<Vector3f> scatterPoints(int budget, Random random) {
      if (this.quads.isEmpty()) {
         return List.of();
      }

      float totalArea = 0.0F;
      float[] areas = new float[this.quads.size()];

      for (int i = 0; i < this.quads.size(); i++) {
         areas[i] = quadArea(this.quads.get(i));
         totalArea += areas[i];
      }

      if (totalArea <= 1.0E-4F) {
         return List.of();
      }

      List<Vector3f> points = new ArrayList<>(budget);

      for (int i = 0; i < this.quads.size(); i++) {
         Vector3f[] quad = this.quads.get(i);
         float share = areas[i] / totalArea * budget;
         int count = (int)share + (random.nextFloat() < share - (int)share ? 1 : 0);

         for (int p = 0; p < count; p++) {
            float u = random.nextFloat();
            float v = random.nextFloat();
            points.add(bilinear(quad, u, v));
         }
      }

      return points;
   }

   private static float quadArea(Vector3f[] quad) {
      Vector3f edge1 = new Vector3f(quad[1]).sub(quad[0]);
      Vector3f edge2 = new Vector3f(quad[3]).sub(quad[0]);
      return new Vector3f(edge1).cross(edge2).length();
   }

   private static Vector3f bilinear(Vector3f[] quad, float u, float v) {
      Vector3f top = new Vector3f(quad[0]).lerp(quad[1], u);
      Vector3f bottom = new Vector3f(quad[3]).lerp(quad[2], u);
      return top.lerp(bottom, v);
   }

   public OrderedSubmitNodeCollector order(int order) {
      return this;
   }

   public <S> void submitModel(
      Model<? super S> model,
      S state,
      PoseStack poseStack,
      RenderType renderType,
      int light,
      int overlay,
      int color,
      TextureAtlasSprite sprite,
      int outlineColor,
      CrumblingOverlay crumblingOverlay
   ) {
      model.setupAnim(state);
      model.renderToBuffer(poseStack, this.consumer, light, overlay, -1);
      this.consumer.finishQuad();
      model.resetPose();
   }

   public void submitShadow(PoseStack poseStack, float alpha, List<ShadowPiece> pieces) {
   }

   public void submitNameTag(PoseStack poseStack, Vec3 offset, int light, Component text, boolean seeThrough, int color, CameraRenderState cameraState) {
   }

   public void submitText(
      PoseStack poseStack,
      float x,
      float y,
      FormattedCharSequence text,
      boolean dropShadow,
      DisplayMode displayMode,
      int light,
      int color,
      int backgroundColor,
      int outlineColor
   ) {
   }

   public void submitFlame(PoseStack poseStack, EntityRenderState state, Quaternionf rotation) {
   }

   public void submitLeash(PoseStack poseStack, LeashState leashState) {
   }

   public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState state, int light) {
   }

   public void submitBlockModel(
      PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tints, int light, int overlay, int outlineColor
   ) {
   }

   public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress) {
   }

   public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float lineWidth, boolean occluded) {
   }

   public void submitItem(
      PoseStack poseStack, ItemDisplayContext context, int light, int overlay, int outlineColor, int[] tints, List<BakedQuad> quads, FoilType foilType
   ) {
   }

   public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer) {
   }

   public void submitQuadParticleGroup(QuadParticleRenderState state) {
   }

   public void submitGizmoPrimitives(Group primitives, CameraRenderState cameraState, boolean depthTest) {
   }

   public <T extends SubmitNode> void submitCustom(SubmitRenderPhase<T> phase, T node) {
   }

   private final class CapturingConsumer implements VertexConsumer {
      private final Vector3f[] pending = new Vector3f[4];
      private int pendingCount;

      public VertexConsumer addVertex(float x, float y, float z) {
         this.pending[this.pendingCount++] = new Vector3f(x, y, z);
         if (this.pendingCount == 4) {
            ModelPointSampler.this.quads.add((Vector3f[])this.pending.clone());
            this.pendingCount = 0;
         }

         return this;
      }

      private void finishQuad() {
         this.pendingCount = 0;
      }

      public VertexConsumer setColor(int red, int green, int blue, int alpha) {
         return this;
      }

      public VertexConsumer setColor(int color) {
         return this;
      }

      public VertexConsumer setUv(float u, float v) {
         return this;
      }

      public VertexConsumer setUv1(int u, int v) {
         return this;
      }

      public VertexConsumer setUv2(int u, int v) {
         return this;
      }

      public VertexConsumer setNormal(float x, float y, float z) {
         return this;
      }

      public VertexConsumer setLineWidth(float width) {
         return this;
      }
   }
}
