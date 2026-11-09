package win.furryhook.mixin.accessor;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HumanoidModel.class)
public interface HumanoidModelAccessor {
   @Accessor("head")
   ModelPart furryhook$getHead();

   @Accessor("body")
   ModelPart furryhook$getBody();

   @Accessor("rightArm")
   ModelPart furryhook$getRightArm();

   @Accessor("leftArm")
   ModelPart furryhook$getLeftArm();

   @Accessor("rightLeg")
   ModelPart furryhook$getRightLeg();

   @Accessor("leftLeg")
   ModelPart furryhook$getLeftLeg();
}
