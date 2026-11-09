package win.furryhook.feature.impl.player;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import win.furryhook.event.EventTarget;
import win.furryhook.event.events.game.GameTickEvent;
import win.furryhook.event.events.input.KeyboardInputEvent;
import win.furryhook.feature.Feature;
import win.furryhook.feature.FeatureCategory;
import win.furryhook.feature.FeatureManager;
import win.furryhook.feature.setting.ModeSetting;
import win.furryhook.feature.setting.NumberSetting;
import win.furryhook.mixin.accessor.EntityAccessor;

public final class FakePlayerFeature extends Feature {
   private static final int FAKE_PLAYER_ENTITY_ID = -23467;
   private static final UUID FAKE_PLAYER_UUID = UUID.fromString("66123666-6666-6666-6666-666666666600");
   private static final String FAKE_PLAYER_NAME = "FakePlayer";
   private static final int SHARED_FLAG_GLIDING = 7;
   private final ModeSetting mode = this.register(new ModeSetting("Mode", "AFK", "AFK", "Elytra"));
   private final NumberSetting elytraSpeed = this.register(
      new NumberSetting("Elytra Speed", 1.0, 0.2, 2.5, 0.1, " b/t").visibleWhen(() -> this.mode.is("Elytra"))
   );
   private RemotePlayer fakePlayer;
   private float moveForward;
   private float moveStrafe;
   private String lastMode;
   private float flightYaw = Float.NaN;
   private Vec3 elytraVelocity = Vec3.ZERO;
   private Vec3 lastFlightPos;

   public FakePlayerFeature() {
      super("FakePlayer", "AFK dummy, or an elytra target that glides away to test overtake", FeatureCategory.PLAYER, -1);
   }

