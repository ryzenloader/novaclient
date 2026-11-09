package win.furryhook.utils.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import win.furryhook.feature.impl.visual.AtmosphereFeature;
import win.furryhook.feature.setting.ColorMode;
import win.furryhook.utils.ColorUtil;
import win.furryhook.utils.render.Render3DUtil;
import win.furryhook.utils.render.post.PostFx;
import win.furryhook.utils.render.post.PostPipelines;
import win.furryhook.utils.render.post.PostTarget;

public final class AtmosphereRenderer {
   private static final float DENSITY_SCALE = 0.005F;
   private static final float HAZE_DENSITY_SCALE = 0.02F;
   private static final double ORIGIN_WRAP = 65536.0;
   private static final int UNIFORM_SIZE = new Std140SizeCalculator()
      .putMat4f()
      .putVec4()
      .putVec4()
      .putVec4()
      .putVec4()
      .putVec4()
      .putVec4()
      .putVec4()
      .putVec4()
      .putVec4()
      .putVec4()
      .putVec4()
      .putVec4()
      .putVec4()
      .putVec4()
      .putVec4()
      .get();
   private final GpuBuffer uniforms = PostFx.createUniforms("Blade Atmosphere UBO", UNIFORM_SIZE);
   private final PostTarget sceneCopy = new PostTarget("furryhook-atmosphere-scene", PostPipelines.EFFECT_FORMAT, false);
   private final Vector4f boltTop = new Vector4f();
   private final Vector4f boltGround = new Vector4f();
   private static final float NEAR_PLANE_GUARD = 0.5F;

   public void render(AtmosphereFeature feature, CameraRenderState cameraState, SkyRenderState skyState) {
      Minecraft minecraft = Minecraft.getInstance();
      RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
      if (valid(mainTarget) && cameraState != null && cameraState.initialized) {
         StormState storm = StormState.INSTANCE;
         storm.update(feature, cameraState.pos);
         boolean ambientActive = feature.isEnabled() && feature.usesAtmosphere();
         if (ambientActive || storm.hasVisibleStrike()) {
            Matrix4f levelProjection = Render3DUtil.levelProjectionCopy();
            if (levelProjection != null) {
               Matrix4f viewProjection = levelProjection.mul(cameraState.viewRotationMatrix);
               Matrix4f inverseViewProjection = new Matrix4f(viewProjection).invert();
               boolean depthZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
               TextureTarget scene = this.sceneCopy.ensure(mainTarget.width, mainTarget.height);
               if (scene != null && scene.getColorTexture() != null) {
                  this.writeUniforms(
                     feature, skyState, storm, cameraState, viewProjection, inverseViewProjection, depthZeroToOne, mainTarget.width, mainTarget.height
                  );
                  RenderSystem.getDevice()
                     .createCommandEncoder()
                     .copyTextureToTexture(mainTarget.getColorTexture(), scene.getColorTexture(), 0, 0, 0, 0, 0, mainTarget.width, mainTarget.height);
                  PostFx.pass("Blade Atmosphere Fog", PostPipelines.ATMOSPHERE_FOG, mainTarget, pass -> {
                     pass.setUniform("AtmosphereUniforms", this.uniforms);
                     pass.bindTexture("DepthSampler", mainTarget.getDepthTextureView(), PostFx.nearestSampler());
                     pass.bindTexture("SceneSampler", scene.getColorTextureView(), PostFx.linearSampler());
                  });
               }
            }
         }
      }
   }

