package win.furryhook.feature.impl.visual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.MultiSelectSetting;

public class ItemReplacerFeature extends Feature {
   private static volatile ItemReplacerFeature.SwordVariant[] swordVariants;
   private static volatile Map<String, ItemReplacerFeature.SwordVariant> variantsByLabel;
   private static ItemReplacerFeature instance;
   private static final ThreadLocal<ItemStack> PENDING_STACK = new ThreadLocal<>();
   private static final ThreadLocal<ItemDisplayContext> PENDING_MODE = new ThreadLocal<>();
   private static final ThreadLocal<String> PENDING_VARIANT = new ThreadLocal<>();
   private final MultiSelectSetting swordTypes;
   private final ModeSetting diamondSwordModel;
   private final ModeSetting netheriteSwordModel;
   private final ModeSetting diamondAxeModel;
   private final ModeSetting netheriteAxeModel;
   private String cachedSelectedLabel;
   private ItemReplacerFeature.SwordVariant cachedSelectedVariant;

   private static void clearPending() {
      PENDING_STACK.remove();
      PENDING_MODE.remove();
      PENDING_VARIANT.remove();
   }

   public ItemReplacerFeature() {
      super("ItemReplacer", "Замены на Меч", FeatureCategory.VISUAL, 0);
      ItemReplacerFeature.SwordVariant[] variants = swordVariants();
      this.cachedSelectedVariant = variants[0];
      this.swordTypes = this.register(
         new MultiSelectSetting(
            "Заменять", List.of("Алмазный меч", "Незеритовый меч"), "Алмазный меч", "Незеритовый меч", "Алмазный топор", "Незеритовый топор"
         )
      );
      this.diamondSwordModel = this.register(this.createModelSetting("Модель алм. меча", "Алмазный меч", variants));
      this.netheriteSwordModel = this.register(this.createModelSetting("Модель нез. меча", "Незеритовый меч", variants));
      this.diamondAxeModel = this.register(this.createModelSetting("Модель алм. топора", "Алмазный топор", variants));
      this.netheriteAxeModel = this.register(this.createModelSetting("Модель нез. топора", "Незеритовый топор", variants));
      instance = this;
   }

   private ModeSetting createModelSetting(String name, String typeKey, ItemReplacerFeature.SwordVariant[] variants) {
      return new ModeSetting(name, variants[0].label, getVariantLabels(variants)).visibleWhen(() -> this.swordTypes.isSelected(typeKey));
   }

   public static boolean captureForRender(ItemStack stack, ItemDisplayContext transformType, @Nullable ItemOwner owner) {
      clearPending();
      if (stack == null || stack.isEmpty() || owner == null || transformType == null) {
         return false;
      }

      if (transformType == ItemDisplayContext.GUI) {
         return false;
      }

      ItemReplacerFeature replacer = instance;
      if (replacer != null && replacer.isEnabled()) {
         String variantOverride = replacer.resolveVariantLabel(owner, stack);
         if (variantOverride == null) {
            return false;
         }

         PENDING_STACK.set(stack);
         PENDING_MODE.set(transformType);
         PENDING_VARIANT.set(variantOverride);
         return true;
      } else {
         return false;
      }
   }

   public static boolean renderPending(PoseStack poseStack, SubmitNodeCollector collector, int combinedLight, int combinedOverlay) {
      ItemStack stack = PENDING_STACK.get();
      ItemDisplayContext transformType = PENDING_MODE.get();
      String variantLabel = PENDING_VARIANT.get();
      clearPending();
      if (stack != null && transformType != null && variantLabel != null && transformType != ItemDisplayContext.GUI) {
         ItemReplacerFeature replacer = instance;
         if (replacer == null || !replacer.isEnabled()) {
            return false;
         } else {
            return collector instanceof SubmitNodeStorage sink
               ? replacer.resolveVariant(variantLabel).getModel().render(stack, transformType, poseStack, sink, combinedLight, combinedOverlay)
               : false;
         }
      } else {
         return false;
      }
   }

   @Nullable
   private String resolveVariantLabel(ItemOwner owner, ItemStack stack) {
      Minecraft client = Minecraft.getInstance();
      LivingEntity entity = owner.asLivingEntity();
      return client.player != null && entity != null && entity == client.player && this.shouldReplace(stack) ? this.getModelLabelFor(stack.getItem()) : null;
   }

   @Nullable
   private String getModelLabelFor(Item item) {
      if (item == Items.DIAMOND_SWORD && this.swordTypes.isSelected("Алмазный меч")) {
         return this.diamondSwordModel.getValue();
      } else if (item == Items.NETHERITE_SWORD && this.swordTypes.isSelected("Незеритовый меч")) {
         return this.netheriteSwordModel.getValue();
      } else if (item == Items.DIAMOND_AXE && this.swordTypes.isSelected("Алмазный топор")) {
         return this.diamondAxeModel.getValue();
      } else {
         return item == Items.NETHERITE_AXE && this.swordTypes.isSelected("Незеритовый топор") ? this.netheriteAxeModel.getValue() : null;
      }
   }

