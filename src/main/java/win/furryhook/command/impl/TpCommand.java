package win.furryhook.command.impl;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.entity.player.Player;
import win.furryhook.command.ClientCommand;
import win.furryhook.context.MinecraftContext;
import win.furryhook.utils.text.ChatUtil;

public final class TpCommand extends ClientCommand implements MinecraftContext {
   public TpCommand() {
      super("tp", "Клиентский телепорт: tp <x> <y> <z>  •  tp <player>");
   }

   @Override
   public void build(LiteralArgumentBuilder<Object> builder) {
      builder.executes(context -> {
         ChatUtil.usage("tp <x> <y> <z>  •  tp <player>");
         return 1;
      });
      builder.then(
         RequiredArgumentBuilder.argument("x", DoubleArgumentType.doubleArg())
            .then(
               RequiredArgumentBuilder.argument("y", DoubleArgumentType.doubleArg())
                  .then(
                     RequiredArgumentBuilder.argument("z", DoubleArgumentType.doubleArg())
                        .executes(
                           context -> this.teleport(
                              DoubleArgumentType.getDouble(context, "x"),
                              DoubleArgumentType.getDouble(context, "y"),
                              DoubleArgumentType.getDouble(context, "z")
                           )
                        )
                  )
            )
      );
      builder.then(
         RequiredArgumentBuilder.argument("player", StringArgumentType.word())
            .executes(context -> this.teleportToPlayer(StringArgumentType.getString(context, "player")))
      );
   }

   private int teleportToPlayer(String name) {
      LocalPlayer self = this.player();
      if (self != null && this.level() != null) {
         Player target = null;

         for (Player candidate : this.level().players()) {
            if (candidate != self && candidate.getGameProfile().name().equalsIgnoreCase(name)) {
               target = candidate;
               break;
            }
         }

         if (target == null) {
            ChatUtil.error("Игрок не найден  •  " + name);
            return 0;
         } else {
            return this.teleport(target.getX(), target.getY(), target.getZ());
         }
      } else {
         return 0;
      }
   }

   private int teleport(double x, double y, double z) {
      LocalPlayer player = this.player();
      if (player != null && player.connection != null) {
         for (int i = 0; i < 10; i++) {
            player.connection.send(new Pos(player.getX(), player.getY(), player.getZ(), false, false));
         }

         for (int i = 0; i < 10; i++) {
            player.connection.send(new Pos(x, y, z, false, false));
         }

         player.setPos(x, y, z);
         ChatUtil.success("TP  •  X " + fmt(x) + "  •  Y " + fmt(y) + "  •  Z " + fmt(z));
         return 1;
      } else {
         return 0;
      }
   }

   private static String fmt(double value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }
}