   private void writeUniforms(
      AtmosphereFeature feature,
      SkyRenderState skyState,
      StormState storm,
      CameraRenderState cameraState,
      Matrix4f viewProjection,
      Matrix4f inverseViewProjection,
      boolean depthZeroToOne,
      int width,
      int height
   ) {
      float starBrightness = skyState != null ? skyState.starBrightness : 0.0F;
      int fogColor = ColorMode.resolve(feature.fogColorMode, feature.fogColor);
      if (feature.syncWithSky.getValue() && skyState != null) {
         fogColor = ColorUtil.lerp(fogColor, skyState.skyColor, feature.skySync.getValue().floatValue());
         int sunsetColor = skyState.sunriseAndSunsetColor;
         float sunsetWeight = Math.min(ColorUtil.alpha(sunsetColor) / 255.0F * feature.sunsetBoost.getValue().floatValue(), 1.0F);
         if (sunsetWeight > 0.001F) {
            fogColor = ColorUtil.lerp(fogColor, sunsetColor, sunsetWeight);
         }
      }

      boolean masterOn = feature.isEnabled();
      boolean usesFog = masterOn && feature.usesFog();
      float density = usesFog ? feature.fogDensity.getValue().floatValue() * 0.005F : 0.0F;
      float ambientStrength = usesFog ? feature.ambientStrength.getValue().floatValue() * (1.0F - 0.55F * starBrightness) : 0.0F;
      if (usesFog && feature.stormFog.getValue()) {
         float intensity = storm.intensity();
         density *= 1.0F + intensity * 1.35F;
         ambientStrength *= 1.0F - 0.4F * intensity;
      }

      float inverseHeight = 1.0F / Math.max(feature.fogHeight.getValue().floatValue(), 1.0F);
      float aspect = height > 0 ? (float)width / height : 1.0F;
      float sunAngle = skyState != null ? skyState.sunAngle : 0.0F;
      float sunUp = (float)Math.cos(sunAngle);
      float sunEast = -((float)Math.sin(sunAngle));
      boolean daylight = sunUp >= 0.0F;
      float lightAmount = usesFog
         ? (Math.clamp(sunUp * 6.0F, 0.0F, 1.0F) + Math.clamp(-sunUp * 6.0F, 0.0F, 1.0F) * 0.3F) * (1.0F - 0.7F * storm.intensity())
         : 0.0F;
      float flash = storm.flashStrength() * feature.flashStrength.getValue().floatValue();
      int boltColor = feature.boltColor.getValue();
      float boltRed = ColorUtil.red(boltColor) / 255.0F;
      float boltGreen = ColorUtil.green(boltColor) / 255.0F;
      float boltBlue = ColorUtil.blue(boltColor) / 255.0F;
      float brightness = 0.0F;
      float seed = 0.0F;
      float strikeDistance = 0.0F;
      StormState.Strike strike = storm.activeStrike();
      if (strike != null
         && project(viewProjection, cameraState.pos, strike.topX, strike.topY, strike.topZ, this.boltTop)
         && project(viewProjection, cameraState.pos, strike.bottomX, strike.bottomY, strike.bottomZ, this.boltGround)) {
         brightness = StormState.boltEnvelope(strike);
         seed = strike.seed;
         double dx = strike.bottomX - cameraState.pos.x();
         double dy = strike.bottomY - cameraState.pos.y();
         double dz = strike.bottomZ - cameraState.pos.z();
         strikeDistance = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
      }

      float radius = Math.max(feature.boltWidth.getValue().floatValue(), 0.1F) / Math.max(height, 1);
      float glow = radius * 6.0F + 0.005F;
      boolean snow = feature.usesSnow();
      int rainColor = feature.rainColor.getValue();
      float rainAmount = masterOn && feature.usesRain()
         ? Math.min(storm.intensity() * feature.rainDensity.getValue().floatValue() * storm.skyVisibility() * 0.7F, 0.72F)
         : 0.0F;
      float rippleStrength = feature.ripples.getValue() && !snow && rainAmount > 0.0F
         ? feature.rippleStrength.getValue().floatValue() * Math.min(1.0F, rainAmount * 2.5F)
         : 0.0F;
      float nightFactor = Math.clamp((-sunUp + 0.12F) * 3.0F, 0.0F, 1.0F);
      boolean usesHaze = masterOn && feature.usesNightHaze();
      float hazeNight = feature.hazeNightOnly.getValue() ? nightFactor : 1.0F;
      float hazeDensity = usesHaze ? feature.hazeAmount.getValue().floatValue() * 0.02F * hazeNight : 0.0F;
      float hazeGlow = feature.hazeGlow.getValue().floatValue();
      int hazeColor = ColorMode.resolve(feature.hazeColorMode, feature.hazeColor);
      float hazeDeck = feature.hazeFollowGround.getValue()
         ? storm.groundHeight() + feature.hazeOffset.getValue().floatValue()
         : feature.hazeLevel.getValue().floatValue();
      float hazeCameraOffset = (float)(cameraState.pos.y() - hazeDeck);
      float hazeInverseThickness = 1.0F / Math.max(feature.hazeThickness.getValue().floatValue(), 0.5F);
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
            .putMat4f(inverseViewProjection)
            .putVec4(ColorUtil.red(fogColor) / 255.0F, ColorUtil.green(fogColor) / 255.0F, ColorUtil.blue(fogColor) / 255.0F, density)
            .putVec4(inverseHeight, ambientStrength, depthZeroToOne ? 1.0F : 0.0F, aspect)
            .putVec4(
               usesFog ? feature.turbulence.getValue().floatValue() : 0.0F, feature.sunScatter.getValue().floatValue(), storm.fogDrift(), storm.intensity()
            )
            .putVec4(daylight ? sunEast : -sunEast, daylight ? sunUp : -sunUp, 0.0F, lightAmount)
            .putVec4((float)wrap(cameraState.pos.x()), (float)cameraState.pos.y(), (float)wrap(cameraState.pos.z()), strikeDistance)
            .putVec4(storm.windX(), storm.windZ(), storm.windStrength(), 0.0F)
            .putVec4(boltRed + (1.0F - boltRed) * 0.3F, boltGreen + (1.0F - boltGreen) * 0.3F, boltBlue + (1.0F - boltBlue) * 0.3F, flash)
            .putVec4(this.boltTop.x, this.boltTop.y, this.boltGround.x, this.boltGround.y)
            .putVec4(boltRed, boltGreen, boltBlue, glow)
            .putVec4(brightness, radius, seed, feature.boltBranches.getValue().floatValue())
            .putVec4(
               ColorUtil.red(rainColor) / 255.0F,
               ColorUtil.green(rainColor) / 255.0F,
               ColorUtil.blue(rainColor) / 255.0F,
               feature.rainOpacity.getValue().floatValue()
            )
            .putVec4(rainAmount, storm.rainPhase(), snow ? 1.0F : 0.0F, snow ? 1.5F : 0.25F)
            .putVec4(rippleStrength, storm.ripplePhase(), Math.min(rainAmount * 1.4F, 0.85F), 0.0F)
            .putVec4(
               ColorUtil.red(hazeColor) / 255.0F * hazeGlow,
               ColorUtil.green(hazeColor) / 255.0F * hazeGlow,
               ColorUtil.blue(hazeColor) / 255.0F * hazeGlow,
               hazeDensity
            )
            .putVec4(hazeInverseThickness, hazeCameraOffset, feature.hazeBanks.getValue().floatValue(), feature.hazeMoonScatter.getValue().floatValue())
            .get();
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.uniforms.slice(), data);
      } catch (Throwable var51) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var50) {
               var51.addSuppressed(var50);
            }
         }

         throw var51;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private static double wrap(double value) {
      return value - Math.floor(value / 65536.0) * 65536.0;
   }

   private static boolean project(Matrix4f viewProjection, Vec3 cameraPos, double x, double y, double z, Vector4f out) {
      out.set((float)(x - cameraPos.x()), (float)(y - cameraPos.y()), (float)(z - cameraPos.z()), 1.0F);
      viewProjection.transform(out);
      if (out.w <= 0.5F) {
         return false;
      }

      out.x = out.x / out.w * 0.5F + 0.5F;
      out.y = out.y / out.w * 0.5F + 0.5F;
      return true;
   }

   private static boolean valid(RenderTarget target) {
      return target != null
         && target.width > 0
         && target.height > 0
         && target.getColorTexture() != null
         && target.getColorTextureView() != null
         && target.getDepthTexture() != null
         && target.getDepthTextureView() != null;
   }

   public void release() {
      this.sceneCopy.release();
      this.uniforms.close();
      StormState.INSTANCE.reset();
   }
}
