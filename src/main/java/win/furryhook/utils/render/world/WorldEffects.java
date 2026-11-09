package win.furryhook.utils.render.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.combat.AuraFeature;
import win.furryhook.feature.impl.player.FullBrightFeature;
import win.furryhook.feature.impl.visual.AtmosphereFeature;
import win.furryhook.feature.impl.visual.ChamsFeature;
import win.furryhook.feature.impl.visual.JumpCirclesFeature;
import win.furryhook.feature.impl.visual.LineGlyphesFeature;
import win.furryhook.feature.impl.visual.ParticlesFeature;
import win.furryhook.feature.impl.visual.RtxFeature;
import win.furryhook.feature.impl.visual.TrailsFeature;
import win.furryhook.feature.impl.visual.TrajectoriesFeature;
import win.furryhook.feature.impl.visual.WorldTweaksFeature;
import win.furryhook.utils.render.chams.ChamsPipeline;
import win.furryhook.utils.render.target.TargetMarkers;

public final class WorldEffects {
   private static final List<WorldEffect> EFFECTS = new ArrayList<>();

   private WorldEffects() {
   }

   public static void bootstrap() {
      if (EFFECTS.isEmpty()) {
         register(
            WorldEffect.lazy(
               () -> gate(WorldTweaksFeature.getEnabled(), WorldTweaksFeature::usesSky),
               WorldTweaksRenderer::new,
               (feature, renderer, context) -> renderer.renderSky(feature, context.cameraRenderState()),
               WorldTweaksRenderer::release
            )
         );
         register(
            WorldEffect.lazy(
               () -> gate(RtxFeature.getEnabled(), RtxFeature::usesReflections),
               RtxReflectionRenderer::new,
               (feature, renderer, context) -> renderer.render(feature, context.cameraRenderState(), context.levelRenderState().skyRenderState),
               RtxReflectionRenderer::release
            )
         );
         register(
            WorldEffect.lazy(
               WorldEffects::atmosphereRenderTarget,
               AtmosphereRenderer::new,
               (feature, renderer, context) -> renderer.render(feature, context.cameraRenderState(), context.levelRenderState().skyRenderState),
               AtmosphereRenderer::release
            )
         );
         register(
            WorldEffect.lazy(
               () -> gate(RtxFeature.getEnabled(), RtxFeature::usesRays),
               RtxRayRenderer::new,
               (feature, renderer, context) -> renderer.render(feature, context.cameraRenderState(), context.levelRenderState().skyRenderState),
               RtxRayRenderer::release
            )
         );
         register(
            WorldEffect.lazy(
               () -> gate(FullBrightFeature.getEnabled(), FullBrightFeature::usesShaderLights),
               DynamicLightRenderer::new,
               (feature, renderer, context) -> renderer.render(feature, context.cameraRenderState(), context.tickDelta()),
               DynamicLightRenderer::release
            )
         );
         register(
            WorldEffect.lazy(
               ChamsFeature::getEnabled,
               ChamsPipeline::new,
               (feature, pipeline, context) -> pipeline.render(context.levelRenderState(), feature),
               ChamsPipeline::release
            )
         );
         register(WorldEffect.direct(JumpCirclesFeature::getEnabled, (feature, context) -> feature.renderWorld()));
         register(WorldEffect.direct(TrajectoriesFeature::getEnabled, (feature, context) -> feature.renderWorld()));
         register(WorldEffect.direct(ParticlesFeature::getEnabled, (feature, context) -> feature.renderWorld(context.tickDelta())));
         register(WorldEffect.direct(TrailsFeature::getEnabled, (feature, context) -> feature.renderWorld(context.tickDelta())));
         register(WorldEffect.direct(LineGlyphesFeature::getEnabled, (feature, context) -> feature.renderWorld()));
         register(
            WorldEffect.lazy(
               AuraFeature::getMarkerFeature,
               TargetMarkers::new,
               (feature, markers, context) -> markers.render(feature, context.tickDelta()),
               TargetMarkers::release
            )
         );
         register(
            WorldEffect.lazy(
               () -> gate(WorldTweaksFeature.getEnabled(), WorldTweaksFeature::usesSaturation),
               WorldTweaksRenderer::new,
               (feature, renderer, context) -> renderer.renderSaturation(feature),
               WorldTweaksRenderer::release
            )
         );
      }
   }

   public static void register(WorldEffect effect) {
      EFFECTS.add(effect);
   }

   public static void render(WorldEffectContext context) {
      for (WorldEffect effect : EFFECTS) {
         if (effect.active()) {
            effect.render(context);
         } else {
            effect.release();
         }
      }
   }

   private static <F> F gate(F feature, Predicate<F> enabled) {
      return feature != null && enabled.test(feature) ? feature : null;
   }

   private static AtmosphereFeature atmosphereRenderTarget() {
      AtmosphereFeature feature = FeatureManager.INSTANCE.getFeature(AtmosphereFeature.class);
      if (feature == null) {
         return null;
      }

      boolean ambient = feature.isEnabled() && feature.usesAtmosphere();
      AuraFeature aura = FeatureManager.INSTANCE.getFeature(AuraFeature.class);
      boolean lightningTargetEsp = aura != null && aura.isEnabled() && aura.usesLightningTargetEsp();
      return !ambient && !lightningTargetEsp ? null : feature;
   }
}
