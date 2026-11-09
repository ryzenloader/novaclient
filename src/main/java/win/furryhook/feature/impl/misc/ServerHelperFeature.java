package win.furryhook.feature.impl.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.input.KeyboardInputEvent;
import win.furryhook.event.events.input.MouseInputEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.InputBindSetting;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.utils.InputGuard;
import win.furryhook.utils.text.ChatUtil;

public final class ServerHelperFeature extends Feature {
   public final ModeSetting mode = this.register(new ModeSetting("Mode", "FunTime", "FunTime", "Spacetime"));
   public final NumberSetting stopTicksBefore = this.register(new NumberSetting("Stop Before", 4.0, 0.0, 20.0, 1.0, " ticks").visibleWhen(this::spacetime));
   public final NumberSetting stopTicksAfter = this.register(new NumberSetting("Stop After", 3.0, 0.0, 20.0, 1.0, " ticks").visibleWhen(this::spacetime));
   public final InputBindSetting ftSnowball = this.register(new InputBindSetting("Снежок заморозка", -1));
   public final InputBindSetting ftGodsAura = this.register(new InputBindSetting("Божья аура", -1));
   public final InputBindSetting ftTrap = this.register(new InputBindSetting("Трапка", -1));
   public final InputBindSetting ftPlast = this.register(new InputBindSetting("Пласт", -1));
   public final InputBindSetting ftClearDust = this.register(new InputBindSetting("Явная пыль", -1));
   public final InputBindSetting ftFireTornado = this.register(new InputBindSetting("Огненный смерч", -1));
   public final InputBindSetting ftDisorientation = this.register(new InputBindSetting("Дезориентация", -1));
   public final InputBindSetting ftStrengthPotion = this.register(new InputBindSetting("Зелье силы", -1));
   public final InputBindSetting ftSpeedPotion = this.register(new InputBindSetting("Зелье скорости", -1));
   public final InputBindSetting ftWindCharge = this.register(new InputBindSetting("Заряд ветра", -1));
   private final List<ServerHelperFeature.ItemBind> itemBinds = new ArrayList<>();
   private Item pendingItem = null;
   private int tickTimer = 0;
   private int itemSlot = -1;
   private int originalSlot = -1;
   private boolean savedForward;
   private boolean savedBack;
   private boolean savedLeft;
   private boolean savedRight;
   private boolean savedJump;

   public ServerHelperFeature() {
      super("ServerHelper", "Quick item usage for FunTime and Spacetime", FeatureCategory.MISC, -1);
      this.initializeBinds();
   }

   private void initializeBinds() {
      this.itemBinds.add(new ServerHelperFeature.ItemBind(this.ftSnowball, Items.SNOWBALL));
      this.itemBinds.add(new ServerHelperFeature.ItemBind(this.ftGodsAura, Items.PHANTOM_MEMBRANE));
      this.itemBinds.add(new ServerHelperFeature.ItemBind(this.ftTrap, Items.NETHERITE_SCRAP));
      this.itemBinds.add(new ServerHelperFeature.ItemBind(this.ftPlast, Items.DRIED_KELP));
      this.itemBinds.add(new ServerHelperFeature.ItemBind(this.ftClearDust, Items.SUGAR));
      this.itemBinds.add(new ServerHelperFeature.ItemBind(this.ftFireTornado, Items.FIRE_CHARGE));
      this.itemBinds.add(new ServerHelperFeature.ItemBind(this.ftDisorientation, Items.ENDER_EYE));
      this.itemBinds
         .add(
            new ServerHelperFeature.ItemBind(this.ftStrengthPotion, stack -> stack.is(Items.SPLASH_POTION) && stack.getHoverName().getString().contains("Силы"))
         );
      this.itemBinds
         .add(
            new ServerHelperFeature.ItemBind(
               this.ftSpeedPotion, stack -> stack.is(Items.SPLASH_POTION) && stack.getHoverName().getString().contains("Скорости")
            )
         );
      this.itemBinds.add(new ServerHelperFeature.ItemBind(this.ftWindCharge, Items.WIND_CHARGE));
   }

