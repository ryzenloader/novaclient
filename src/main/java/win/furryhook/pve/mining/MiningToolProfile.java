package win.furryhook.pve.mining;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import win.furryhook.pve.economy.EconomyItemText;
import win.furryhook.pve.economy.EconomyTextParser;
import win.furryhook.pve.server.ServerProfile;

public enum MiningToolProfile {
   STANDARD(0, 1),
   BULLDOZER_I(1, 9),
   BULLDOZER_II(2, 27);

   private static final Pattern BULLDOZER = Pattern.compile("(?iuU)\\bбульдозер\\s*(?:[:\\-]?\\s*)?(ii|i|2|1)\\b");
   private final int bulldozerLevel;
   private final int maximumBlocksPerBreak;

   MiningToolProfile(int bulldozerLevel, int maximumBlocksPerBreak) {
      this.bulldozerLevel = bulldozerLevel;
      this.maximumBlocksPerBreak = maximumBlocksPerBreak;
   }

   public int bulldozerLevel() {
      return this.bulldozerLevel;
   }

   public int maximumBlocksPerBreak() {
      return this.maximumBlocksPerBreak;
   }

   public int durabilityReserve(ItemStack stack, double minimumPercent) {
      if (stack != null && !stack.isEmpty() && stack.isDamageableItem()) {
         int configured = (int)Math.ceil(stack.getMaxDamage() * Math.max(0.0, minimumPercent) / 100.0);
         return configured + this.maximumBlocksPerBreak;
      } else {
         return 0;
      }
   }

   public String displayName() {
      return switch (this) {
         case STANDARD -> "Standard";
         case BULLDOZER_I -> "Бульдозер I (3x3x1)";
         case BULLDOZER_II -> "Бульдозер II (3x3x3)";
      };
   }

   public static MiningToolProfile detect(ServerProfile server, ItemStack stack) {
      if (server == ServerProfile.FUNTIME && stack != null && !stack.isEmpty()) {
         MiningToolProfile enchantmentProfile = STANDARD;

         for (Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
            String id = String.valueOf(entry.getKey());
            String description = ((Enchantment)((Holder)entry.getKey()).value()).description().getString();
            if (EconomyTextParser.containsAny(id + " " + description, "bulldozer", "бульдозер")) {
               if (entry.getIntValue() >= 2) {
                  return BULLDOZER_II;
               }

               enchantmentProfile = BULLDOZER_I;
            }
         }

         return enchantmentProfile != STANDARD ? enchantmentProfile : detect(EconomyItemText.combined(stack));
      } else {
         return STANDARD;
      }
   }

   static MiningToolProfile detect(String text) {
      Matcher matcher = BULLDOZER.matcher(EconomyTextParser.normalize(text));

      MiningToolProfile detected;
      for (detected = STANDARD; matcher.find(); detected = BULLDOZER_I) {
         String level = matcher.group(1);
         if ("ii".equalsIgnoreCase(level) || "2".equals(level)) {
            return BULLDOZER_II;
         }
      }

      return detected;
   }
}