   public static FakePlayerFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(FakePlayerFeature.class);
   }

   public void ensureSpawned() {
      this.spawnFakePlayerIfNeeded(Minecraft.getInstance());
   }

   public void despawn() {
      this.removeFakePlayer();
   }

   public RemotePlayer getFakePlayerEntity() {
      return this.fakePlayer;
   }

   public boolean hasFakePlayer() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level != null && this.fakePlayer != null && !this.fakePlayer.isRemoved() && mc.level.getEntity(this.fakePlayer.getId()) == this.fakePlayer;
   }

   public void moveFakeTo(Vec3 position, float yaw, float pitch) {
      if (position != null && this.fakePlayer != null) {
         this.fakePlayer.snapTo(position.x, position.y, position.z, yaw, pitch);
         this.fakePlayer.setYHeadRot(yaw);
         this.fakePlayer.yBodyRot = yaw;
      }
   }

   public int getFakePlayerEntityId() {
      return -23467;
   }

   public boolean isFakePlayer(Entity entity) {
      return this.fakePlayer != null && (entity == this.fakePlayer || entity.getId() == this.fakePlayer.getId());
   }

   public void registerHit() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null && this.fakePlayer != null) {
         if (this.fakePlayer.hurtTime == 0) {
            mc.level
               .playSound(
                  mc.player, this.fakePlayer.getX(), this.fakePlayer.getY(), this.fakePlayer.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 1.0F
               );
            if (mc.player.fallDistance > 0.0) {
               mc.level
                  .playSound(
                     mc.player,
                     this.fakePlayer.getX(),
                     this.fakePlayer.getY(),
                     this.fakePlayer.getZ(),
                     SoundEvents.PLAYER_ATTACK_CRIT,
                     SoundSource.PLAYERS,
                     1.0F,
                     1.0F
                  );
            } else {
               mc.level
                  .playSound(
                     mc.player,
                     this.fakePlayer.getX(),
                     this.fakePlayer.getY(),
                     this.fakePlayer.getZ(),
                     SoundEvents.PLAYER_ATTACK_SWEEP,
                     SoundSource.PLAYERS,
                     1.0F,
                     1.0F
                  );
            }

            this.fakePlayer.handleDamageEvent(mc.level.damageSources().generic());
            this.fakePlayer.setHealth(this.fakePlayer.getHealth() + this.fakePlayer.getAbsorptionAmount() - 1.0F);
            if (this.fakePlayer.isDeadOrDying()) {
               this.fakePlayer.setHealth(10.0F);
               if (mc.player.connection != null) {
                  new ClientboundEntityEventPacket(this.fakePlayer, (byte)35).handle(mc.player.connection);
               }
            }
         }
      }
   }

   @Override
   protected void onEnable() {
      this.spawnFakePlayerIfNeeded(Minecraft.getInstance());
   }

   @Override
   protected void onDisable() {
      this.removeFakePlayer();
   }

   @EventTarget
   public void onTick(GameTickEvent event) {
      Minecraft mc = event.getClient();
      if (mc.level != null && mc.player != null) {
         if (this.lastMode != null && !this.lastMode.equals(this.mode.getValue())) {
            this.removeFakePlayer();
         }

         boolean existsInCurrentWorld = this.fakePlayer != null && mc.level.getEntity(this.fakePlayer.getId()) == this.fakePlayer;
         if (this.fakePlayer == null || this.fakePlayer.isRemoved() || !existsInCurrentWorld) {
            this.spawnFakePlayerIfNeeded(mc);
         }

         this.lastMode = this.mode.getValue();
         if (this.mode.is("Elytra")) {
            this.updateElytraFlight(mc);
         } else {
            this.updateFakeMovement(mc);
         }
      } else {
         this.removeFakePlayer();
      }
   }

   @EventTarget
   public void onKey(KeyboardInputEvent event) {
      if (this.fakePlayer != null && Minecraft.getInstance().gui.screen() == null) {
         int key = event.getKey();
         int action = event.getAction();
         if (key == 265) {
            this.moveForward = this.isKeyDown(action) ? 1.0F : 0.0F;
         } else if (key == 264) {
            this.moveForward = this.isKeyDown(action) ? -1.0F : 0.0F;
         } else if (key == 263) {
            this.moveStrafe = this.isKeyDown(action) ? 1.0F : 0.0F;
         } else if (key == 262) {
            this.moveStrafe = this.isKeyDown(action) ? -1.0F : 0.0F;
         }
      }
   }

   private void spawnFakePlayerIfNeeded(Minecraft mc) {
      if (mc.level != null && mc.player != null) {
         if (this.fakePlayer == null || this.fakePlayer.isRemoved() || mc.level.getEntity(this.fakePlayer.getId()) != this.fakePlayer) {
            this.removeFakePlayer();
            Entity existing = mc.level.getEntity(-23467);
            if (existing != null) {
               existing.discard();
            }

            this.fakePlayer = new RemotePlayer(mc.level, new GameProfile(FAKE_PLAYER_UUID, "FakePlayer"));
            this.fakePlayer.setId(-23467);
            this.fakePlayer.copyPosition(mc.player);
            this.fakePlayer.setYHeadRot(mc.player.getYHeadRot());
            this.fakePlayer.yBodyRot = mc.player.yBodyRot;
            this.fakePlayer.setPose(mc.player.getPose());
            this.fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().copy());
            this.fakePlayer.setItemInHand(InteractionHand.OFF_HAND, mc.player.getOffhandItem().copy());

            for (EquipmentSlot slot : EquipmentSlot.values()) {
               if (slot.getType() == Type.HUMANOID_ARMOR) {
                  this.fakePlayer.setItemSlot(slot, mc.player.getItemBySlot(slot).copy());
               }
            }

            this.fakePlayer.setHealth(Math.max(1.0F, mc.player.getHealth()));
            this.fakePlayer.setAbsorptionAmount(mc.player.getAbsorptionAmount());
            this.fakePlayer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 9999, 2, false, false));
            this.fakePlayer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 9999, 4, false, false));
            this.fakePlayer.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 9999, 1, false, false));
            mc.level.addEntity(this.fakePlayer);
         }
      }
   }

   private void removeFakePlayer() {
      this.moveForward = 0.0F;
      this.moveStrafe = 0.0F;
      this.flightYaw = Float.NaN;
      this.elytraVelocity = Vec3.ZERO;
      this.lastFlightPos = null;
      if (this.fakePlayer != null) {
         this.fakePlayer.discard();
         this.fakePlayer = null;
      }
   }

   private void updateElytraFlight(Minecraft mc) {
      if (this.fakePlayer != null && mc.player != null) {
         if (!this.fakePlayer.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            this.fakePlayer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
         }

         ((EntityAccessor)this.fakePlayer).invokeSetSharedFlag(7, true);
         this.fakePlayer.setNoGravity(true);
         if (Float.isNaN(this.flightYaw)) {
            this.flightYaw = mc.player.getYRot();
         }

         if (this.lastFlightPos == null) {
            this.lastFlightPos = this.fakePlayer.position();
         }

         double speed = this.elytraSpeed.getValue();
         double yawRad = Math.toRadians(this.flightYaw);
         this.elytraVelocity = new Vec3(-Math.sin(yawRad) * speed, 0.0, Math.cos(yawRad) * speed);
         this.fakePlayer.setDeltaMovement(this.elytraVelocity);
         this.fakePlayer.setYRot(this.flightYaw);
         this.fakePlayer.setYHeadRot(this.flightYaw);
         this.fakePlayer.yBodyRot = this.flightYaw;
         this.fakePlayer.setXRot(0.0F);
         this.fakePlayer.walkAnimation.setSpeed(0.0F);
      }
   }

   public void advanceElytraPostTick() {
      if (this.isEnabled() && this.mode.is("Elytra") && this.fakePlayer != null && this.lastFlightPos != null) {
         Vec3 base = this.lastFlightPos;
         Vec3 next = base.add(this.elytraVelocity);
         this.fakePlayer.xOld = base.x;
         this.fakePlayer.yOld = base.y;
         this.fakePlayer.zOld = base.z;
         this.fakePlayer.setPos(next.x, next.y, next.z);
         this.lastFlightPos = next;
      }
   }

   private void updateFakeMovement(Minecraft mc) {
      if (this.fakePlayer != null && mc.player != null) {
         if (this.moveForward == 0.0F && this.moveStrafe == 0.0F) {
            this.fakePlayer.setSprinting(false);
            this.fakePlayer.setDeltaMovement(0.0, this.fakePlayer.getDeltaMovement().y, 0.0);
            this.fakePlayer.walkAnimation.setSpeed(0.0F);
         } else {
            float yaw = mc.player.getYRot();
            double speed = 0.2;
            double motionX = this.moveStrafe * Math.cos(Math.toRadians(yaw)) - this.moveForward * Math.sin(Math.toRadians(yaw));
            double motionZ = this.moveForward * Math.cos(Math.toRadians(yaw)) + this.moveStrafe * Math.sin(Math.toRadians(yaw));
            Vec3 velocity = new Vec3(motionX * speed, this.fakePlayer.getDeltaMovement().y, motionZ * speed);
            this.fakePlayer.setDeltaMovement(velocity);
            this.fakePlayer.move(MoverType.SELF, velocity);
            this.fakePlayer.setSprinting(true);
         }
      }
   }

   private boolean isKeyDown(int action) {
      return action == 1 || action == 2;
   }
}