   private ItemReplacerFeature.SwordVariant resolveVariant(@Nullable String labelOverride) {
      if (labelOverride != null && !labelOverride.isBlank()) {
         ItemReplacerFeature.SwordVariant variant = variantsByLabel().get(labelOverride.toLowerCase(Locale.ROOT));
         return variant != null ? variant : this.getSelectedVariant();
      } else {
         return this.getSelectedVariant();
      }
   }

   private boolean shouldReplace(ItemStack stack) {
      return this.getModelLabelFor(stack.getItem()) != null;
   }

   public ItemReplacerFeature.SwordVariant getSelectedVariant() {
      return this.getSelectedVariant(this.diamondSwordModel.getValue());
   }

   public ItemReplacerFeature.SwordVariant getSelectedVariant(@Nullable String label) {
      if (label == null || label.isBlank()) {
         return swordVariants()[0];
      }

      if (label.equalsIgnoreCase(this.cachedSelectedLabel)) {
         return this.cachedSelectedVariant;
      }

      ItemReplacerFeature.SwordVariant variant = variantsByLabel().get(label.toLowerCase(Locale.ROOT));
      if (variant == null) {
         variant = swordVariants()[0];
      }

      this.cachedSelectedLabel = label;
      this.cachedSelectedVariant = variant;
      return variant;
   }

   @Override
   protected void onDisable() {
      this.clearLoadedModels();
      clearPending();
      super.onDisable();
   }

   public void clearLoadedModels() {
      for (ItemReplacerFeature.SwordVariant variant : swordVariants()) {
         variant.clear();
      }
   }

   public static String[] getVariantLabels() {
      return getVariantLabels(swordVariants());
   }

   private static String[] getVariantLabels(ItemReplacerFeature.SwordVariant[] variants) {
      String[] labels = new String[variants.length];

      for (int i = 0; i < variants.length; i++) {
         labels[i] = variants[i].label;
      }

      return labels;
   }

   private static ItemReplacerFeature.SwordVariant[] swordVariants() {
      ItemReplacerFeature.SwordVariant[] cached = swordVariants;
      if (cached != null) {
         return cached;
      }

      synchronized (ItemReplacerFeature.class) {
         cached = swordVariants;
         if (cached == null) {
            cached = createSwordVariants();
            swordVariants = cached;
         }

         return cached;
      }
   }

   private static Map<String, ItemReplacerFeature.SwordVariant> variantsByLabel() {
      Map<String, ItemReplacerFeature.SwordVariant> cached = variantsByLabel;
      if (cached != null) {
         return cached;
      }

      synchronized (ItemReplacerFeature.class) {
         cached = variantsByLabel;
         if (cached == null) {
            variantsByLabel = cached = createVariantMap(swordVariants());
         }

         return cached;
      }
   }

   private static Map<String, ItemReplacerFeature.SwordVariant> createVariantMap(ItemReplacerFeature.SwordVariant[] variants) {
      HashMap<String, ItemReplacerFeature.SwordVariant> map = new HashMap<>(variants.length * 2);

      for (ItemReplacerFeature.SwordVariant variant : variants) {
         map.put(variant.label.toLowerCase(Locale.ROOT), variant);
      }

      return map;
   }

