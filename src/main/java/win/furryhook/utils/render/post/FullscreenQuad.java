package win.furryhook.utils.render.post;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;

public final class FullscreenQuad {
   private static GpuBuffer buffer;
   private static int vertexCount;

   private FullscreenQuad() {
   }

   public static GpuBuffer buffer() {
      ensure();
      return buffer;
   }

   public static int vertexCount() {
      ensure();
      return vertexCount;
   }

   private static void ensure() {
      if (buffer == null) {
         BufferBuilder builder = new BufferBuilder(
            ByteBufferBuilder.exactlySized(6 * DefaultVertexFormat.POSITION_TEX.getVertexSize()), PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_TEX
         );
         builder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
         builder.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F);
         builder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
         builder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
         builder.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F);
         builder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
         MeshData mesh = builder.buildOrThrow();

         try {
            vertexCount = mesh.drawState().vertexCount();
            buffer = RenderSystem.getDevice().createBuffer(() -> "Blade fullscreen quad", 32, mesh.vertexBuffer());
         } catch (Throwable var5) {
            if (mesh != null) {
               try {
                  mesh.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }

         if (mesh != null) {
            mesh.close();
         }
      }
   }
}
