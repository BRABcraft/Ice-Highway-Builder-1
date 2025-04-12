package K_K_L_L.IceRail.addon.modules;
import static K_K_L_L.IceRail.addon.Utils.*;
import static K_K_L_L.IceRail.addon.Utils.setKeyPressed;
import static K_K_L_L.IceRail.addon.modules.IceHighwayBuilder.*;
import static K_K_L_L.IceRail.addon.modules.IceRailAutoEat.getIsEating;


import K_K_L_L.IceRail.addon.IceRail;


import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;

public class PickaxeRepairer extends Module {
    MinecraftClient mc = MinecraftClient.getInstance();
    //Record starting coordinates
    //Use a combination of highway elytra bounce, blue ice boating, and open nether to reach a specified nether portal coordinate
    //Go through the nether portal, then fly towards a specified overworld coordinate (the block to stand on while grinding)
    //Place down a pickaxe shulker and retreive all broken pickaxes
    //Bring a non-full durability tool to offhand if the offhand is not a tool or has a full durability tool (recode automend basically)
    //Swap to a sword
    //Enable killaura in meteor
    //If there is no non-full durability tool in inventory, dump into shulker and check for another shulker
    //Also check for shulkers in echest
    //If all tools have full durability: Fly back to the starting coordinates using same route
    public PickaxeRepairer() {
        super(IceRail.CATEGORY, "pickaxe-repairer", "Automatically repairs pickaxes and gear at a user-specified mob farm.");
    }
    private final SettingGroup sgGrinderPos = settings.createGroup("Grinder Position");
    private final Setting<Integer> grinderX = sgGrinderPos.add(new IntSetting.Builder()
            .name("Grinder x")
            .description("X coordinate to stand on when grinding.")
            .defaultValue(0)
            .build());

    private final Setting<Integer> grinderY = sgGrinderPos.add(new IntSetting.Builder()
            .name("Grinder y")
            .description("Y coordinate to stand on when grinding.")
            .defaultValue(64)
            .min(-64)
            .max(320)
            .sliderRange(-64,320)
            .build());

    private final Setting<Integer> grinderZ = sgGrinderPos.add(new IntSetting.Builder()
            .name("Grinder z")
            .description("Z coordinate to stand on when grinding.")
            .defaultValue(0)
            .build());
}
