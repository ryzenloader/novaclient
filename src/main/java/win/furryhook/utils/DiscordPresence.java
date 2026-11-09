package win.furryhook.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.lifecycle.ClientStartEvent;
import win.furryhook.event.events.lifecycle.ShutdownEvent;
import win.furryhook.event.events.lifecycle.WorldJoinEvent;
import win.furryhook.event.events.lifecycle.WorldLeaveEvent;

public final class DiscordPresence {
   public static final DiscordPresence INSTANCE = new DiscordPresence();
   private static final String CLIENT_ID = "1543358183077322802";
   private static final String BUILD_TAG = "Build » Alpha";
   private static final String SMALL_IMAGE_KEY = "dsf";
   private static final String SMALL_IMAGE_TEXT = "furryhook";
   private static final int UPDATE_INTERVAL_TICKS = 40;
   private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "furryhook-discord-rpc");
      thread.setDaemon(true);
      return thread;
   });
   private final AtomicReference<DiscordPresence.Presence> pending = new AtomicReference<>();
   private DiscordPresence.Transport transport;
   private boolean connected;
   private volatile boolean started;
   private volatile boolean stopped;
   private DiscordPresence.Presence lastSent;
   private long lastReconnectAttempt;
   private int tickCounter;
   private volatile String discordUsername;
   private volatile String discordUserId;

   public String username() {
      return this.discordUsername;
   }

   private DiscordPresence() {
   }

   @EventTarget
   public void onClientStart(ClientStartEvent event) {
      if (!this.started && !this.stopped) {
         this.started = true;
         this.pending.set(this.computePresence(null));
         this.worker.scheduleWithFixedDelay(this::pump, 0L, 3L, TimeUnit.SECONDS);
      }
   }

   @EventTarget
   public void onGameTick(GameTickEvent event) {
      if (!this.stopped) {
         if (++this.tickCounter >= 40) {
            this.tickCounter = 0;
            this.pending.set(this.computePresence(event.getClient()));
         }
      }
   }

   @EventTarget
   public void onWorldJoin(WorldJoinEvent event) {
      if (!this.stopped) {
         this.tickCounter = 0;
         this.pending.set(this.computePresence(event.getClient()));
      }
   }

   @EventTarget
   public void onWorldLeave(WorldLeaveEvent event) {
      if (!this.stopped) {
         this.tickCounter = 0;
         this.pending.set(this.computePresence(null));
      }
   }

   @EventTarget
   public void onShutdown(ShutdownEvent event) {
      this.shutdown();
   }

   private DiscordPresence.Presence computePresence(Minecraft client) {
      return new DiscordPresence.Presence("UID » 0", "Build » Alpha");
   }

   private void pump() {
      if (!this.stopped) {
         try {
            if (!this.connected) {
               long now = System.currentTimeMillis();
               if (now - this.lastReconnectAttempt < 15000L) {
                  return;
               }

               this.lastReconnectAttempt = now;
               this.connect();
            }

            if (!this.connected) {
               return;
            }

            DiscordPresence.Presence presence = this.pending.get();
            if (presence != null && !presence.equals(this.lastSent)) {
               this.sendActivity(presence);
               this.lastSent = presence;
            }
         } catch (IOException e) {
            this.closeQuietly();
         } catch (RuntimeException e) {
            this.closeQuietly();
         }
      }
   }

   private void connect() throws IOException {
      this.transport = DiscordPresence.Transport.open();
      if (this.transport != null) {
         JsonObject handshake = new JsonObject();
         handshake.addProperty("v", 1);
         handshake.addProperty("client_id", "1543358183077322802");
         this.transport.send(0, handshake.toString());
         JsonObject readyUser = parseUser(this.transport.readFrame());
         this.discordUsername = readyUser != null && readyUser.get("username") != null && !readyUser.get("username").isJsonNull()
            ? readyUser.get("username").getAsString()
            : null;
         this.discordUserId = readyUser != null && readyUser.get("id") != null && !readyUser.get("id").isJsonNull() ? readyUser.get("id").getAsString() : null;
         this.connected = true;
         this.lastSent = null;
      }
   }

   private void sendActivity(DiscordPresence.Presence presence) throws IOException {
      JsonObject activity = new JsonObject();
      activity.addProperty("details", presence.details);
      if (presence.state != null) {
         activity.addProperty("state", presence.state);
      }

      JsonObject assets = new JsonObject();
      assets.addProperty("small_image", "dsf");
      assets.addProperty("small_text", "furryhook");
      activity.add("assets", assets);
      JsonObject args = new JsonObject();
      args.addProperty("pid", (int)ProcessHandle.current().pid());
      args.add("activity", activity);
      JsonObject frame = new JsonObject();
      frame.addProperty("cmd", "SET_ACTIVITY");
      frame.add("args", args);
      frame.addProperty("nonce", UUID.randomUUID().toString());
      this.transport.send(1, frame.toString());
      this.transport.readFrame();
   }

   private static JsonObject parseUser(byte[] payload) {
      try {
         JsonElement root = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8));
         JsonElement user = root.getAsJsonObject().getAsJsonObject("data").get("user");
         return user != null && !user.isJsonNull() ? user.getAsJsonObject() : null;
      } catch (RuntimeException e) {
         return null;
      }
   }

   private void shutdown() {
      if (!this.stopped) {
         this.stopped = true;
         this.worker.execute(this::closeQuietly);
         this.worker.shutdown();
      }
   }

   private void closeQuietly() {
      this.connected = false;
      this.lastSent = null;
      if (this.transport != null) {
         this.transport.close();
         this.transport = null;
      }
   }

   private record Presence(String details, String state) {
      @Override
      public boolean equals(Object o) {
         return this == o
            ? true
            : o instanceof DiscordPresence.Presence other && Objects.equals(this.details, other.details) && Objects.equals(this.state, other.state);
      }
   }

   private static final class Transport {
      static final int OP_HANDSHAKE = 0;
      static final int OP_FRAME = 1;
      private final InputStream in;
      private final OutputStream out;
      private final AutoCloseable owner;

      private Transport(InputStream in, OutputStream out, AutoCloseable owner) {
         this.in = in;
         this.out = out;
         this.owner = owner;
      }

      static DiscordPresence.Transport open() {
         boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

         for (int i = 0; i < 10; i++) {
            try {
               return windows ? openPipe(i) : openSocket(i);
            } catch (IOException var3) {
            }
         }

         return null;
      }

      private static DiscordPresence.Transport openPipe(int slot) throws IOException {
         final RandomAccessFile pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + slot, "rw");
         InputStream in = new InputStream() {
            @Override
            public int read() throws IOException {
               return pipe.read();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
               return pipe.read(b, off, len);
            }
         };
         OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
               pipe.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
               pipe.write(b, off, len);
            }
         };
         return new DiscordPresence.Transport(in, out, pipe);
      }

      private static DiscordPresence.Transport openSocket(int slot) throws IOException {
         Path socketPath = Path.of(ipcDir(), "discord-ipc-" + slot);
         SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
         channel.connect(UnixDomainSocketAddress.of(socketPath));
         return new DiscordPresence.Transport(Channels.newInputStream(channel), Channels.newOutputStream(channel), channel);
      }

      private static String ipcDir() {
         for (String key : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
               return value;
            }
         }

         return "/tmp";
      }

      void send(int opcode, String payload) throws IOException {
         byte[] data = payload.getBytes(StandardCharsets.UTF_8);
         ByteBuffer buffer = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
         buffer.putInt(opcode);
         buffer.putInt(data.length);
         buffer.put(data);
         this.out.write(buffer.array());
         this.out.flush();
      }

      byte[] readFrame() throws IOException {
         byte[] header = this.readFully(8);
         int length = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
         if (length >= 0 && length <= 1048576) {
            return this.readFully(length);
         } else {
            throw new IOException("Unexpected Discord frame length: " + length);
         }
      }

      private byte[] readFully(int length) throws IOException {
         byte[] data = new byte[length];
         int read = 0;

         while (read < length) {
            int count = this.in.read(data, read, length - read);
            if (count < 0) {
               throw new IOException("Discord pipe closed");
            }

            read += count;
         }

         return data;
      }

      void close() {
         try {
            this.owner.close();
         } catch (Exception var2) {
         }
      }
   }
}
