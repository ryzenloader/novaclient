package win.furryhook.mixin.render;

import java.util.EnumMap;
import java.util.Map.Entry;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.furryhook.emote.Axis;
import win.furryhook.emote.EmotePart;
import win.furryhook.emote.EmotePoseCarrier;
import win.furryhook.mixin.accessor.HumanoidModelAccessor;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
   @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
   private void furryhook$applyEmotePose(AvatarRenderState state, CallbackInfo ci) {
      EnumMap<EmotePart, float[]> pose = ((EmotePoseCarrier)state).furryhook$getEmotePose();
      if (pose != null) {
         HumanoidModelAccessor accessor = (HumanoidModelAccessor)this;

         for (Entry<EmotePart, float[]> entry : pose.entrySet()) {
            ModelPart part = switch ((EmotePart)entry.getKey()) {
               case HEAD -> accessor.furryhook$getHead();
               case BODY -> accessor.furryhook$getBody();
               case RIGHT_ARM -> accessor.furryhook$getRightArm();
               case LEFT_ARM -> accessor.furryhook$getLeftArm();
               case RIGHT_LEG -> accessor.furryhook$getRightLeg();
               case LEFT_LEG -> accessor.furryhook$getLeftLeg();
            };
            furryhook$apply(part, entry.getValue());
         }
      }
   }

   private static void furryhook$apply(ModelPart part, float[] values) {
      float x = values[Axis.POS_X.ordinal()];
      float y = values[Axis.POS_Y.ordinal()];
      float z = values[Axis.POS_Z.ordinal()];
      if (!Float.isNaN(x)) {
         part.x = x;
      }

      if (!Float.isNaN(y)) {
         part.y = y;
      }

      if (!Float.isNaN(z)) {
         part.z = z;
      }

      float pitch = values[Axis.ROT_PITCH.ordinal()];
      float yaw = values[Axis.ROT_YAW.ordinal()];
      float roll = values[Axis.ROT_ROLL.ordinal()];
      if (!Float.isNaN(pitch)) {
         part.xRot = pitch;
      }

      if (!Float.isNaN(yaw)) {
         part.yRot = yaw;
      }

      if (!Float.isNaN(roll)) {
         part.zRot = roll;
      }
   }
}
