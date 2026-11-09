package win.furryhook.feature.impl.misc;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.utils.ColorUtil;

public final class AuctionHelperFeature extends Feature {
   private static final Pattern PAGE_FRACTION = Pattern.compile("(?U)\\b\\d+\\s*/\\s*\\d+\\b");
   private static final Pattern AMOUNT = Pattern.compile("(?iu)(\\d+(?:[\\s\\u00A0,_.'’]\\d+)*)(?:\\s*([kкmмbб]))?");
   private static final Pattern EXPIRED = Pattern.compile("(?iu)(?:^|\\W)(?:ист[её]к|истекло|просрочен(?:а|о|ы)?|expired|outdated)(?:\\W|$)");
   private static AuctionHelperFeature instance;
   public final ModeSetting server = this.register(new ModeSetting("Server", "FunTime", "FunTime", "HolyWorld"));
   public final BooleanSetting priceForOneItem = this.register(new BooleanSetting("Price For 1 Item", true));
   public final BooleanSetting donItemInfo = this.register(new BooleanSetting("Don Item Info", true));
   public final BooleanSetting potionEffects = this.register(new BooleanSetting("Potion Effects", true));
   public final BooleanSetting effectDuration = this.register(new BooleanSetting("Effect Duration", true).visibleWhen(() -> this.potionEffects.getValue()));
   public final BooleanSetting highlightDonItems = this.register(new BooleanSetting("Highlight Don Items", true));
   public final BooleanSetting highlightPotions = this.register(new BooleanSetting("Highlight Potions", true));
   public final BooleanSetting highlightExpired = this.register(new BooleanSetting("Highlight Expired", true));
   public final BooleanSetting armorFilter = this.register(new BooleanSetting("Armor Filter", false));
   public final BooleanSetting swordFilter = this.register(new BooleanSetting("Sword Filter", false));
   public final BooleanSetting potionFilter = this.register(new BooleanSetting("Potion Filter", false));
   public final BooleanSetting knownPotionsOnly = this.register(new BooleanSetting("Known Potions Only", true).visibleWhen(() -> this.potionFilter.getValue()));
   public final BooleanSetting maxEffectLevel = this.register(new BooleanSetting("Max Effect Level", true).visibleWhen(() -> this.potionFilter.getValue()));
   public final BooleanSetting fullEffectDuration = this.register(
      new BooleanSetting("Full Effect Duration", true).visibleWhen(() -> this.potionFilter.getValue())
   );
   public final NumberSetting minDurability = this.register(
      new NumberSetting("Min Durability", 0.0, 0.0, 100.0, 1.0, "%").visibleWhen(() -> this.armorFilter.getValue() || this.swordFilter.getValue())
   );
   public final NumberSetting minUnbreaking = this.register(
      new NumberSetting("Min Unbreaking", 0.0, 0.0, 10.0, 1.0, "").visibleWhen(() -> this.armorFilter.getValue() || this.swordFilter.getValue())
   );
   public final BooleanSetting requireMending = this.register(
      new BooleanSetting("Require Mending", false).visibleWhen(() -> this.armorFilter.getValue() || this.swordFilter.getValue())
   );
   public final NumberSetting minProtection = this.register(
      new NumberSetting("Min Protection", 0.0, 0.0, 10.0, 1.0, "").visibleWhen(() -> this.armorFilter.getValue())
   );
   public final BooleanSetting noThorns = this.register(new BooleanSetting("No Thorns", false).visibleWhen(() -> this.armorFilter.getValue()));
   public final NumberSetting minDepthStrider = this.register(
      new NumberSetting("Min Depth Strider", 0.0, 0.0, 5.0, 1.0, "").visibleWhen(() -> this.armorFilter.getValue())
   );
   public final NumberSetting minSharpness = this.register(
      new NumberSetting("Min Sharpness", 0.0, 0.0, 10.0, 1.0, "").visibleWhen(() -> this.swordFilter.getValue())
   );
   public final BooleanSetting noKnockback = this.register(new BooleanSetting("No Knockback", false).visibleWhen(() -> this.swordFilter.getValue()));
   public final BooleanSetting dimRejected = this.register(new BooleanSetting("Dim Rejected", true).visibleWhen(this::hasAnyFilter));
   public final BooleanSetting filterReason = this.register(new BooleanSetting("Filter Reason", true).visibleWhen(this::hasAnyFilter));

