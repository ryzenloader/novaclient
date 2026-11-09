package win.furryhook.utils;

import win.furryhook.context.RotationContext;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureManager;
import win.furryhook.utils.inventory.HotbarSyncGuard;
import win.furryhook.utils.inventory.InventorySwap;

public final class ClientStateWatchdog {
   public static final ClientStateWatchdog INSTANCE = new ClientStateWatchdog();

   private ClientStateWatchdog() {
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      for (Feature feature : FeatureManager.INSTANCE.getFeatures()) {
         if (feature.isEnabled()) {
            return;
         }
      }

      if (RotationContext.isActive()) {
         RotationContext.clear();
      }

      if (InventorySwap.isBusy()) {
         InventorySwap.abort();
      }

      if (HotbarSyncGuard.isActive()) {
         HotbarSyncGuard.reset();
      }
   }
}
