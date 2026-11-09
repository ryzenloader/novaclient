package win.furryhook.mixin.core;

import net.minecraft.client.GameLoadCookie;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import win.furryhook.context.RotationContext;
import win.furryhook.event.EventManager;
import win.furryhook.event.Events;
import win.furryhook.event.events.game.AttackEvent;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.game.TickContext;
import win.furryhook.event.events.lifecycle.ClientStartEvent;
import win.furryhook.event.events.lifecycle.DisconnectEvent;
import win.furryhook.event.events.lifecycle.ResourceReloadEvent;
import win.furryhook.event.events.lifecycle.ShutdownEvent;
import win.furryhook.event.events.lifecycle.WorldJoinEvent;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;
import win.furryhook.event.events.screen.ScreenOpenEvent;
import win.furryhook.feature.impl.movement.TimerFeature;
import win.furryhook.feature.impl.player.FakePlayerFeature;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
   private static final TickContext TICK_CONTEXT = new TickContext();
   @Unique
   private ClientLevel previousLevel;
   @Unique
   private boolean furryhook$runningTimerTick;
   @Shadow
   public ClientLevel level;

   @Inject(method = "onGameLoadFinished", at = @At("TAIL"))
   private void onClientStart(GameLoadCookie gameLoadCookie, CallbackInfo ci) {
      if (EventManager.hasListeners(ClientStartEvent.class)) {
         EventManager.call(Events.CLIENT_START.set((Minecraft)this));
      }
   }

   @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   private void onTick(CallbackInfo ci) {
      if (!this.furryhook$runningTimerTick && TimerFeature.shouldSkipBaseTick()) {
         ci.cancel();
      } else if (EventManager.hasListeners(GameTickEvent.class)) {
         Minecraft client = (Minecraft)this;
         EventManager.call(Events.GAME_TICK.set(client, TICK_CONTEXT.begin(client)));
      }
   }

   @Inject(method = "tick", at = @At("TAIL"))
   private void runTimerTick(CallbackInfo ci) {
      if (!this.furryhook$runningTimerTick && TimerFeature.consumeExtraTick()) {
         this.furryhook$runningTimerTick = true;

         try {
            ((Minecraft)this).tick();
         } finally {
            this.furryhook$runningTimerTick = false;
         }
      }
   }

   @Inject(method = "tick", at = @At("TAIL"))
   private void furryhook$advanceFakePlayer(CallbackInfo ci) {
      FakePlayerFeature fakePlayer = FakePlayerFeature.getEnabled();
      if (fakePlayer != null) {
         fakePlayer.advanceElytraPostTick();
      }
   }

   @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
   private void onAttack(CallbackInfoReturnable<Boolean> cir) {
      if (EventManager.hasListeners(AttackEvent.class)) {
         if (EventManager.call(Events.ATTACK.set((Minecraft)this)).isCancelled()) {
            cir.setReturnValue(false);
         }
      }
   }

   @Inject(method = "setLevel", at = @At("HEAD"))
   private void capturePreviousLevel(ClientLevel level, CallbackInfo ci) {
      this.previousLevel = this.level;
   }

   @Inject(method = "setLevel", at = @At("TAIL"))
   private void onSetLevel(ClientLevel level, CallbackInfo ci) {
      Minecraft client = (Minecraft)this;
      ClientLevel previous = this.previousLevel;
      if (level == null) {
         RotationContext.clear();
      }

      if (previous == null && level != null && EventManager.hasListeners(WorldJoinEvent.class)) {
         EventManager.call(Events.WORLD_JOIN.set(client, level));
      }

      if (previous != null && level == null && EventManager.hasListeners(WorldLeaveEvent.class)) {
         EventManager.call(Events.WORLD_LEAVE.set(client, previous));
      }
   }

   @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
   private void onDisconnect(Screen screen, boolean transferring, boolean resetting, CallbackInfo ci) {
      if (EventManager.hasListeners(DisconnectEvent.class)) {
         EventManager.call(Events.DISCONNECT.set((Minecraft)this, screen, transferring, resetting));
      }
   }

   @Inject(method = "setScreenAndShow", at = @At("HEAD"), cancellable = true)
   private void onSetScreen(Screen screen, CallbackInfo ci) {
      Minecraft client = (Minecraft)this;
      if (screen != null && EventManager.hasListeners(ScreenOpenEvent.class)) {
         if (EventManager.call(Events.SCREEN_OPEN.set(client, screen)).isCancelled()) {
            ci.cancel();
         }
      }
   }

   @Inject(method = "stop", at = @At("HEAD"))
   private void onShutdown(CallbackInfo ci) {
      if (EventManager.hasListeners(ShutdownEvent.class)) {
         EventManager.call(Events.SHUTDOWN.set((Minecraft)this));
      }
   }

   @Inject(method = "onResourceLoadFinished", at = @At("TAIL"))
   private void onResourceReload(GameLoadCookie gameLoadCookie, CallbackInfo ci) {
      if (EventManager.hasListeners(ResourceReloadEvent.class)) {
         EventManager.call(Events.RESOURCE_RELOAD.set((Minecraft)this));
      }
   }
}
