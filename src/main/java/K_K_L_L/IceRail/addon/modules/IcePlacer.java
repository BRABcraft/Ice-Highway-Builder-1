package K_K_L_L.IceRail.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import K_K_L_L.IceRail.addon.IceRail;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.fluid.FluidState;
import net.minecraft.block.BlockState;

import static K_K_L_L.IceRail.addon.Utils.airPlace;
import static K_K_L_L.IceRail.addon.Utils.switchToItem;
import static K_K_L_L.IceRail.addon.modules.IceHighwayBuilder.*;
import static K_K_L_L.IceRail.addon.modules.IceRailAutoEat.getIsEating;

public class IcePlacer extends Module {
    public IcePlacer() {
        super(IceRail.CATEGORY, "ice-placer", "Places ice blocks with air gaps between them.");
    }
    int tick = 0;
    public static BlockPos targetPos;

    IceHighwayBuilder iceHighwayBuilder = Modules.get().get(IceHighwayBuilder.class);
    Setting<Boolean> airPlaceBlueIce = iceHighwayBuilder.airPlaceBlueIce;

    private boolean place(Item item, BlockPos blockPos, boolean onlyOnLava) {
        assert mc.world != null;
        boolean condition = onlyOnLava ? !mc.world.getBlockState(blockPos).getFluidState().isEmpty() : mc.world.isAir(blockPos);
        if (condition) {
            switchToItem(item);
//            airPlace(blockPos, switch (getPlayerDirection()) {
//                case NORTH, SOUTH -> Direction.EAST;
//                case EAST, WEST -> Direction.NORTH;
//                default -> null;
//            });
            mc.options.attackKey.setPressed(false);
            airPlace(blockPos, Direction.DOWN);
            return true;
        }
        return false;
    }

    private boolean sourceRemover() {
        assert mc.player != null;
        assert mc.world != null;
        int X = mc.player.getBlockX();
        int Y = mc.player.getBlockY();
        int Z = mc.player.getBlockZ();
        for (int x = X - 2; x <= X + 1; x++) {
            for (int y = Y - 1; y <= Y + 4; y++) {
                for (int z = Z - 2; z <= Z + 2; z++) {
                    BlockPos block = new BlockPos(x, y, z);
                    if (mc.world.getBlockState(block).getBlock() == Blocks.LAVA) {
                        switchToItem(Items.NETHERRACK);
                        airPlace(block, Direction.DOWN);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        tick++;
        //if (tick % 3 < 2) return;
        IceHighwayBuilder IceHighwayBuilder = Modules.get().get(IceHighwayBuilder.class);
        if (!IceHighwayBuilder.isActive()) return;
        playerX = mc.player.getBlockX();
        playerY = mc.player.getBlockY();
        playerZ = mc.player.getBlockZ();
        if (isGoingToHighway || getIsEating()) return;

        Direction direction = getPlayerDirection();
        if (direction == null) {
            toggle();
            return;
        }

        boolean shouldPlace;
        BlockPos targetPos2, guardrail1, guardrail2;

        switch (direction) {
            case NORTH -> {
                targetPos = new BlockPos(playerX + 1, playerY + 1, mc.player.getBlockZ() - 2);
                targetPos2 = new BlockPos(playerX + 2, playerY + 1, mc.player.getBlockZ() - 2);
                guardrail1 = new BlockPos(playerX - 1, playerY + 2, mc.player.getBlockZ() - 2);
                guardrail2 = new BlockPos(playerX + 2, playerY + 2, mc.player.getBlockZ() - 2);
                shouldPlace = Math.abs(mc.player.getBlockZ()) % 2 == 0;
            }
            case SOUTH -> {
                targetPos = new BlockPos(playerX + 1, playerY + 1, mc.player.getBlockZ() + 2);
                targetPos2 = new BlockPos(playerX + 2, playerY + 1, mc.player.getBlockZ() + 2);
                guardrail1 = new BlockPos(playerX - 1, playerY + 2, mc.player.getBlockZ() + 2);
                guardrail2 = new BlockPos(playerX + 2, playerY + 2, mc.player.getBlockZ() + 2);
                shouldPlace = Math.abs(mc.player.getBlockZ()) % 2 == 0;
            }
            case WEST -> {
                targetPos = new BlockPos(mc.player.getBlockX() - 2, playerY + 1, playerZ - 1);
                targetPos2 = new BlockPos(mc.player.getBlockX() - 2, playerY + 1, playerZ - 2);
                guardrail1 = new BlockPos(mc.player.getBlockX() - 2, playerY + 2, playerZ - 2);
                guardrail2 = new BlockPos(mc.player.getBlockX() - 2, playerY + 2, playerZ + 1);
                shouldPlace = Math.abs(mc.player.getBlockX()) % 2 == 0;
            }
            case EAST -> {
                targetPos = new BlockPos(mc.player.getBlockX() + 2, playerY + 1, playerZ - 1);
                targetPos2 = new BlockPos(mc.player.getBlockX() + 2, playerY + 1, playerZ - 2);
                guardrail1 = new BlockPos(mc.player.getBlockX() + 2, playerY + 2, playerZ - 2);
                guardrail2 = new BlockPos(mc.player.getBlockX() + 2, playerY + 2, playerZ + 1);
                shouldPlace = Math.abs(mc.player.getBlockX()) % 2 == 0;
            }
            default -> {
                return;
            }
        }
        if (sourceRemover()) return;
        if (place(Items.NETHERRACK, guardrail1, false)) return;
        if (place(Items.NETHERRACK, guardrail2, false)) return;
        if (place(Items.NETHERRACK, guardrail1.up(-1), true)) return;
        if (place(Items.NETHERRACK, guardrail2.up(-1), true)) return;
        if (shouldPlace) {
            if (place(Items.NETHERRACK, targetPos2, false)) return;
            if (airPlaceBlueIce.get()) {
                place(Items.BLUE_ICE, targetPos, false);
            } else {
                if (mc.world.isAir(targetPos)) switchToItem(Items.BLUE_ICE);
                BlockUtils.place(targetPos, InvUtils.findInHotbar(itemStack ->
                        itemStack.getItem() == Items.BLUE_ICE), false, 0, true, true);
            }
        }
    }
}
