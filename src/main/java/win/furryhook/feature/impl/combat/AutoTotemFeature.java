package win.furryhook.feature.impl.combat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.render.FinalGuiRenderEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.mixin.accessor.KeyMappingAccessor;

public final class AutoTotemFeature extends Feature {
   public final ModeSetting mode = this.register(new ModeSetting("Режим", "Default", "Default", "Legit"));
   public final BooleanSetting crystal = this.register(new BooleanSetting("Кристалы", true));
   public final NumberSetting healthThreshold = this.register(new NumberSetting("Порог здоровья", 4.5, 1.0, 20.0, 0.5, ""));
   public final NumberSetting elytraHealth = this.register(new NumberSetting("Здоровье на элитре", 8.5, 1.0, 20.0, 0.5, ""));
   public final NumberSetting crystalDistance = this.register(
      new NumberSetting("Дистанция до кристалла", 4.0, 1.0, 6.0, 0.5, "").visibleWhen(this.crystal::getValue)
   );
   public final NumberSetting crystalMaxHealth = this.register(
      new NumberSetting("Здоровье с шаром", 10.0, 1.0, 20.0, 0.5, "").visibleWhen(this.crystal::getValue)
   );
   public final BooleanSetting fallCheck = this.register(new BooleanSetting("Падение", true));
   public final BooleanSetting saveTaliks = this.register(new BooleanSetting("Сейв таликов", true));
   public final BooleanSetting returnItem = this.register(new BooleanSetting("Возвращать предмет", true));
   public final NumberSetting returnDelay = this.register(new NumberSetting("Задержка возврата", 500.0, 0.0, 2000.0, 50.0, "ms"));
   public final NumberSetting swapDelay = this.register(new NumberSetting("Замедление", 150.0, 0.0, 1000.0, 50.0, "ms"));
   public final NumberSetting restoreDelay = this.register(new NumberSetting("Задержка клавиш", 100.0, 0.0, 1000.0, 50.0, "ms"));
   public final BooleanSetting drawCount = this.register(new BooleanSetting("Отображать количество", true));
   private int savedSlot = -1;
   private int totemSlot = -1;
   private int savedItemSlot = -1;
   private int phaseTicks = 0;
   private long totemEquipTime = 0L;
   private long phaseStartMs = 0L;
   private boolean keysOverridden = false;
   private AutoTotemFeature.Phase phase = AutoTotemFeature.Phase.READY;