   private static ItemReplacerFeature.SwordVariant[] createSwordVariants() {
      String[][] data = new String[][]{
         {"Abominable Blade", "abominableblade"},
         {"Abominable Greatsaber", "abominablegreatsaber"},
         {"Abominable Scythe", "abominablescythe"},
         {"Acid Demon", "aciddemon"},
         {"Amethyst Shuriken", "amethyst_shuriken"},
         {"Ancient Royal Greatsword", "ancient_royal_great_sword"},
         {"Aquantic Sacred Blade", "aquantic_sacred_blade"},
         {"Aquantic Trident", "aquantictrident"},
         {"Arcanethyst", "arcanethyst"},
         {"Ashura Blade", "ashura_blade"},
         {"Awakened Lichblade", "awakened_lichblade"},
         {"Blood Edge", "bloodedge"},
         {"Bloody Death", "bloodydeath"},
         {"Bramblethorn", "bramblethorn"},
         {"Brimstone Claymore", "brimstone_claymore"},
         {"Carian Sword", "cariansword"},
         {"Chrono Blade", "chrono_blade"},
         {"Corrupted Mythic Blade", "corruptedmythicblade"},
         {"Creation Splitter", "creationsplitter"},
         {"Crescent Rose", "crescentrose"},
         {"Cyber Katana", "cyberkatana"},
         {"Cyber Mantis Blade", "cybermantisblade"},
         {"Cybernetic Katana", "cybernetickatana"},
         {"Cybernetic Knife", "cyberneticknife"},
         {"Cybernetic Sawblade", "cyberneticsawblade"},
         {"Cyber Sword", "cybersword"},
         {"Dainsleif", "dainsleif"},
         {"Dark Blade", "dark_blade"},
         {"Dark Cleaver", "dark_cleaver"},
         {"Death Knight Dagger", "death_knight_dagger"},
         {"Death Knight Sword", "death_knight_sword"},
         {"Demigod's Unholy Blade", "demigodsunholyblade"},
         {"Demigod's Unholy Halberd", "demigodsunholyhalberd"},
         {"Demonic Blade", "demonicblade"},
         {"Demonic Cleaver", "demoniccleaver"},
         {"Demon Lord's Great Axe", "demonlordsgreataxe"},
         {"Demon Lord's Sword", "demonlordsword"},
         {"Divine Justice", "divine_justice"},
         {"Divine Reaper", "divine_reaper"},
         {"Divine Axe Rhitta", "divineaxerhitta"},
         {"Divine Punisher", "divinepunisher"},
         {"Dragon Slaying Blade", "dragonslayingblade"},
         {"Edge of the Astral Plane", "edgeoftheastralplane"},
         {"Emberblade", "emberblade"},
         {"Enigma", "enigma"},
         {"Epic Sword", "epicsword"},
         {"Estoc", "estoc"},
         {"Excalibur", "excalibur"},
         {"Fallen God Spear", "fallengodspear"},
         {"Fallen God Sword", "fallengodsword"},
         {"Floral Longsword", "floral_longsword"},
         {"Floral Sabre", "floral_sabre"},
         {"Forest Guardian Glaive", "forest_guardian_glaive"},
         {"Frost Axe", "frostaxe"},
         {"Frostblade", "frostblade"},
         {"Frost Scythe", "frostscythe"},
         {"Green Scythe", "greenscythe"},
         {"Hearthflame", "hearthflame"},
         {"Hero Sword", "herosword"},
         {"Holy Moonlight Sword", "holymoonlightsword"},
         {"Hornet's Needle", "hornetsneedle"},
         {"Ice Whisper", "icewhisper"},
         {"Jade Halberd", "jadehalberd"},
         {"Katana", "katana"},
         {"Legendary Sword", "legendarysword"},
         {"Longsword", "longsword"},
         {"Magi Scythe", "magiscythe"},
         {"Masamune", "masamune"},
         {"Mjolnir", "mjolnir"},
         {"Molten Blade", "moltenblade"},
         {"Molten Sword", "moltensword"},
         {"Muramasa", "muramasa"},
         {"Mystical Spellblade", "mysticalspellblade"},
         {"Mythic Blade", "mythicblade"},
         {"Partisan", "partisan"},
         {"Pharaoh's Treasure", "pharaohs_treasure"},
         {"Phoenix Grace", "pheonixgrace"},
         {"Powerfuse Hammer", "powerfusehammer"},
         {"Powerfuse Sword", "powerfusesword"},
         {"Requiem of Hell", "requiem_of_hell"},
         {"Ribbon Cleaver", "ribboncleaver"},
         {"Righteous Relic", "righteous_relic"},
         {"Rivers of Blood", "riversofblood"},
         {"Royal Chakram", "royalchakram"},
         {"Royal Rapier", "royalrapier"},
         {"Sabre", "sabre"},
         {"Scissor Blade", "scissorblade"},
         {"Sculk Cleaver", "sculkcleaver"},
         {"Sculk Scythe", "sculkscythe"},
         {"Sculk Sword", "sculksword"},
         {"Sentinel's Will", "sentinels_will"},
         {"Silverine Blade", "silverine_blade"},
         {"Soul Claws", "soulclaws"},
         {"Soul Edge", "souledge"},
         {"Soul Harvester", "soulharvester"},
         {"Soul Render", "soulrender"},
         {"Soul Stealer", "soulstealer"},
         {"Soul Collector", "soul_collector"},
         {"Soul Devourer", "soul_devourer"},
         {"Star's Edge", "stars_edge"},
         {"Steel Sword", "steelsword"},
         {"Stop Sign", "stop_sign"},
         {"Stormbringer", "stormbringer"},
         {"Storm's Edge", "storms_edge"},
         {"Sunbreak", "sunbreak"},
         {"Tengen's Blade", "tengensblade"},
         {"Terrablade", "terrablade"},
         {"Thousand Demon Daggers", "thousanddemondaggers"},
         {"Thunderbrand", "thunderbrand"},
         {"Thunderbringer", "thunderbringer"},
         {"Toxic Longsword", "toxic_longsword"},
         {"Vampiric Needle", "vampiricneedle"},
         {"Wakizashi", "wakizashi"},
         {"Watcher Claymore", "watcher_claymore"},
         {"Watching Warglaive", "watching_warglaive"},
         {"Waxweaver", "waxweaver"},
         {"Whisperwind", "whisperwind"},
         {"Wickpiercer", "wickpiercer"},
         {"Yoru", "yoru"}
      };
      ItemReplacerFeature.SwordVariant[] variants = new ItemReplacerFeature.SwordVariant[data.length];

      for (int i = 0; i < data.length; i++) {
         variants[i] = new ItemReplacerFeature.SwordVariant(data[i][0], data[i][1]);
      }

      return variants;
   }

