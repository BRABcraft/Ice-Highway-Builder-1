/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Edited by K-K-L-L (Discord:theorangedot).
 */

package K_K_L_L.IceRail.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.BlockBreakingCooldownEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import K_K_L_L.IceRail.addon.IceRail;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static K_K_L_L.IceRail.addon.Utils.switchToBestTool;
import static K_K_L_L.IceRail.addon.modules.IceHighwayBuilder.*;
import static K_K_L_L.IceRail.addon.modules.IceRailAutoEat.getIsEating;

public class IceRailNuker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final MinecraftClient mc = MinecraftClient.getInstance();
    static boolean isBreaking;
    static boolean isBreakingHardBlock;
    int tick = 0;

    IceHighwayBuilder iceHighwayBuilder = Modules.get().get(IceHighwayBuilder.class);
    Setting<Integer> delay = iceHighwayBuilder.nukerDelay;
    Setting<Integer> maxBlocksPerTick = iceHighwayBuilder.nukerMaxBlocksPerTick;
    Setting<Boolean> rotate = iceHighwayBuilder.nukerRotate;
    Setting<IceRailNuker.ListMode> listMode = iceHighwayBuilder.nukerListMode;
    Setting<List<Block>> blacklist = iceHighwayBuilder.nukerBlacklist;
    Setting<List<Block>> whitelist = iceHighwayBuilder.nukerWhitelist;
    Setting<Boolean> enableRenderBreaking = iceHighwayBuilder.nukerEnableRenderBreaking;
    Setting<ShapeMode> shapeModeBreak = iceHighwayBuilder.shapeModeBreak;
    Setting<SettingColor> sideColor = iceHighwayBuilder.sideColor;
    Setting<SettingColor> lineColor = iceHighwayBuilder.lineColor;
    Setting<Boolean> loweringFloor = iceHighwayBuilder.loweringFloor;
    Setting<Boolean> packetMine = iceHighwayBuilder.nukerPacketMine;

    private final List<BlockPos> blocks = new ArrayList<>();
    private boolean firstBlock;
    private final BlockPos.Mutable lastBlockPos = new BlockPos.Mutable();
    private int timer;
    private int noBlockTimer;

    public static boolean getIsBreaking() {
        return isBreaking;
    }

    public static void setIsBreaking(boolean value) {
        isBreaking = value;
    }


    public IceRailNuker() {
        super(IceRail.CATEGORY, "ice-rail-nuker", "A helper module that cleans the highway.");
    }

    private BlockPos getRegion1Start() {
        if (mc.player == null) return null;
        int Z = mc.player.getBlockZ();
        int X = mc.player.getBlockX();
        IceHighwayBuilder i = Modules.get().get(IceHighwayBuilder.class);
        if (!loweringFloor.get()) {
            return switch (i.highway.get()) {
                case North -> new BlockPos(playerX + 1, playerY,  Z - 2 + Math.abs(Z) % 2);
                case South -> new BlockPos(playerX + 1, playerY, Z + 2 - Math.abs(Z) % 2);
                case East -> new BlockPos(X + 2 - Math.abs(X) % 2, playerY, playerZ - 1);
                case West -> new BlockPos(X - 2 + Math.abs(X) % 2, playerY, playerZ - 1);
                default -> new BlockPos(0, 64, 0); // This shouldn't happen
            };
        } else {
            return switch (i.highway.get()) {
                case North -> new BlockPos(playerX + 1, playerY,  Z - 3);
                case South -> new BlockPos(playerX + 1, playerY, Z + 3);
                case East -> new BlockPos(X + 3, playerY, playerZ - 1);
                case West -> new BlockPos(X - 3, playerY, playerZ - 1);
                default -> new BlockPos(0, 64, 0); // This shouldn't happen
            };
        }
    }

    private BlockPos getRegion1End() {
        if (mc.player == null) return null;
        IceHighwayBuilder i = Modules.get().get(IceHighwayBuilder.class);

        return switch (i.highway.get()) {
            case North -> new BlockPos(playerX, playerY + 4, mc.player.getBlockZ() + 4);
            case South -> new BlockPos(playerX, playerY + 4, mc.player.getBlockZ() - 4);
            case East -> new BlockPos(mc.player.getBlockX() - 4, playerY + 4, playerZ);
            case West -> new BlockPos(mc.player.getBlockX() + 4, playerY + 4, playerZ);
            default -> new BlockPos(0, 64, 0); // This shouldn't happen
        };
    }

    private boolean isInRegion(BlockPos pos, BlockPos regionStart, BlockPos regionEnd) {
        if (regionStart == null || regionEnd == null) return false;
        int minX = Math.min(regionStart.getX(), regionEnd.getX());
        int maxX = Math.max(regionStart.getX(), regionEnd.getX());
        int minY = Math.min(regionStart.getY(), regionEnd.getY());
        int maxY = Math.max(regionStart.getY(), regionEnd.getY());
        int minZ = Math.min(regionStart.getZ(), regionEnd.getZ());
        int maxZ = Math.max(regionStart.getZ(), regionEnd.getZ());

        return pos.getX() >= minX && pos.getX() <= maxX &&
                pos.getY() >= minY && pos.getY() <= maxY &&
                pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private boolean isInAnyRegion(BlockPos pos) {
        return isInRegion(pos, getRegion1Start(), getRegion1End());
    }

    @Override
    public void onActivate() {
        firstBlock = true;
        timer = 0;
        noBlockTimer = 0;
        tick = 0;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        tick++;
        //if (tick % 2 == 0) { return; }
        IceHighwayBuilder IceHighwayBuilder = Modules.get().get(IceHighwayBuilder.class);
        if (!IceHighwayBuilder.isActive()) return;
        if (playerX == null || playerY == null || playerZ == null) return;
        if (isGoingToHighway || getIsEating() || mc.world.getDimension().bedWorks()) return;

        if (timer > 0) {
            timer--;
            return;
        }

        if (mc.player == null) return;
        if (getPlayerDirection() == null) return;

        BlockPos pos1 = getRegion1Start();
        BlockPos pos2 = getRegion1End();
        if (pos1 == null || pos2 == null) return;

        int maxWidth = Math.abs(pos2.getX() - pos1.getX()) + 1;
        int maxHeight = Math.abs(pos2.getY() - pos1.getY()) + 1;

        BlockIterator.register(maxWidth, maxHeight, (blockPos, blockState) -> {
            if (!isInAnyRegion(blockPos)) return;
            if (!BlockUtils.canBreak(blockPos, blockState)) return;
            if (!(blockState.getBlock() == Blocks.BLUE_ICE && blockPos.getY() != 115)) {
                if (!isBlueIceBlock(blockPos)) {
                    if (listMode.get() == ListMode.Whitelist && !whitelist.get().contains(blockState.getBlock()))return;
                    if (listMode.get() == ListMode.Blacklist && blacklist.get().contains(blockState.getBlock())) return;
                }
            }
            blocks.add(blockPos.toImmutable());
        });

        BlockIterator.after(() -> {
            blocks.sort(Comparator.comparingDouble(value -> -value.getY()));
            processBlocks();
        });
    }

    private boolean isBlueIceBlock(BlockPos block) {
        assert mc.world != null;
        return switch (getPlayerDirection()) {
            case NORTH, SOUTH -> block.getZ() % 2 == 0 && block.getX() == -200;
            case EAST, WEST -> block.getX() % 2 == 0 && block.getZ() == -200;
            default -> false;
        } && block.getY() == 115 && mc.world.getBlockState(block).getBlock() != Blocks.BLUE_ICE;
    }

    private void processBlocks() {
        assert mc.world != null;
        if (blocks.isEmpty()) {
            if (noBlockTimer++ >= delay.get()) {
                firstBlock = true;
                setIsBreaking(false);
            }
            return;
        } else {
            noBlockTimer = 0;
        }

        if (!firstBlock && !lastBlockPos.equals(blocks.getFirst())) {
            timer = delay.get();
            firstBlock = false;
            lastBlockPos.set(blocks.getFirst());
            if (timer > 0) return;
        }

        int count = 0;
        for (BlockPos block : blocks) {
            if (count >= maxBlocksPerTick.get() && block.getY() < 115) break;
            if (count >= 4 && block.getY() > 115) break;

            boolean canInstaMine = BlockUtils.canInstaBreak(block);
            Block blockType = mc.world.getBlockState(block).getBlock();
            breakBlock(block);

//            if (rotate.get()) {
//                Rotations.rotate(Rotations.getYaw(block), Rotations.getPitch(block), () -> breakBlock(block));
//            }

            if (enableRenderBreaking.get()) {
                RenderUtils.renderTickingBlock(block, sideColor.get(), lineColor.get(), shapeModeBreak.get(), 0, 8, true, false);
            }

            lastBlockPos.set(block);
            count++;
            if (!canInstaMine) break;
        }

        firstBlock = false;
        blocks.clear();
    }

    private void breakBlock(BlockPos blockPos) {
        assert mc.world != null;
        assert mc.player != null;
        if (mc.world.isAir(blockPos)) return;
        if (blockPos == null) return;
        setIsBreaking(true);
        switchToBestTool(blockPos);
        if (!packetMine.get()) {
            BlockUtils.breakBlock(blockPos, true);
        } else {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos, BlockUtils.getDirection(blockPos)));
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, BlockUtils.getDirection(blockPos)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onBlockBreakingCooldown(BlockBreakingCooldownEvent event) {
        event.cooldown = 0;
    }

    public enum ListMode {
        Whitelist,
        Blacklist
    }
}
 