   @Override
   protected void onDisable() {
      this.resetSwapState();
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (event.getAction() == 1 && !InputGuard.isBindingBlocked()) {
         for (ServerHelperFeature.ItemBind bind : this.itemBinds) {
            if (bind.setting.matches(event.getKey()) && this.pendingItem == null) {
               this.queueItemUse(bind);
               event.cancel();
               break;
            }
         }
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (event.getAction() == 1 && !InputGuard.isBindingBlocked()) {
         for (ServerHelperFeature.ItemBind bind : this.itemBinds) {
            if (bind.setting.matchesMouse(event.getButton()) && this.pendingItem == null) {
               this.queueItemUse(bind);
               event.cancel();
               break;
            }
         }
      }
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft mc = event.getClient();
      LocalPlayer player = mc.player;
      if (player != null && this.pendingItem != null) {
         this.tickTimer++;
         if (this.tickTimer == 1) {
            this.saveAndStopMovement(mc);
            this.itemSlot = this.findItemSlot(player, this.pendingItem);
            if (this.itemSlot == -1) {
               ChatUtil.error("Предмет не найден");
               this.resetSwapState();
               return;
            }
         }

         if (this.spacetime()) {
            this.sustainMovementStop(mc, player);
         }

         int selectedSlot = player.getInventory().getSelectedSlot();
         int syncId = player.containerMenu.containerId;
         if (!this.spacetime()) {
            switch (this.tickTimer) {
               case 2:
                  this.swapToHand(mc, player, selectedSlot, syncId);
                  break;
               case 3:
                  this.useItem(mc, player);
                  break;
               case 4:
                  this.swapBack(mc, player, selectedSlot, syncId);
                  break;
               case 5:
                  this.resetSwapState();
            }
         } else {
            int swapTick = 2 + this.stopTicksBefore.getValue().intValue();
            if (this.tickTimer == swapTick) {
               this.swapToHand(mc, player, selectedSlot, syncId);
            } else if (this.tickTimer == swapTick + 1) {
               this.useItem(mc, player);
            } else if (this.tickTimer == swapTick + 2) {
               this.swapBack(mc, player, selectedSlot, syncId);
            } else if (this.tickTimer == swapTick + 3 + this.stopTicksAfter.getValue().intValue()) {
               this.resetSwapState();
            }
         }
      }
   }

   private void queueItemUse(ServerHelperFeature.ItemBind bind) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.gui.screen() == null) {
         int slot = this.findItemSlot(player, bind);
         if (slot == -1) {
            ChatUtil.error("Предмет не найден");
         } else {
            this.originalSlot = player.getInventory().getSelectedSlot();
            this.pendingItem = bind.item != null ? bind.item : Items.SPLASH_POTION;
            this.tickTimer = 0;
         }
      }
   }

   private void saveAndStopMovement(Minecraft mc) {
      this.savedForward = mc.options.keyUp.isDown();
      this.savedBack = mc.options.keyDown.isDown();
      this.savedLeft = mc.options.keyLeft.isDown();
      this.savedRight = mc.options.keyRight.isDown();
      this.savedJump = mc.options.keyJump.isDown();
      mc.options.keyUp.setDown(false);
      mc.options.keyDown.setDown(false);
      mc.options.keyLeft.setDown(false);
      mc.options.keyRight.setDown(false);
      mc.options.keyJump.setDown(false);
   }

   private void sustainMovementStop(Minecraft mc, LocalPlayer player) {
      mc.options.keyUp.setDown(false);
      mc.options.keyDown.setDown(false);
      mc.options.keyLeft.setDown(false);
      mc.options.keyRight.setDown(false);
      mc.options.keyJump.setDown(false);
      player.setSprinting(false);
      player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
   }

   private void swapToHand(Minecraft mc, LocalPlayer player, int selectedSlot, int syncId) {
      if (this.itemSlot >= 9) {
         mc.gameMode.handleContainerInput(syncId, this.itemSlot, selectedSlot, ContainerInput.SWAP, player);
      } else {
         player.getInventory().setSelectedSlot(this.itemSlot);
      }
   }

   private void useItem(Minecraft mc, LocalPlayer player) {
      mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
      player.swing(InteractionHand.MAIN_HAND);
   }

   private void swapBack(Minecraft mc, LocalPlayer player, int selectedSlot, int syncId) {
      if (this.itemSlot >= 9) {
         mc.gameMode.handleContainerInput(syncId, this.itemSlot, selectedSlot, ContainerInput.SWAP, player);
      } else {
         player.getInventory().setSelectedSlot(this.originalSlot);
      }
   }

   private boolean spacetime() {
      return this.mode.is("Spacetime");
   }

   private void resetSwapState() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.options != null) {
         mc.options.keyUp.setDown(this.savedForward);
         mc.options.keyDown.setDown(this.savedBack);
         mc.options.keyLeft.setDown(this.savedLeft);
         mc.options.keyRight.setDown(this.savedRight);
         mc.options.keyJump.setDown(this.savedJump);
      }

      this.pendingItem = null;
      this.itemSlot = -1;
      this.tickTimer = 0;
   }

   private int findItemSlot(LocalPlayer player, Item item) {
      for (int i = 0; i < 45; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.is(item)) {
            return i;
         }
      }

      return -1;
   }

   private int findItemSlot(LocalPlayer player, ServerHelperFeature.ItemBind bind) {
      if (bind.predicate != null) {
         for (int i = 0; i < 45; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (bind.predicate.test(stack)) {
               return i;
            }
         }
      } else if (bind.item != null) {
         return this.findItemSlot(player, bind.item);
      }

      return -1;
   }

   private static class ItemBind {
      final InputBindSetting setting;
      final Item item;
      final Predicate<ItemStack> predicate;

      ItemBind(InputBindSetting setting, Item item) {
         this.setting = setting;
         this.item = item;
         this.predicate = null;
      }

      ItemBind(InputBindSetting setting, Predicate<ItemStack> predicate) {
         this.setting = setting;
         this.item = null;
         this.predicate = predicate;
      }
   }
}
