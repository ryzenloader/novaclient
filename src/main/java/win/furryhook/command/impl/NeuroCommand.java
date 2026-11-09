package win.furryhook.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import win.furryhook.command.ClientCommand;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.impl.combat.AuraFeature;
import win.furryhook.utils.combat.neuro.NeuroDataset;
import win.furryhook.utils.text.ChatUtil;

public final class NeuroCommand extends ClientCommand {
   private static final Logger LOGGER = LoggerFactory.getLogger(NeuroCommand.class);

   public NeuroCommand() {
      super("neuro", "Manages the Neuro aura: learn/save/load/remove/list/clear/dir");
   }

   @Override
   public void build(LiteralArgumentBuilder<Object> builder) {
      builder.executes(context -> {
         this.status();
         return 1;
      });
      builder.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)LiteralArgumentBuilder.literal("learn").executes(context -> this.setLearning(!this.isLearning())))
               .then(LiteralArgumentBuilder.literal("on").executes(context -> this.setLearning(true))))
            .then(LiteralArgumentBuilder.literal("off").executes(context -> this.setLearning(false)))
      );
      builder.then(this.named("save", this::executeSave));
      builder.then(this.named("load", this::executeLoad));
      builder.then(this.named("remove", this::executeRemove));
      builder.then(LiteralArgumentBuilder.literal("list").executes(context -> {
         List<String> names = NeuroDataset.INSTANCE.names();
         ChatUtil.header("Neuro datasets  •  " + names.size());
         names.forEach(name -> ChatUtil.entry(name, name.equals(NeuroDataset.INSTANCE.activeName()) ? "active" : null));
         return 1;
      }));
      builder.then(LiteralArgumentBuilder.literal("clear").executes(context -> this.executeClear()));
      builder.then(LiteralArgumentBuilder.literal("dir").executes(this::executeDir));
   }

   private void status() {
      NeuroDataset dataset = NeuroDataset.INSTANCE;
      ChatUtil.header("Neuro  •  " + dataset.activeName());
      ChatUtil.entry("Samples", dataset.sampleCount() + "  •  " + dataset.frameCount() + " frames");
      ChatUtil.entry("Landed", dataset.hitCount() + " swings ended on a hit");
      ChatUtil.entry("Duels", dataset.duelCount() + " recorded one on one");
      ChatUtil.entry("Smoothness", String.format(Locale.ROOT, "%.2f", dataset.averageSmoothness()));
      ChatUtil.entry("Learning", this.isLearning() ? "recording" : "off");
      if (dataset.isDirty()) {
         ChatUtil.entry("Unsaved", "yes  •  .neuro save " + dataset.activeName());
      }

      ChatUtil.usage("neuro learn [on|off]  •  save <name>  •  load <name>  •  remove <name>  •  list  •  clear  •  dir");
   }

   private boolean isLearning() {
      AuraFeature aura = this.aura();
      return aura != null && aura.neuroLearning.getValue();
   }

   private int setLearning(boolean learning) {
      AuraFeature aura = this.aura();
      if (aura == null) {
         ChatUtil.error("Aura is unavailable");
         return 0;
      }

      aura.neuroLearning.setValue(learning);
      if (learning) {
         ChatUtil.success("Neuro learning on  •  fight with Aura enabled to record swings");
         if (!aura.componentMode.is("Neuro")) {
            ChatUtil.info("Rotation Mode is " + aura.componentMode.getValue() + "  •  switch it to Neuro to record");
         }
      } else {
         ChatUtil.success("Neuro learning off  •  " + NeuroDataset.INSTANCE.sampleCount() + " samples");
      }

      return 1;
   }

   private int executeSave(String name) {
      if (!NeuroDataset.isValidName(name)) {
         ChatUtil.error("Invalid dataset name  •  Use letters, digits, - or _  •  " + name);
         return 0;
      }

      if (!NeuroDataset.INSTANCE.save(name)) {
         ChatUtil.error("Failed to save dataset  •  " + name);
         return 0;
      }

      AuraFeature aura = this.aura();
      if (aura != null) {
         aura.selectNeuroDataset(name);
      }

      ChatUtil.success("Saved dataset  •  " + name + "  •  " + NeuroDataset.INSTANCE.sampleCount() + " samples");
      return 1;
   }

   private int executeLoad(String name) {
      NeuroDataset.INSTANCE.saveIfDirty();
      if (!NeuroDataset.INSTANCE.load(name)) {
         ChatUtil.error("Dataset not found  •  " + name);
         return 0;
      }

      AuraFeature aura = this.aura();
      if (aura != null) {
         aura.selectNeuroDataset(name);
      }

      ChatUtil.success("Loaded dataset  •  " + name + "  •  " + NeuroDataset.INSTANCE.sampleCount() + " samples");
      return 1;
   }

   private int executeRemove(String name) {
      if (!NeuroDataset.INSTANCE.delete(name)) {
         ChatUtil.error("Dataset not found  •  " + name);
         return 0;
      } else {
         ChatUtil.success("Removed dataset  •  " + name);
         return 1;
      }
   }

   private int executeClear() {
      int samples = NeuroDataset.INSTANCE.sampleCount();
      NeuroDataset.INSTANCE.clear();
      ChatUtil.success("Cleared " + samples + " samples from memory");
      ChatUtil.info("File is untouched  •  .neuro load " + NeuroDataset.INSTANCE.activeName() + " to get them back");
      return 1;
   }

   private int executeDir(CommandContext<Object> context) {
      Path directory = NeuroDataset.INSTANCE.directory();

      try {
         Files.createDirectories(directory);
         if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.OPEN)) {
            Desktop.getDesktop().open(directory.toFile());
         } else {
            new ProcessBuilder("explorer.exe", directory.toString()).start();
         }

         ChatUtil.success("Opened neuro directory  •  " + directory);
         return 1;
      } catch (IOException | UnsupportedOperationException exception) {
         LOGGER.warn("Failed to open neuro directory", exception);
         ChatUtil.error("Failed to open neuro directory  •  " + directory);
         return 0;
      }
   }

   private LiteralArgumentBuilder<Object> named(String literal, Function<String, Integer> action) {
      return (LiteralArgumentBuilder<Object>)LiteralArgumentBuilder.literal(literal)
         .then(
            RequiredArgumentBuilder.argument("name", StringArgumentType.word())
               .executes(context -> action.apply(StringArgumentType.getString(context, "name")))
         );
   }

   private AuraFeature aura() {
      return FeatureManager.INSTANCE.getFeature(AuraFeature.class);
   }
}
