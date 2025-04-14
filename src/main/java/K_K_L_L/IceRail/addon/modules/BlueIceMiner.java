package K_K_L_L.IceRail.addon.modules;
import static K_K_L_L.IceRail.addon.Utils.*;
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
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.*;

public class BlueIceMiner extends Module {
    public static ArrayList<Object> groups = null;
    public static ArrayList<Integer> validGroups = null;
    public static ArrayList<Double> iceBergDistances = null;
    public static boolean miningIce = false;
    public static String state = "idle";
    public static String returnToState = "idle";
    public static int retrieveCount;
    public static Item retrieveItem;
    public static int retrieveType;
    public static String NewState;
    public static int foundCount;
    private int slotNumber;
    private int stealingDelay = 0;
    public static int repairCount = 0;
    public static BlockPos portalOriginBlock = null;
    public static int buildTimer = 0;
    public static final int SEARCH_RADIUS = 90;
    private static final int MAX_Y = 122;
    public static ArrayList<BlockPos> portalBlocks = new ArrayList<BlockPos>();
    public static ArrayList<Integer> portalObby = new ArrayList<Integer>();
    public static boolean scanningWorld = true;
    public int tick = 0;
    public static boolean isPathing = false;
    public static DimensionType dimension;
    boolean reached = false;
    public static boolean foundBlock;
    public static int leg = 0;
    public static BlockPos vertex;
    public static BlockPos landCoords;
    public static int prevCount;
    public boolean hasFoundFrozenOcean = false;
    public static int currentIceberg = 0;
    public static int initialEchests;
    private int echestSlot = -1;
    private int slice_maxY = 0;
    private int slice_minY = 0;
    private boolean wasGathering = false;
    private boolean wasBreathing;

    MinecraftClient mc = MinecraftClient.getInstance();

    public BlueIceMiner() {
        super(IceRail.CATEGORY, "blue-ice-miner", "Automatically finds and mines more blue ice when you run out.");
    }


