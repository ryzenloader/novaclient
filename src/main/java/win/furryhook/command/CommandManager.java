package win.furryhook.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import win.furryhook.command.impl.BindCommand;
import win.furryhook.command.impl.ConfigCommand;
import win.furryhook.command.impl.FriendCommand;
import win.furryhook.command.impl.GpsCommand;
import win.furryhook.command.impl.HelpCommand;
import win.furryhook.command.impl.IconsCommand;
import win.furryhook.command.impl.MacroCommand;
import win.furryhook.command.impl.NbtParserCommand;
import win.furryhook.command.impl.NeuroCommand;
import win.furryhook.command.impl.PrefixCommand;
import win.furryhook.command.impl.RctCommand;
import win.furryhook.command.impl.TpCommand;
import win.furryhook.command.impl.VClipCommand;
import win.furryhook.context.MinecraftContext;
import win.furryhook.event.EventManager;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.input.KeyboardInputEvent;
import win.furryhook.event.events.input.MouseInputEvent;
import win.furryhook.feature.setting.BindSetting;
import win.furryhook.utils.text.ChatUtil;

public final class CommandManager {
   public static final CommandManager INSTANCE = new CommandManager();
   public static final String DEFAULT_PREFIX = ".";
   private final CommandDispatcher<Object> dispatcher = new CommandDispatcher();
   private final List<ClientCommand> commands = new ArrayList<>();
   private final Map<String, Macro> macros = new LinkedHashMap<>();
   private final CommandStore store = new CommandStore();
   private String prefix = ".";
   private boolean initialized;

   private CommandManager() {
   }

   public void initialize() {
      if (!this.initialized) {
         List.of(
               new HelpCommand(this),
               new PrefixCommand(this),
               new BindCommand(),
               new FriendCommand(),
               new MacroCommand(this),
               new GpsCommand(),
               new ConfigCommand(),
               new NbtParserCommand(),
               new NeuroCommand(),
               new RctCommand(),
               new VClipCommand(),
               new IconsCommand(),
               new TpCommand()
            )
            .forEach(this::register);
         this.store.load(this);
         this.initialized = true;
      }
   }

   public List<ClientCommand> getCommands() {
      return Collections.unmodifiableList(this.commands);
   }

   public CommandDispatcher<Object> getDispatcher() {
      return this.dispatcher;
   }

   public String getPrefix() {
      return this.prefix;
   }

   public void setPrefix(String prefix) {
      this.prefix = prefix != null && !prefix.isBlank() ? prefix.trim() : ".";
      this.store.save(this);
   }

   public boolean handleChat(String message) {
      if (this.initialized && message != null && message.startsWith(this.prefix)) {
         String input = message.substring(this.prefix.length()).trim();
         if (input.isEmpty()) {
            return false;
         }

         try {
            this.dispatcher.execute(input, new Object());
         } catch (CommandSyntaxException exception) {
            String detail = exception.getRawMessage() == null ? null : exception.getRawMessage().getString();
            ChatUtil.error(
               detail != null && !detail.isBlank() ? detail + "  •  Try " + this.prefix + "help" : "Unknown command or syntax  •  Try " + this.prefix + "help"
            );
         } catch (Exception exception) {
            ChatUtil.error("Command failed  •  " + exception.getMessage());
         }

         return true;
      } else {
         return false;
      }
   }

   public Collection<Macro> getMacros() {
      return Collections.unmodifiableCollection(this.macros.values());
   }

   public Macro getMacro(String name) {
      return this.macros.get(this.normalize(name));
   }

   public void putMacro(Macro macro) {
      this.macros.put(this.normalize(macro.name()), macro);
      this.store.save(this);
   }

   public boolean removeMacro(String name) {
      boolean removed = this.macros.remove(this.normalize(name)) != null;
      if (removed) {
         this.store.save(this);
      }

      return removed;
   }

   void loadState(String prefix, List<Macro> loadedMacros) {
      this.prefix = prefix != null && !prefix.isBlank() ? prefix : ".";
      this.macros.clear();

      for (Macro macro : loadedMacros) {
         this.macros.put(this.normalize(macro.name()), macro);
      }
   }

   @EventTarget
   public void onKeyboardInput(KeyboardInputEvent event) {
      if (event.getAction() == 1) {
         this.dispatchMacros(BindSetting.key(event.getKey()));
      }
   }

   @EventTarget
   public void onMouseInput(MouseInputEvent event) {
      if (event.getAction() == 1) {
         this.dispatchMacros(BindSetting.mouse(event.getButton()));
      }
   }

   private void dispatchMacros(int bindCode) {
      Minecraft mc = MinecraftContext.mc;
      if (mc.player != null && mc.gui.screen() == null) {
         for (Macro macro : this.macros.values()) {
            if (macro.bindCode() == bindCode) {
               this.send(mc, macro.text());
            }
         }
      }
   }

   private void send(Minecraft mc, String text) {
      if (!this.handleChat(text)) {
         if (text.startsWith("/")) {
            mc.player.connection.sendCommand(text.substring(1));
         } else {
            mc.player.connection.sendChat(text);
         }
      }
   }

   private void register(ClientCommand command) {
      this.registerLiteral(command.name(), command);

      for (String alias : command.aliases()) {
         this.registerLiteral(alias, command);
      }

      this.commands.add(command);
      EventManager.subscribe(command);
   }

   private void registerLiteral(String literal, ClientCommand command) {
      LiteralArgumentBuilder<Object> builder = LiteralArgumentBuilder.literal(literal);
      command.build(builder);
      this.dispatcher.register(builder);
   }

   private String normalize(String value) {
      return value.toLowerCase(Locale.ROOT);
   }
}