   public AuctionHelperFeature() {
      super("AuctionHelper", "FunTime/HolyWorld auction prices and custom items", FeatureCategory.MISC, -1);
      instance = this;
   }

   public static boolean activeFor(String title) {
      return instance != null && instance.isEnabled() && isAuctionTitle(title);
   }

   public static List<Component> augmentTooltip(String title, ItemStack stack, List<Component> original) {
      if (activeFor(title) && stack != null && !stack.isEmpty() && original != null) {
         AuctionHelperFeature.AuctionServer server = instance.selectedServer();
         long totalPrice = server.extractPrice(stack);
         if (totalPrice <= 0L && !server.hasListingMarker(stack)) {
            return original;
         }

         List<Component> tooltip = new ArrayList<>(original);
         Optional<DonItems.DonItem> donItem = DonItems.find(stack, server.catalogue);
         if (instance.donItemInfo.getValue()) {
            donItem.ifPresent(item -> insertDonItemInfo(tooltip, item));
         }

         if (instance.priceForOneItem.getValue() && totalPrice > 0L && stack.getCount() > 1) {
            long unit = Math.max(1L, totalPrice / stack.getCount());
            server.insertAfterPrice(tooltip, Component.literal(server.unitPriceLabel(formatPrice(unit))).withStyle(ChatFormatting.GRAY));
         }

         if (instance.potionEffects.getValue()) {
            appendPotionEffects(tooltip, stack, server, donItem, instance.effectDuration.getValue());
         }

         AuctionHelperFeature.FilterResult filter = instance.evaluateFilters(stack, donItem);
         if (!filter.accepted && instance.filterReason.getValue()) {
            tooltip.add(Component.literal("Фильтр: " + filter.reason).withStyle(ChatFormatting.RED));
         }

         return tooltip;
      } else {
         return original;
      }
   }

   public static int slotOverlayColor(String title, ItemStack stack) {
      if (activeFor(title) && stack != null && !stack.isEmpty()) {
         AuctionHelperFeature.AuctionServer server = instance.selectedServer();
         if (server.extractPrice(stack) <= 0L && !server.hasListingMarker(stack)) {
            return 0;
         }

         if (instance.highlightExpired.getValue() && isOutdated(stack)) {
            return ColorUtil.rgba(255, 52, 62, 105);
         }

         Optional<DonItems.DonItem> donItem = DonItems.find(stack, server.catalogue);
         if (instance.dimRejected.getValue() && !instance.evaluateFilters(stack, donItem).accepted) {
            return ColorUtil.rgba(12, 12, 14, 145);
         }

         if (donItem.isPresent()) {
            DonItems.Category category = donItem.get().category();
            boolean potionLike = category == DonItems.Category.POTION || category == DonItems.Category.ARROW;
            if (potionLike && instance.highlightPotions.getValue() || !potionLike && instance.highlightDonItems.getValue()) {
               return categoryOverlay(category);
            }
         }

         return instance.highlightPotions.getValue() && hasRealPotionEffects(stack, server, donItem) ? ColorUtil.rgba(185, 85, 255, 68) : 0;
      } else {
         return 0;
      }
   }

   public static boolean isAuctionTitle(String title) {
      return instance != null && instance.selectedServer().matchesTitle(title);
   }

   public static long extractPrice(ItemStack stack) {
      return instance == null ? -1L : instance.selectedServer().extractPrice(stack);
   }

   public static String formatPrice(long value) {
      return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
   }

