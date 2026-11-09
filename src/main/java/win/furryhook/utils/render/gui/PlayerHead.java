package win.furryhook.utils.render.gui;

import net.minecraft.resources.Identifier;

public final class PlayerHead {
   private PlayerHead() {
   }

   public static void draw(float x, float y, float size, Identifier skin, float radius, int tint) {
      Render2DUtil.texture(x, y, size, size, skin).managed().uv(0.125F, 0.125F, 0.25F, 0.25F).radius(radius).color(tint).draw();
      Render2DUtil.texture(x, y, size, size, skin).managed().uv(0.625F, 0.125F, 0.75F, 0.25F).radius(radius).color(tint).draw();
   }
}
