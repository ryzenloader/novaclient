package win.furryhook.feature.impl.pve;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.WorldJoinEvent;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;
import win.furryhook.event.events.packet.PacketReceiveEvent;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.BooleanSetting;
import win.furryhook.mixin.accessor.FishingHookAccessor;
import win.furryhook.pve.AutomationPriority;
import win.furryhook.pve.AutomationResource;
import win.furryhook.pve.PveAutomationCoordinator;
import win.furryhook.pve.PveFeature;

public final class AutoFishFeature extends PveFeature {
   private static final int SAVE_DURABILITY = 10;
   private static final int INVENTORY_SIZE = 36;
   private static final int HOTBAR_SIZE = 9;
   private static final long HOOK_APPEAR_TIMEOUT_TICKS = 40L;
   private static final long RECAST_DELAY_TICKS = 8L;
   private static final long BITE_LATCH_TIMEOUT_TICKS = 40L;
   private static final long SPLASH_MAX_AGE_NANOS = 2000000000L;
   private static final double SPLASH_DISTANCE_SQUARED = 9.0;
   public final BooleanSetting saveRod = this.register(new BooleanSetting("Save Rod", false));
   private final AtomicReference<AutoFishFeature.SplashSignal> splashSignal = new AtomicReference<>();
   private AutoFishFeature.FishingState state = AutoFishFeature.FishingState.READY_TO_CAST;
   private long tick;
   private long stateSinceTick;
   private int observedHookId = -1;
   private boolean wasBiting;
   private boolean biteLatched;
   private long biteLatchedAtTick;
   private InteractionHand activeHand;
   private int swapSettleTicks;
   private int managedHotbarSlot = -1;
   private int managedInventorySlot = -1;
   private int restoreSelectedSlot = -1;
   private ItemStack displacedStack = ItemStack.EMPTY;

   public AutoFishFeature() {
      super("AutoFish", "Casts and reels a fishing rod automatically", FeatureCategory.PLAYER, -1, AutomationPriority.FEATURE);
   }

   @Override
   protected void onPveEnable() {
      this.resetRuntime();
   }

   @Override
   protected void onPveDisable() {
      this.restoreManagedRod();
      this.resetRuntime();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      this.tick++;
      Minecraft client = event.getClient();
      LocalPlayer player = client.player;
      if (player != null && client.level != null && client.gameMode != null) {
         if (client.gui.screen() == null) {
            FishingHook hook = player.fishing;
            if (hook != null && hook.getPlayerOwner() == player && !hook.isRemoved()) {
               this.handleHookPresent(client, player, hook);
            } else {
               this.handleHookAbsent(client, player);
            }
         }
      } else {
         if (player != null && this.hasManagedRod()) {
            this.restoreManagedRod();
         }

         this.resetForMissingWorld();
      }
   }

