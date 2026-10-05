package bat.grapnel.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;

import java.util.Optional;

public class GrapnelGunItem extends Item {
    
    public GrapnelGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!(entity instanceof Player player)) return;

        // Check if the item is held in either hand
        boolean isHeld = isSelected || player.getOffhandItem() == stack;

        if (isHeld) {
            // 1. Handle cursor ledge lookup
            Optional<Vec3> ledgeSpot = findTargetLedgeSmooth(player, level);

            if (!ledgeSpot.isPresent()) {
                if (level.isClientSide) {
                    GrapnelClientTracker.renderTargetPos = null;
                }
            }

            // --- PULL & STAND LOGIC ---
            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = customData.copyTag();

            if (tag.getBoolean("IsPulling")) {
                double targetX = tag.getDouble("TargetX");
                double targetY = tag.getDouble("TargetY");
                double targetZ = tag.getDouble("TargetZ");
                Vec3 targetLandingVec = new Vec3(targetX, targetY, targetZ);

                Vec3 playerPos = player.position();
                int pullTicks = tag.getInt("PullTicks");
                
                if (pullTicks == 0) {
                    tag.putDouble("StartX", playerPos.x);
                    tag.putDouble("StartY", playerPos.y);
                    tag.putDouble("StartZ", playerPos.z);
                    
                    double totalFlightDist = playerPos.distanceTo(targetLandingVec);
                    tag.putDouble("TotalDistance", totalFlightDist);
                }
                
                pullTicks++;
                tag.putInt("PullTicks", pullTicks);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                Vec3 startPos = new Vec3(tag.getDouble("StartX"), tag.getDouble("StartY"), tag.getDouble("StartZ"));
                double totalDistance = tag.getDouble("TotalDistance");

                double travelRatePerTick = 0.055D; 
                double progression = pullTicks * travelRatePerTick;

                if (progression >= 1.0D || totalDistance <= 0.5D) {
                    player.resetFallDistance();

                    Vec3 lookDir = player.getLookAngle();
                    Vec3 landingPush = new Vec3(lookDir.x, 0.1D, lookDir.z).normalize().scale(0.35D);
                    player.setDeltaMovement(landingPush);

                    tag.putBoolean("IsPulling", false);
                    tag.putInt("PullTicks", 0);
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                    // Forcefully updates the hand packet on arrival to return texture to idle
                    if (!level.isClientSide) {
                        player.containerMenu.broadcastChanges();
                    }
                } 
                else {
                    double smoothAlpha = progression * progression * (3.0D - 2.0D * progression);
                    Vec3 expectedNextFramePos = startPos.lerp(targetLandingVec, smoothAlpha);
                    Vec3 necessaryMotionVelocity = expectedNextFramePos.subtract(playerPos);
                    
                    player.setDeltaMovement(necessaryMotionVelocity);
                    player.hurtMarked = true;
                }
            }
        } else {
            // --- THE INSTANT CANCEL MOMENTUM-SAFE FIX ---
            // If the item is no longer held, cancel the pulling state but preserve player speed
            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = customData.copyTag();

            if (tag.getBoolean("IsPulling")) {
                // Shut down the item's internal pulling calculations
                tag.putBoolean("IsPulling", false);
                tag.putInt("PullTicks", 0);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                // Force fully clear data component layout sync states
                if (!level.isClientSide) {
                    player.containerMenu.broadcastChanges();
                }
            }

            if (level.isClientSide && GrapnelClientTracker.renderTargetPos != null) {
                GrapnelClientTracker.renderTargetPos = null;
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        
        Optional<Vec3> ledgeSpot = findTargetLedgeSmooth(player, level);

        if (ledgeSpot.isPresent()) {
            Vec3 standSpot = ledgeSpot.get();

            CustomData customData = itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = customData.copyTag();
            
            tag.putBoolean("IsPulling", true);
            tag.putDouble("TargetX", standSpot.x);
            tag.putDouble("TargetY", standSpot.y);
            tag.putDouble("TargetZ", standSpot.z);
            
            itemstack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            if (!level.isClientSide) {
                player.containerMenu.broadcastChanges();
            }

            return InteractionResultHolder.success(itemstack);
        }

        return InteractionResultHolder.pass(itemstack);
    }

    // --- RAYCAST OCCLUSION-TESTED LEDGE TRACER ---
    private Optional<Vec3> findTargetLedgeSmooth(Player player, Level level) {
        Vec3 lookVec = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition();
        double maxReach = 21.0D;

        BlockPos bestLedgeBlock = null;
        Vec3 bestExactCrosshairPos = null;
        double closestLedgeDistToCursor = Double.MAX_VALUE;

        BlockPos playerFloorPos = player.blockPosition();
        int rangeLimit = 22; 

        for (int x = -rangeLimit; x <= rangeLimit; x++) {
            for (int z = -rangeLimit; z <= rangeLimit; z++) {
                for (int y = -rangeLimit; y <= rangeLimit; y++) {
                    BlockPos checkPos = playerFloorPos.offset(x, y, z);
                    Vec3 blockCenterVec = Vec3.atCenterOf(checkPos);

                    // 1. PHYSICAL RANGE CONSTRAINT
                    if (eyePos.distanceToSqr(blockCenterVec) <= (maxReach * maxReach)) {
                        
                        // 2. BASELINE LEDGE CRITERIA RULES
                        if (level.getBlockState(checkPos).isSolidRender(level, checkPos)) {
                            BlockState stateAbove = level.getBlockState(checkPos.above());
                            BlockState stateTwoAbove = level.getBlockState(checkPos.above(2));

                            if (!stateAbove.isSolidRender(level, checkPos.above()) && 
                                !stateTwoAbove.isSolidRender(level, checkPos.above(2))) {
                                
                                if (checkPos.getY() >= playerFloorPos.getY() + 3) {
                                    
                                    // 3. CROSSHAIR PROXIMITY MEASUREMENT
                                    Vec3 vectorToBlock = blockCenterVec.subtract(eyePos);
                                    double projectionLength = vectorToBlock.dot(lookVec);
                                    
                                    if (projectionLength > 0) {
                                        Vec3 pointOnRayAxis = eyePos.add(lookVec.scale(projectionLength));
                                        double distanceToCrosshairAxisSqr = blockCenterVec.distanceToSqr(pointOnRayAxis);

                                        if (distanceToCrosshairAxisSqr < closestLedgeDistToCursor) {
                                            
                                            // --- THE OBSCURED BLOCK OCCLUSION FIX ---
                                            // Run a real physical raycast trace from the player's eyes directly to the ledge landing spot.
                                            // ClipContext.Block.COLLIDER checks for full solid blocks that block visibility.
                                            net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
                                                eyePos, 
                                                blockCenterVec, 
                                                net.minecraft.world.level.ClipContext.Block.COLLIDER, 
                                                net.minecraft.world.level.ClipContext.Fluid.NONE, 
                                                player
                                            );
                                            
                                            net.minecraft.world.phys.BlockHitResult raycastResult = level.clip(context);
                                            
                                            // A ledge is ONLY valid if the raycast makes it all the way to the target block 
                                            // without crashing into an obscuring wall or ceiling along the path!
                                            if (raycastResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS || 
                                                raycastResult.getBlockPos().equals(checkPos)) {
                                                
                                                closestLedgeDistToCursor = distanceToCrosshairAxisSqr;
                                                bestLedgeBlock = checkPos;
                                                bestExactCrosshairPos = pointOnRayAxis;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // If a valid visible ledge block passed all viewport and raycast check passes
        if (bestLedgeBlock != null && bestExactCrosshairPos != null) {
            double minX = bestLedgeBlock.getX();
            double maxX = minX + 1.0D;
            double minZ = bestLedgeBlock.getZ();
            double maxZ = minZ + 1.0D;

            double smoothX = Math.max(minX, Math.min(maxX, bestExactCrosshairPos.x));
            double smoothZ = Math.max(minZ, Math.min(maxZ, bestExactCrosshairPos.z));
            double landingY = bestLedgeBlock.getY() + 1.0D;

            Vec3 blockCenter = Vec3.atCenterOf(bestLedgeBlock);
            Vec3 pullInwardDir = new Vec3(blockCenter.x - smoothX, 0, blockCenter.z - smoothZ);
            if (pullInwardDir.lengthSqr() > 1e-4) {
                pullInwardDir = pullInwardDir.normalize().scale(0.2D);
            } else {
                pullInwardDir = Vec3.ZERO;
            }
            
            Vec3 smoothLedgeVector = new Vec3(smoothX, landingY, smoothZ).add(pullInwardDir);

            if (level.isClientSide) {
                double visualY = bestLedgeBlock.getY() + 0.9D;
                
                double diffX = smoothX - blockCenter.x;
                double diffZ = smoothZ - blockCenter.z;
                
                double pushX = 0.0D;
                double pushZ = 0.0D;

                if (Math.abs(diffX) > Math.abs(diffZ)) {
                    pushX = Math.signum(diffX) * 0.08D;
                } else {
                    pushZ = Math.signum(diffZ) * 0.08D;
                }

                if (pushX == 0 && pushZ == 0) {
                    Vec3 outwardFaceDir = lookVec.reverse().normalize().scale(0.08D);
                    pushX = outwardFaceDir.x;
                    pushZ = outwardFaceDir.z;
                }

                GrapnelClientTracker.renderTargetPos = new Vec3(smoothX + pushX, visualY, smoothZ + pushZ);
            }

            return Optional.of(smoothLedgeVector);
        }

        return Optional.empty();
    }

     @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // If the player simply switched hotbar slots, allow the normal swap animation
        if (slotChanged) return true;

        // If the data components changed because we started or stopped pulling, 
        // return FALSE! This stops the hand model from resetting and forces the texture to swap instantly!
        return false;
    }

}