   private AuctionHelperFeature.AuctionServer selectedServer() {
      return this.server.is("HolyWorld") ? AuctionHelperFeature.AuctionServer.HOLYWORLD : AuctionHelperFeature.AuctionServer.FUNTIME;
   }

   private boolean hasAnyFilter() {
      return this.armorFilter.getValue() || this.swordFilter.getValue() || this.potionFilter.getValue();
   }

   private AuctionHelperFeature.FilterResult evaluateFilters(ItemStack stack, Optional<DonItems.DonItem> donItem) {
      if (this.armorFilter.getValue() && isArmor(stack)) {
         AuctionHelperFeature.FilterResult equipment = this.evaluateEquipmentRequirements(stack);
         if (!equipment.accepted) {
            return equipment;
         }

         int protection = enchantmentLevel(stack, "protection");
         int requiredProtection = this.minProtection.getValue().intValue();
         if (protection < requiredProtection) {
            return AuctionHelperFeature.FilterResult.reject("Protection " + protection + " < " + requiredProtection);
         }

         if (this.noThorns.getValue() && enchantmentLevel(stack, "thorns") > 0) {
            return AuctionHelperFeature.FilterResult.reject("есть Thorns");
         }

         if (stack.is(ItemTags.FOOT_ARMOR)) {
            int depthStrider = enchantmentLevel(stack, "depth_strider");
            int requiredDepthStrider = this.minDepthStrider.getValue().intValue();
            if (depthStrider < requiredDepthStrider) {
               return AuctionHelperFeature.FilterResult.reject("Depth Strider " + depthStrider + " < " + requiredDepthStrider);
            }
         }
      }

      if (this.swordFilter.getValue() && stack.is(ItemTags.SWORDS)) {
         AuctionHelperFeature.FilterResult equipment = this.evaluateEquipmentRequirements(stack);
         if (!equipment.accepted) {
            return equipment;
         }

         int sharpness = enchantmentLevel(stack, "sharpness");
         int requiredSharpness = this.minSharpness.getValue().intValue();
         if (sharpness < requiredSharpness) {
            return AuctionHelperFeature.FilterResult.reject("Sharpness " + sharpness + " < " + requiredSharpness);
         }

         if (this.noKnockback.getValue() && enchantmentLevel(stack, "knockback") > 0) {
            return AuctionHelperFeature.FilterResult.reject("есть Knockback");
         }
      }

      return this.potionFilter.getValue() && isPotionOrArrow(stack) ? this.evaluatePotionProfile(stack, donItem) : AuctionHelperFeature.FilterResult.PASS;
   }

   private AuctionHelperFeature.FilterResult evaluateEquipmentRequirements(ItemStack stack) {
      if (stack.isDamageableItem()) {
         double remaining = (stack.getMaxDamage() - stack.getDamageValue()) * 100.0 / Math.max(1, stack.getMaxDamage());
         if (remaining + 1.0E-6 < this.minDurability.getValue()) {
            return AuctionHelperFeature.FilterResult.reject("прочность " + Math.round(remaining) + "% < " + this.minDurability.getValue().intValue() + "%");
         }
      }

      int unbreaking = enchantmentLevel(stack, "unbreaking");
      int requiredUnbreaking = this.minUnbreaking.getValue().intValue();
      if (unbreaking < requiredUnbreaking) {
         return AuctionHelperFeature.FilterResult.reject("Unbreaking " + unbreaking + " < " + requiredUnbreaking);
      } else {
         return this.requireMending.getValue() && enchantmentLevel(stack, "mending") <= 0
            ? AuctionHelperFeature.FilterResult.reject("нет Mending")
            : AuctionHelperFeature.FilterResult.PASS;
      }
   }