   private static final class DisplayTransform {
      private final float txN;
      private final float txM;
      private final float ty;
      private final float tz;
      private final float rx;
      private final float ry;
      private final float rz;
      private final float sx;
      private final float sy;
      private final float sz;
      private final boolean hasTrans;
      private final boolean hasRot;
      private final boolean hasScale;

      private DisplayTransform(float[] rotation, float[] translation, float[] scale) {
         this.txN = translation[0] / 16.0F;
         this.txM = -this.txN;
         this.ty = translation[1] / 16.0F;
         this.tz = translation[2] / 16.0F;
         this.rx = rotation[0];
         this.ry = rotation[1];
         this.rz = rotation[2];
         this.sx = scale[0];
         this.sy = scale[1];
         this.sz = scale[2];
         this.hasTrans = translation[0] != 0.0F || translation[1] != 0.0F || translation[2] != 0.0F;
         this.hasRot = rotation[0] != 0.0F || rotation[1] != 0.0F || rotation[2] != 0.0F;
         this.hasScale = scale[0] != 1.0F || scale[1] != 1.0F || scale[2] != 1.0F;
      }

      private void apply(PoseStack ms, boolean leftHand) {
         if (this.hasTrans) {
            ms.translate(leftHand ? this.txM : this.txN, this.ty, this.tz);
         }

         if (this.hasRot) {
            float yaw = leftHand ? -this.ry : this.ry;
            float pitch = leftHand ? -this.rx : this.rx;
            float roll = this.rz;
            if (yaw != 0.0F) {
               ms.mulPose(new Quaternionf().rotationY((float)Math.toRadians(yaw)));
            }

            if (pitch != 0.0F) {
               ms.mulPose(new Quaternionf().rotationX((float)Math.toRadians(pitch)));
            }

            if (roll != 0.0F) {
               ms.mulPose(new Quaternionf().rotationZ((float)Math.toRadians(roll)));
            }
         }

         if (this.hasScale) {
            ms.scale(this.sx, this.sy, this.sz);
         }
      }
   }

   private static final class FaceUv {
      final float u1;
      final float v1;
      final float u2;
      final float v2;

      FaceUv(float u1, float v1, float u2, float v2) {
         this.u1 = u1;
         this.v1 = v1;
         this.u2 = u2;
         this.v2 = v2;
      }
   }

   private static final class ModelCube {
      static final int ROTATE_X = 1;
      static final int ROTATE_Y = 2;
      static final int ROTATE_Z = 3;
      final float x1;
      final float y1;
      final float z1;
      final float x2;
      final float y2;
      final float z2;
      ItemReplacerFeature.FaceUv north;
      ItemReplacerFeature.FaceUv south;
      ItemReplacerFeature.FaceUv east;
      ItemReplacerFeature.FaceUv west;
      ItemReplacerFeature.FaceUv up;
      ItemReplacerFeature.FaceUv down;
      float rotationOriginX;
      float rotationOriginY;
      float rotationOriginZ;
      int rotationAxis = 0;
      float rotationSin;
      float rotationCos = 1.0F;

      ModelCube(float x1, float y1, float z1, float x2, float y2, float z2) {
         this.x1 = x1;
         this.y1 = y1;
         this.z1 = z1;
         this.x2 = x2;
         this.y2 = y2;
         this.z2 = z2;
      }

      int countFaces() {
         int n = 0;
         if (this.north != null) {
            n++;
         }

         if (this.south != null) {
            n++;
         }

         if (this.east != null) {
            n++;
         }

         if (this.west != null) {
            n++;
         }

         if (this.up != null) {
            n++;
         }

         if (this.down != null) {
            n++;
         }

         return n;
      }

      void setRotation(String axis, float angle) {
         switch (axis) {
            case "x":
               this.rotationAxis = 1;
               break;
            case "y":
               this.rotationAxis = 2;
               break;
            case "z":
               this.rotationAxis = 3;
               break;
            default:
               this.rotationAxis = 0;
               return;
         }

         float rad = (float)Math.toRadians(angle);
         this.rotationSin = (float)Math.sin(rad);
         this.rotationCos = (float)Math.cos(rad);
      }
   }

   private static final class RuntimeSwordModel {
      private static final String MODEL_ROOT = "item_replacer/sword/models/";
      private static final String TEXTURE_ROOT = "item_replacer/sword/textures/";
      private static final String NAMESPACE = "mre";
      private static final float PIXEL = 0.0625F;
      private static final float MODEL_CENTER = 0.5F;
      private static final int VERTEX_STRIDE = 8;
      private final String modelPath;
      private final ArrayList<ItemReplacerFeature.ModelCube> cubes = new ArrayList<>();
      private final ItemReplacerFeature.DisplayTransform[] displayTransforms = new ItemReplacerFeature.DisplayTransform[8];
      private Identifier textureId;
      private RenderType renderLayer;
      private float[] bakedMesh = new float[0];
      private int vertexCount;
      private boolean loaded;