   public AutoTotemFeature() {
      super("AutoTotem", "Автоматически экипирует тотем с фазами", FeatureCategory.COMBAT, -1);
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft mc = event.getClient();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         if (this.phase != AutoTotemFeature.Phase.READY) {
            this.execute(mc, player);
         } else {
            float health = player.getHealth() + player.getAbsorptionAmount();
            boolean shouldEquip = false;
            boolean isFlying = player.isFallFlying();
            if (isFlying && health <= this.elytraHealth.getValue()) {
               shouldEquip = true;
            } else if (health <= this.healthThreshold.getValue()) {
               shouldEquip = true;
            } else if (this.fallCheck.getValue() && player.fallDistance > 10.0) {
               shouldEquip = true;
            } else if (this.crystal.getValue() && this.crystalCheck(mc, player)) {
               shouldEquip = true;
            }

            if (shouldEquip) {
               this.tryEquipTotem(mc, player);
            } else if (this.savedItemSlot != -1 && this.returnItem.getValue()) {
               long timeSinceEquip = System.currentTimeMillis() - this.totemEquipTime;
               if (timeSinceEquip >= this.returnDelay.getValue()) {
                  if (!this.mode.is("Legit") && !(this.swapDelay.getValue() > 0.0)) {
                     this.executeDefaultReturnItem(mc, player);
                  } else {
                     this.startLegitReturnItem(mc, player);
                  }
               }
            }
         }
      }
   }

   @EventTarget
   public void onFinalGuiRender(FinalGuiRenderEvent event) {
      if (this.drawCount.getValue()) {
         Minecraft mc = event.getClient();
         LocalPlayer player = mc.player;
         if (player != null) {
            int totemCount = this.getTotemCount(player);
            if (totemCount > 0) {
               GuiGraphicsExtractor extractor = event.getGuiGraphicsExtractor();
               int screenWidth = mc.getWindow().getGuiScaledWidth();
               int screenHeight = mc.getWindow().getGuiScaledHeight();
               float x = screenWidth / 2.0F + 10.0F;
               float y = screenHeight - 264;
               this.drawItem(mc, extractor, new ItemStack(Items.TOTEM_OF_UNDYING), x, y, 16.0F);
               String countText = totemCount + "x";
               extractor.text(mc.font, countText, Math.round(x + 17.0F), Math.round(y + 4.0F), -1, true);
            }
         }
      }
   }

   @Override
   protected void onDisable() {
      this.resetState();
      this.savedItemSlot = -1;
   }

   private void tryEquipTotem(Minecraft mc, LocalPlayer player) {
      if (this.phase == AutoTotemFeature.Phase.READY) {
         if (!this.isTotemInOffhand(player)) {
            this.savedSlot = player.getInventory().getSelectedSlot();
            this.totemSlot = this.findTotemInHotbar(player);
            if (this.totemSlot != -1) {
               this.savedItemSlot = this.totemSlot;
               if (!this.mode.is("Legit") && !(this.swapDelay.getValue() > 0.0)) {
                  this.executeDefaultSwap(mc, player);
               } else {
                  this.startLegitEquip(mc, player);
               }
            } else {
               this.totemSlot = this.findTotemInInventory(player);
               if (this.totemSlot != -1) {
                  if (this.savedItemSlot == -1) {
                     this.savedItemSlot = this.totemSlot;
                  }

                  if (!this.mode.is("Legit") && !(this.swapDelay.getValue() > 0.0)) {
                     this.executeDefaultSwap(mc, player);
                  } else {
                     this.startLegitEquip(mc, player);
                  }
               }
            }
         }
      }
   }

   private void executeDefaultSwap(Minecraft mc, LocalPlayer player) {
      if (this.totemSlot < 0) {
         this.resetState();
      } else {
         this.swapSlotWithOffhand(mc, player, this.totemSlot);
         this.totemEquipTime = System.currentTimeMillis();
         if (this.savedSlot >= 0) {
            player.getInventory().setSelectedSlot(this.savedSlot);
         }

         this.resetState();
      }
   }

   private void executeDefaultReturnItem(Minecraft mc, LocalPlayer player) {
      if (this.savedItemSlot >= 0) {
         this.swapSlotWithOffhand(mc, player, this.savedItemSlot);
      }

      this.savedItemSlot = -1;
   }

   private void startLegitEquip(Minecraft mc, LocalPlayer player) {
      this.suppressKeys(mc, player);
      this.phase = AutoTotemFeature.Phase.SLOWING_DOWN;
      this.phaseStartMs = System.currentTimeMillis();
      this.phaseTicks = 0;
   }

   private void startLegitReturnItem(Minecraft mc, LocalPlayer player) {
      this.suppressKeys(mc, player);
      this.phase = AutoTotemFeature.Phase.RETURN_SLOWING_DOWN;
      this.phaseStartMs = System.currentTimeMillis();
      this.phaseTicks = 0;
   }

   private void execute(Minecraft mc, LocalPlayer player) {
      this.phaseTicks++;
      long now = System.currentTimeMillis();
      switch (this.phase) {
         case SLOWING_DOWN:
            this.suppressKeys(mc, player);
            this.phase = AutoTotemFeature.Phase.WAIT_SWAP;
            this.phaseStartMs = now;
            this.phaseTicks = 0;
            break;
         case WAIT_SWAP:
            this.suppressKeys(mc, player);
            if (now - this.phaseStartMs >= this.swapDelay.getValue()) {
               if (this.totemSlot < 0) {
                  this.resetState();
                  return;
               }

               this.swapSlotWithOffhand(mc, player, this.totemSlot);
               this.phase = AutoTotemFeature.Phase.SWAP_TOTEM;
               this.phaseStartMs = now;
               this.phaseTicks = 0;
            }
            break;
         case SWAP_TOTEM:
            this.suppressKeys(mc, player);
            if (this.isTotemInOffhand(player) || this.phaseTicks >= 2) {
               this.totemEquipTime = System.currentTimeMillis();
               if (this.savedSlot >= 0 && this.savedSlot <= 8) {
                  player.getInventory().setSelectedSlot(this.savedSlot);
               }

               this.phase = AutoTotemFeature.Phase.WAIT_RESTORE;
               this.phaseStartMs = now;
               this.phaseTicks = 0;
            }
            break;
         case WAIT_RESTORE:
            this.suppressKeys(mc, player);
            if (now - this.phaseStartMs >= this.restoreDelay.getValue()) {
               this.restoreKeys(mc);
               this.resetState();
            }
            break;
         case RETURN_SLOWING_DOWN:
            this.suppressKeys(mc, player);
            this.phase = AutoTotemFeature.Phase.RETURN_WAIT_SWAP;
            this.phaseStartMs = now;
            this.phaseTicks = 0;
            break;
         case RETURN_WAIT_SWAP:
            this.suppressKeys(mc, player);
            if (now - this.phaseStartMs >= this.swapDelay.getValue()) {
               if (this.savedItemSlot >= 0) {
                  this.swapSlotWithOffhand(mc, player, this.savedItemSlot);
               }

               this.savedItemSlot = -1;
               this.phase = AutoTotemFeature.Phase.RETURN_WAIT_RESTORE;
               this.phaseStartMs = now;
               this.phaseTicks = 0;
            }
            break;
         case RETURN_WAIT_RESTORE:
            this.suppressKeys(mc, player);
            if (now - this.phaseStartMs >= this.restoreDelay.getValue()) {
               this.restoreKeys(mc);
               this.resetState();
            }
      }
   }

   private LocalPlayer player(Minecraft mc) {
      return mc.player;
   }

   private void suppressKeys(Minecraft mc, LocalPlayer player) {
      mc.options.keyUp.setDown(false);
      mc.options.keyDown.setDown(false);
      mc.options.keyLeft.setDown(false);
      mc.options.keyRight.setDown(false);
      mc.options.keyJump.setDown(false);
      mc.options.keySprint.setDown(false);
      if (player != null && player.isSprinting()) {
         player.setSprinting(false);
      }

      this.keysOverridden = true;
   }

   private void restoreKeys(Minecraft mc) {
      if (this.keysOverridden) {
         this.keysOverridden = false;
         mc.options.keyUp.setDown(this.isPhysicallyDown(mc, mc.options.keyUp));
         mc.options.keyDown.setDown(this.isPhysicallyDown(mc, mc.options.keyDown));
         mc.options.keyLeft.setDown(this.isPhysicallyDown(mc, mc.options.keyLeft));
         mc.options.keyRight.setDown(this.isPhysicallyDown(mc, mc.options.keyRight));
         mc.options.keyJump.setDown(this.isPhysicallyDown(mc, mc.options.keyJump));
         mc.options.keySprint.setDown(this.isPhysicallyDown(mc, mc.options.keySprint));
         if (mc.getConnection() != null && mc.player != null) {
            mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
         }
      }
   }

   private boolean isPhysicallyDown(Minecraft mc, KeyMapping mapping) {
      Key key = ((KeyMappingAccessor)mapping).getKey();
      return key.getType() == Type.MOUSE
         ? GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == 1
         : InputConstants.isKeyDown(mc.getWindow(), key.getValue());
   }

   private boolean crystalCheck(Minecraft mc, LocalPlayer player) {
      double radius = 6.5;
      double myY = player.getY();

      for (Player other : mc.level.players()) {
         if (other != player) {
            double distance = player.distanceTo(other);
            if (!(distance > radius)) {
               boolean hasCrystal = other.getMainHandItem().is(Items.END_CRYSTAL)
                  || other.getOffhandItem().is(Items.END_CRYSTAL)
                  || other.getMainHandItem().is(Items.RESPAWN_ANCHOR)
                  || other.getOffhandItem().is(Items.RESPAWN_ANCHOR);
               if (hasCrystal) {
                  double dy = myY - other.getY();
                  if (dy > 0.3 && dy <= 6.0) {
                     return true;
                  }
               }
            }
         }
      }

      for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, player.getBoundingBox().inflate(radius))) {
         double distance = player.position().distanceTo(crystal.getBoundingBox().getCenter());
         if (distance <= this.crystalDistance.getValue() && player.getY() >= crystal.getY()) {
            ItemStack offhand = player.getOffhandItem();
            if (!offhand.is(Items.SHIELD) || !offhand.isEnchanted() || player.getHealth() <= this.crystalMaxHealth.getValue()) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean isTotemInOffhand(LocalPlayer player) {
      return player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
   }

   private int findTotemInHotbar(LocalPlayer player) {
      for (int i = 0; i < 9; i++) {
         if (player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
            return i;
         }
      }

      return -1;
   }

   private int findTotemInInventory(LocalPlayer player) {
      if (this.saveTaliks.getValue()) {
         for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.TOTEM_OF_UNDYING) && !stack.isEnchanted()) {
               return i;
            }
         }
      }

      for (int i = 9; i < 36; i++) {
         if (player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
            return i;
         }
      }

      return -1;
   }

   private int getTotemCount(LocalPlayer player) {
      int count = 0;

      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.is(Items.TOTEM_OF_UNDYING)) {
            count += stack.getCount();
         }
      }

      return count;
   }

   private void swapSlotWithOffhand(Minecraft mc, LocalPlayer player, int slot) {
      if (mc.gameMode != null && player.containerMenu != null) {
         int slotIndex = slot;
         if (slotIndex >= 0 && slotIndex <= 8) {
            slotIndex += 36;
         }

         mc.gameMode.handleContainerInput(player.containerMenu.containerId, slotIndex, 40, ContainerInput.SWAP, player);
      }
   }

   private void drawItem(Minecraft mc, GuiGraphicsExtractor extractor, ItemStack stack, float x, float y, float size) {
      if (!stack.isEmpty()) {
         float guiScale = mc.getWindow().getGuiScale();
         float itemX = Math.round(x * guiScale) / guiScale;
         float itemY = Math.round(y * guiScale) / guiScale;
         Matrix3x2fStack pose = extractor.pose();
         pose.pushMatrix();
         pose.translate(itemX, itemY);
         pose.scale(size / 16.0F);
         extractor.item(mc.player, stack, 0, 0, 0);
         pose.popMatrix();
      }
   }

   private void resetState() {
      if (this.keysOverridden) {
         this.restoreKeys(Minecraft.getInstance());
      }

      this.totemSlot = -1;
      this.savedSlot = -1;
      this.phaseTicks = 0;
      this.phaseStartMs = 0L;
      this.phase = AutoTotemFeature.Phase.READY;
   }

   private enum Phase {
      READY,
      SLOWING_DOWN,
      WAIT_SWAP,
      SWAP_TOTEM,
      WAIT_RESTORE,
      RETURN_SLOWING_DOWN,
      RETURN_WAIT_SWAP,
      RETURN_WAIT_RESTORE;
   }
}