   private AuctionHelperFeature.FilterResult evaluatePotionProfile(ItemStack stack, Optional<DonItems.DonItem> donItem) {
      Optional<DonItems.DonItem> potionItem = donItem.filter(
         itemx -> itemx.category() == DonItems.Category.POTION || itemx.category() == DonItems.Category.ARROW
      );
      if (potionItem.isEmpty()) {
         return this.knownPotionsOnly.getValue()
            ? AuctionHelperFeature.FilterResult.reject("зелье отсутствует в DonItems")
            : AuctionHelperFeature.FilterResult.PASS;
      } else {
         DonItems.DonItem item = potionItem.get();
         if (!DonItems.hasPotionProfile(item)) {
            return AuctionHelperFeature.FilterResult.PASS;
         } else {
            Optional<Boolean> effectIds = DonItems.matchesPotionProfile(stack, item, false, false);
            if (effectIds.isPresent() && !effectIds.get()) {
               return AuctionHelperFeature.FilterResult.reject("набор эффектов не совпадает с " + item.displayName());
            } else if (this.maxEffectLevel.getValue() && !DonItems.matchesPotionProfile(stack, item, true, false).orElse(true)) {
               return AuctionHelperFeature.FilterResult.reject("уровень эффектов ниже максимального");
            } else {
               return this.fullEffectDuration.getValue() && !DonItems.matchesPotionProfile(stack, item, false, true).orElse(true)
                  ? AuctionHelperFeature.FilterResult.reject("длительность эффектов ниже максимальной")
                  : AuctionHelperFeature.FilterResult.PASS;
            }
         }
      }
   }

