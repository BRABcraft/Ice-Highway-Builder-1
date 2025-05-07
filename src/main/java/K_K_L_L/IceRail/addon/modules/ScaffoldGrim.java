
package K_K_L_L.IceRail.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import K_K_L_L.IceRail.addon.IceRail;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Position;

import java.util.List;

import static K_K_L_L.IceRail.addon.Utils.*;
import static K_K_L_L.IceRail.addon.modules.IceHighwayBuilder.*;

public class ScaffoldGrim extends Module {
    public ScaffoldGrim() {
        super(IceRail.CATEGORY, "scaffold-grim", "Places blocks in front of you.");
    }

    private final SettingGroup sgGeneral = settings.createGroup("General");

    public final Setting<ScaffoldGrim.ListMode> blocksFilter = sgGeneral.add(new EnumSetting.Builder<ScaffoldGrim.ListMode>()
            .name("blocks-filter")
            .description("How to use the block list setting")
            .defaultValue(ScaffoldGrim.ListMode.Blacklist)
            .build()
    );

    public final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
            .name("blocks")
            .description("Selected blocks.")
            .defaultValue(
                    Blocks.BLUE_ICE,
                    Blocks.ENDER_CHEST,
                    Blocks.SHULKER_BOX,
                    Blocks.WHITE_SHULKER_BOX,
                    Blocks.ORANGE_SHULKER_BOX,
                    Blocks.MAGENTA_SHULKER_BOX,
                    Blocks.LIGHT_BLUE_SHULKER_BOX,
                    Blocks.YELLOW_SHULKER_BOX,
                    Blocks.LIME_SHULKER_BOX,
                    Blocks.PINK_SHULKER_BOX,
                    Blocks.GRAY_SHULKER_BOX,
                    Blocks.LIGHT_GRAY_SHULKER_BOX,
                    Blocks.CYAN_SHULKER_BOX,
                    Blocks.PURPLE_SHULKER_BOX,
                    Blocks.BLUE_SHULKER_BOX,
                    Blocks.BROWN_SHULKER_BOX,
                    Blocks.GREEN_SHULKER_BOX,
                    Blocks.RED_SHULKER_BOX,
                    Blocks.BLACK_SHULKER_BOX
            )
            .build()
    );

    private int tickCounter = 0;

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        assert mc.world != null;
        tickCounter++;

        // Only place a block every 3 ticks to avoid rubberband
        if (tickCounter % 4 != 0) return;
        if (!isActive()) return;
        playerDirection = mc.player.getHorizontalFacing();
        FindItemResult item = null;

        // Find a block in the player's inventory
        for (int i = 0; i < 9; i++) {
            Item foundItem = mc.player.getInventory().getStack(i).getItem();
            if (foundItem instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();

                // Check blacklist/whitelist
                if (blocksFilter.get() == ListMode.Blacklist && blocks.get().contains(block)) continue;
                if (blocksFilter.get() == ListMode.Whitelist && !blocks.get().contains(block)) continue;
                item = InvUtils.findInHotbar(itemStack -> itemStack.getItem() == Items.NETHERRACK);
                break;
            }
        }

        if (item == null) return;
        line(0, -1, false);
//        line(1, -1, false);
//        line(-1, 2, false);
//        line(2, 2, false);
//        line(-1, 1, true);
    }

    private void line(int shift, int raise, boolean skipOdd) {
        assert mc.player != null;
        BlockPos playerPos = mc.player.getBlockPos();
        for (int offset = 0; offset <= 3; offset++) {
            IceHighwayBuilder i = Modules.get().get(IceHighwayBuilder.class);
            BlockPos targetPos = switch (i.highway.get()) {
                case North -> playerPos.add(-shift, raise, -offset);
                case South -> playerPos.add(shift, raise, offset);
                case East -> playerPos.add(offset, raise, shift);
                case West -> playerPos.add(-offset, raise, -shift);
                default -> null;
            };
            assert targetPos != null;
            boolean shouldPlace = switch (i.highway.get()) {
                case North, South -> targetPos.getZ() % 2 == 0;
                case East, West -> targetPos.getX() % 2 == 0;
                default -> false;
            };
            if (skipOdd && !shouldPlace) continue;

            if (targetPos == null) return;
            if (mc.getNetworkHandler() == null) return;
            assert mc.world != null;
            if (mc.world.getBlockState(targetPos).getBlock() == Blocks.SOUL_SAND) {
                BlockUtils.breakBlock(targetPos, true);
            }
            BlockState blockstate = mc.world.getBlockState(targetPos);
            Block block = blockstate.getBlock();
            if (blockstate.isAir() || block == Blocks.FIRE || !mc.world.getFluidState(targetPos).isEmpty()) {
                //InvUtils.swap(InvUtils.findInHotbar(itemStack -> itemStack.getItem() == Items.NETHERRACK).slot(), false);
                airPlace(Items.NETHERRACK, targetPos, Direction.DOWN);
            }
        }
    }

    public enum ListMode {
        Whitelist,
        Blacklist
    }
}