   @EventTarget
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPhase() == PacketReceiveEvent.Phase.PRE) {
         if (event.getPacket() instanceof ClientboundSoundPacket packet && isBobberSplash(packet.getSound())) {
            this.splashSignal.set(new AutoFishFeature.SplashSignal(packet.getX(), packet.getY(), packet.getZ(), -1, System.nanoTime()));
         } else {
            if (event.getPacket() instanceof ClientboundSoundEntityPacket packet && isBobberSplash(packet.getSound())) {
               this.splashSignal.set(new AutoFishFeature.SplashSignal(0.0, 0.0, 0.0, packet.getId(), System.nanoTime()));
            }
         }
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.restoreManagedRod();
      this.resetRuntime();
   }

   @EventTarget
   public void onWorldJoin(WorldJoinEvent event) {
      this.resetRuntime();
   }

   private void handleHookAbsent(Minecraft client, LocalPlayer player) {
      this.clearObservedHook();
      if (this.state == AutoFishFeature.FishingState.REEL_SENT || this.state == AutoFishFeature.FishingState.WAITING_FOR_BITE) {
         this.transition(AutoFishFeature.FishingState.RECAST_COOLDOWN);
      } else if (this.state == AutoFishFeature.FishingState.WAITING_FOR_HOOK) {
         if (this.ticksInState() >= 40L) {
            this.transition(AutoFishFeature.FishingState.READY_TO_CAST);
         }
      } else if (this.state == AutoFishFeature.FishingState.RECAST_COOLDOWN) {
         if (this.ticksInState() >= 8L) {
            this.transition(AutoFishFeature.FishingState.READY_TO_CAST);
         }
      } else {
         InteractionHand hand = this.prepareRod(player);
         if (hand != null) {
            this.splashSignal.set(null);
            this.useRod(client, player, hand);
            this.activeHand = hand;
            this.transition(AutoFishFeature.FishingState.WAITING_FOR_HOOK);
         }
      }
   }

   private void handleHookPresent(Minecraft client, LocalPlayer player, FishingHook hook) {
      if (hook.getId() != this.observedHookId) {
         this.observedHookId = hook.getId();
         this.wasBiting = false;
         this.biteLatched = false;
         this.transition(AutoFishFeature.FishingState.WAITING_FOR_BITE);
      } else if (this.state != AutoFishFeature.FishingState.REEL_SENT && this.state != AutoFishFeature.FishingState.WAITING_FOR_BITE) {
         this.transition(AutoFishFeature.FishingState.WAITING_FOR_BITE);
      }

      boolean biting = ((FishingHookAccessor)hook).furryhook$isBiting();
      if (biting && !this.wasBiting) {
         this.latchBite();
      }

      if (this.consumeSplashNear(hook)) {
         this.latchBite();
      }

      this.wasBiting = biting;
      if (this.state != AutoFishFeature.FishingState.REEL_SENT && this.biteLatched) {
         if (this.tick - this.biteLatchedAtTick > 40L) {
            this.biteLatched = false;
         } else {
            InteractionHand hand = this.prepareRod(player);
            if (hand != null) {
               this.useRod(client, player, hand);
               this.activeHand = hand;
               this.biteLatched = false;
               this.transition(AutoFishFeature.FishingState.REEL_SENT);
            }
         }
      }
   }

   private InteractionHand prepareRod(LocalPlayer player) {
      if (this.swapSettleTicks > 0) {
         this.swapSettleTicks--;
         return null;
      }

      InteractionHand heldHand = this.findHeldRodHand(player);
      if (heldHand == null) {
         return null;
      }

      ItemStack held = player.getItemInHand(heldHand);
      if (!this.saveRod.getValue() || remainingDurability(held) > 10) {
         return heldHand;
      }

      if (this.hasManagedRod()) {
         if (this.restoreManagedRod()) {
            this.swapSettleTicks = Math.max(this.swapSettleTicks, 1);
         }

         return null;
      } else {
         AutoFishFeature.RodCandidate candidate = this.findBestUsableRod(player);
         if (candidate == null) {
            return null;
         } else {
            return candidate.offhand() ? InteractionHand.OFF_HAND : this.activateInventoryRod(player, candidate.inventoryIndex());
         }
      }
   }

   private InteractionHand findHeldRodHand(LocalPlayer player) {
      if (this.managedHotbarSlot >= 0 && player.getInventory().getSelectedSlot() == this.managedHotbarSlot && isRod(player.getMainHandItem())) {
         return InteractionHand.MAIN_HAND;
      } else if (this.activeHand != null && isRod(player.getItemInHand(this.activeHand))) {
         return this.activeHand;
      } else if (isRod(player.getMainHandItem())) {
         return InteractionHand.MAIN_HAND;
      } else {
         return isRod(player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
      }
   }

   private AutoFishFeature.RodCandidate findBestUsableRod(LocalPlayer player) {
      AutoFishFeature.RodCandidate best = null;
      int selectedSlot = player.getInventory().getSelectedSlot();

      for (int index = 0; index < 36; index++) {
         ItemStack stack = player.getInventory().getItem(index);
         if (isUsableReplacement(stack)) {
            int accessibility = index == selectedSlot ? 3 : (index < 9 ? 2 : 1);
            AutoFishFeature.RodCandidate candidate = new AutoFishFeature.RodCandidate(
               index, false, remainingDurability(stack), unbreakingLevel(stack), accessibility
            );
            if (isBetter(candidate, best)) {
               best = candidate;
            }
         }
      }

      ItemStack offhand = player.getOffhandItem();
      if (isUsableReplacement(offhand)) {
         AutoFishFeature.RodCandidate candidate = new AutoFishFeature.RodCandidate(-1, true, remainingDurability(offhand), unbreakingLevel(offhand), 3);
         if (isBetter(candidate, best)) {
            best = candidate;
         }
      }

      return best;
   }

   private InteractionHand activateInventoryRod(LocalPlayer player, int inventoryIndex) {
      int selectedSlot = player.getInventory().getSelectedSlot();
      if (inventoryIndex == selectedSlot) {
         return InteractionHand.MAIN_HAND;
      }

      if (!this.claim(AutomationResource.INVENTORY)) {
         return null;
      }

      try {
         if (!isUsableReplacement(player.getInventory().getItem(inventoryIndex))) {
            return null;
         } else if (inventoryIndex < 9) {
            this.restoreSelectedSlot = selectedSlot;
            this.managedHotbarSlot = inventoryIndex;
            player.getInventory().setSelectedSlot(inventoryIndex);
            return InteractionHand.MAIN_HAND;
         } else if (player.containerMenu == player.inventoryMenu && player.inventoryMenu.getCarried().isEmpty()) {
            this.displacedStack = player.getInventory().getItem(selectedSlot).copy();
            Minecraft.getInstance().gameMode.handleContainerInput(player.inventoryMenu.containerId, inventoryIndex, selectedSlot, ContainerInput.SWAP, player);
            this.managedInventorySlot = inventoryIndex;
            this.managedHotbarSlot = selectedSlot;
            this.swapSettleTicks = 1;
            return null;
         } else {
            return null;
         }
      } finally {
         PveAutomationCoordinator.INSTANCE.release(this);
      }
   }

   private boolean restoreManagedRod() {
      if (!this.hasManagedRod()) {
         return true;
      }

      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.gameMode != null && this.claim(AutomationResource.INVENTORY)) {
         try {
            if (this.managedInventorySlot >= 0) {
               if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) {
                  return false;
               }

               ItemStack source = player.getInventory().getItem(this.managedInventorySlot);
               ItemStack managed = player.getInventory().getItem(this.managedHotbarSlot);
               if (ItemStack.matches(source, this.displacedStack) && isRod(managed)) {
                  client.gameMode
                     .handleContainerInput(player.inventoryMenu.containerId, this.managedInventorySlot, this.managedHotbarSlot, ContainerInput.SWAP, player);
                  this.swapSettleTicks = 1;
               }
            }

            if (this.restoreSelectedSlot >= 0 && player.getInventory().getSelectedSlot() == this.managedHotbarSlot) {
               player.getInventory().setSelectedSlot(this.restoreSelectedSlot);
            }

            this.clearManagedRod();
            return true;
         } finally {
            PveAutomationCoordinator.INSTANCE.release(this);
         }
      } else {
         return false;
      }
   }

   private void useRod(Minecraft client, LocalPlayer player, InteractionHand hand) {
      if (isRod(player.getItemInHand(hand))) {
         InteractionResult result = client.gameMode.useItem(player, hand);
         if (result.consumesAction()) {
            player.swing(hand);
         }
      }
   }

   private boolean consumeSplashNear(FishingHook hook) {
      AutoFishFeature.SplashSignal signal = this.splashSignal.getAndSet(null);
      if (signal == null || System.nanoTime() - signal.receivedAtNanos() > 2000000000L) {
         return false;
      } else {
         return signal.entityId() >= 0 ? signal.entityId() == hook.getId() : hook.distanceToSqr(signal.x(), signal.y(), signal.z()) <= 9.0;
      }
   }

   private void latchBite() {
      this.biteLatched = true;
      this.biteLatchedAtTick = this.tick;
   }

   private void transition(AutoFishFeature.FishingState next) {
      if (this.state != next) {
         this.state = next;
         this.stateSinceTick = this.tick;
      }
   }

   private long ticksInState() {
      return Math.max(0L, this.tick - this.stateSinceTick);
   }

   private void clearObservedHook() {
      this.observedHookId = -1;
      this.wasBiting = false;
      this.biteLatched = false;
   }

   private void resetForMissingWorld() {
      this.splashSignal.set(null);
      this.state = AutoFishFeature.FishingState.READY_TO_CAST;
      this.stateSinceTick = this.tick;
      this.activeHand = null;
      this.swapSettleTicks = 0;
      this.clearObservedHook();
      this.clearManagedRod();
   }

   private void resetRuntime() {
      this.tick = 0L;
      this.state = AutoFishFeature.FishingState.READY_TO_CAST;
      this.stateSinceTick = 0L;
      this.activeHand = null;
      this.swapSettleTicks = 0;
      this.splashSignal.set(null);
      this.clearObservedHook();
      this.clearManagedRod();
   }

   private boolean hasManagedRod() {
      return this.managedHotbarSlot >= 0;
   }

   private void clearManagedRod() {
      this.managedHotbarSlot = -1;
      this.managedInventorySlot = -1;
      this.restoreSelectedSlot = -1;
      this.displacedStack = ItemStack.EMPTY;
   }

   private static boolean isBobberSplash(Holder<SoundEvent> sound) {
      return sound != null && sound.value() == SoundEvents.FISHING_BOBBER_SPLASH;
   }

   private static boolean isRod(ItemStack stack) {
      return stack != null && stack.is(Items.FISHING_ROD);
   }

   private static boolean isUsableReplacement(ItemStack stack) {
      return isRod(stack) && isUsableRodDurability(remainingDurability(stack));
   }

   static boolean isUsableRodDurability(int remainingDurability) {
      return remainingDurability > 10;
   }

   private static int remainingDurability(ItemStack stack) {
      return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
   }

   private static int unbreakingLevel(ItemStack stack) {
      for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
         if (enchantment.is(Enchantments.UNBREAKING)) {
            return stack.getEnchantments().getLevel(enchantment);
         }
      }

      return 0;
   }

   static int compareRodQuality(int remainingA, int unbreakingA, int remainingB, int unbreakingB) {
      int durability = Integer.compare(remainingA, remainingB);
      return durability != 0 ? durability : Integer.compare(unbreakingA, unbreakingB);
   }

   private static boolean isBetter(AutoFishFeature.RodCandidate candidate, AutoFishFeature.RodCandidate currentBest) {
      if (currentBest == null) {
         return true;
      } else {
         int quality = compareRodQuality(
            candidate.remainingDurability(), candidate.unbreakingLevel(), currentBest.remainingDurability(), currentBest.unbreakingLevel()
         );
         if (quality != 0) {
            return quality > 0;
         } else {
            return candidate.accessibility() != currentBest.accessibility()
               ? candidate.accessibility() > currentBest.accessibility()
               : candidate.inventoryIndex() < currentBest.inventoryIndex();
         }
      }
   }

   private enum FishingState {
      READY_TO_CAST,
      WAITING_FOR_HOOK,
      WAITING_FOR_BITE,
      REEL_SENT,
      RECAST_COOLDOWN;
   }

   private record RodCandidate(int inventoryIndex, boolean offhand, int remainingDurability, int unbreakingLevel, int accessibility) {
   }

   private record SplashSignal(double x, double y, double z, int entityId, long receivedAtNanos) {
   }
}
