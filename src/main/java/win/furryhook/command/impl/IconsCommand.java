package win.furryhook.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import win.furryhook.command.ClientCommand;
import win.furryhook.utils.render.gui.GuiTexture;
import win.furryhook.utils.text.ChatUtil;

public final class IconsCommand extends ClientCommand {
   public IconsCommand() {
      super("icons", "Иконки интерфейса: icons on|off");
   }

   @Override
   public List<String> aliases() {
      return List.of("svg");
   }

   @Override
   public void build(LiteralArgumentBuilder<Object> builder) {
      builder.executes(context -> this.report());
      builder.then(LiteralArgumentBuilder.literal("on").executes(context -> this.set(false)));
      builder.then(LiteralArgumentBuilder.literal("off").executes(context -> this.set(true)));
   }

   private int set(boolean disabled) {
      GuiTexture.setSvgIconsDisabled(disabled);
      if (disabled) {
         ChatUtil.success("Иконки выключены  •  меню без картинок, растеризация SVG не выполняется");
      } else {
         ChatUtil.success("Иконки включены");
      }

      return 1;
   }

   private int report() {
      ChatUtil.info(GuiTexture.svgIconsDisabled() ? "Иконки: выключены" : "Иконки: включены");
      ChatUtil.usage("icons on  •  icons off");
      return 1;
   }
}