      private RuntimeSwordModel(String modelName) {
         this.modelPath = "item_replacer/sword/models/" + modelName + ".json";
         this.textureId = Identifier.fromNamespaceAndPath("mre", "item_replacer/sword/textures/" + modelName + ".png");
      }

      private void clear() {
         this.loaded = false;
         this.textureId = null;
         this.renderLayer = null;
         this.bakedMesh = new float[0];
         this.vertexCount = 0;
         this.cubes.clear();

         for (int i = 0; i < this.displayTransforms.length; i++) {
            this.displayTransforms[i] = null;
         }
      }

      private boolean render(
         ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack, SubmitNodeStorage sink, int combinedLight, int combinedOverlay
      ) {
         this.loadIfNeeded();
         if (this.vertexCount != 0 && this.renderLayer != null) {
            poseStack.pushPose();
            this.applyDisplayTransform(poseStack, transformType);
            float[] mesh = this.bakedMesh;
            int light = combinedLight;
            if (stack.hasFoil()) {
               RenderType glintLayer = RenderTypes.entityGlint();
               sink.submitCustomGeometry(
                  poseStack, glintLayer, (pose, consumer) -> drawMesh(mesh, consumer, pose.pose(), pose.normal(), light, combinedOverlay)
               );
            }

            RenderType base = this.renderLayer;
            sink.submitCustomGeometry(poseStack, base, (pose, consumer) -> drawMesh(mesh, consumer, pose.pose(), pose.normal(), light, combinedOverlay));
            poseStack.popPose();
            return true;
         } else {
            return false;
         }
      }

      private Minecraft mc() {
         return Minecraft.getInstance();
      }

      private void loadIfNeeded() {
         if (!this.loaded) {
            this.loaded = true;

            try (
               InputStream input = this.mc().getResourceManager().open(Identifier.fromNamespaceAndPath("mre", this.modelPath));
               Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            ) {
               JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
               this.readModel(root);
            } catch (Exception e) {
               System.err.println("[ItemReplacer] Failed to load model " + this.modelPath + ": " + e.getMessage());
            }
         }
      }

      private void readModel(JsonObject root) {
         JsonObject texturesObj = root.getAsJsonObject("textures");
         if (texturesObj != null) {
            HashMap<String, String> textureMap = new HashMap<>();

            for (Entry<String, JsonElement> e : texturesObj.entrySet()) {
               textureMap.put(e.getKey(), e.getValue().getAsString());
            }

            String primaryKey = this.resolvePrimaryTextureKey(textureMap, root);
            String primaryPath = textureMap.get(primaryKey);
            if (primaryPath != null) {
               String texName = primaryPath.contains("/") ? primaryPath.substring(primaryPath.lastIndexOf(47) + 1) : primaryPath;
               Identifier modelTexture = Identifier.fromNamespaceAndPath("mre", "item_replacer/sword/textures/" + texName + ".png");
               if (this.textureExists(modelTexture)) {
                  this.textureId = modelTexture;
               }

               this.renderLayer = this.hasPartialAlpha(this.textureId) ? RenderTypes.itemTranslucent(this.textureId) : RenderTypes.itemCutout(this.textureId);
               JsonArray elements = root.getAsJsonArray("elements");
               if (elements != null) {
                  String primaryRef = "#" + primaryKey;

                  for (JsonElement el : elements) {
                     JsonObject obj = el.getAsJsonObject();
                     JsonArray from = obj.getAsJsonArray("from");
                     JsonArray to = obj.getAsJsonArray("to");
                     if (from != null && to != null) {
                        ItemReplacerFeature.ModelCube cube = new ItemReplacerFeature.ModelCube(
                           from.get(0).getAsFloat() * 0.0625F,
                           from.get(1).getAsFloat() * 0.0625F,
                           from.get(2).getAsFloat() * 0.0625F,
                           to.get(0).getAsFloat() * 0.0625F,
                           to.get(1).getAsFloat() * 0.0625F,
                           to.get(2).getAsFloat() * 0.0625F
                        );
                        JsonObject faces = obj.getAsJsonObject("faces");
                        if (faces != null && this.readFaces(cube, faces, primaryRef)) {
                           if (obj.has("rotation")) {
                              this.readRotation(cube, obj.getAsJsonObject("rotation"));
                           }

                           this.cubes.add(cube);
                        }
                     }
                  }

                  this.bakeMesh();
                  this.readDisplayTransforms(root);
               }
            }
         }
      }

