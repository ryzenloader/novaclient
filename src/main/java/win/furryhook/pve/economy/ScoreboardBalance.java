package win.furryhook.pve.economy;

import java.util.OptionalLong;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public final class ScoreboardBalance {
   private static final String[] LABELS = new String[]{"balance", "money", "coins", "баланс", "монет"};

   private ScoreboardBalance() {
   }

   public static OptionalLong read(LocalPlayer player) {
      if (player == null) {
         return OptionalLong.empty();
      }

      Scoreboard scoreboard = player.level().getScoreboard();
      Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
      if (objective == null) {
         return OptionalLong.empty();
      }

      for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
         if (!entry.isHidden()) {
            Component name = (Component)(entry.display() == null ? Component.literal(entry.owner()) : entry.display());
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            String line = PlayerTeam.formatNameForTeam(team, name).getString();
            if (EconomyTextParser.containsAny(line, LABELS)) {
               OptionalLong amount = EconomyTextParser.amountNearAnyLabel(line, LABELS);
               if (amount.isEmpty()) {
                  amount = EconomyTextParser.largestAmount(line);
               }

               if (amount.isPresent()) {
                  return amount;
               }
            }
         }
      }

      return OptionalLong.empty();
   }
}
