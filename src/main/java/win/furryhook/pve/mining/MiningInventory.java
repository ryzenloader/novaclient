package win.furryhook.pve.mining;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class MiningInventory {
   private static final List<Block> COMMON_TUNNEL_BLOCKS = List.of(
      Blocks.STONE,
      Blocks.COBBLESTONE,
      Blocks.DEEPSLATE,
      Blocks.COBBLED_DEEPSLATE,
      Blocks.TUFF,
      Blocks.ANDESITE,
      Blocks.DIORITE,
      Blocks.GRANITE,
      Blocks.DIRT,
      Blocks.COARSE_DIRT,
      Blocks.GRAVEL,
      Blocks.NETHERRACK,
      Blocks.BLACKSTONE,
      Blocks.BASALT,
      Blocks.END_STONE
   );

   private MiningInventory() {
   }

   public static int freeSlots(LocalPlayer player) {
      int free = 0;

      for (int slot = 0; slot < 36; slot++) {
         if (player.getInventory().getItem(slot).isEmpty()) {
            free++;
         }
      }

      return free;
   }

   public static int bestPickaxeSlot(LocalPlayer player) {
      int bestSlot = -1;
      int bestRemaining = -1;

      for (int slot = 0; slot < 36; slot++) {
         ItemStack stack = player.getInventory().getItem(slot);
         if (isPickaxe(stack)) {
            int remaining = remainingDurability(stack);
            if (remaining > bestRemaining) {
               bestRemaining = remaining;
               bestSlot = slot;
            }
         }
      }

      return bestSlot;
   }

   public static boolean isPickaxe(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.is(ItemTags.PICKAXES);
   }

   public static int remainingDurability(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.isDamageableItem() ? Math.max(0, stack.getMaxDamage() - stack.getDamageValue()) : Integer.MAX_VALUE;
   }

   public static double durabilityPercent(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.isDamageableItem() ? remainingDurability(stack) * 100.0 / Math.max(1, stack.getMaxDamage()) : 100.0;
   }

   public static String itemId(ItemStack stack) {
      return stack != null && !stack.isEmpty() ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT) : "";
   }

   public static boolean isTrash(ItemStack stack, Set<String> trashIds) {
      if (stack == null || stack.isEmpty() || trashIds == null || trashIds.isEmpty()) {
         return false;
      } else {
         return !stack.isDamageableItem() && stack.getEnchantments().isEmpty() ? trashIds.contains(itemId(stack)) : false;
      }
   }

   public static boolean isDepositItem(ItemStack stack, Set<String> configuredIds) {
      if (stack != null && !stack.isEmpty()) {
         String id = itemId(stack);
         if (configuredIds != null && configuredIds.contains(id)) {
            return true;
         }

         String path = id.substring(id.indexOf(58) + 1);
         return path.startsWith("raw_")
            || path.endsWith("_ore")
            || path.equals("coal")
            || path.equals("diamond")
            || path.equals("emerald")
            || path.equals("lapis_lazuli")
            || path.equals("redstone")
            || path.equals("amethyst_shard")
            || path.equals("nether_quartz")
            || path.equals("ancient_debris")
            || path.equals("gold_nugget");
      } else {
         return false;
      }
   }

   public static List<Block> resolveBlocks(Set<String> ids) {
      List<Block> blocks = new ArrayList<>();
      if (ids == null) {
         return blocks;
      }

      for (String id : ids) {
         try {
            Block block = (Block)BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
            if (block != null && block != Blocks.AIR && !blocks.contains(block)) {
               blocks.add(block);
            }
         } catch (RuntimeException var5) {
         }
      }

      return List.copyOf(blocks);
   }

   public static boolean isOre(Block block) {
      if (block != null && block != Blocks.AIR) {
         String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
         return path.endsWith("_ore") || path.equals("ancient_debris") || path.equals("gilded_blackstone");
      } else {
         return false;
      }
   }

   public static List<Block> commonTunnelBlocks() {
      return COMMON_TUNNEL_BLOCKS;
   }

   public static boolean isCommonTunnelBlock(Block block) {
      return COMMON_TUNNEL_BLOCKS.contains(block);
   }

   public static boolean isSafeBreakTarget(Block block) {
      if (block != null && block != Blocks.AIR) {
         String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
         return !Set.of(
               "bedrock",
               "barrier",
               "command_block",
               "chain_command_block",
               "repeating_command_block",
               "structure_block",
               "jigsaw",
               "end_portal",
               "end_portal_frame",
               "nether_portal",
               "moving_piston",
               "light"
            )
            .contains(path);
      } else {
         return false;
      }
   }
}