      private boolean readFaces(ItemReplacerFeature.ModelCube cube, JsonObject faces, String primaryRef) {
         boolean has = false;
         if (faces.has("north")) {
            cube.north = this.readFace(faces.getAsJsonObject("north"), primaryRef);
            has |= cube.north != null;
         }

         if (faces.has("south")) {
            cube.south = this.readFace(faces.getAsJsonObject("south"), primaryRef);
            has |= cube.south != null;
         }

         if (faces.has("east")) {
            cube.east = this.readFace(faces.getAsJsonObject("east"), primaryRef);
            has |= cube.east != null;
         }

         if (faces.has("west")) {
            cube.west = this.readFace(faces.getAsJsonObject("west"), primaryRef);
            has |= cube.west != null;
         }

         if (faces.has("up")) {
            cube.up = this.readFace(faces.getAsJsonObject("up"), primaryRef);
            has |= cube.up != null;
         }

         if (faces.has("down")) {
            cube.down = this.readFace(faces.getAsJsonObject("down"), primaryRef);
            has |= cube.down != null;
         }

         return has;
      }

      @Nullable
      private ItemReplacerFeature.FaceUv readFace(JsonObject face, String primaryRef) {
         if (face.has("texture") && !face.get("texture").getAsString().equals(primaryRef)) {
            return null;
         }

         JsonArray uv = face.getAsJsonArray("uv");
         return uv != null && uv.size() >= 4
            ? new ItemReplacerFeature.FaceUv(
               uv.get(0).getAsFloat() * 0.0625F, uv.get(1).getAsFloat() * 0.0625F, uv.get(2).getAsFloat() * 0.0625F, uv.get(3).getAsFloat() * 0.0625F
            )
            : null;
      }

      private void readRotation(ItemReplacerFeature.ModelCube cube, JsonObject rotation) {
         if (rotation != null && rotation.has("origin") && rotation.has("axis") && rotation.has("angle")) {
            JsonArray origin = rotation.getAsJsonArray("origin");
            cube.rotationOriginX = origin.get(0).getAsFloat() * 0.0625F;
            cube.rotationOriginY = origin.get(1).getAsFloat() * 0.0625F;
            cube.rotationOriginZ = origin.get(2).getAsFloat() * 0.0625F;
            cube.setRotation(rotation.get("axis").getAsString(), rotation.get("angle").getAsFloat());
         }
      }

      private String resolvePrimaryTextureKey(Map<String, String> textureMap, JsonObject root) {
         if (textureMap.containsKey("0")) {
            return "0";
         }

         HashMap<String, Integer> useCount = new HashMap<>();
         JsonArray elements = root.getAsJsonArray("elements");
         if (elements != null) {
            for (JsonElement el : elements) {
               JsonObject faces = el.getAsJsonObject().getAsJsonObject("faces");
               if (faces != null) {
                  for (Entry<String, JsonElement> fe : faces.entrySet()) {
                     JsonObject f = fe.getValue().getAsJsonObject();
                     if (f.has("texture")) {
                        String ref = f.get("texture").getAsString().replace("#", "");
                        useCount.merge(ref, 1, Integer::sum);
                     }
                  }
               }
            }
         }

         String best = null;
         int bestCount = -1;

         for (Entry<String, Integer> e : useCount.entrySet()) {
            if (textureMap.containsKey(e.getKey()) && !"particle".equals(e.getKey()) && e.getValue() > bestCount) {
               best = e.getKey();
               bestCount = e.getValue();
            }
         }

         if (best != null) {
            return best;
         }

         for (String key : textureMap.keySet()) {
            if (!"particle".equals(key)) {
               return key;
            }
         }

         return textureMap.keySet().iterator().next();
      }

      private boolean textureExists(Identifier id) {
         try (InputStream in = this.mc().getResourceManager().open(id)) {
            return true;
         } catch (Exception e) {
            return false;
         }
      }

      private boolean hasPartialAlpha(Identifier id) {
         try (InputStream in = this.mc().getResourceManager().open(id)) {
            NativeImage image = NativeImage.read(in);
            if (image == null) {
               return false;
            }

            try {
               for (int y = 0; y < image.getHeight(); y++) {
                  for (int x = 0; x < image.getWidth(); x++) {
                     int alpha = image.getPixel(x, y) >> 24 & 0xFF;
                     if (alpha > 0 && alpha < 255) {
                        return true;
                     }
                  }
               }
            } finally {
               image.close();
            }

            return false;
         } catch (Exception e) {
            return false;
         }
      }

      private void bakeMesh() {
         int faceCount = 0;

         for (ItemReplacerFeature.ModelCube c : this.cubes) {
            faceCount += c.countFaces();
         }

         float[] baked = new float[faceCount * 4 * 8];
         int offset = 0;

         for (ItemReplacerFeature.ModelCube c : this.cubes) {
            offset = this.bakeCube(baked, offset, c);
         }

         this.bakedMesh = baked;
         this.vertexCount = offset / 8;
         this.cubes.clear();
         this.cubes.trimToSize();
      }

