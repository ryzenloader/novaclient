package win.furryhook.feature.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.player.Input;
import org.lwjgl.glfw.GLFW;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;
import win.furryhook.event.events.packet.PacketSendEvent;
import win.furryhook.event.events.screen.ScreenCloseEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.menu.core.MenuOverlay;
import win.furryhook.mixin.accessor.KeyMappingAccessor;

public final class InventoryMoveFeature extends Feature implements MinecraftContext {
   public final ModeSetting mode = this.register(
      new ModeSetting(
         "Mode",
         InventoryMoveFeature.Mode.DEFAULT.getName(),
         InventoryMoveFeature.Mode.DEFAULT.getName(),
         InventoryMoveFeature.Mode.BYPASS.getName(),
         InventoryMoveFeature.Mode.LEGIT.getName()
      )
   );
   private final List<Packet<?>> delayedPackets = new CopyOnWriteArrayList<>();
   private boolean processingPackets = false;
   private boolean movedInGui = false;
   private boolean movementLocked = false;
   private int scriptStep = -1;
   private int stepTimer = 0;

   public InventoryMoveFeature() {
      super("GuiMove", "Walk around while your inventory is open with anti-cheat bypasses", FeatureCategory.MOVEMENT, -1);
   }

   public static InventoryMoveFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(InventoryMoveFeature.class);
   }

   public static Input screenInput() {
      InventoryMoveFeature feature = getEnabled();
      boolean clickGuiOpen = MenuOverlay.isOpen();
      if (mc.player == null) {
         return null;
      } else {
         return clickGuiOpen || feature != null && (feature.screen() instanceof InventoryScreen || feature.screen() instanceof CreativeModeInventoryScreen)
            ? new Input(
               isKeyDown(mc.options.keyUp),
               isKeyDown(mc.options.keyDown),
               isKeyDown(mc.options.keyLeft),
               isKeyDown(mc.options.keyRight),
               isKeyDown(mc.options.keyJump),
               isKeyDown(mc.options.keyShift),
               isKeyDown(mc.options.keySprint)
            )
            : null;
      }
   }

   public static boolean shouldStopMovement() {
      InventoryMoveFeature feature = getEnabled();
      return feature != null && (feature.movementLocked || feature.processingPackets);
   }

   public static boolean isClickPipelineBusy() {
      InventoryMoveFeature feature = getEnabled();
      return feature != null && (feature.processingPackets || !feature.delayedPackets.isEmpty());
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      LocalPlayer player = this.player();
      if (player == null) {
         this.cleanup();
      } else if (!(this.screen() instanceof InventoryScreen) && !(this.screen() instanceof CreativeModeInventoryScreen) && !MenuOverlay.isOpen()) {
         if (!this.processingPackets && this.delayedPackets.isEmpty()) {
            this.movedInGui = false;
         }

         if (this.processingPackets) {
            this.tickScript();
         }
      } else {
         this.movedInGui = this.movedInGui | (this.movementKeysDown() && !this.delayedPackets.isEmpty());
         if (this.processingPackets) {
            this.tickScript();
         }
      }
   }

   @EventTarget
   public void onPacketSend(PacketSendEvent event) {
      LocalPlayer player = this.player();
      if (player != null && event.getPhase() == PacketSendEvent.Phase.PRE) {
         if (!this.mode.is(InventoryMoveFeature.Mode.DEFAULT.getName())) {
            boolean moving = this.movedInGui || this.movementKeysDown();
            this.movedInGui = this.movedInGui | (moving && !this.delayedPackets.isEmpty());
            Packet<?> packet = event.getPacket();
            if (packet instanceof ServerboundContainerClickPacket clickPacket
               && this.screen() instanceof InventoryScreen
               && moving
               && this.shouldAllowMovement()) {
               this.delayedPackets.add(clickPacket);
               event.cancel();
            } else if (packet instanceof ServerboundContainerClosePacket closePacket && closePacket.getContainerId() == 0 && moving && !this.processingPackets) {
               if (this.delayedPackets.isEmpty()) {
                  event.cancel();
               } else {
                  this.delayedPackets.add(closePacket);
                  event.cancel();
                  this.processDelayedPackets();
               }
            }

            if (this.processingPackets && packet instanceof ServerboundPlayerInputPacket) {
               event.cancel();
               if (player.connection != null) {
                  player.connection.send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, false, false)));
               }
            }

            if (!this.delayedPackets.isEmpty()
               && this.processingPackets
               && (
                  packet instanceof ServerboundSwingPacket
                     || packet instanceof ServerboundInteractPacket
                     || packet instanceof ServerboundUseItemPacket
                     || packet instanceof ServerboundUseItemOnPacket
               )) {
               event.cancel();
            }
         }
      }
   }

   private void processDelayedPackets() {
      this.processingPackets = true;
      this.scriptStep = 0;
      this.stepTimer = 0;
   }

   private void tickScript() {
      if (this.processingPackets && this.scriptStep >= 0) {
         LocalPlayer player = this.player();
         if (player != null && player.connection != null) {
            boolean isBypass = this.mode.is(InventoryMoveFeature.Mode.BYPASS.getName());
            if (isBypass) {
               switch (this.scriptStep) {
                  case 0:
                     this.movementLocked = true;
                     this.scriptStep++;
                     break;
                  case 1:
                     for (Packet<?> p : this.delayedPackets) {
                        player.connection.send(p);
                     }

                     this.delayedPackets.clear();
                     this.processingPackets = false;
                     this.movedInGui = false;
                     this.scriptStep++;
                     break;
                  case 2:
                     for (Packet<?> p : this.delayedPackets) {
                        if (p instanceof ServerboundContainerClosePacket) {
                           player.connection.send(p);
                        }
                     }

                     this.movementLocked = false;
                     this.scriptStep = -1;
               }
            } else {
               switch (this.scriptStep) {
                  case 0:
                     this.movementLocked = true;
                     this.scriptStep++;
                     this.stepTimer = 0;
                     break;
                  case 1:
                  case 2:
                     this.stepTimer++;
                     if (this.stepTimer >= 2) {
                        this.scriptStep++;
                        this.stepTimer = 0;
                     }
                     break;
                  case 3:
                     for (Packet<?> p : this.delayedPackets) {
                        if (!(p instanceof ServerboundContainerClosePacket)) {
                           player.connection.send(p);
                        }
                     }

                     this.scriptStep++;
                     this.stepTimer = 0;
                     break;
                  case 4:
                  case 5:
                     this.stepTimer++;
                     if (this.stepTimer >= 2) {
                        this.scriptStep++;
                        this.stepTimer = 0;
                     }
                     break;
                  case 6:
                     for (Packet<?> p : this.delayedPackets) {
                        if (p instanceof ServerboundContainerClosePacket) {
                           player.connection.send(p);
                        }
                     }

                     this.delayedPackets.clear();
                     this.scriptStep++;
                     this.stepTimer = 0;
                     break;
                  case 7:
                     this.movementLocked = false;
                     this.processingPackets = false;
                     this.movedInGui = false;
                     this.scriptStep = -1;
               }
            }
         } else {
            this.cleanup();
         }
      }
   }

   private boolean movementKeysDown() {
      if (mc != null && mc.getWindow() != null && mc.options != null) {
         boolean inventory = this.screen() instanceof InventoryScreen || this.screen() instanceof CreativeModeInventoryScreen;
         KeyMapping[] keys = new KeyMapping[]{
            mc.options.keyUp, mc.options.keyDown, mc.options.keyRight, mc.options.keyLeft, mc.options.keyJump, mc.options.keyShift, mc.options.keySprint
         };

         for (KeyMapping binding : keys) {
            if ((!inventory || binding != mc.options.keyShift && (binding != mc.options.keySprint || mc.options.keyUp.equals(mc.options.keySprint)))
               && isKeyDown(binding)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean shouldAllowMovement() {
      LocalPlayer player = this.player();
      return player != null && player.containerMenu != null && player.containerMenu.slots.size() >= 27;
   }

   private void cleanup() {
      this.delayedPackets.clear();
      this.processingPackets = false;
      this.movedInGui = false;
      this.movementLocked = false;
      this.scriptStep = -1;
      this.stepTimer = 0;
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      this.cleanup();
   }

   @EventTarget
   public void onScreenClose(ScreenCloseEvent event) {
      if (event.getScreen() instanceof InventoryScreen) {
         KeyMapping.releaseAll();
      }
   }

   @Override
   protected void onDisable() {
      LocalPlayer player = this.player();
      if (player != null && player.connection != null) {
         for (Packet<?> p : this.delayedPackets) {
            player.connection.send(p);
         }
      }

      this.cleanup();
   }

   private static boolean isKeyDown(KeyMapping mapping) {
      Key key = ((KeyMappingAccessor)mapping).getKey();
      return key.getType() == Type.MOUSE
         ? GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == 1
         : InputConstants.isKeyDown(mc.getWindow(), key.getValue());
   }

   public enum Mode {
      DEFAULT("Default"),
      BYPASS("Bypass"),
      LEGIT("Legit");

      private final String name;

      Mode(String name) {
         this.name = name;
      }

      public String getName() {
         return this.name;
      }
   }
}
