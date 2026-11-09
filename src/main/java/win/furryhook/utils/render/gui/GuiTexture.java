package win.furryhook.utils.render.gui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.FloatSize;
import com.github.weisj.jsvg.view.ViewBox;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import win.furryhook.context.MinecraftContext;

public final class GuiTexture {
   private static final Logger LOGGER = LoggerFactory.getLogger(GuiTexture.class);
   private static final Map<Identifier, GuiTexture> CACHE = new ConcurrentHashMap<>();
   private static final int MAX_VARIANTS = 32;
   private static final int MIN_BUCKET = 16;
   private static volatile boolean svgIconsDisabled;
   private static final int SUPERSAMPLE = 2;
   private static final int MAX_TEXTURE_SIZE = 2048;
   private final Identifier textureId;
   private final boolean svg;
   private volatile SVGDocument document;
   private TextureSetup staticSetup;
   private final LinkedHashMap<Long, TextureSetup> svgVariants = new LinkedHashMap<Long, TextureSetup>(8, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<Long, TextureSetup> eldest) {
         if (this.size() <= 32) {
            return false;
         }

         MinecraftContext.mc.getTextureManager().release(GuiTexture.variantId(GuiTexture.this.textureId, eldest.getKey()));
         return true;
      }
   };

   public static boolean svgIconsDisabled() {
      return svgIconsDisabled;
   }

   public static void setSvgIconsDisabled(boolean disabled) {
      svgIconsDisabled = disabled;
   }

   private GuiTexture(Identifier textureId) {
      this.textureId = textureId;
      this.svg = textureId.getPath().endsWith(".svg");
   }

   public static GuiTexture load(Identifier textureId) {
      return CACHE.computeIfAbsent(textureId, GuiTexture::new);
   }

   public static void prewarm(Collection<Identifier> ids) {
      Thread thread = new Thread(() -> {
         for (Identifier id : ids) {
            try {
               GuiTexture texture = load(id);
               if (texture.svg) {
                  texture.document();
               }
            } catch (Throwable throwable) {
               LOGGER.warn("Failed to prewarm GUI texture {}", id, throwable);
            }
         }
      }, "Blade SVG Prewarm");
      thread.setDaemon(true);
      thread.start();
   }

   public TextureSetup textureSetup(float deviceWidth, float deviceHeight) {
      if (!this.svg) {
         return this.staticSetup();
      }

      long key = packSize(quantize(deviceWidth), quantize(deviceHeight));
      TextureSetup cached = this.svgVariants.get(key);
      if (cached != null) {
         return cached;
      }

      TextureSetup created = this.createSvgSetup(key);
      this.svgVariants.put(key, created);
      return created;
   }

   public TextureSetup textureSetup() {
      if (!this.svg) {
         return this.staticSetup();
      }

      FloatSize size = this.documentSize();
      return this.textureSetup((float)size.getWidth(), (float)size.getHeight());
   }

   private TextureSetup staticSetup() {
      if (this.staticSetup == null) {
         DynamicTexture texture = new DynamicTexture(() -> this.textureId.toString(), readPng(this.textureId));
         texture.upload();
         MinecraftContext.mc.getTextureManager().register(this.textureId, texture);
         this.staticSetup = TextureSetup.singleTexture(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, false));
      }

      return this.staticSetup;
   }

   private TextureSetup createSvgSetup(long key) {
      int width = Math.min(2048, unpackWidth(key) * 2);
      int height = Math.min(2048, unpackHeight(key) * 2);
      Identifier variantId = variantId(this.textureId, key);
      DynamicTexture texture = new DynamicTexture(variantId::toString, rasterizeSvg(this.textureId, this.document(), width, height));
      texture.upload();
      MinecraftContext.mc.getTextureManager().register(variantId, texture);
      return TextureSetup.singleTexture(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, false));
   }

   private SVGDocument document() {
      SVGDocument loaded = this.document;
      if (loaded == null) {
         loaded = loadDocument(this.textureId);
         this.document = loaded;
      }

      return loaded;
   }

   private FloatSize documentSize() {
      return this.document().size();
   }

   private static int quantize(float devicePx) {
      int wanted = Math.max(16, (int)Math.ceil(devicePx));
      return wanted >= 2048 ? 2048 : Integer.highestOneBit(wanted - 1) << 1;
   }

   private static long packSize(int width, int height) {
      return (long)width << 32 | height & 4294967295L;
   }

   private static int unpackWidth(long key) {
      return (int)(key >>> 32);
   }

   private static int unpackHeight(long key) {
      return (int)key;
   }

   private static Identifier variantId(Identifier base, long key) {
      return base.withPath(path -> path + "/" + unpackWidth(key) + "x" + unpackHeight(key));
   }

   private static NativeImage readPng(Identifier textureId) {
      try (InputStream inputStream = MinecraftContext.mc.getResourceManager().open(textureId)) {
         return NativeImage.read(inputStream);
      } catch (IOException exception) {
         throw new IllegalStateException("Failed to read GUI texture: " + textureId, exception);
      }
   }

   private static SVGDocument loadDocument(Identifier textureId) {
      try (InputStream inputStream = MinecraftContext.mc.getResourceManager().open(textureId)) {
         URI documentUri = URI.create("resource://" + textureId.getNamespace() + "/" + textureId.getPath());
         SVGDocument document = new SVGLoader().load(inputStream, documentUri, LoaderContext.createDefault());
         if (document == null) {
            throw new IOException("Failed to parse SVG document: " + textureId);
         } else {
            return document;
         }
      } catch (IOException exception) {
         throw new IllegalStateException("Failed to read GUI texture: " + textureId, exception);
      }
   }

   private static NativeImage rasterizeSvg(Identifier textureId, SVGDocument document, int width, int height) {
      BufferedImage bufferedImage = new BufferedImage(width, height, 3);
      Graphics2D graphics = bufferedImage.createGraphics();

      try {
         graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
         graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
         graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
         document.render(null, graphics, new ViewBox(0.0F, 0.0F, width, height));
      } finally {
         graphics.dispose();
      }

      int clearBorder = textureId.getPath().contains("logo_boot") ? Math.max(1, Math.round(4.0F * width / 512.0F)) : 0;
      NativeImage nativeImage = new NativeImage(Format.RGBA, width, height, false);

      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            if (clearBorder <= 0 || x >= clearBorder && x < width - clearBorder && y >= clearBorder && y < height - clearBorder) {
               int argb = bufferedImage.getRGB(x, y);
               if ((argb >> 24 & 0xFF) == 0) {
                  argb = 16777215;
               }

               nativeImage.setPixel(x, y, argb);
            } else {
               nativeImage.setPixel(x, y, 16777215);
            }
         }
      }

      return nativeImage;
   }
}