      private int bakeCube(float[] out, int offset, ItemReplacerFeature.ModelCube cube) {
         float x1 = cube.x1;
         float y1 = cube.y1;
         float z1 = cube.z1;
         float x2 = cube.x2;
         float y2 = cube.y2;
         float z2 = cube.z2;
         if (cube.north != null) {
            ItemReplacerFeature.FaceUv f = cube.north;
            offset = this.writeBakedVertex(out, offset, cube, x2, y1, z1, f.u1, f.v2, 0.0F, 0.0F, -1.0F);
            offset = this.writeBakedVertex(out, offset, cube, x1, y1, z1, f.u2, f.v2, 0.0F, 0.0F, -1.0F);
            offset = this.writeBakedVertex(out, offset, cube, x1, y2, z1, f.u2, f.v1, 0.0F, 0.0F, -1.0F);
            offset = this.writeBakedVertex(out, offset, cube, x2, y2, z1, f.u1, f.v1, 0.0F, 0.0F, -1.0F);
         }

         if (cube.south != null) {
            ItemReplacerFeature.FaceUv f = cube.south;
            offset = this.writeBakedVertex(out, offset, cube, x1, y1, z2, f.u1, f.v2, 0.0F, 0.0F, 1.0F);
            offset = this.writeBakedVertex(out, offset, cube, x2, y1, z2, f.u2, f.v2, 0.0F, 0.0F, 1.0F);
            offset = this.writeBakedVertex(out, offset, cube, x2, y2, z2, f.u2, f.v1, 0.0F, 0.0F, 1.0F);
            offset = this.writeBakedVertex(out, offset, cube, x1, y2, z2, f.u1, f.v1, 0.0F, 0.0F, 1.0F);
         }

         if (cube.east != null) {
            ItemReplacerFeature.FaceUv f = cube.east;
            offset = this.writeBakedVertex(out, offset, cube, x2, y1, z2, f.u1, f.v2, 1.0F, 0.0F, 0.0F);
            offset = this.writeBakedVertex(out, offset, cube, x2, y1, z1, f.u2, f.v2, 1.0F, 0.0F, 0.0F);
            offset = this.writeBakedVertex(out, offset, cube, x2, y2, z1, f.u2, f.v1, 1.0F, 0.0F, 0.0F);
            offset = this.writeBakedVertex(out, offset, cube, x2, y2, z2, f.u1, f.v1, 1.0F, 0.0F, 0.0F);
         }

         if (cube.west != null) {
            ItemReplacerFeature.FaceUv f = cube.west;
            offset = this.writeBakedVertex(out, offset, cube, x1, y1, z1, f.u1, f.v2, -1.0F, 0.0F, 0.0F);
            offset = this.writeBakedVertex(out, offset, cube, x1, y1, z2, f.u2, f.v2, -1.0F, 0.0F, 0.0F);
            offset = this.writeBakedVertex(out, offset, cube, x1, y2, z2, f.u2, f.v1, -1.0F, 0.0F, 0.0F);
            offset = this.writeBakedVertex(out, offset, cube, x1, y2, z1, f.u1, f.v1, -1.0F, 0.0F, 0.0F);
         }

         if (cube.up != null) {
            ItemReplacerFeature.FaceUv f = cube.up;
            offset = this.writeBakedVertex(out, offset, cube, x1, y2, z1, f.u1, f.v1, 0.0F, 1.0F, 0.0F);
            offset = this.writeBakedVertex(out, offset, cube, x1, y2, z2, f.u1, f.v2, 0.0F, 1.0F, 0.0F);
            offset = this.writeBakedVertex(out, offset, cube, x2, y2, z2, f.u2, f.v2, 0.0F, 1.0F, 0.0F);
            offset = this.writeBakedVertex(out, offset, cube, x2, y2, z1, f.u2, f.v1, 0.0F, 1.0F, 0.0F);
         }

         if (cube.down != null) {
            ItemReplacerFeature.FaceUv f = cube.down;
            offset = this.writeBakedVertex(out, offset, cube, x1, y1, z2, f.u1, f.v1, 0.0F, -1.0F, 0.0F);
            offset = this.writeBakedVertex(out, offset, cube, x1, y1, z1, f.u1, f.v2, 0.0F, -1.0F, 0.0F);
            offset = this.writeBakedVertex(out, offset, cube, x2, y1, z1, f.u2, f.v2, 0.0F, -1.0F, 0.0F);
            offset = this.writeBakedVertex(out, offset, cube, x2, y1, z2, f.u2, f.v1, 0.0F, -1.0F, 0.0F);
         }

         return offset;
      }

      private int writeBakedVertex(
         float[] out, int offset, ItemReplacerFeature.ModelCube cube, float x, float y, float z, float u, float v, float nx, float ny, float nz
      ) {
         if (cube.rotationAxis != 0) {
            float dx = x - cube.rotationOriginX;
            float dy = y - cube.rotationOriginY;
            float dz = z - cube.rotationOriginZ;
            float sin = cube.rotationSin;
            float cos = cube.rotationCos;
            switch (cube.rotationAxis) {
               case 1:
                  y = cube.rotationOriginY + dy * cos - dz * sin;
                  z = cube.rotationOriginZ + dy * sin + dz * cos;
                  float rny = ny * cos - nz * sin;
                  nz = ny * sin + nz * cos;
                  ny = rny;
                  break;
               case 2: {
                  x = cube.rotationOriginX + dx * cos + dz * sin;
                  z = cube.rotationOriginZ - dx * sin + dz * cos;
                  float rnx = nx * cos + nz * sin;
                  nz = -nx * sin + nz * cos;
                  nx = rnx;
                  break;
               }
               case 3: {
                  x = cube.rotationOriginX + dx * cos - dy * sin;
                  y = cube.rotationOriginY + dx * sin + dy * cos;
                  float rnx = nx * cos - ny * sin;
                  ny = nx * sin + ny * cos;
                  nx = rnx;
               }
            }
         }

         out[offset++] = x - 0.5F;
         out[offset++] = y - 0.5F;
         out[offset++] = z - 0.5F;
         out[offset++] = u;
         out[offset++] = v;
         out[offset++] = nx;
         out[offset++] = ny;
         out[offset++] = nz;
         return offset;
      }

