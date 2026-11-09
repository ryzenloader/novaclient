package win.furryhook.account;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class MicrosoftAuth {
   private static final String CLIENT_ID = "4f2b7c4a-3b8d-4e2a-9c1f-7d5a8e6b2c90";
   private static final String TENANT = "consumers";
   private static final String SCOPE = "XboxLive.signin offline_access";
   private static final String DEVICE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
   private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
   private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
   private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
   private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
   private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
   private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15L);
   private static final Gson GSON = new Gson();
   private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "furryhook-ms-auth");
      thread.setDaemon(true);
      return thread;
   });
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).executor(this.executor).followRedirects(Redirect.NEVER).build();
   private volatile boolean cancelled;

   public void shutdown() {
      this.executor.shutdownNow();
   }

   public void requestDeviceCode(Consumer<MicrosoftAuth.DeviceCode> onCode, Consumer<String> onError) {
      this.executor
         .submit(
            () -> {
               try {
                  String body = "client_id=4f2b7c4a-3b8d-4e2a-9c1f-7d5a8e6b2c90&scope="
                     + URLEncoder.encode("XboxLive.signin offline_access", StandardCharsets.UTF_8);
                  JsonObject response = this.postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", body);
                  onCode.accept(
                     new MicrosoftAuth.DeviceCode(
                        required(response, "user_code"),
                        required(response, "device_code"),
                        response.has("verification_uri") ? response.get("verification_uri").getAsString() : "https://www.microsoft.com/link",
                        response.has("interval") ? response.get("interval").getAsInt() : 5
                     )
                  );
               } catch (Exception e) {
                  onError.accept(message(e));
               }
            }
         );
   }

   public void login(String deviceCode, Consumer<MicrosoftAuth.MsAccount> onDone, Consumer<String> onError) {
      this.executor.submit(() -> {
         try {
            JsonObject msToken = this.pollForToken(deviceCode);
            onDone.accept(this.complete(msToken));
         } catch (Exception e) {
            if (!this.cancelled) {
               onError.accept(message(e));
            }
         }
      });
   }

   public void refresh(String refreshToken, Consumer<MicrosoftAuth.MsAccount> onDone, Consumer<String> onError) {
      this.executor
         .submit(
            () -> {
               try {
                  JsonObject msToken = this.postForm(
                     "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                     "client_id=4f2b7c4a-3b8d-4e2a-9c1f-7d5a8e6b2c90&grant_type=refresh_token&refresh_token="
                        + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                  );
                  if (msToken.has("error")) {
                     throw new IllegalStateException(errorMessage(msToken));
                  }

                  onDone.accept(this.complete(msToken));
               } catch (Exception e) {
                  onError.accept(message(e));
               }
            }
         );
   }

   public void cancel() {
      this.cancelled = true;
   }

   private MicrosoftAuth.MsAccount complete(JsonObject msToken) throws Exception {
      String msAccess = required(msToken, "access_token");
      String refresh = msToken.has("refresh_token") ? msToken.get("refresh_token").getAsString() : null;
      String xblToken = this.xboxLiveAuthenticate(msAccess);
      JsonObject xsts = this.xstsAuthorize(xblToken);
      String xstsToken = required(xsts, "Token");
      String xuid = required(xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject(), "uhs");
      String mcToken = this.minecraftLogin(xstsToken, xuid);
      JsonObject profile = this.minecraftProfile(mcToken);
      long expiresIn = msToken.has("expires_in") ? msToken.get("expires_in").getAsLong() : 3600L;
      return new MicrosoftAuth.MsAccount(required(profile, "name"), required(profile, "id"), mcToken, refresh, Instant.now().plusSeconds(expiresIn));
   }

   private JsonObject pollForToken(String deviceCode) throws Exception {
      int interval = 5;
      long deadline = System.currentTimeMillis() + 900000L;

      while (!this.cancelled && System.currentTimeMillis() < deadline) {
         JsonObject response = this.postForm(
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
            "client_id=4f2b7c4a-3b8d-4e2a-9c1f-7d5a8e6b2c90&grant_type=urn:ietf:params:oauth:grant-type:device_code&device_code="
               + URLEncoder.encode(deviceCode, StandardCharsets.UTF_8)
         );
         if (!response.has("error")) {
            return response;
         }

         String error = response.get("error").getAsString();
         switch (error) {
            case "authorization_pending":
               Thread.sleep(interval * 1000L);
               break;
            case "slow_down":
               interval += 5;
               Thread.sleep(interval * 1000L);
               break;
            default:
               throw new IllegalStateException(errorMessage(response));
         }
      }

      throw new IllegalStateException("authorization timed out or was cancelled");
   }

   private String xboxLiveAuthenticate(String msAccessToken) throws Exception {
      JsonObject request = new JsonObject();
      JsonObject properties = new JsonObject();
      properties.addProperty("AuthMethod", "RPS");
      properties.addProperty("SiteName", "user.auth.xboxlive.com");
      properties.addProperty("RpsTicket", "d=" + msAccessToken);
      request.add("Properties", properties);
      request.addProperty("RelyingParty", "http://auth.xboxlive.com");
      request.addProperty("TokenType", "JWT");
      return required(this.postJson("https://user.auth.xboxlive.com/user/authenticate", request), "Token");
   }

   private JsonObject xstsAuthorize(String xblToken) throws Exception {
      JsonObject request = new JsonObject();
      JsonObject properties = new JsonObject();
      properties.addProperty("SandboxId", "RETAIL");
      JsonArray tokens = new JsonArray();
      tokens.add(xblToken);
      properties.add("UserTokens", tokens);
      request.add("Properties", properties);
      request.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
      request.addProperty("TokenType", "JWT");
      JsonObject response = this.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", request);
      if (response.has("XErr")) {
         long xerr = response.get("XErr").getAsLong();

         throw new IllegalStateException(switch ((int)xerr) {
            case -2144894863 -> "this Microsoft account has no Xbox profile - sign in at xbox.com first";
            case -2144894861 -> "Xbox Live is not available in this account's region";
            default -> "Xbox Live refused the login (XErr " + xerr + ")";
         });
      } else {
         return response;
      }
   }

   private String minecraftLogin(String xstsToken, String xuid) throws Exception {
      JsonObject request = new JsonObject();
      request.addProperty("identityToken", "XBL3.0 x=" + xuid + ";" + xstsToken);
      return required(this.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", request), "access_token");
   }

   private JsonObject minecraftProfile(String mcAccessToken) throws Exception {
      JsonObject response = this.get("https://api.minecraftservices.com/minecraft/profile", mcAccessToken);
      if (response.has("error")) {
         throw new IllegalStateException(
            response.has("errorMessage") ? response.get("errorMessage").getAsString() : "this account does not own Minecraft: Java Edition"
         );
      } else {
         return response;
      }
   }

   private JsonObject postForm(String url, String body) throws Exception {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
         .timeout(HTTP_TIMEOUT)
         .header("Content-Type", "application/x-www-form-urlencoded")
         .header("Accept", "application/json")
         .POST(BodyPublishers.ofString(body))
         .build();
      return this.send(request);
   }

   private JsonObject postJson(String url, JsonObject body) throws Exception {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
         .timeout(HTTP_TIMEOUT)
         .header("Content-Type", "application/json")
         .header("Accept", "application/json")
         .POST(BodyPublishers.ofString(GSON.toJson(body)))
         .build();
      return this.send(request);
   }

   private JsonObject get(String url, String bearer) throws Exception {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
         .timeout(HTTP_TIMEOUT)
         .header("Authorization", "Bearer " + bearer)
         .header("Accept", "application/json")
         .GET()
         .build();
      return this.send(request);
   }

   private JsonObject send(HttpRequest request) throws Exception {
      HttpResponse<String> response = this.http.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
      String body = response.body() == null ? "" : response.body();
      JsonElement parsed = (JsonElement)(body.isBlank() ? new JsonObject() : JsonParser.parseString(body));
      if (!parsed.isJsonObject()) {
         throw new IllegalStateException("HTTP " + response.statusCode() + ": unexpected response");
      } else {
         JsonObject json = parsed.getAsJsonObject();
         if (response.statusCode() >= 400 && !json.has("error")) {
            throw new IllegalStateException("HTTP " + response.statusCode());
         } else {
            return json;
         }
      }
   }

   private static String required(JsonObject json, String key) {
      if (json.has(key) && !json.get(key).isJsonNull()) {
         return json.get(key).getAsString();
      } else {
         throw new IllegalStateException("auth response missing \"" + key + "\"");
      }
   }

   private static String errorMessage(JsonObject json) {
      if (json.has("error_description")) {
         return json.get("error_description").getAsString();
      } else if (json.has("errorMessage")) {
         return json.get("errorMessage").getAsString();
      } else {
         return json.has("error") ? json.get("error").getAsString() : "unknown error";
      }
   }

   private static String message(Exception e) {
      String msg = e.getMessage();
      return msg != null && !msg.isBlank() ? msg : e.getClass().getSimpleName();
   }

   public record DeviceCode(String userCode, String deviceCode, String verificationUri, int intervalSeconds) {
   }

   public record MsAccount(String username, String uuid, String accessToken, String refreshToken, Instant expiresAt) {
      public boolean tokenExpired() {
         return this.expiresAt == null || Instant.now().isAfter(this.expiresAt.minusSeconds(60L));
      }
   }
}
