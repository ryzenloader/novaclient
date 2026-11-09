package win.furryhook.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ClientSounds {
   public static final SoundEvent MODULE_ENABLE = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("furryhook", "module_enable"));
   public static final SoundEvent MODULE_DISABLE = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("furryhook", "module_disable"));
   public static final SoundEvent MENU_OPEN = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("furryhook", "menu_open"));
   public static final SoundEvent MENU_CLOSE = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("furryhook", "menu_close"));
   public static final SoundEvent SLIDER = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("furryhook", "slider"));
   public static final SoundEvent UI_ON = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("furryhook", "ui_on"));
   public static final SoundEvent UI_OFF = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("furryhook", "ui_off"));
   private static volatile boolean enabled = true;
   private static final float GUI_VOLUME = 1.6F;

   private ClientSounds() {
   }

   public static void setEnabled(boolean value) {
      enabled = value;
   }

   public static void playModuleToggle(boolean moduleEnabled) {
      play(moduleEnabled ? MODULE_ENABLE : MODULE_DISABLE);
   }

   public static void playMenuOpen() {
      play(MENU_OPEN);
   }

   public static void playMenuClose() {
      play(MENU_CLOSE);
   }

   public static void playSlider() {
      play(SLIDER);
   }

   public static void playUiOn() {
      play(UI_ON);
   }

   public static void playUiOff() {
      play(UI_OFF);
   }

   private static void play(SoundEvent event) {
      if (enabled) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, 1.6F));
         }
      }
   }
}