      private void readDisplayTransforms(JsonObject root) {
         if (root.has("display")) {
            JsonObject d = root.getAsJsonObject("display");
            this.displayTransforms[0] = this.readDT(d, "thirdperson_righthand");
            this.displayTransforms[1] = this.readDT(d, "thirdperson_lefthand");
            this.displayTransforms[2] = this.readDT(d, "firstperson_righthand");
            this.displayTransforms[3] = this.readDT(d, "firstperson_lefthand");
            this.displayTransforms[4] = this.readDT(d, "ground");
            this.displayTransforms[5] = this.readDT(d, "gui");
            this.displayTransforms[6] = this.readDT(d, "head");
            this.displayTransforms[7] = this.readDT(d, "fixed");
         }
      }

      private ItemReplacerFeature.DisplayTransform readDT(JsonObject display, String key) {
         if (!display.has(key)) {
            return null;
         }

         JsonObject obj = display.getAsJsonObject(key);
         float[] rot = new float[]{0.0F, 0.0F, 0.0F};
         float[] trans = new float[]{0.0F, 0.0F, 0.0F};
         float[] scale = new float[]{1.0F, 1.0F, 1.0F};
         if (obj.has("rotation")) {
            this.readFA(obj.getAsJsonArray("rotation"), rot);
         }

         if (obj.has("translation")) {
            this.readFA(obj.getAsJsonArray("translation"), trans);
         }

         if (obj.has("scale")) {
            this.readFA(obj.getAsJsonArray("scale"), scale);
         }

         return new ItemReplacerFeature.DisplayTransform(rot, trans, scale);
      }

      private void readFA(JsonArray arr, float[] target) {
         for (int i = 0; i < target.length && i < arr.size(); i++) {
            target[i] = arr.get(i).getAsFloat();
         }
      }

      private void applyDisplayTransform(PoseStack ms, ItemDisplayContext type) {
         ItemReplacerFeature.DisplayTransform dt = this.getDisplayTransform(type);
         if (dt != null) {
            dt.apply(ms, type.leftHand());
         }
      }

      @Nullable
      private ItemReplacerFeature.DisplayTransform getDisplayTransform(ItemDisplayContext type) {
         return switch (type) {
            case THIRD_PERSON_RIGHT_HAND -> this.displayTransforms[0];
            case THIRD_PERSON_LEFT_HAND -> this.displayTransforms[1];
            case FIRST_PERSON_RIGHT_HAND -> this.displayTransforms[2];
            case FIRST_PERSON_LEFT_HAND -> this.displayTransforms[3];
            case GROUND -> this.displayTransforms[4];
            case GUI -> this.displayTransforms[5];
            case HEAD -> this.displayTransforms[6];
            case FIXED -> this.displayTransforms[7];
            default -> null;
         };
      }

      private static void drawMesh(float[] mesh, VertexConsumer vc, Matrix4f matrix, Matrix3f normalMatrix, int light, int overlay) {
         int limit = mesh.length;
         Vector4f pos = new Vector4f();
         Vector3f norm = new Vector3f();

         for (int i = 0; i < limit; i += 8) {
            pos.set(mesh[i], mesh[i + 1], mesh[i + 2], 1.0F);
            matrix.transform(pos);
            norm.set(mesh[i + 5], mesh[i + 6], mesh[i + 7]);
            normalMatrix.transform(norm);
            vc.addVertex(pos.x, pos.y, pos.z)
               .setColor(255, 255, 255, 255)
               .setUv(mesh[i + 3], mesh[i + 4])
               .setOverlay(overlay)
               .setLight(light)
               .setNormal(norm.x, norm.y, norm.z);
         }
      }
   }

   public static final class SwordVariant {
      public final String label;
      public final String modelName;
      private ItemReplacerFeature.RuntimeSwordModel model;

      private SwordVariant(String label, String modelName) {
         this.label = label;
         this.modelName = modelName;
      }

      ItemReplacerFeature.RuntimeSwordModel getModel() {
         if (this.model == null) {
            this.model = new ItemReplacerFeature.RuntimeSwordModel(this.modelName);
         }

         return this.model;
      }

      void clear() {
         if (this.model != null) {
            this.model.clear();
         }
      }
   }
}