   private static boolean isArmor(ItemStack stack) {
      return stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR) || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR);
   }

   private static boolean isPotionOrArrow(ItemStack stack) {
      return stack.get(DataComponents.POTION_CONTENTS) != null;
   }

   private static int enchantmentLevel(ItemStack stack, String path) {
      for (Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
         Optional<ResourceKey<Enchantment>> key = ((Holder)entry.getKey()).unwrapKey();
         if (key.isPresent() && key.get().identifier().getPath().equals(path)) {
            return entry.getIntValue();
         }
      }

      return 0;
   }

   private static void insertDonItemInfo(List<Component> tooltip, DonItems.DonItem item) {
      String serverName = item.server() == DonItems.Server.FUNTIME ? "FunTime" : "HolyWorld";
      String label = serverName + " • " + item.displayName();
      if (!tooltip.stream().anyMatch(line -> line.getString().equals(label))) {
         tooltip.add(Math.min(1, tooltip.size()), Component.literal(label).withStyle(categoryFormatting(item.category())));
      }
   }

   private static void appendPotionEffects(
      List<Component> tooltip, ItemStack stack, AuctionHelperFeature.AuctionServer server, Optional<DonItems.DonItem> donItem, boolean showDuration
   ) {
      if (hasRealPotionEffects(stack, server, donItem)) {
         PotionContents contents = (PotionContents)stack.get(DataComponents.POTION_CONTENTS);
         if (contents != null) {
            tooltip.add(Component.literal("Эффекты:").withStyle(ChatFormatting.DARK_GRAY));

            for (MobEffectInstance effect : contents.getAllEffects()) {
               String effectName = ((MobEffect)effect.getEffect().value()).getDisplayName().getString();
               String duration = !showDuration ? "" : (effect.isInfiniteDuration() ? "∞" : formatDuration(effect.getDuration()));
               String suffix = duration.isEmpty() ? "" : " • " + duration;
               tooltip.add(
                  Component.literal(" • " + effectName + " " + roman(effect.getAmplifier() + 1) + suffix)
                     .withStyle(((MobEffect)effect.getEffect().value()).getCategory().getTooltipFormatting())
               );
            }
         }
      }
   }

   private static boolean hasRealPotionEffects(ItemStack stack, AuctionHelperFeature.AuctionServer server, Optional<DonItems.DonItem> donItem) {
      PotionContents contents = (PotionContents)stack.get(DataComponents.POTION_CONTENTS);
      if (contents == null) {
         return false;
      } else {
         List<MobEffectInstance> effects = new ArrayList<>();
         contents.getAllEffects().forEach(effects::add);
         if (effects.isEmpty()) {
            return false;
         } else if (server == AuctionHelperFeature.AuctionServer.HOLYWORLD
            && donItem.filter(item -> item.category() == DonItems.Category.POTION).isPresent()
            && effects.size() == 1) {
            MobEffectInstance effect = effects.getFirst();
            return !effect.getDescriptionId().endsWith("instant_health") || effect.getDuration() > 1;
         } else {
            return true;
         }
      }
   }

   private static String formatDuration(int ticks) {
      if (ticks <= 0) {
         return "";
      }

      long seconds = Math.max(1L, (ticks + 19L) / 20L);
      long hours = seconds / 3600L;
      long minutes = seconds % 3600L / 60L;
      long remainder = seconds % 60L;
      return hours > 0L ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder) : String.format(Locale.ROOT, "%d:%02d", minutes, remainder);
   }

   private static int categoryOverlay(DonItems.Category category) {
      return switch (category) {
         case WEAPON -> ColorUtil.rgba(255, 74, 84, 62);
         case ARMOR -> ColorUtil.rgba(70, 145, 255, 62);
         case COMBAT_ITEM, EXPLOSIVE -> ColorUtil.rgba(255, 130, 45, 68);
         case POTION -> ColorUtil.rgba(190, 80, 255, 72);
         case ARROW -> ColorUtil.rgba(55, 210, 255, 68);
         case TALISMAN -> ColorUtil.rgba(255, 205, 55, 72);
         case SPHERE -> ColorUtil.rgba(215, 100, 255, 72);
         case CUSTOM_ENCHANTMENT -> ColorUtil.rgba(170, 105, 255, 62);
         case RUNE -> ColorUtil.rgba(85, 255, 145, 62);
         case BACKPACK -> ColorUtil.rgba(50, 190, 205, 58);
         case TOOL -> ColorUtil.rgba(90, 180, 255, 58);
         case MISC -> ColorUtil.rgba(210, 210, 220, 42);
      };
   }

   private static ChatFormatting categoryFormatting(DonItems.Category category) {
      return switch (category) {
         case WEAPON, COMBAT_ITEM, EXPLOSIVE -> ChatFormatting.RED;
         case ARMOR, TOOL -> ChatFormatting.BLUE;
         case POTION, SPHERE, CUSTOM_ENCHANTMENT -> ChatFormatting.LIGHT_PURPLE;
         case ARROW, BACKPACK -> ChatFormatting.AQUA;
         case TALISMAN -> ChatFormatting.GOLD;
         case RUNE -> ChatFormatting.GREEN;
         case MISC -> ChatFormatting.GRAY;
      };
   }

   private static boolean isOutdated(ItemStack stack) {
      for (Component line : lore(stack)) {
         String raw = line.getString();
         String normalized = DonItems.normalizeName(raw);
         if (EXPIRED.matcher(raw).find()
            || normalized.contains("снят с продажи")
            || normalized.contains("уже куплен")
            || normalized.contains("товар недоступен")
            || normalized.contains("listing unavailable")) {
            return true;
         }
      }

      return false;
   }

   private static List<Component> lore(ItemStack stack) {
      ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
      return lore == null ? List.of() : lore.lines();
   }

   private static long parseLargestAmount(String line) {
      long largest = -1L;
      Matcher matcher = AMOUNT.matcher(line);

      while (matcher.find()) {
         long parsed = parseAmount(matcher.group(1), matcher.group(2));
         largest = Math.max(largest, parsed);
      }

      return largest;
   }

   private static long parseAmount(String token, String suffix) {
      long multiplier = switch (suffix == null ? "" : suffix.toLowerCase(Locale.ROOT)) {
         case "k", "к" -> 1000L;
         case "m", "м" -> 1000000L;
         case "b", "б" -> 1000000000L;
         default -> 1L;
      };

      try {
         if (multiplier > 1L) {
            String compact = token.replaceAll("[\\s\\u00A0_'’]", "");
            int separator = Math.max(compact.lastIndexOf(46), compact.lastIndexOf(44));
            if (separator >= 0 && compact.length() - separator - 1 <= 2) {
               double value = Double.parseDouble(compact.replace(',', '.'));
               return Math.round(value * multiplier);
            }
         }

         String digits = token.replaceAll("\\D", "");
         if (digits.isEmpty()) {
            return -1L;
         }

         long value = Long.parseLong(digits);
         return multiplier != 1L && value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
      } catch (NumberFormatException ignored) {
         return -1L;
      }
   }

   private static String roman(int level) {
      return switch (level) {
         case 1 -> "I";
         case 2 -> "II";
         case 3 -> "III";
         case 4 -> "IV";
         case 5 -> "V";
         case 6 -> "VI";
         case 7 -> "VII";
         case 8 -> "VIII";
         case 9 -> "IX";
         case 10 -> "X";
         default -> String.valueOf(level);
      };
   }

   private enum AuctionServer {
      FUNTIME(DonItems.Server.FUNTIME),
      HOLYWORLD(DonItems.Server.HOLYWORLD);

      private final DonItems.Server catalogue;

      AuctionServer(DonItems.Server catalogue) {
         this.catalogue = catalogue;
      }

      private boolean matchesTitle(String title) {
         if (title != null && !title.isBlank()) {
            String normalized = DonItems.normalizeName(title);
            if (!normalized.contains("донат магазин")
               && !normalized.contains("все для pvp")
               && !normalized.startsWith("помощь")
               && !normalized.contains("премиум магазин")) {
               boolean explicitAuction = normalized.contains("аукцион")
                  || normalized.contains("auction")
                  || normalized.contains("поиск")
                  || normalized.contains("search");
               return this == FUNTIME
                  ? explicitAuction || title.contains("漢:") || title.contains(":") && AuctionHelperFeature.PAGE_FRACTION.matcher(title).find()
                  : explicitAuction || normalized.contains("торговая площадка");
            } else {
               return false;
            }
         } else {
            return false;
         }
      }

      private long extractPrice(ItemStack stack) {
         long price = -1L;

         for (Component line : AuctionHelperFeature.lore(stack)) {
            String raw = line.getString();
            if (this.isPriceLine(raw)) {
               price = Math.max(price, AuctionHelperFeature.parseLargestAmount(raw));
            }
         }

         return price;
      }

      private boolean hasListingMarker(ItemStack stack) {
         for (Component line : AuctionHelperFeature.lore(stack)) {
            String normalized = DonItems.normalizeName(line.getString());
            if (normalized.contains("нажмите чтобы купить")
               || normalized.contains("продавец")
               || normalized.contains("seller")
               || normalized.contains("click to buy")
               || normalized.contains("купить сейчас")) {
               return true;
            }
         }

         return false;
      }

      private boolean isPriceLine(String line) {
         String normalized = DonItems.normalizeName(line);
         return normalized.contains("цен")
            || normalized.contains("стоимост")
            || normalized.contains("price")
            || normalized.contains("cost")
            || normalized.contains("за штуку")
            || line.contains("$")
            || line.contains("⛃");
      }

      private void insertAfterPrice(List<Component> tooltip, Component addition) {
         for (int index = 0; index < tooltip.size(); index++) {
            if (this.isPriceLine(tooltip.get(index).getString())) {
               tooltip.add(index + 1, addition);
               return;
            }
         }

         tooltip.add(addition);
      }

      private String unitPriceLabel(String formattedPrice) {
         return this == FUNTIME ? "$ За штуку: $" + formattedPrice : "Цена за штуку: " + formattedPrice;
      }
   }

   private record FilterResult(boolean accepted, String reason) {
      private static final AuctionHelperFeature.FilterResult PASS = new AuctionHelperFeature.FilterResult(true, "");

      private static AuctionHelperFeature.FilterResult reject(String reason) {
         return new AuctionHelperFeature.FilterResult(false, reason);
      }
   }
}