    private final SettingGroup sgToggle = settings.createGroup("Toggle");
    private final SettingGroup sgPortal = settings.createGroup("Nether Portals");
    private final SettingGroup sgMining = settings.createGroup("Mining");
    private final SettingGroup sgPickaxeRepairer = settings.createGroup("PickaxeRepairer");
    // Toggle settings
    private final Setting<Boolean> autoToggle = sgToggle.add(new BoolSetting.Builder()
            .name("auto-toggle")
            .description("Turns on when out of blue ice.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Integer> minDistanceToSpawn = sgToggle.add(new IntSetting.Builder()
            .name("min-distance-to-spawn")
            .description("Minimum nether distance from spawn that Auto Toggle will turn on to avoid old chunks.")
            .defaultValue(30000)
            .min(0)
            .max(100000)
            .sliderRange(0, 100000)
            .visible(autoToggle::get)
            .build()
    );
    // Nether Portal settings
    private final Setting<Boolean> buildNetherPortal = sgPortal.add(new BoolSetting.Builder()
            .name("build-nether-portal")
            .description("Builds a portal if none are in render distance.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> searchOnHighway = sgPortal.add(new BoolSetting.Builder()
            .name("search-on-highway")
            .description("Searches for portals on the highway if none are found in render distance.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Integer> cruiseAltitude = sgPortal.add(new IntSetting.Builder()
            .name("cruise-altitude")
            .description("Distance to maintain while flying to a cold ocean.")
            .defaultValue(400)
            .min(0)
            .max(2000)
            .sliderRange(0, 1000)
            .build()
    );
    // Blue Ice mining settings
    private final Setting<Integer> groupsizeThreshold = sgMining.add(new IntSetting.Builder()
            .name("iceberg-size-threshold")
            .description("Mines the iceberg if it has at least this much blue ice.")
            .defaultValue(250)
            .min(0)
            .max(1000)
            .sliderRange(0, 500)
            .build()
    );
    private final Setting<Integer> sliceHeight = sgMining.add(new IntSetting.Builder()
            .name("slice-height")
            .description("Number of iceberg Y level layers to mine at once.")
            .defaultValue(3)
            .min(1)
            .max(4)
            .sliderRange(1, 4)
            .build()
    );
    private final Setting<Boolean> enablePickaxeRepairer = sgPickaxeRepairer.add(new BoolSetting.Builder()
            .name("double-mine")
            .description("Enables pickaxe repairer when there are too few pickaxes.")
            .defaultValue(true)
            .build()
    );
    @Override
    public void onActivate() {
        assert mc.world != null;
        dimension = mc.world.getDimension();
        Module scaffoldGrim = Modules.get().get("scaffold-grim");
        if (dimension.bedWorks()) {
            state = "flyToBlueIce";
            if (scaffoldGrim.isActive()) {
                scaffoldGrim.toggle();
            }
        } else {
            state = "goToPortal";
            if (!scaffoldGrim.isActive()) {
                scaffoldGrim.toggle();
            }
        }
        scanningWorld = true;
        mc.options.attackKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        wasGathering = false;
    }
    @Override
    public void onDeactivate() {
        mc.options.attackKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        wasGathering = false;
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tick++;

        // Validity checks
        if (mc.player == null || mc.world == null) {return;}
        if (!isActive()) return;
        if (state == null || state.equals("idle")) {isPathing = false; return;}

        error(state);
        IceHighwayBuilder iceHighwayBuilder = Modules.get().get(IceHighwayBuilder.class);


        if (dimension != mc.world.getDimension()) {
            onChangedDimension();
        }
        if (state.equals("retrievingFromShulker")) {
            handleItemRetrieve(retrieveCount, retrieveItem);
            return;
        }
        if (state.equals("clearInventory")) {
            iceHighwayBuilder.handleClearInventory();
            return;
        }
        if (state.equals("waitingForPostRestock") || state.equals("waitingForGather")) {
            return;
        }
        if (state.equals("miningEchests")) {
            handleMiningEchests();
            return;
        }
        if (state.equals("goToPortal")) {
            handleGoToPortal();
            return;
        }
        if (state.equals("buildPortal")) {
            handleBuildPortal();
            return;
        }
        if (state.equals("flyToBlueIce")) {
            handleFlyToBlueIce();
            return;
        }
        if (state.equals("land")) {
            handleLand();
            return;
        }

        //Hostile mob elimination methods
        pathToTridentDrowned();
        if (killaura()) {
            error("killaura");
            return;
        }
        if (state.equals("goToBlueIce")) {
            handleGoToBlueIce();
            return;
        }
        if (state.equals("mineBlueIceInRange")) {
            handleMineBlueIceInRange();
            return;
        }
    }
    private boolean isTargetMob(LivingEntity entity){
        if (entity instanceof EndermanEntity enderman && !enderman.isAngry()) return false;
        if (entity instanceof PolarBearEntity) return true;
        return (entity instanceof HostileEntity);
    }
    private boolean killaura() {
        assert mc.world != null;
        assert mc.player != null;
        List<LivingEntity> killRange = mc.world.getEntitiesByClass(
                LivingEntity.class,
                mc.player.getBoundingBox().expand(2),
                this::isTargetMob);
        if (killRange == null || killRange.isEmpty()) {
            return false;
        }
        //<editor-fold desc="Get sword">
        int swordSlot = -1;
        for (int i = 0; i < 9; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            if (item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD) {
                swordSlot = i;
                break;
            }
        }
        if (swordSlot == -1) {
            for (int i = 9; i < 36; i++) {
                Item item = mc.player.getInventory().getStack(i).getItem();
                if (item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD) {
                    InvUtils.quickSwap().fromId(5).toId(i);
                    prevCount = mc.player.getInventory().getStack(swordSlot).getCount();
                    swordSlot = 5;
                    break;
                }
            }
        }
        //</editor-fold>
        boolean ret = false;
        for (LivingEntity entity : killRange) {
            //if cannot see, continue
            if (PlayerUtils.canSeeEntity(entity)) {
                Rotations.rotate(Rotations.getYaw(entity), Rotations.getPitch(entity, Target.Body));
                error("rotated towards entity");
                if (PlayerUtils.distanceTo(entity) <= 4 && tick % 10 == 0) {
                    assert mc.interactionManager != null;
                    InvUtils.swap(swordSlot, false);
                    mc.interactionManager.attackEntity(mc.player, entity);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
                ret = true;
            }
        }
        return ret;
    }

    private void pathToTridentDrowned() {
        assert mc.world != null;
        assert mc.player != null;
        List<LivingEntity> targets = mc.world.getEntitiesByClass(
                LivingEntity.class,
                mc.player.getBoundingBox().expand(16),
                e -> e instanceof ZombieEntity);
        if (targets == null || targets.isEmpty()) {
            return;
        }
        LivingEntity target = null;
        for (LivingEntity entity : targets) {
            if (entity.getMainHandStack().getItem() == Items.TRIDENT) {
                target = entity;
                break;
            }
        }
        if (target == null) return;
        BlockPos targetLocation = target.getBlockPos();
        if (PlayerUtils.isWithin(targetLocation, 2.0)) return;
        mc.player.setYaw((float) Rotations.getYaw(targetLocation));
        mc.player.setPitch((float) Rotations.getPitch(targetLocation));
        mc.options.forwardKey.setPressed(true);
    }

    private void steal(ScreenHandler handler, int slot_number) {
        MeteorExecutor.execute(() -> moveSlots(handler, slot_number));
    }

    private void moveSlots(ScreenHandler handler, int i) {
        if (handler.getSlot(i).hasStack() && Utils.canUpdate()) {
            InvUtils.shiftClick().slotId(i);
            stacksStolen++;
        }
    }

    private boolean isNotSilkPick(ItemStack stack) {
        return (stack.getItem() instanceof PickaxeItem && stack.getDamage() < stack.getMaxDamage() - 50
                && !Utils.hasEnchantments(stack, Enchantments.SILK_TOUCH));
    }

    private boolean condition(ItemStack stack, int type, Item item) {
        if (type == 0 || type > 2) {
            if (item == Items.OBSIDIAN) {
                return (stack.getItem() == item && stack.getCount() > 15);
            } else if (item == Items.ENDER_CHEST){
                return (stack.getItem() == item && stack.getCount() > 1);
            } else {
                return (stack.getItem() == item);
            }
        } else if (type == 1) {
            return isNotSilkPick(stack);
        } else {
            if (stack.getItem() == Items.DIAMOND_PICKAXE || stack.getItem() == Items.NETHERITE_PICKAXE) {
                if (stack.getDamage() < stack.getMaxDamage() - 50) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean search(Item item, int slot, int type) {
        MinecraftClient mc = MinecraftClient.getInstance();
        assert (mc.player != null);
        Inventory inventory = mc.player.getInventory();
        ItemStack hotbarStack = inventory.getStack(slot);

        foundCount = 0;
        if (condition(hotbarStack, type, item)) {
            foundCount = 1;
            return true; // The slot already has the right item
        }
        int bestSlot = -1;

        for (int i = 0; i < inventory.size(); i++) {
            if (i == slot) continue; // Skip the  slot that has already been checked
            ItemStack stack = inventory.getStack(i);

            if (condition(stack, type, item)) {
                if (bestSlot == -1) {
                    bestSlot = i;
                }
                foundCount ++;
            }
        }
        if (bestSlot != -1) {
            if (type != 2) {
                if (type < 2) {InvUtils.quickSwap().fromId(slot).toId(bestSlot);}
                return true;
            }
        }
        //search the shulkers in the inventory
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock) {

                boolean Return = false;
                ItemStack[] containerItems = new ItemStack[27];
                Utils.getItemsInContainerItem(stack, containerItems);

                for (ItemStack stack1 : containerItems) {
                    if (!stack1.isEmpty() && condition(stack1, type, item)) {
                        Return = true;
                        foundCount++;
                    }
                }
                if (Return) {
                    if (bestSlot == -1) {
                        bestSlot = i;
                    }

                }
            }
        }
        if (type != 2) {
            if (bestSlot != -1) {
                if (bestSlot > 9) {
                    InvUtils.quickSwap().fromId(5).toId(bestSlot);
                } else {
                    InvUtils.quickSwap().fromId(bestSlot).toId(9);
                    InvUtils.quickSwap().fromId(5).toId(9);
                }
                isPathing = false;
                shulkerRestock(item == Items.FIREWORK_ROCKET ? 2 : 1, item, type);
                return true;
            } else {
                return false;
            }
        } else {
            return foundCount < 5;
        }
    }

    public static List<BlockPos> searchWorld(Block blockType) {
        final MinecraftClient mc = MinecraftClient.getInstance();
        // Get the player's current position
        assert mc.player != null;
        BlockPos playerPos = mc.player.getBlockPos();

        // Get the current render distance in chunks and convert it to block distance (16 blocks per chunk)
        int renderDistance = 6 * 16;

        List<BlockPos> blockPosList = new ArrayList<>();
        int minY = blockType == Blocks.BLUE_ICE ? 40 : 32;
        int maxY = blockType == Blocks.BLUE_ICE ? 80 : 122;
        // Iterate through blocks in the render distance around the player
        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = -renderDistance; z <= renderDistance; z++) {
                    // Calculate the BlockPos for each block
                    BlockPos currentPos = playerPos.east(x).withY(y).south(z);
                    if (mc.world.getBlockState(currentPos).getBlock() == blockType) {
                        blockPosList.add(currentPos);
                    }
                }
            }
        }
        return blockPosList;
    }

    public void getBlockGroups(Block block) {
        List<BlockPos> locations = searchWorld(block);
        double distance;
        double smallestDistance;
        int xDiff, zDiff, iceBergIndex = 0;
        groups = new ArrayList<>();
        validGroups = new ArrayList<>();
        iceBergDistances = new ArrayList<>();
        for (BlockPos foundIce : locations) {
            smallestDistance = Double.MAX_VALUE;
            for (int i = 0; i < groups.size(); i+=2) {
                BlockPos block2 = (BlockPos) groups.get(i);
                xDiff = foundIce.getX() - block2.getX();
                zDiff = foundIce.getZ() - block2.getZ();
                distance = Math.sqrt(Math.pow(xDiff, 2) + Math.pow(zDiff, 2));
                if (distance < smallestDistance) {
                    smallestDistance = distance;
                    iceBergIndex = i;
                }
            }
            if (smallestDistance > 5 || groups.isEmpty()) {
                groups.add(new BlockPos(foundIce));
                groups.add(1);
            } else {
                if (((BlockPos) groups.get(iceBergIndex)).getY() < foundIce.getY()) {
                    groups.set(iceBergIndex, new BlockPos(foundIce));
                }
                groups.set(iceBergIndex + 1, (Integer) groups.get(iceBergIndex + 1) + 1);
            }
        }
        error("found groups at line 476: " + groups);
        for (int i=0; i < groups.size(); i+=2){
            if ((int)groups.get(i+1) > groupsizeThreshold.get() ||
                    (PlayerUtils.isWithin(((BlockPos) groups.get(i)),8.0) && ((int) groups.get(i+1)) > 10)) {
                validGroups.add(i);
            }
        }
        ArrayList<Object> temporary = new ArrayList<>();
        assert mc.player != null;
        for (int i : validGroups) {
            BlockPos block1 = (BlockPos) groups.get(i);
            double xSquared = Math.pow(block1.getX()-mc.player.getX(), 2);
            double zSquared = Math.pow(block1.getZ()-mc.player.getZ(), 2);
            iceBergDistances.add(Math.sqrt(xSquared+zSquared));
            temporary.add(block1);
            temporary.add(groups.get(i+1));
        }
        groups = temporary;
    }

    public BlockPos nearestGroupCoords(boolean updateCurrentIceberg){
        assert mc.player != null;
        int j = 0;
        double min = Double.MAX_VALUE;
        for (int i = 0; i<iceBergDistances.size(); i++) {
            if (iceBergDistances.get(i) < min) {
                j = i;
                min = iceBergDistances.get(i);
            }
        }
        if (updateCurrentIceberg) {
            currentIceberg = j*2;
        }
        return (BlockPos) groups.get(j*2);
    }
    private void disableAllModules() {
        String[] modulesToDisable = {
                "ice-rail-gather-item",
                "ice-placer",
                "ice-rail-auto-replenish",
                "ice-rail-nuker",
                "scaffold-grim",
                "ice-highway-builder"
        };

        for (String moduleName : modulesToDisable) {
            Module module = Modules.get().get(moduleName);
            if (module != null && module.isActive()) {
                module.toggle();
            }
        }
    }
    public static void packetMine(BlockPos block) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, block, BlockUtils.getDirection(block)));
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, block, BlockUtils.getDirection(block)));
    }
    public static boolean isInColdBiome(){
        MinecraftClient mc = MinecraftClient.getInstance();
        assert mc.player != null;
        assert mc.world != null;
        RegistryEntry<Biome> biome = mc.world.getBiome(mc.player.getBlockPos());
        return biome.getKey().equals(Optional.of(BiomeKeys.COLD_OCEAN)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.DEEP_COLD_OCEAN)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.DEEP_FROZEN_OCEAN)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.FROZEN_OCEAN)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.ICE_SPIKES)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.SNOWY_BEACH)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.SNOWY_PLAINS)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.SNOWY_TAIGA)) ||
                biome.getKey().equals(Optional.of(BiomeKeys.TAIGA));
    }
    public ArrayList<BlockPos> getBlueIceInRange() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ArrayList<BlockPos> blockPosList = new ArrayList<>();

        assert mc.player != null;
        assert mc.world != null;
        BlockPos playerPos = mc.player.getBlockPos();
        int sx = playerPos.getX();
        int sz = playerPos.getZ();

        for (int j = 1; j <= 3; j++) {
            for (int y = slice_maxY; y >= slice_minY; y--) {
                for (int z = sz - j; z <= sz + j; z++) {
                    for (int x = sx - j; x <= sx + j; x++) {
                        BlockPos blockPos = new BlockPos(x, y, z);
                        if (mc.world.getBlockState(blockPos).getBlock() == Blocks.BLUE_ICE &&
                                !blockPosList.contains(blockPos) && PlayerUtils.isWithin(blockPos, 3.5)) {
                            blockPosList.add(blockPos);
                        }
                    }
                }
            }
        }
        return blockPosList;
    }

    private ArrayList<BlockPos> getIcebergSlice() {
        BlockPos playerPos = mc.player.getBlockPos();
        int sx = playerPos.getX();
        int sz = playerPos.getZ();
        ArrayList<BlockPos> slice = new ArrayList<>();
        for(int y = slice_maxY; y >= slice_minY; y--) {
            for(int x = sx - 7; x <= sx + 7; x++) {
                for(int z = sz - 7; z <= sz + 7; z++) {
                    BlockPos block = new BlockPos(x, y, z);
                    if (PlayerUtils.isWithin(block, 7.0) && mc.world.getBlockState(block).getBlock() == Blocks.BLUE_ICE) {
                        slice.add(block);
                    }
                }
            }
        }
        return slice;
    }

    public static int getMaxY(BlockPos block) {
        MinecraftClient mc = MinecraftClient.getInstance();
        assert mc.player != null;
        assert mc.world != null;

        int j = 200;
        while (mc.world.getBlockState(mc.player.getBlockPos().withY(j)).isAir()) {
            j --;
        }
        return j;
    }
    public static int getEchestSlot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int echestSlot = -1;
        for (int i = 0; i < 9; i++) {
            assert mc.player != null;
            if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_CHEST) {
                echestSlot = i;
                break;
            }
        }
        if (echestSlot == -1) {
            for (int i = 9; i < 36; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_CHEST) {
                    InvUtils.quickSwap().fromId(4).toId(i);
                    echestSlot = 4;
                    break;
                }
            }
        }
        return echestSlot;
    }

    private void onChangedDimension() {
        assert mc.player != null;
        assert mc.world != null;

        scanningWorld = true;
        leg = 0;
        Module scaffoldGrim = Modules.get().get("scaffold-grim");
        if (!dimension.bedWorks()) {
            state = "flyToBlueIce";
            hasFoundFrozenOcean = false;
            mc.player.setPitch((float) -45.0);
            if (scaffoldGrim.isActive()) {
                scaffoldGrim.toggle();
            }
        } else {
            state = "resumeBuilding";
            if (!scaffoldGrim.isActive()) {
                scaffoldGrim.toggle();
            }
        }
        dimension = mc.world.getDimension();
    }

    private void handleMiningEchests() {
        assert mc.player != null;
        assert mc.world != null;

        if (getEchestSlot() != -1) {echestSlot = getEchestSlot();}

        if (!mc.player.getBlockPos().equals(restockingStartPosition)) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(restockingStartPosition));
            resumeBaritone();
            return;
        }
        if (mc.world.getBlockState(shulkerBlockPos).getBlock() != Blocks.ENDER_CHEST) {
            BlockUtils.place(shulkerBlockPos, InvUtils.findInHotbar(itemStack -> itemStack.getItem() == Items.ENDER_CHEST), true, 0, true, true);
        } else {
            int swapSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (isNotSilkPick(mc.player.getInventory().getStack(i))) {
                    swapSlot = i;
                    break;
                }
            }
            if (swapSlot == -1) {
                for (int i = 9; i < 36; i++) {
                    if (isNotSilkPick(mc.player.getInventory().getStack(i))) {
                        InvUtils.quickSwap().fromId(3).toId(i);
                        swapSlot = 3;
                        break;
                    }
                }
            }
            InvUtils.swap(swapSlot,false);
            assert mc.getNetworkHandler() != null;
            lookAtBlock(shulkerBlockPos);
            packetMine(shulkerBlockPos);
        }
        if (initialEchests - mc.player.getInventory().getStack(echestSlot).getCount() > 0) {
            restockingType = 0;
            slotNumber = 0;
            stealingDelay = 0;
            oldYaw = mc.player.getYaw();
            state = "clearInventory";
        }
    }

    private void handleGoToPortal() {
        assert mc.player != null;
        assert mc.world != null;

        //check if there are under 5 usable pickaxes
        reached = false;
        if (search(null, 6, 2) && countUsablePickaxes() < 5) {
            if (search(null, 5, 1)) {
                //check if there are non-silk pickaxes to get xp
                returnToState = "goToPortal";
                repairCount = 5;
                //Toggle off and activate PickaxeRepairer if the setting is true
                return;
            } else {
                error("Too low on pickaxes and no non-silk pickaxes for repairing.");
                disableAllModules();
                isPathing = false;
                toggle();
                return;
            }
        }
        if (!search(Items.ELYTRA, 6, 3)) {
            error("No elytra, cannot fly to blue ice.");
            disableAllModules();
            toggle();
            return;
        } else {
            boolean test = false;
            for (int i = 0; i < mc.player.getInventory().size(); i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == Items.ELYTRA) {
                    test = true;
                    break;
                }
            }
            if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {test = true;}
            if (test) {
                if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                    for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
                        ItemStack item = mc.player.getInventory().main.get(i);

                        if (item.getItem() == Items.ELYTRA) {
                            InvUtils.move().from(i).toArmor(2);
                            break;
                        }
                    }
                }
            } else {
                error("no elytra found");
                return;
            }
        }
        if (!search(Items.FIREWORK_ROCKET, 4, 3)) {
            error("No firework rockets, cannot fly to blue ice.");
            disableAllModules();
            toggle();
            return;
        } else {
            int slot = -1;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == Items.FIREWORK_ROCKET) {
                    slot = i;
                    break;
                }
            }
            if (slot > 8) {
                InvUtils.quickSwap().fromId(4).toId(slot);
                return;}
        }

        //search render distance for nether_portal, only search once to avoid lag
        if (scanningWorld) {
            scanningWorld = false;
            foundBlock = !searchWorld(Blocks.NETHER_PORTAL).isEmpty();
            error("scanningWorld found? " + foundBlock);
        }
        //go to the coords of the nearest group of nether portal frames if the player isn't there already
        if (foundBlock) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getGetToBlockProcess().getToBlock(Blocks.NETHER_PORTAL);
            isPathing = true;
            error("going to nearest coords");
            return;
        }
        //search moves the item to the slot if it returns true
        if (!search(Items.OBSIDIAN, 6, 0) || !buildNetherPortal.get()) {
            // search for a non-silk touch pickaxe
            if (!search(null, 5, 1) || !buildNetherPortal.get()) {
                if (searchOnHighway.get()) {
                    error("search on highway");
                    BlockPos goal = switch (getPlayerDirection()) {
                        case NORTH, SOUTH -> new BlockPos(0, 120, mc.player.getBlockZ());
                        case EAST, WEST -> new BlockPos(mc.player.getBlockX(), 120, 0);
                        default -> new BlockPos(0, 0, 0); // This shouldn't happen.
                    };
                    boolean test1 = switch (getPlayerDirection()) {
                        case NORTH, SOUTH -> mc.player.getBlockX() == 0;
                        case EAST, WEST -> mc.player.getBlockZ() == 0;
                        default -> false;
                    };
                    //  if searchOnHighway is enabled and player isn't yet at the highway then path to it
                    if (test1) {
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                        resumeBaritone();
                        error(String.valueOf(goal.withY(mc.player.getBlockY())));
                        isPathing = true;
                        return;
                    } else {
                        //if player is already at highway then walk forwards (add elytra bounce later)
                        BaritoneAPI.getProvider().getPrimaryBaritone().getGetToBlockProcess().getToBlock(Blocks.NETHER_PORTAL);
                        resumeBaritone();
                        isPathing = true;
                        scanningWorld = tick % 200 == 0;
                        return;
                    }

                } else {
                    error("No obsidian, echest, or non-silk touch pickaxe in inventory, and searchOnHighway is disabled.");
                    disableAllModules();
                    toggle();
                    return;
                }
            } else {
                if(search(Items.ENDER_CHEST, 5, 0)) {
                    getRestockingStartPos();
                    shulkerBlockPos = mc.player.getBlockPos();
                    initialEchests = mc.player.getInventory().getStack(getEchestSlot()).getCount();
                    state = "miningEchests";
                    NewState = "waitingForGather";
                    returnToState = "goToPortal";
                    return;
                } else {
                    error("No echests, cannot get obsidian for the portal");
                    disableAllModules();
                    toggle();
                    return;
                }
            }
        } else {
            if (search(Items.FLINT_AND_STEEL, 5, 0)) {
                portalOriginBlock = null;
                buildTimer = 0;
                state = "buildPortal";
                returnToState = "goToPortal";
                return;
            } else {
                error("No flint and steel found, cannot light portal");
                disableAllModules();
                isPathing = false;
                toggle();
                return;
            }
        }
    }

    private void handleBuildPortal() {
        assert mc.player != null;
        assert mc.world != null;
        assert (getPlayerDirection() != null);

        int goalOff;
        boolean test;
        goalOff = -210;
        test = switch (getPlayerDirection()) {
            case NORTH, SOUTH -> mc.player.getBlockX() != goalOff;
            case EAST, WEST -> mc.player.getBlockZ() != goalOff;
            default -> false;
        };
        switch (getPlayerDirection()) {
            case NORTH, SOUTH -> {
                if (mc.player.getBlockX() > goalOff + 2) {
                    reached = false;
                }
            }
            case EAST, WEST -> {
                if (mc.player.getBlockZ() > goalOff + 2) {
                    reached = false;
                }
            }
            default -> {
                reached = false;
            }
        }
        if (test && !reached) {
            portalOriginBlock = null;
            BlockPos goal = switch (getPlayerDirection()) {
                case NORTH, SOUTH -> new BlockPos(goalOff, 114, mc.player.getBlockZ());
                case EAST, WEST -> new BlockPos(mc.player.getBlockX(), 114, goalOff);
                default -> new BlockPos(0, 0, 0); // This shouldn't happen.
            };
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
            isPathing = true;
            return;
        } else {
            reached = true;
            isPathing = true;
            //only generate a new portal location if it is null
            if (portalOriginBlock == null) {
                switch (getPlayerDirection()) {
                    case NORTH, SOUTH -> {
                        portalOriginBlock = mc.player.getBlockPos().east(-1);
                    }
                    case EAST, WEST -> {
                        portalOriginBlock = mc.player.getBlockPos().south(-1);
                    }
                }                    ;
            }
            error(String.valueOf(portalOriginBlock));
            ArrayList<Integer> offset = new ArrayList<>(Arrays.asList(
                    0, 0, 1, 0, 2, 0, 3, 0,
                    0, 1, 1, 1, 2, 1, 3, 1,
                    0, 2, 1, 2, 2, 2, 3, 2,
                    0, 3, 1, 3, 2, 3, 3, 3,
                    0, 4, 1, 4, 2, 4, 3, 4)
            );
            portalObby = new ArrayList<>(Arrays.asList(1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 1, 1, 1));
            portalBlocks = new ArrayList<>();
            assert portalOriginBlock != null;
            for (int i = 0; i < 20; i++) {
                switch (getPlayerDirection()) {
                    case NORTH, SOUTH -> {
                        portalBlocks.add(portalOriginBlock.south(offset.get(i*2)).up(offset.get(i*2 + 1)));
                    }
                    case EAST, WEST -> {
                        portalBlocks.add(portalOriginBlock.east(offset.get(i*2)).up(offset.get(i*2 + 1)));
                    }
                };
            }
        }
        buildTimer++;
        //make sure the block is air
        if (buildTimer < 200) {
            isPathing = true;
            BlockPos target = portalBlocks.get((buildTimer)/10);
            boolean placeObby = (portalObby.get((buildTimer)/10)==1);
            createPortal(target, placeObby);
        } else {
            if (buildTimer < 280) {
                BlockPos target = portalBlocks.get((buildTimer-200)/4);
                boolean placeObby = (portalObby.get((buildTimer-200)/4)==1);
                createPortal(target, placeObby);
            } else {
                if (mc.world.getBlockState(portalBlocks.get(2).up(1)).getBlock() != Blocks.NETHER_PORTAL) {
                    isPathing = true;
                    for (int i = 0; i < 9; i++) {
                        if (mc.player.getInventory().getStack(i).getItem() == Items.FLINT_AND_STEEL) {
                            InvUtils.swap(i, false);
                        }
                    }
                    lookAtBlock(portalBlocks.get(2));
                    //right click
                    if (!mc.player.isUsingItem() && buildTimer % 5 == 0) {
                        Utils.rightClick();
                    }
                } else {
                    //portal finished
                    scanningWorld = true;
                    isPathing = false;
                    state = returnToState;
                }
            }
        }
    }

    private void handleFlyToBlueIce() {
        isPathing = true;
        assert mc.player != null;
        assert mc.world != null;
        boolean isInFrozenOcean = mc.world.getBiome(mc.player.getBlockPos()).getKey().equals(Optional.of(BiomeKeys.FROZEN_OCEAN)) ||
                mc.world.getBiome(mc.player.getBlockPos()).getKey().equals(Optional.of(BiomeKeys.DEEP_FROZEN_OCEAN));
        if (isInFrozenOcean && !hasFoundFrozenOcean) {
            hasFoundFrozenOcean = true;
        }
        if (scanningWorld) {
            scanningWorld = false;
            getBlockGroups(Blocks.BLUE_ICE);
            if (iceBergDistances == null) {
                foundBlock = false;
            }
            return;
        }
        foundBlock = !iceBergDistances.isEmpty();
        ArrayList<BlockPos> range = getBlueIceInRange();
        if (!range.isEmpty()) {
            state = "mineBlueIceInRange";
            wasGathering = false;
            return;
        }

        if (countDroppedBlueIce(mc.player,10) > 0) {
            gatherBlueIce();
            return;
        }

        int fireworkSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.FIREWORK_ROCKET) {
                fireworkSlot = i;
                break;
            }
        }
        if (fireworkSlot == -1) {
            for (int i = 9; i < 36; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.FIREWORK_ROCKET) {
                    InvUtils.quickSwap().fromId(5).toId(i);
                    prevCount = mc.player.getInventory().getStack(fireworkSlot).getCount();
                    fireworkSlot = 5;
                    break;
                }
            }
        }
        if (foundBlock) {
            BlockPos nearestCoord = nearestGroupCoords(true);
            landCoords = nearestCoord.withY(getMaxY(nearestCoord));
            if (Math.abs(mc.player.getX()-landCoords.getX()) < 1.0 && Math.abs(mc.player.getZ()-landCoords.getZ()) < 1.0) {
                state = "land";
                vertex = landCoords.withY(mc.player.getBlockY());
                returnToState = "goToBlueIce";
            } else {
                if (mc.player.getY() < 120) {
                    BlockPos pos = mc.player.getBlockPos();
                    boolean canJump = mc.world.getBlockState(pos.up(2)).isAir();
                    if (!mc.player.isFallFlying() && !canJump) {
                        BlockPos goal = pos.withY(getMaxY(pos));
                        isPathing = true;
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                        resumeBaritone();
                        return;
                    }
                    isPathing = false;
                    InvUtils.swap(fireworkSlot, false);
                    if (tick % (mc.player.getY() < getMaxY(mc.player.getBlockPos()) + 5 ? 10 : 80) == 0) {
                        mc.player.setPitch((float) -80.0);
                        if (mc.player.isFallFlying()) {
                            Utils.rightClick();
                        } else {
                            mc.options.useKey.setPressed(false);
                        }
                    } else {
                        setKeyPressed(mc.options.jumpKey, tick % 4 < 2);
                    }
                } else {
                    float[] angles = PlayerUtils.calculateAngle(new Vec3d(landCoords.getX(),landCoords.getY(),landCoords.getZ()));
                    mc.player.setYaw(angles[0]);
                    mc.player.setPitch(15);
                }
            }
            return;
        }

        if (mc.world.getBlockState(mc.player.getBlockPos()).getBlock() == Blocks.NETHER_PORTAL) {
            BlockPos pos = mc.player.getBlockPos();
            BlockPos goal;
            if (mc.player.getY() > getMaxY(pos.east(2))) {
                goal = pos.east(2).withY(getMaxY(pos.east(2)));
            } else if (mc.player.getY() > getMaxY(pos.east(-2))) {
                goal = pos.east(-2).withY(getMaxY(pos.east(-2)));
            } else if (mc.player.getY() > getMaxY(pos.south(2))) {
                goal = pos.east(2).withY(getMaxY(pos.south(2)));
            } else {
                goal = pos.east(-2).withY(getMaxY(pos.south(-2)));
            }
            isPathing = true;
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
            resumeBaritone();
            prevCount = mc.player.getInventory().getStack(fireworkSlot).getCount();
            return;
        }
        if (isInFrozenOcean && tick % 60 == 30) {
            scanningWorld = true;
        }

        //pitch and firework control
        if (mc.player.getY() < cruiseAltitude.get()) {
            InvUtils.swap(fireworkSlot, false);
            //use fireworks every 4 seconds while spamming spacebar
            //if the elytra is open but player is still near the ground then spam fireworks faster
            if (tick % (mc.player.getY() < getMaxY(mc.player.getBlockPos())+5 ? 10 : 80) == 0) {
                mc.player.setPitch((float) -45.0);
                if (mc.player.isFallFlying()) {
                    if (getLookingAtBlock() != Blocks.AIR) {
                        mc.options.attackKey.setPressed(true);
                    } else {
                        Utils.rightClick();
                        mc.options.attackKey.setPressed(false);
                    }
                }
            } else {
                setKeyPressed(mc.options.jumpKey, tick % 4 < 2);
            }
        } else {
            double velocitySquared = Math.pow(mc.player.getVelocity().x,2)+Math.pow(mc.player.getVelocity().y,2)+Math.pow(mc.player.getVelocity().z,2);
            //velocitySquared is the square of the blocks per tick
            if (velocitySquared < 0.25) {
                mc.player.setPitch((float) 15.0);
                setKeyPressed(mc.options.jumpKey, false);}
        }
        //yaw control
        if (!hasFoundFrozenOcean) {
            if (getPlayerDirection() == null) return;
            float yaw = switch(getPlayerDirection()) {
                case NORTH -> (float)90;
                case SOUTH -> (float)-90;
                case EAST -> (float)180;
                case WEST -> (float)0;
                default -> 0;
            };
            if (isInColdBiome() || leg > 0) {
                //if player is in a cold biome then there will likely be a frozen ocean nearby so it will fly in an isosceles triangle shape
                if (vertex == null) {
                    vertex = mc.player.getBlockPos();
                }
                double squaredDistance = Math.pow(vertex.getX()-mc.player.getX(), 2)+Math.pow(vertex.getZ()-mc.player.getZ(), 2);
                if (leg == 0) {
                    leg = 1;
                }
                if (leg == 1) {
                    mc.player.setYaw(yaw-45);
                    if (squaredDistance > 9000000) {
                        vertex = mc.player.getBlockPos();
                        leg = 2;
                    }
                }
                if (leg == 2) {
                    mc.player.setYaw(yaw+90);
                    if (squaredDistance > 18000000) {
                        vertex = mc.player.getBlockPos();
                        leg = 3;
                    }
                }
                if (leg == 3) {
                    mc.player.setYaw(yaw-135);
                    if (squaredDistance > 9000000) {
                        vertex = mc.player.getBlockPos();
                        leg = 0;
                    }
                }
            } else {
                mc.player.setYaw(yaw);
                vertex = mc.player.getBlockPos();
            }
        }
    }
    private void handleLand() {
        assert mc.player != null;
        assert mc.world != null;

        isPathing = true;
        if (mc.player.isFallFlying()) {
            double xDiff = vertex.getX()-mc.player.getX();
            if (xDiff > 1) {
                mc.player.setYaw(-90);
            } else if (xDiff < -1) {
                mc.player.setYaw(90);
            }
            if (mc.player.getY() > landCoords.getY() + 60) {
                mc.player.setPitch(90);
            } else if (mc.player.getY() > landCoords.getY() + 30) {
                mc.player.setPitch(45);
            } else {
                mc.player.setPitch(15);
            }
            mc.options.sneakKey.setPressed(true);
        } else {
            state = returnToState;
            slice_maxY = ((BlockPos) groups.get(currentIceberg)).getY();
            slice_minY = slice_maxY - sliceHeight.get() + 1;
            mc.options.sneakKey.setPressed(false);

        }
    }
    private void handleGoToBlueIce() {
        assert mc.player != null;
        assert mc.world != null;

        boolean isInFrozenOcean = mc.world.getBiome(mc.player.getBlockPos()).getKey().equals(Optional.of(BiomeKeys.FROZEN_OCEAN)) ||
                mc.world.getBiome(mc.player.getBlockPos()).getKey().equals(Optional.of(BiomeKeys.DEEP_FROZEN_OCEAN));

        ArrayList<BlockPos> range = getBlueIceInRange();
        if (range.isEmpty()) {
            if (currentIceberg >= groups.size() || (int)groups.get(currentIceberg+1) == 0) {
                if (!isInFrozenOcean && tick % 60 == 0) {
                    mc.player.setYaw((float)Math.random()*360-180);
                }
                state = "flyToBlueIce";
            } else {
                BlockPos goal = nearestGroupCoords(true);
                if (PlayerUtils.isWithin(goal, 2.0)) {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                } else {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                    resumeBaritone();
                }
            }
        } else {
            state = "mineBlueIceInRange";
            wasGathering = false;
        }
    }

    public static int countDroppedBlueIce(PlayerEntity player, int radius) {
        World world = player.getWorld();

        return world.getEntitiesByClass(ItemEntity.class,
                        player.getBoundingBox().expand(radius),
                        item -> item.getStack().getItem() == Items.BLUE_ICE)
                .stream()
                .mapToInt(item -> item.getStack().getCount())
                .sum();
    }

    private Block getLookingAtBlock() {
        assert mc.world != null;
        assert mc.crosshairTarget != null;
        HitResult target = mc.crosshairTarget;
        if (target.getType() == HitResult.Type.BLOCK) {
            return mc.world.getBlockState(((BlockHitResult) target).getBlockPos()).getBlock();
        } else {
            return Blocks.AIR;
        }
    }

    private void gatherBlueIce() {
        boolean isLookingAtIce = getLookingAtBlock() == Blocks.ICE;
        mc.options.attackKey.setPressed(isLookingAtIce);
        mc.options.sneakKey.setPressed(false);
        mc.options.forwardKey.setPressed(true);
        mc.options.jumpKey.setPressed(false);
        IceRailGatherItem(Items.BLUE_ICE);
    }

    private void centerPlayer() {
        assert mc.player != null;
        mc.player.setYaw(-90.0f);
        if (mc.player.getX() % 1 > 0.6) {
            mc.options.backKey.setPressed(true);
        } else if (mc.player.getX() % 1 < 0.4) {
            mc.options.forwardKey.setPressed(true);
        } else {
            mc.options.backKey.setPressed(false);
            mc.options.forwardKey.setPressed(false);
        }
        if (mc.player.getZ() % 1 > 0.6) {
            mc.options.rightKey.setPressed(true);
        } else if (mc.player.getZ() % 1 < 0.4) {
            mc.options.leftKey.setPressed(true);
        } else {
            mc.options.rightKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
        }
    }

    private void handleMineBlueIceInRange() {
        assert mc.player != null;
        assert mc.world != null;

        ArrayList<BlockPos> range = getBlueIceInRange();
        ArrayList<BlockPos> slice = getIcebergSlice();

        if (mc.player.getAir() < 2 || wasBreathing) {
            wasBreathing = mc.player.getAir() < 10;
            mc.options.jumpKey.setPressed(true);
            mc.options.sneakKey.setPressed(false);
            mc.options.attackKey.setPressed(true);
            mc.player.setPitch(-90.0f);
            centerPlayer();
            return;
        }
        int blueIceInRange = countDroppedBlueIce(mc.player, 10);
        if (blueIceInRange > 60 || wasGathering) {
            wasGathering = blueIceInRange > 5;
            gatherBlueIce();
            return;
        }

        boolean isUnderWater = mc.world.getFluidState(mc.player.getBlockPos()).getFluid() == Fluids.WATER;
        boolean jump = mc.player.getY() < slice_minY && mc.player.getY() < 60;
        if (range.isEmpty()) {
            if (slice.isEmpty()) {
                if (tick % 80 == 0) getBlockGroups(Blocks.BLUE_ICE);
                if (groups.size() > currentIceberg && (int) groups.get(currentIceberg + 1) > 0) {
                    slice_minY -= sliceHeight.get();
                    slice_maxY -= sliceHeight.get();
                } else {
                    state = "goToBlueIce";
                    mc.options.attackKey.setPressed(false);
                    mc.options.sneakKey.setPressed(false);
                    mc.options.forwardKey.setPressed(false);
                }
            } else {
                error("walking forwards to get more blue ice");
                lookAtBlock(slice.getFirst());
                mc.options.forwardKey.setPressed(true);

                mc.options.jumpKey.setPressed(jump);
                mc.options.sneakKey.setPressed(!jump);

                airPlace(Items.BLUE_ICE, mc.player.getBlockPos().up(-1), Direction.DOWN);
            }
        } else {
            error("slice_minY: " + slice_minY + "slice_maxY: " + slice_maxY);
            BlockPos playerPos = mc.player.getBlockPos();
            boolean diggingStraightDown = range.getFirst().getY() == playerPos.getY()-1;

            HitResult hit = mc.crosshairTarget;
            assert hit != null;
            lookAtBlock(range.getFirst());

            //Prevents the staring at snow layer glitch
            if (hit.getType() != HitResult.Type.BLOCK && !isUnderWater) {
                lookAtBlock(range.getFirst().up(-1));
            }

            //Check if player is over blue ice
            boolean isOverBlueIce = false;
            for (int i = -1; i > -5; i--) {
                if (mc.world.getBlockState(playerPos.up(i)).getBlock() == Blocks.BLUE_ICE) {
                    isOverBlueIce = true;
                } else if (!isUnderWater) {
                    break;
                }
            }

            //Controls
            if (isOverBlueIce) {
                mc.options.jumpKey.setPressed(jump);
            } else if (isUnderWater) {
                mc.options.jumpKey.setPressed(true);
            }

            mc.options.sneakKey.setPressed(!jump);
            mc.options.forwardKey.setPressed(!diggingStraightDown);
            error("mining");
            mc.options.attackKey.setPressed(true);
        }
    }

    private void disablemodules() {
        Module iceRailNuker = Modules.get().get("ice-rail-nuker");
        Module icePlacer = Modules.get().get("ice-placer");
        assert mc.player != null;

        getRestockingStartPos();
        if (icePlacer.isActive()) {
            if (getIsEating()) { // Toggle off
                icePlacer.toggle();
                iceRailNuker.toggle();
            }
        }
    }

    private void createPortal(BlockPos target, boolean placeObby) {
        assert mc.player != null;
        assert mc.world != null;

        if (buildTimer % 10 < 3) {
            switch (getPlayerDirection()) {
                case NORTH, SOUTH -> {
                    if (!mc.world.getBlockState(target.east(1)).isAir()) {
                        packetMine(target.east(1));
                    }
                }
                case EAST, WEST -> {
                    if (!mc.world.getBlockState(target.south(1)).isAir()) {
                        packetMine(target.south(1));
                    }
                }
            };
            //place the block
        } else if (buildTimer % 10 < 6) {
            if (!mc.world.getBlockState(target).isAir() && mc.world.getBlockState(target).getBlock() != Blocks.OBSIDIAN) {
                packetMine(target);
            }
        } else if (buildTimer % 10 == 8) {
            if (placeObby) {
                if (mc.world.getBlockState(target).getBlock() != Blocks.OBSIDIAN) {
                    BlockUtils.place(target, InvUtils.findInHotbar(itemStack -> itemStack.getItem() == Items.OBSIDIAN), true, 0, true, true);
                }
            }
            //delay
        }
    }

    private void getRestockingStartPos() {
        assert mc.player != null;

        restockingStartPosition = switch (getPlayerDirection()) {
            case NORTH -> new BlockPos(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ() + 2);
            case SOUTH -> new BlockPos(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ() - 2);
            case EAST -> new BlockPos(mc.player.getBlockX() - 2, mc.player.getBlockY(), mc.player.getBlockZ());
            case WEST -> new BlockPos(mc.player.getBlockX() + 2, mc.player.getBlockY(), mc.player.getBlockZ());
            default -> new BlockPos(0, 0, 0); // This shouldn't happen.
        };
    }

    private void shulkerRestock(int count, Item item, int type) {
        //runs inventory clear and stops onTick.
        //state will then cycle through the following before getting reset (to returnToState)


        //retrievingFromShulker
        //waitingForPostRestock
        //waitingForGather

        disablemodules();
        restockingType = 2;
        oldYaw = mc.player.getYaw();
        returnToState = state;
        NewState = "retrievingFromShulker";
        isClearingInventory = true;
        retrieveCount = 1;
        retrieveItem = item;
        retrieveType = type;
        if (type > 2) {retrieveType = 0;}
    }
    private @NotNull BlockPos getBlockPos() {
        int offset = 0;
        assert mc.player != null;
        return switch (getPlayerDirection()) {
            case NORTH -> new BlockPos(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ() + offset);
            case SOUTH -> new BlockPos(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ() - offset);
            case EAST -> new BlockPos(mc.player.getBlockX() + offset, mc.player.getBlockY(), mc.player.getBlockZ());
            case WEST -> new BlockPos(mc.player.getBlockX() - offset, mc.player.getBlockY(), mc.player.getBlockZ());
            default -> new BlockPos(0, 0, 0); // This shouldn't happen.
        };
    }
    private void handleItemRetrieve(int count, Item item) {
        if (isGatheringItems()) {
            state = "idle";
            return;}


        assert mc.player != null;
        assert mc.world != null;


        if (!restockingStartPosition.equals(mc.player.getBlockPos())) {
            if (!isPause) {
                isPause = true;
                return;
            }
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(restockingStartPosition));
            resumeBaritone();
        } else {
            isPause = false;
        }


        if (isPause) {
            return;
        }


        shulkerBlockPos = getBlockPos();
        error(String.valueOf(shulkerBlockPos));

        if (hasLookedAtShulker < 10) { // To add a 10 tick delay
            if (hasLookedAtShulker == 0) {
                InvUtils.swap(5, false);
                lookAtBlock(shulkerBlockPos.withY(mc.player.getBlockY() - 1)); // To minimize the chance of the shulker being placed upside down
            }


            hasLookedAtShulker++;
            return;
        }


        if (!(mc.world.getBlockState(shulkerBlockPos).getBlock() instanceof ShulkerBoxBlock)) {
            if (BlockUtils.canPlace(shulkerBlockPos, false) && !BlockUtils.canPlace(shulkerBlockPos, true)) return;
            place(shulkerBlockPos, Hand.MAIN_HAND, 5, true, true, true);
            return;
        }


        if (!hasOpenedShulker) {
            mc.setScreen(null);
            // Open the shulker
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(shulkerBlockPos), Direction.DOWN,
                            shulkerBlockPos, false), 0));


            mc.setScreen(null);
            hasOpenedShulker = true;
            return;
        }


        mc.setScreen(null);
        if (stacksStolen == null) {
            stacksStolen = 0;
        }
        if ((stacksStolen >= count) || stacksStolen >= 27) {
            // Run post restocking
            isPostRestocking = true;


            stacksStolen = 0;
            slotNumber = 0;

            wasRestocking = true;
            state = "waitingForPostRestock";
            isPause = false;
            isPlacingShulker = false;
            restockingStartPosition = null;
            hasLookedAtShulker = 0;
            stealingDelay = 0;
            hasOpenedShulker = false;
            isRestocking = false;
        } else {
            ScreenHandler handler = mc.player.currentScreenHandler;
            ItemStack slotStack = handler.getSlot(slotNumber).getStack();
            Item slotItem = slotStack.getItem();
            if (hasOpenedShulker) {
                boolean condition;
                if (retrieveType == 0){
                    condition = (slotItem != item);
                } else {
                    condition = !isNotSilkPick(slotStack);
                }
                if (stealingDelay < 5) { // To add a 5 tick delay
                    stealingDelay++;
                    return;
                }
                if (!condition) {
                    steal(handler, slotNumber);
                }
                slotNumber++;
                stealingDelay = 0;
                //THe glitch is that it's searching in the inventory, not the shulker box
            }
        }
    }
    public boolean getIsPathing() {
        return isPathing;
    }
}
