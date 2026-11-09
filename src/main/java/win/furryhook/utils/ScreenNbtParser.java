package win.furryhook.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.ShutdownEvent;
import win.furryhook.event.events.screen.ScreenCloseEvent;
import win.furryhook.feature.impl.misc.DonItems;
import win.furryhook.utils.text.ChatUtil;

public final class ScreenNbtParser {
   public static final ScreenNbtParser INSTANCE = new ScreenNbtParser();
   public static final String OUTPUT_FILE = "furryhook-nbt-parser.jsonl";
   private static final Logger LOGGER = LoggerFactory.getLogger(ScreenNbtParser.class);
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
   private static final int STABLE_TICKS = 2;
   private static final int MAX_DIRTY_TICKS = 20;
   private static final int SCHEMA_VERSION = 1;
   private boolean capturing;
   private Path outputPath;
   private Screen trackedScreen;
   private String observedFingerprint;
   private String dumpedFingerprint;
   private int stableTicks;
   private int dirtyTicks;
   private int snapshotCount;
   private String lastError;

   private ScreenNbtParser() {
   }

   public boolean start(Minecraft client) {
      Path path = this.resolveOutputPath(client);

      try {
         Files.createDirectories(path.getParent());
         Files.writeString(path, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
         this.outputPath = path;
         this.capturing = true;
         this.snapshotCount = 0;
         this.lastError = null;
         this.resetTrackedScreen();
         this.writeRecord(this.sessionRecord("session_start", client));
         return true;
      } catch (IOException exception) {
         return this.fail("Unable to start NBT parser", exception);
      }
   }

   public boolean stop(Minecraft client) {
      if (!this.capturing) {
         this.lastError = "NBT parser is not running";
         return false;
      }

      try {
         JsonObject record = this.sessionRecord("session_end", client);
         record.addProperty("snapshots", this.snapshotCount);
         this.writeRecord(record);
         this.capturing = false;
         this.resetTrackedScreen();
         return true;
      } catch (IOException exception) {
         this.capturing = false;
         return this.fail("Unable to finish NBT parser log", exception);
      }
   }

   public boolean clear(Minecraft client) {
      Path path = this.resolveOutputPath(client);

      try {
         Files.createDirectories(path.getParent());
         Files.writeString(path, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
         this.outputPath = path;
         this.snapshotCount = 0;
         this.lastError = null;
         this.resetTrackedScreen();
         if (this.capturing) {
            this.writeRecord(this.sessionRecord("session_start", client));
         }

         return true;
      } catch (IOException exception) {
         return this.fail("Unable to clear NBT parser log", exception);
      }
   }

   public boolean captureNow(Minecraft client) {
      if (!this.capturing) {
         this.lastError = "NBT parser is not running";
         return false;
      }

      if (client.gui.screen() instanceof AbstractContainerScreen<?> screen) {
         try {
            this.track(screen);
            this.dumpSnapshot(client, screen, "manual");
            return true;
         } catch (Exception exception) {
            return this.failAndStop("Unable to capture container screen", exception);
         }
      } else {
         this.lastError = "No container screen is currently open";
         return false;
      }
   }

   public boolean isCapturing() {
      return this.capturing;
   }

   public int getSnapshotCount() {
      return this.snapshotCount;
   }

   public String getLastError() {
      return this.lastError;
   }

   public Path getOutputPath(Minecraft client) {
      return this.outputPath == null ? this.resolveOutputPath(client) : this.outputPath;
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      if (this.capturing) {
         if (event.getClient().gui.screen() instanceof AbstractContainerScreen<?> screen) {
            try {
               this.track(screen);
               String fingerprint = this.fingerprint(screen);
               if (Objects.equals(fingerprint, this.observedFingerprint)) {
                  this.stableTicks++;
               } else {
                  this.observedFingerprint = fingerprint;
                  this.stableTicks = 0;
               }

               if (Objects.equals(fingerprint, this.dumpedFingerprint)) {
                  this.dirtyTicks = 0;
                  return;
               }

               this.dirtyTicks++;
               if (this.stableTicks >= 2 || this.dirtyTicks >= 20) {
                  this.dumpSnapshot(event.getClient(), screen, this.stableTicks >= 2 ? "stable" : "timeout");
               }
            } catch (Exception exception) {
               this.failAndStop("NBT parser stopped after a capture error", exception);
               ChatUtil.error("NBT parser stopped  •  " + this.lastError);
            }
         } else {
            this.resetTrackedScreen();
         }
      }
   }

   @EventTarget
   public void onScreenClose(ScreenCloseEvent event) {
      if (this.capturing && event.getScreen() instanceof AbstractContainerScreen<?> screen && screen == this.trackedScreen) {
         try {
            String fingerprint = this.fingerprint(screen);
            if (!Objects.equals(fingerprint, this.dumpedFingerprint)) {
               this.observedFingerprint = fingerprint;
               this.dumpSnapshot(event.getClient(), screen, "screen_close");
            }
         } catch (Exception exception) {
            this.failAndStop("NBT parser stopped while saving the closing screen", exception);
            ChatUtil.error("NBT parser stopped  •  " + this.lastError);
         } finally {
            this.resetTrackedScreen();
         }
      }
   }

   @EventTarget
   public void onShutdown(ShutdownEvent event) {
      if (this.capturing) {
         try {
            JsonObject record = this.sessionRecord("session_end", event.getClient());
            record.addProperty("snapshots", this.snapshotCount);
            record.addProperty("reason", "client_shutdown");
            this.writeRecord(record);
         } catch (IOException exception) {
            LOGGER.error("Unable to finish NBT parser log during shutdown", exception);
         } finally {
            this.capturing = false;
         }
      }
   }

   private void track(AbstractContainerScreen<?> screen) {
      if (screen != this.trackedScreen) {
         this.trackedScreen = screen;
         this.observedFingerprint = null;
         this.dumpedFingerprint = null;
         this.stableTicks = 0;
         this.dirtyTicks = 0;
      }
   }

   private void resetTrackedScreen() {
      this.trackedScreen = null;
      this.observedFingerprint = null;
      this.dumpedFingerprint = null;
      this.stableTicks = 0;
      this.dirtyTicks = 0;
   }

   private String fingerprint(AbstractContainerScreen<?> screen) {
      AbstractContainerMenu menu = screen.getMenu();
      StringBuilder value = new StringBuilder(64 + menu.slots.size() * 16);
      value.append(screen.getClass().getName()).append('\u0000').append(screen.getTitle().getString()).append('\u0000').append(menu.getClass().getName());

      for (int index = 0; index < menu.slots.size(); index++) {
         ItemStack stack = ((Slot)menu.slots.get(index)).getItem();
         value.append('|').append(index).append(':');
         this.appendStackFingerprint(value, stack);
      }

      value.append("|carried:");
      this.appendStackFingerprint(value, menu.getCarried());
      return value.toString();
   }

   private void appendStackFingerprint(StringBuilder target, ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         target.append(stack.getCount()).append(':').append(ItemStack.hashItemAndComponents(stack));
      } else {
         target.append('0');
      }
   }

   private void dumpSnapshot(Minecraft client, AbstractContainerScreen<?> screen, String reason) throws IOException {
      Provider registries = (Provider)(client.level == null ? RegistryAccess.EMPTY : client.level.registryAccess());
      RegistryOps<JsonElement> jsonOps = RegistryOps.create(JsonOps.INSTANCE, registries);
      RegistryOps<Tag> nbtOps = RegistryOps.create(NbtOps.INSTANCE, registries);
      List<String> snapshotErrors = new ArrayList<>();
      AbstractContainerMenu menu = screen.getMenu();
      JsonObject record = this.baseRecord("screen_snapshot");
      record.addProperty("sequence", ++this.snapshotCount);
      record.addProperty("reason", reason);
      record.addProperty("screen_class", screen.getClass().getName());
      record.addProperty("title", screen.getTitle().getString());
      record.add("title_json", this.encodeComponent(screen.getTitle(), jsonOps, snapshotErrors));
      record.addProperty("menu_class", menu.getClass().getName());
      record.addProperty("menu_type", this.menuTypeId(menu));
      record.addProperty("container_id", menu.containerId);
      record.addProperty("state_id", menu.getStateId());
      record.addProperty("slot_count", menu.slots.size());
      int occupiedSlots = 0;
      JsonArray slots = new JsonArray();

      for (int index = 0; index < menu.slots.size(); index++) {
         Slot slot = (Slot)menu.slots.get(index);
         if (slot.hasItem()) {
            occupiedSlots++;
         }

         slots.add(this.encodeSlot(index, slot, jsonOps, nbtOps));
      }

      record.addProperty("occupied_slot_count", occupiedSlots);
      record.add("slots", slots);
      ItemStack carried = menu.getCarried();
      if (carried != null && !carried.isEmpty()) {
         record.add("carried", this.encodeItem(carried, jsonOps, nbtOps));
      } else {
         record.add("carried", JsonNull.INSTANCE);
      }

      if (!snapshotErrors.isEmpty()) {
         record.add("serialization_errors", this.strings(snapshotErrors));
      }

      this.writeRecord(record);
      this.dumpedFingerprint = this.fingerprint(screen);
      this.observedFingerprint = this.dumpedFingerprint;
      this.stableTicks = 0;
      this.dirtyTicks = 0;
   }

   private JsonObject encodeSlot(int menuIndex, Slot slot, RegistryOps<JsonElement> jsonOps, RegistryOps<Tag> nbtOps) {
      JsonObject result = new JsonObject();
      result.addProperty("menu_index", menuIndex);
      result.addProperty("container_slot", slot.getContainerSlot());
      result.addProperty("x", slot.x);
      result.addProperty("y", slot.y);
      result.addProperty("container_class", slot.container.getClass().getName());
      result.addProperty("active", slot.isActive());
      result.addProperty("fake", slot.isFake());
      ItemStack stack = slot.getItem();
      boolean empty = stack == null || stack.isEmpty();
      result.addProperty("empty", empty);
      if (!empty) {
         try {
            result.add("item", this.encodeItem(stack, jsonOps, nbtOps));
         } catch (RuntimeException exception) {
            JsonObject failed = new JsonObject();
            failed.addProperty("item_id", this.itemId(stack));
            failed.addProperty("count", stack.getCount());
            failed.addProperty("error", exception.toString());
            result.add("item", failed);
         }
      }

      return result;
   }

   private JsonObject encodeItem(ItemStack stack, RegistryOps<JsonElement> jsonOps, RegistryOps<Tag> nbtOps) {
      List<String> errors = new ArrayList<>();
      JsonObject result = new JsonObject();
      result.addProperty("item_id", this.itemId(stack));
      result.addProperty("item_class", stack.getItem().getClass().getName());
      result.addProperty("count", stack.getCount());
      result.addProperty("hover_name", stack.getHoverName().getString());
      result.add("hover_name_json", this.encodeComponent(stack.getHoverName(), jsonOps, errors));
      result.addProperty("item_name", stack.getItemName().getString());
      result.add("item_name_json", this.encodeComponent(stack.getItemName(), jsonOps, errors));
      result.addProperty("foil", stack.hasFoil());
      result.addProperty("damageable", stack.isDamageableItem());
      if (stack.isDamageableItem()) {
         result.addProperty("damage", stack.getDamageValue());
         result.addProperty("max_damage", stack.getMaxDamage());
      }

      result.addProperty("component_patch_size", stack.getComponentsPatch().size());
      result.add("component_patch", this.encodeComponentPatch(stack.getComponentsPatch(), jsonOps, errors));
      result.add("resolved_component_ids", this.resolvedComponentIds(stack));
      result.add("lore", this.encodeLore(stack, jsonOps, errors));
      result.add("enchantments", this.encodeEnchantments(stack));
      result.add("potion", this.encodePotion(stack));
      DonItems.findAny(stack).ifPresent(item -> {
         JsonObject recognized = new JsonObject();
         recognized.addProperty("server", item.server().name());
         recognized.addProperty("category", item.category().name());
         recognized.addProperty("enum", ((Enum)item).name());
         recognized.addProperty("display_name", item.displayName());
         result.add("don_item", recognized);
      });
      result.add("stack_json", this.valueOrFallback(ItemStack.CODEC.encodeStart(jsonOps, stack), errors, new JsonPrimitive(stack.toString())));
      Tag encodedNbt = this.valueOrFallback(ItemStack.CODEC.encodeStart(nbtOps, stack), errors, null);
      result.addProperty("stack_snbt", encodedNbt == null ? stack.toString() : encodedNbt.toString());
      if (!errors.isEmpty()) {
         result.add("serialization_errors", this.strings(errors));
      }

      return result;
   }

   private JsonObject encodeComponentPatch(DataComponentPatch patch, RegistryOps<JsonElement> jsonOps, List<String> errors) {
      List<Entry<DataComponentType<?>, Optional<?>>> entries = new ArrayList<>(patch.entrySet());
      entries.sort(Comparator.comparing(entryx -> this.componentId((DataComponentType<?>)entryx.getKey())));
      JsonObject result = new JsonObject();

      for (Entry<DataComponentType<?>, Optional<?>> entry : entries) {
         String id = this.componentId(entry.getKey());
         if (entry.getValue().isEmpty()) {
            result.add(id, JsonNull.INSTANCE);
         } else {
            TypedDataComponent<?> component = TypedDataComponent.createUnchecked(entry.getKey(), entry.getValue().get());
            result.add(id, this.valueOrFallback(component.encodeValue(jsonOps), errors, new JsonPrimitive(String.valueOf(entry.getValue().get()))));
         }
      }

      return result;
   }

   private JsonArray resolvedComponentIds(ItemStack stack) {
      List<String> ids = new ArrayList<>(stack.getComponents().size());

      for (TypedDataComponent<?> component : stack.getComponents()) {
         ids.add(this.componentId(component.type()));
      }

      ids.sort(String::compareTo);
      return this.strings(ids);
   }

   private JsonArray encodeLore(ItemStack stack, RegistryOps<JsonElement> jsonOps, List<String> errors) {
      JsonArray result = new JsonArray();
      ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
      if (lore == null) {
         return result;
      }

      List<Component> lines = lore.styledLines();

      for (int index = 0; index < lines.size(); index++) {
         Component line = lines.get(index);
         JsonObject encoded = new JsonObject();
         encoded.addProperty("index", index);
         encoded.addProperty("text", line.getString());
         encoded.add("json", this.encodeComponent(line, jsonOps, errors));
         result.add(encoded);
      }

      return result;
   }

   private JsonArray encodeEnchantments(ItemStack stack) {
      JsonArray result = new JsonArray();
      stack.getEnchantments().entrySet().stream().sorted(Comparator.comparing(entry -> this.holderId((Holder<?>)entry.getKey()))).forEach(entry -> {
         JsonObject enchantment = new JsonObject();
         enchantment.addProperty("id", this.holderId((Holder<?>)entry.getKey()));
         enchantment.addProperty("level", entry.getIntValue());
         result.add(enchantment);
      });
      return result;
   }

   private JsonElement encodePotion(ItemStack stack) {
      PotionContents contents = (PotionContents)stack.get(DataComponents.POTION_CONTENTS);
      if (contents == null) {
         return JsonNull.INSTANCE;
      }

      JsonObject result = new JsonObject();
      contents.potion()
         .ifPresentOrElse(potion -> result.addProperty("base_potion", this.holderId((Holder<?>)potion)), () -> result.add("base_potion", JsonNull.INSTANCE));
      contents.customColor().ifPresentOrElse(color -> result.addProperty("custom_color", color), () -> result.add("custom_color", JsonNull.INSTANCE));
      contents.customName().ifPresentOrElse(name -> result.addProperty("custom_name", name), () -> result.add("custom_name", JsonNull.INSTANCE));
      JsonArray effects = new JsonArray();

      for (MobEffectInstance effect : contents.getAllEffects()) {
         JsonObject encoded = new JsonObject();
         encoded.addProperty("id", this.holderId(effect.getEffect()));
         encoded.addProperty("description_id", effect.getDescriptionId());
         encoded.addProperty("amplifier", effect.getAmplifier());
         encoded.addProperty("level", effect.getAmplifier() + 1);
         encoded.addProperty("duration_ticks", effect.getDuration());
         encoded.addProperty("infinite", effect.isInfiniteDuration());
         encoded.addProperty("ambient", effect.isAmbient());
         encoded.addProperty("visible", effect.isVisible());
         effects.add(encoded);
      }

      result.add("effects", effects);
      return result;
   }

   private JsonElement encodeComponent(Component component, RegistryOps<JsonElement> jsonOps, List<String> errors) {
      return this.valueOrFallback(ComponentSerialization.CODEC.encodeStart(jsonOps, component), errors, new JsonPrimitive(component.getString()));
   }

   private <T> T valueOrFallback(DataResult<T> result, List<String> errors, T fallback) {
      return (T)result.resultOrPartial(errors::add).orElse(fallback);
   }

   private JsonObject sessionRecord(String type, Minecraft client) {
      JsonObject record = this.baseRecord(type);
      record.addProperty("schema_version", 1);
      record.addProperty("minecraft_version", SharedConstants.getCurrentVersion().name());
      record.addProperty("output_file", "furryhook-nbt-parser.jsonl");
      ServerData server = client.getCurrentServer();
      if (server != null) {
         record.addProperty("server_name", server.name);
         record.addProperty("server_address", server.ip);
      }

      return record;
   }

   private JsonObject baseRecord(String type) {
      JsonObject record = new JsonObject();
      record.addProperty("type", type);
      record.addProperty("captured_at", Instant.now().toString());
      return record;
   }

   private JsonArray strings(List<String> values) {
      JsonArray result = new JsonArray();
      values.forEach(result::add);
      return result;
   }

   private String menuTypeId(AbstractContainerMenu menu) {
      if (menu.getType() == null) {
         return "unregistered";
      }

      Identifier id = BuiltInRegistries.MENU.getKey(menu.getType());
      return id == null ? "unregistered" : id.toString();
   }

   private String itemId(ItemStack stack) {
      Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
      return id == null ? "unregistered" : id.toString();
   }

   private String componentId(DataComponentType<?> type) {
      Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
      return id == null ? "unregistered@" + Integer.toHexString(System.identityHashCode(type)) : id.toString();
   }

   private String holderId(Holder<?> holder) {
      return holder.unwrapKey().map(key -> key.identifier().toString()).orElseGet(() -> "direct:" + holder.value());
   }

   private Path resolveOutputPath(Minecraft client) {
      return client.gameDirectory.toPath().resolve("logs").resolve("furryhook-nbt-parser.jsonl");
   }

   private void writeRecord(JsonObject record) throws IOException {
      if (this.outputPath == null) {
         throw new IOException("Output path is not initialized");
      }

      Files.writeString(
         this.outputPath, GSON.toJson(record) + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND
      );
   }

   private boolean fail(String message, Exception exception) {
      this.lastError = message + ": " + exception.getMessage();
      LOGGER.error(message, exception);
      return false;
   }

   private boolean failAndStop(String message, Exception exception) {
      this.capturing = false;
      return this.fail(message, exception);
   }
}
