package win.furryhook.command.impl;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import win.furryhook.command.ClientCommand;
import win.furryhook.context.MinecraftContext;
import win.furryhook.utils.text.ChatUtil;

public final class VClipCommand extends ClientCommand implements MinecraftContext {
   public VClipCommand() {
      super("vclip", "Вертикальный клип: vclip <y> [extra]");
   }

   @Override
   public List<String> aliases() {
      return List.of("clip");
   }

   @Override
   public void build(LiteralArgumentBuilder<Object> builder) {
      builder.executes(context -> {
         ChatUtil.usage("vclip <y>  •  vclip <y> <extra>");
         return 1;
      });
      builder.then(
         ((RequiredArgumentBuilder)RequiredArgumentBuilder.argument("y", DoubleArgumentType.doubleArg())
               .executes(context -> this.clip(DoubleArgumentType.getDouble(context, "y"), false)))
            .then(
               RequiredArgumentBuilder.argument("extra", BoolArgumentType.bool())
                  .executes(context -> this.clip(DoubleArgumentType.getDouble(context, "y"), BoolArgumentType.getBool(context, "extra")))
            )
      );
   }

   private int clip(double offset, boolean extra) {
      LocalPlayer player = this.player();
      if (player != null && player.connection != null) {
         if (extra) {
            for (int i = 0; i < 10; i++) {
               player.connection.send(new Pos(player.getX(), player.getY(), player.getZ(), false, false));
            }

            for (int i = 0; i < 10; i++) {
               player.connection.send(new Pos(player.getX(), player.getY() + offset, player.getZ(), false, false));
            }
         }

         player.setPos(player.getX(), player.getY() + offset, player.getZ());
         ChatUtil.success("VClip  •  " + (offset >= 0.0 ? "+" : "") + offset + " Y" + (extra ? "  •  extra" : ""));
         return 1;
      } else {
         return 0;
      }
   }
}
