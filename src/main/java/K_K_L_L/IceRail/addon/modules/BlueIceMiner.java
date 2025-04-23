package K_K_L_L.IceRail.addon.modules;
import static K_K_L_L.IceRail.addon.Utils.*;
import static K_K_L_L.IceRail.addon.modules.IceHighwayBuilder.*;
import static K_K_L_L.IceRail.addon.modules.IceRailAutoEat.getIsEating;


import K_K_L_L.IceRail.addon.IceRail;


import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IElytraProcess;
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
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
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
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.world.dimension.DimensionType;
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
    public boolean scanningWorld2 = true;
    public int tick = 0;
    public static boolean isPathing = false;
    public static DimensionType dimension;
    boolean reached = false;
    public static boolean foundBlock;
    public static int leg = 0;
    public static BlockPos vertex;
    public static BlockPos landCoords;
    public boolean hasFoundFrozenOcean = false;
    public static int currentIceberg = 0;
    public static int initialEchests;
    private int echestSlot = -1;
    private int slice_maxY = 0;
    private int slice_minY = 0;
    private boolean wasGathering = false;
    private boolean wasBreathing;
    private BlockPos previousRangeFirst = null;
    private int mineDuration = 0;
    private boolean wasTowering = false;
    private int toolSlot;
    private int shulkerSlot;
    private int placingSlot;
    private int firstValidShulker = -1;
    private boolean full = false;
    private boolean returnToSlot = false;

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
            .description("Y level to maintain while flying to a frozen ocean.")
            .defaultValue(400)
            .min(150)
            .max(2000)
            .sliderRange(150, 1000)
            .build()
    );
    private final Setting<Integer> frozenOceanCruiseAltitude = sgPortal.add(new IntSetting.Builder()
            .name("frozen-ocean-cruise-altitude")
            .description("Y level to maintain when searching for icebergs within a frozen ocean.")
            .defaultValue(120)
            .min(70)
            .max(200)
            .sliderRange(70, 200)
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
        Module iceRailAutoEat = Modules.get().get("ice-rail-auto-eat");
        IceRailAutoReplenish autoReplenish = Modules.get().get(IceRailAutoReplenish.class);
        if (dimension.bedWorks()) {
            state = "flyToBlueIce";
            if (scaffoldGrim.isActive()) {
                scaffoldGrim.toggle();
            }
            if (!iceRailAutoEat.isActive()) {
                iceRailAutoEat.toggle();
            }
        } else {
            state = "goToPortal";
            if (!scaffoldGrim.isActive()) {
                scaffoldGrim.toggle();
            }
        }
        scanningWorld = true;
        scanningWorld2 = true;
        mc.options.attackKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.backKey.setPressed(false);

        toolSlot = autoReplenish.toolSlot.get();
        shulkerSlot = autoReplenish.shulkerSlot.get();
        placingSlot = autoReplenish.placingSlot.get();
        firstValidShulker = -1;
        wasGathering = false;
        returnToSlot = false;

        cancelBaritone();
        error("cancelBaritone 235");
    }

    @Override
    public void onDeactivate() {
        mc.options.attackKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        firstValidShulker = -1;
        wasGathering = false;
        returnToSlot = false;
        Module iceRailGatherItem = Modules.get().get("ice-rail-gather-item");
        if (iceRailGatherItem.isActive()) iceRailGatherItem.toggle();
        cancelBaritone();
        error("cancelBaritone 250");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tick++;

        // Validity checks
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (!isActive()) return;
        if (state == null || state.equals("idle")) {
            isPathing = false;
            return;
        }

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

        //Hostile mob elimination methods
        if (killaura()) {
            error("killaura");
            return;
        }
        if (!state.equals("flyToBlueIce") && pathToTridentDrowned()) {
            error("Moving towards a trident drowned");
            return;
        }
        if (manageInventory()) {
            return;
        } else {
            firstValidShulker = -1;
            returnToSlot = false;
        }

        if (state.equals("flyToBlueIce")) {
            handleFlyToBlueIce();
            return;
        }
        if (state.equals("land")) {
            handleLand();
            return;
        }

        //Pause mining when eating
        if (getIsEating()) {
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

        //After returning to the nether
        if (state.equals("resumeBuilding")) {
            handleResumeBuilding();
        }
    }

    private void handleResumeBuilding() {
        IceHighwayBuilder iceHighwayBuilder = Modules.get().get(IceHighwayBuilder.class);
        if (!iceHighwayBuilder.isPlayerInValidPosition()) {
            //[NEEDS TESTING] baritone efly
            IElytraProcess elytraProcess = BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess();
            if (!elytraProcess.isActive()) {
                elytraProcess.pathTo(getHighwayCoords());
            }
        } else {
            //backtrack to blue ice
        }
    }

    private boolean manageInventory() {
        assert mc.world != null;
        assert mc.player != null;
        assert mc.interactionManager != null;

        mc.options.attackKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);

        int blueIceSlots = 0;
        int blueIceTotal = 0;
        int remainingShulkers = 0;
        int firstEmptySlot = -1;
        int firstBlueIceSlot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (itemStack.getItem().equals(Items.BLUE_ICE) || itemStack.isEmpty()) {
                blueIceSlots++;
                blueIceTotal += itemStack.getCount();
                if (itemStack.isEmpty()) {
                    if (firstEmptySlot == -1) firstEmptySlot = i;
                } else {
                    if (i > 8 && (firstBlueIceSlot == -1 || mc.player.getInventory().getStack(firstBlueIceSlot).getCount() < blueIceSlots))
                        firstBlueIceSlot = i;
                }
            }
            if (itemStack.getItem() instanceof BlockItem &&
                    ((BlockItem) itemStack.getItem()).getBlock() instanceof ShulkerBoxBlock) {
                ItemStack[] containerItems = new ItemStack[27];
                Utils.getItemsInContainerItem(itemStack, containerItems);
                boolean found = false;
                for (ItemStack stack : containerItems) {
                    if (stack.isEmpty()) {
                        found = true;
                        if (firstValidShulker == -1) firstValidShulker = i;
                    }
                }
                if (found) {
                    remainingShulkers++;
                }
            }
        }

        if (state.equals("dumpBlueIce")) {
            //Possible improvement: Use shiftClick() with an ID from 27-63 to dump into shulker.
            cancelBaritone();
            error("cancelBaritone 400");
            scanningWorld2 = true;
            if (!hasOpenedShulker) {
                openShulker();
                error("8");
                return true;
            }
            if (full || blueIceTotal - 64 < blueIceSlots) {
                if (mc.world.getBlockState(shulkerBlockPos).getBlock() instanceof ShulkerBoxBlock) {
                    if (PlayerUtils.isWithinReach(shulkerBlockPos)) {
                        if (BlockUtils.breakBlock(shulkerBlockPos, true))
                            return true;
                    }
                }
                Item[] items = getShulkerBoxesNearby();
                if (areShulkerBoxesNearby()) {
                    for (Item item : items) {
                        if (!isGatheringItems()) {
                            IceRailGatherItem(item);
                            return true;
                        }
                        return true;
                    }
                }
                state = returnToState;
                error("9");
                return true;
            }
            if (tick % 2 == 0) return true;
            for (int i = 0; i < 36; i++) {
                ItemStack itemStack = mc.player.getInventory().getStack(i);
                if (itemStack.getItem() == Items.BLUE_ICE) {
                    int toSlot = -1;
                    for (int j = 0; j < 27; j++) {
                        if (mc.player.currentScreenHandler.getSlot(j).getStack().isEmpty()) {
                            toSlot = j;
                            break;
                        }
                    }
                    if (toSlot == -1) {
                        full = true;
                        return true;
                    }
                    if (tick % 4 == 1) {
                        if (i < 9) {
                            InvUtils.quickSwap().fromId(i).toId(toSlot);
                        } else {
                            InvUtils.move().from(i).to(shulkerSlot);
                            error("10: swapped from inventory to shulkerSlot");
                        }
                    } else {
                        InvUtils.quickSwap().fromId(shulkerSlot).toId(toSlot);
                    }
                    error("10, fromSlot = " + i + " toSlot = " + toSlot);
                    return true;
                }
            }
            state = returnToState;
            error("11");
            return true;
        } else {
            if (blueIceTotal == 0) {
                error("blueIceTotal = 0");
                return false;
            }
            if (blueIceTotal == blueIceSlots * 64 || remainingShulkers == 0) {
                cancelBaritone();
                error("cancelBaritone 466");
                if (remainingShulkers > 0) {
                    assert firstValidShulker != -1;
                    scanningWorld2 = true;
                    if (firstValidShulker != shulkerSlot) {
                        if (firstValidShulker < 9 || firstValidShulker == 500) {
                            if (firstValidShulker < 9) {
                                InvUtils.quickSwap().fromId(firstValidShulker).toId(9);
                                firstValidShulker = 500;
                                error("1: swapped to 9");
                                return true;
                            }
                            InvUtils.quickSwap().fromId(shulkerSlot).toId(9);
                            error("1: swapped from 9");
                        } else {
                            InvUtils.quickSwap().fromId(shulkerSlot).toId(firstValidShulker);
                        }
                        error("1, firstValidShulker = " + firstValidShulker + " shulkerSlot = " + shulkerSlot);
                        firstValidShulker = -1;
                        return true;
                    }
                    if (mc.player.getInventory().selectedSlot != shulkerSlot) {
                        InvUtils.swap(shulkerSlot, false);
                        error("2, blueIceTotal = " + blueIceTotal + "blueIceSlots = " + blueIceSlots);
                        return true;
                    }
                    BlockPos block = mc.player.getBlockPos().down();
                    boolean blockIsShulker = mc.world.getBlockState(block).getBlock() instanceof ShulkerBoxBlock;
                    if (!blockIsShulker) {
                        mc.options.jumpKey.setPressed(true);
                        if (!BlockUtils.canPlace(block, true)) {
                            error("3");
                            return true;
                        }
                        place(block, Hand.MAIN_HAND, shulkerSlot, true, true, true);
                    }
                    returnToState = state;
                    state = "dumpBlueIce";
                    hasOpenedShulker = false;
                    shulkerBlockPos = block;
                    foundBlock = false;
                    full = false;
                    error("4");
                    return true;
                } else {
                    error("remaining shulkers = 0");
                    if (scanningWorld2) {
                        scanningWorld2 = false;
                        foundBlock = !searchWorld(Blocks.NETHER_PORTAL).isEmpty();
                    }
                    if (!foundBlock) {
                        if (!search(Items.OBSIDIAN, placingSlot, 0)) {
                            error("No obsidian, cannot build a portal to return to the nether.");
                            disableAllModules();
                            toggle();
                            return true;
                        }
                        if (!search(Items.FLINT_AND_STEEL, toolSlot, 0)) {
                            error("No flint and steel, cannot build a portal to return to the nether.");
                            disableAllModules();
                            toggle();
                            return true;
                        }
                        handleBuildPortal();
                        error("6");
                    } else {
                        BaritoneAPI.getProvider().getPrimaryBaritone().getGetToBlockProcess().getToBlock(Blocks.NETHER_PORTAL);
                        isPathing = true;
                        resumeBaritone();
                        error("7");
                    }
                    return true;
                }
            } else {
                scanningWorld2 = true;
                if (firstEmptySlot != -1 && firstBlueIceSlot != -1) {
                    cancelBaritone();
                    error("cancelBaritone 542");
                    if (tick % 2 == 0) return true;
                    //spread firstBlueIceSlot to firstEmptySlot
                    int syncId = mc.player.playerScreenHandler.syncId;
                    if (firstEmptySlot < 9) firstEmptySlot += 36;
                    if (returnToSlot) {
                        returnToSlot = false;
                        mc.interactionManager.clickSlot(syncId, firstBlueIceSlot, 0, SlotActionType.PICKUP, mc.player);
                        error("placed down cursor slot at " + firstBlueIceSlot);
                    } else if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                        returnToSlot = false;
                        mc.interactionManager.clickSlot(syncId, firstBlueIceSlot, 1, SlotActionType.PICKUP, mc.player);
                        error("picked up half of blue ice slot at " + firstBlueIceSlot);
                    } else {
                        returnToSlot = true;
                        mc.interactionManager.clickSlot(syncId, firstEmptySlot, 1, SlotActionType.PICKUP, mc.player);
                        error("right clicked cursor slot at " + firstEmptySlot);
                    }
                    return true;
                }
                returnToSlot = false;
            }
        }
        return false;
    }

    private boolean isTargetMob(LivingEntity entity) {
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

    private boolean pathToTridentDrowned() {
        assert mc.world != null;
        assert mc.player != null;
        List<LivingEntity> targets = mc.world.getEntitiesByClass(
                LivingEntity.class,
                mc.player.getBoundingBox().expand(20),
                e -> e instanceof ZombieEntity);
        if (targets == null || targets.isEmpty()) {
            return false;
        }
        LivingEntity target = null;
        for (LivingEntity entity : targets) {
            Item heldItem = entity.getMainHandStack().getItem();
            if (heldItem == Items.TRIDENT || heldItem == Items.BLUE_ICE) {
                target = entity;
                break;
            }
        }
        if (target == null) return false;
        BlockPos targetLocation = target.getBlockPos();
        if (PlayerUtils.isWithin(targetLocation, 2.0)) return false;
        if (!PlayerUtils.canSeeEntity(target)) return false;
        if (mineTrappedBlocks()) return true;

        error("drowned has trident");
        Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, Target.Body));
        mc.player.setYaw((float) Rotations.getYaw(targetLocation));
        mc.player.setPitch((float) Rotations.getPitch(targetLocation));
        mc.options.forwardKey.setPressed(true);
        mc.options.sneakKey.setPressed(false);
        return true;
    }

    private boolean mineTrappedBlocks() {
        assert mc.world != null;
        assert mc.player != null;
        BlockPos pos = mc.player.getBlockPos();
        List<BlockPos> targetBlocks = switch (mc.player.getHorizontalFacing()) {
            case EAST -> Arrays.asList(pos.add(1, 0, 0), pos.add(1, 1, 0));
            case WEST -> Arrays.asList(pos.add(-1, 0, 0), pos.add(-1, 1, 0));
            case NORTH -> Arrays.asList(pos.add(0, 0, -1), pos.add(0, 1, -1));
            case SOUTH -> Arrays.asList(pos.add(0, 0, 1), pos.add(0, 1, 1));
            case DOWN -> Collections.singletonList(pos.add(0, -1, 0));
            case UP -> Collections.singletonList(pos.add(0, 1, 0));
        };
        for (BlockPos target : targetBlocks) {
            if (!isAirOrWater(target)) {
                lookAtBlock(target);
                mc.options.attackKey.setPressed(true);
                return true;
            }
        }
        swapToPickaxe();
        mc.options.attackKey.setPressed(false);
        return false;
    }

    private boolean isAirOrWater(BlockPos pos) {
        assert mc.world != null;
        return (mc.world.getBlockState(pos).isAir() ||
                mc.world.getFluidState(pos).getFluid() != Fluids.WATER);
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
            } else if (item == Items.ENDER_CHEST) {
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

        /*This method searches the inventory for an item and returns if the search was successful.
         Depending on the Type, it also moves the item to the slot, or executes a shulker restock,
         changing the state then changing it back when restocking is completed.*/

        //Type 0: Searches for item, moves it to slot, opens shulker and retrieves the item
        //Type 1: Searches for a non-silk pickaxe, moves it to slot, opens shulker and retrieves
        //Type 2: Checks if inventory contains a pickaxe, slot doesn't matter
        //Type 3: Check if inventory contains an item, shulker restocks it if not

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
                foundCount++;
            }
        }
        if (bestSlot != -1) {
            if (type != 2) {
                if (type < 2) {
                    InvUtils.quickSwap().fromId(slot).toId(bestSlot);
                }
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
                    InvUtils.quickSwap().fromId(shulkerSlot).toId(bestSlot);
                } else {
                    InvUtils.quickSwap().fromId(bestSlot).toId(9);
                    InvUtils.quickSwap().fromId(shulkerSlot).toId(9);
                }
                isPathing = false;
                shulkerRestock(item == Items.FIREWORK_ROCKET ? 2 : 1, item, type);
                return true;
            }
        } else {
            if (foundCount < 5) {
                return true;
            }
        }

        //echest
        return false;
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
                    assert mc.world != null;
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
            for (int i = 0; i < groups.size(); i += 2) {
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
        for (int i = 0; i < groups.size(); i += 2) {
            if ((int) groups.get(i + 1) > groupsizeThreshold.get() ||
                    (PlayerUtils.isWithin(((BlockPos) groups.get(i)), 8.0) && ((int) groups.get(i + 1)) > 10)) {
                validGroups.add(i);
            }
        }
        ArrayList<Object> temporary = new ArrayList<>();
        assert mc.player != null;
        for (int i : validGroups) {
            BlockPos block1 = (BlockPos) groups.get(i);
            double xSquared = Math.pow(block1.getX() - mc.player.getX(), 2);
            double zSquared = Math.pow(block1.getZ() - mc.player.getZ(), 2);
            iceBergDistances.add(Math.sqrt(xSquared + zSquared));
            temporary.add(block1);
            temporary.add(groups.get(i + 1));
        }
        groups = temporary;
    }

    public BlockPos nearestGroupCoords(boolean updateCurrentIceberg) {
        assert mc.player != null;
        if (groups.isEmpty()) return mc.player.getBlockPos();
        int j = 0;
        double min = Double.MAX_VALUE;
        for (int i = 0; i < iceBergDistances.size(); i++) {
            if (iceBergDistances.get(i) < min) {
                j = i;
                min = iceBergDistances.get(i);
            }
        }
        if (updateCurrentIceberg) {
            currentIceberg = j * 2;
        }
        return (BlockPos) groups.get(j * 2);
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
        lookAtBlock(block);
        mc.options.attackKey.setPressed(true);
    }

    public static boolean isInColdBiome() {
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
                        Block block = mc.world.getBlockState(blockPos).getBlock();
                        if ((block == Blocks.BLUE_ICE) &&
                                !blockPosList.contains(blockPos) && PlayerUtils.isWithin(blockPos, 3)) {
                            blockPosList.add(blockPos);
                        }
                    }
                }
            }
        }
        return blockPosList;
    }

    private boolean mineObstructingBlocks() {
        assert mc.player != null;
        assert mc.world != null;
        BlockPos playerPos = mc.player.getBlockPos();
        int sx = playerPos.getX();
        int sz = playerPos.getZ();
        for (int y = slice_maxY; y >= slice_minY; y--) {
            for (int x = sx - 2; x <= sx + 2; x++) {
                for (int z = sz - 2; z <= sz + 2; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    Block block = mc.world.getBlockState(blockPos).getBlock();
                    List<Block> obstructionBlocks = Arrays.asList(
                            Blocks.PACKED_ICE,
                            Blocks.ICE,
                            Blocks.DIRT,
                            Blocks.GRAVEL,
                            Blocks.CLAY,
                            Blocks.SAND,
                            Blocks.SNOW_BLOCK
                    );
                    if (PlayerUtils.isWithin(blockPos, 2) && obstructionBlocks.contains(block)) {
                        lookAtBlock(blockPos);
                        swapToPickaxe();
                        mc.options.attackKey.setPressed(true);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private ArrayList<BlockPos> getIcebergSlice() {
        assert mc.player != null;
        assert mc.world != null;
        BlockPos playerPos = mc.player.getBlockPos();
        int sx = playerPos.getX();
        int sz = playerPos.getZ();
        ArrayList<BlockPos> slice = new ArrayList<>();
        for (int y = slice_maxY; y >= slice_minY; y--) {
            for (int x = sx - 7; x <= sx + 7; x++) {
                for (int z = sz - 7; z <= sz + 7; z++) {
                    BlockPos block = new BlockPos(x, y, z);
                    if (mc.world.getBlockState(block).getBlock() == Blocks.BLUE_ICE) {
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
            j--;
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

        if (getEchestSlot() != -1) {
            echestSlot = getEchestSlot();
        }

        if (!mc.player.getBlockPos().equals(restockingStartPosition)) {
            createCustomGoalProcess(restockingStartPosition);
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
            InvUtils.swap(swapSlot, false);
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
        //Validity checks
        assert mc.player != null;
        assert mc.world != null;

        reached = false;

        //If there are under 5 usable pickaxes:
        if (countUsablePickaxes() < 5) {

            //If pickaxe repairer is enabled in the settings:
            if (enablePickaxeRepairer.get()) {

                //Enable PickaxeRepairer
                Module pickaxeRepairer = Modules.get().get("pickaxe-repairer");
                if (!pickaxeRepairer.isActive()) {
                    pickaxeRepairer.toggle();
                }
                toggle();
                return;
            } else {
                disableAllModules();
                error("Cannot mine blue ice because there are too few usable pickaxes");
            }
        }

        //If there are enough pickaxes, but no elytra, disable.
        if (!search(Items.ELYTRA, 6, 3)) {
            error("No elytra, cannot fly to blue ice.");
            disableAllModules();
            toggle();
            return;

            //If there is an elytra in the inventory:
        } else {
            boolean test = false;
            for (int i = 0; i < mc.player.getInventory().size(); i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == Items.ELYTRA) {
                    test = true;
                    break;
                }
            }
            if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                test = true;
            }

            //Equip the elytra
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

        //If there are enough pickaxes and the elytra was equipped but there aren't enough fireworks:
        if (!search(Items.FIREWORK_ROCKET, 4, 3)) {
            error("No firework rockets, cannot fly to blue ice.");
            disableAllModules();
            toggle();
            return;

            //If there are enough fireworks:
        } else {

            //Ensure fireworks are in hotbar
            int slot = -1;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == Items.FIREWORK_ROCKET) {
                    slot = i;
                    break;
                }
            }
            if (slot > 8) {
                InvUtils.quickSwap().fromId(toolSlot).toId(slot);
                return;
            }
        }

        //Search render distance for nether a portal, only search for one tick to avoid lag.
        if (scanningWorld) {
            scanningWorld = false;
            foundBlock = !searchWorld(Blocks.NETHER_PORTAL).isEmpty();
            error("scanningWorld found? " + foundBlock);
        }

        //If the player isn't there already go to the nearest nether portal
        if (foundBlock) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getGetToBlockProcess().getToBlock(Blocks.NETHER_PORTAL);
            isPathing = true;
            error("going to nearest coords");
            return;
        }

        //If player fails to retrieve obsidian from inventory or build nether portal is disabled:
        if (!search(Items.OBSIDIAN, 6, 0) || !buildNetherPortal.get()) {

            //If player fails to retrieve a non-silk pickaxe, or build nether portal is disabled:
            if (!search(null, 5, 1) || !buildNetherPortal.get()) {

                //If player is allowed to search on the main highway for a portal:
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

                    //If the player isn't at the highway yet, path to it.
                    if (test1) {
                        createCustomGoalProcess(goal);
                        error(String.valueOf(goal.withY(mc.player.getBlockY())));
                        isPathing = true;
                    } else {

                        //[ADD ELYTRA BOUNCE] If the player is already at highway then walk forwards.
                        BaritoneAPI.getProvider().getPrimaryBaritone().getGetToBlockProcess().getToBlock(Blocks.NETHER_PORTAL);
                        resumeBaritone();
                        isPathing = true;
                        scanningWorld = tick % 200 == 0;
                    }

                    //If player can't build a portal but can't search on highway, or player can build portals but has no non-silk pickaxes and no obsidian, disable.
                } else {
                    error("No obsidian or non-silk touch pickaxe in inventory and searchOnHighway is disabled.");
                    disableAllModules();
                    toggle();
                }

                //If player has a non-silk pickaxe, retrieve an echest
            } else {
                if (search(Items.ENDER_CHEST, 5, 0)) {

                    //If retrieving succeeds, start mining echests
                    getRestockingStartPos();
                    shulkerBlockPos = mc.player.getBlockPos();
                    initialEchests = mc.player.getInventory().getStack(getEchestSlot()).getCount();
                    state = "miningEchests";
                    NewState = "waitingForGather";
                    returnToState = "goToPortal";
                } else {
                    error("No echests, cannot get obsidian for the portal");
                    disableAllModules();
                    toggle();
                }
            }
        } else {

            //If player has obsidian and is allowed to build portals:
            if (search(Items.FLINT_AND_STEEL, toolSlot, 0)) {

                //If flint and steel retrieving succeeds, build the portal
                portalOriginBlock = null;
                buildTimer = 0;
                state = "buildPortal";
                returnToState = "goToPortal";
            } else {
                error("No flint and steel found, cannot light portal");
                disableAllModules();
                isPathing = false;
                toggle();
            }
        }
    }

    private void handleBuildPortal() {
        assert mc.player != null;
        assert mc.world != null;
        assert (getPlayerDirection() != null);

        int goalOff = 0;
        boolean test = true;
        boolean inOverworld = mc.world.getDimension().bedWorks();
        if (!inOverworld) {
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
        } else {
            test = mc.player.getBlockY() < 63;
            reached = false;
        }
        if (test && !reached) {
            if (!inOverworld) {
                portalOriginBlock = null;
                BlockPos goal = switch (getPlayerDirection()) {
                    case NORTH, SOUTH -> new BlockPos(goalOff, 114, mc.player.getBlockZ());
                    case EAST, WEST -> new BlockPos(mc.player.getBlockX(), 114, goalOff);
                    default -> new BlockPos(0, 0, 0); // This shouldn't happen.
                };
                createCustomGoalProcess(goal);
                isPathing = true;
            } else {
                mc.options.jumpKey.setPressed(true);
            }
            return;
        } else {
            if (!reached) {
                reached = true;
                mc.options.jumpKey.setPressed(false);
            }
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
                }
                ;
            }
            error(String.valueOf(portalOriginBlock));
            ArrayList<Integer> offset = new ArrayList<>(Arrays.asList(
                    0, -1, 1, -1, 2, -1, 3, -1,
                    0, 0, 1, 0, 2, 0, 3, 0,
                    0, 1, 1, 1, 2, 1, 3, 1,
                    0, 2, 1, 2, 2, 2, 3, 2,
                    0, 3, 1, 3, 2, 3, 3, 3)
            );
            portalObby = new ArrayList<>(Arrays.asList(1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 1, 1, 1));
            portalBlocks = new ArrayList<>();
            assert portalOriginBlock != null;
            for (int i = 0; i < 20; i++) {
                switch (getPlayerDirection()) {
                    case NORTH, SOUTH -> {
                        portalBlocks.add(portalOriginBlock.south(offset.get(i * 2)).up(offset.get(i * 2 + 1)));
                    }
                    case EAST, WEST -> {
                        portalBlocks.add(portalOriginBlock.east(offset.get(i * 2)).up(offset.get(i * 2 + 1)));
                    }
                }
                ;
            }
        }
        buildTimer++;
        //make sure the block is air
        int ticksPerBlock = (inOverworld ? 20 : 10);
        if (buildTimer < ticksPerBlock * 20) {
            isPathing = true;
            BlockPos target = portalBlocks.get((buildTimer) / ticksPerBlock);
            boolean placeObby = (portalObby.get((buildTimer) / ticksPerBlock) == 1);
            createPortal(target, placeObby);
        } else {
            if (buildTimer < ticksPerBlock * 28) {
                BlockPos target = portalBlocks.get((buildTimer - ticksPerBlock * 20) / ((int) (ticksPerBlock * 0.4)));
                boolean placeObby = (portalObby.get((buildTimer - ticksPerBlock * 20) / ((int) (ticksPerBlock * 0.4))) == 1);
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
                        airPlace(portalBlocks.get(2), switch (getPlayerDirection()) {
                            case NORTH, SOUTH -> Direction.WEST;
                            case EAST, WEST -> Direction.NORTH;
                            default -> null;
                        });
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

    private int getItemInInventory(Item item, int resortToSlot) {
        assert mc.player != null;
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            Item invItem = mc.player.getInventory().getStack(i).getItem();
            if (invItem == item || item == Items.DIAMOND_PICKAXE && invItem == Items.NETHERITE_PICKAXE) {
                slot = i;
                break;
            }
        }
        if (slot == -1) {
            for (int i = 9; i < 36; i++) {
                Item invItem = mc.player.getInventory().getStack(i).getItem();
                if (invItem == item || item == Items.DIAMOND_PICKAXE && invItem == Items.NETHERITE_PICKAXE) {
                    InvUtils.quickSwap().fromId(resortToSlot).toId(i);
                    slot = resortToSlot;
                    break;
                }
            }
        }
        return slot;
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
        if (currentIceberg < groups.size()) {
            slice_maxY = ((BlockPos) groups.get(currentIceberg)).getY();
            slice_minY = slice_maxY - sliceHeight.get() + 1;
            ArrayList<BlockPos> range = getBlueIceInRange();
            if (!range.isEmpty()) {
                state = "mineBlueIceInRange";
                wasGathering = false;
                wasBreathing = false;
                return;
            }
        }

        if (countDroppedBlueIce(mc.player, 10) > 0) {
            gatherBlueIce();
            return;
        }
        IceRailAutoReplenish autoReplenish = Modules.get().get(IceRailAutoReplenish.class);
        int fireworkSlot = getItemInInventory(Items.FIREWORK_ROCKET, toolSlot);

        //If there are valid icebergs in render distance
        if (foundBlock) {
            BlockPos nearestCoord = nearestGroupCoords(true);
            landCoords = nearestCoord.withY(getMaxY(nearestCoord));

            //If player is above the closest iceberg, land.
            if (Math.abs(mc.player.getX() - landCoords.getX()) < 1.0 && Math.abs(mc.player.getZ() - landCoords.getZ()) < 1.0) {
                state = "land";
                vertex = landCoords.withY(mc.player.getBlockY());
                scanningWorld = true;
                returnToState = "goToBlueIce";

                //Otherwise, fly to the iceberg.
            } else {
                //If the player is too low, fly up.
                if (mc.player.getY() < frozenOceanCruiseAltitude.get()) {

                    //If the player doesn't have headspace to jump, path up.
                    BlockPos pos = mc.player.getBlockPos();
                    boolean canJump = mc.world.getBlockState(pos.up(2)).isAir();
                    error("head up 2: " + mc.world.getBlockState(pos.up(2)).getBlock());
                    if (!canJump) {
                        BlockPos goal = pos.withY(getMaxY(pos));
                        createCustomGoalProcess(goal);
                        return;
                    }

                    //Otherwise, spam space and use fireworks.
                    isPathing = false;
                    InvUtils.swap(fireworkSlot, false);
                    if (tick % (mc.player.getY() < getMaxY(mc.player.getBlockPos()) + 5 ? 20 : 80) == 0) {
                        mc.player.setPitch((float) -80.0);
                        if (mc.player.isFallFlying()) {
                            Utils.rightClick();
                        } else {
                            mc.options.useKey.setPressed(false);
                        }
                    } else {
                        setKeyPressed(mc.options.jumpKey, mc.player.getY() < 62 || tick % 4 < 2);
                    }

                    //If the player is high enough, pitch down and look towards the iceberg.
                } else {
                    float[] angles = PlayerUtils.calculateAngle(new Vec3d(landCoords.getX(), landCoords.getY(), landCoords.getZ()));
                    mc.player.setYaw(angles[0]);
                    mc.player.setPitch(15);
                }
            }
            return;
        }

        //If the player is in a nether portal, path out of the nether portal.
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
            createCustomGoalProcess(goal);
            return;
        }

        //Every two seconds, scan for blue ice.
        if (isInFrozenOcean && tick % 60 == 30) {
            scanningWorld = true;
        }

        //Determine altitude to fly at.
        int flyHeight = (hasFoundFrozenOcean ? frozenOceanCruiseAltitude.get() : cruiseAltitude.get());

        //If there is no valid blue ice chunks:
        if (mc.player.getY() < flyHeight) {
            InvUtils.swap(fireworkSlot, false);

            //Use fireworks and spam spacebar. Spam fireworks when close to ground.
            if (tick % (mc.player.getY() < getMaxY(mc.player.getBlockPos()) + 5 ? 20 : 80) == 0) {
                mc.player.setPitch((float) -45.0);
                if (mc.player.isFallFlying()) {

                    //[GLITCHED] If something is blocking player (like an ice block), mine it.
                    if (getLookingAtBlock() != Blocks.AIR) {
                        swapToPickaxe();
                        mc.options.attackKey.setPressed(true);

                        //Otherwise, use fireworks.
                    } else {
                        Utils.rightClick();
                        mc.options.attackKey.setPressed(false);
                    }
                }
            } else {
                setKeyPressed(mc.options.jumpKey, tick % 4 < 2);
            }

            //When player has reached Fly Height:
        } else {
            //When player has reached peak height, begin descending.
            double velocitySquared = Math.pow(mc.player.getVelocity().x, 2) + Math.pow(mc.player.getVelocity().y, 2) + Math.pow(mc.player.getVelocity().z, 2);
            if (velocitySquared < 0.25) {
                mc.player.setPitch((float) 15.0);
                setKeyPressed(mc.options.jumpKey, false);
            }
        }

        //If player has not found a frozen ocean yet:
        if (!hasFoundFrozenOcean) {

            //Determine direction to search for frozen ocean.
            if (getPlayerDirection() == null) return;
            float yaw = switch (getPlayerDirection()) {
                case NORTH -> (float) 90;
                case SOUTH -> (float) -90;
                case EAST -> (float) 180;
                case WEST -> (float) 0;
                default -> 0;
            };

            //If player has found a cold biome, search around for a frozen ocean.
            if (isInColdBiome() || leg > 0) {
                if (vertex == null) {
                    vertex = mc.player.getBlockPos();
                }
                double squaredDistance = Math.pow(vertex.getX() - mc.player.getX(), 2) + Math.pow(vertex.getZ() - mc.player.getZ(), 2);
                if (leg == 0) {
                    leg = 1;
                }
                if (leg == 1) {
                    mc.player.setYaw(yaw - 45);
                    if (squaredDistance > 9000000) {
                        vertex = mc.player.getBlockPos();
                        leg = 2;
                    }
                }
                if (leg == 2) {
                    mc.player.setYaw(yaw + 90);
                    if (squaredDistance > 18000000) {
                        vertex = mc.player.getBlockPos();
                        leg = 3;
                    }
                }
                if (leg == 3) {
                    mc.player.setYaw(yaw - 135);
                    if (squaredDistance > 9000000) {
                        vertex = mc.player.getBlockPos();
                        leg = 0;
                    }
                }

                //Before finding a cold biome, fly in a straight line.
            } else {
                mc.player.setYaw(yaw);
                vertex = mc.player.getBlockPos();
            }

            //If player has already found a frozen ocean (and is searching for blue icebergs):
        } else {

            //[NEEDS TESTING] If player has left the frozen ocean, look randomly every 3 seconds until it is back.
            if (!isInFrozenOcean && tick % 60 == 0) {
                lookInRandomDirection();
            }
        }
    }

    private void handleLand() {
        assert mc.player != null;
        assert mc.world != null;

        isPathing = true;
        if (mc.player.isFallFlying() && mc.player.getBlockY() > 60) {
            double xDiff = vertex.getX() - mc.player.getX();
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

    private void cancelBaritone() {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getPathingBehavior().cancelEverything();
        baritone.getCustomGoalProcess().setGoalAndPath(null);
        Module iceRailGatherItem = Modules.get().get("ice-rail-gather-item");
        if (iceRailGatherItem.isActive()) {
            iceRailGatherItem.toggle();
        }
        error("cancelBaritone");
    }

    private void lookInRandomDirection() {
        assert mc.player != null;
        mc.player.setYaw((float) (Math.random() * 360));
    }

    //This method is used when player has landed but is not near blue ice.
    private void handleGoToBlueIce() {
        assert mc.player != null;
        assert mc.world != null;

        //Check if player is in a frozen ocean.
        Optional<RegistryKey<Biome>> biome = mc.world.getBiome(mc.player.getBlockPos()).getKey();
        boolean isInFrozenOcean = biome.equals(Optional.of(BiomeKeys.FROZEN_OCEAN)) ||
                biome.equals(Optional.of(BiomeKeys.DEEP_FROZEN_OCEAN));

        ArrayList<BlockPos> range = getBlueIceInRange();

        //If there is no blue ice in range:
        if (range.isEmpty()) {

            //If the current iceberg no longer exists, fly to a new one.
            if (groups.isEmpty() || currentIceberg >= groups.size() - 1 || (int) groups.get(currentIceberg + 1) == 0) {
                error("Failed to go to blue ice because current iceberg doesn't exist, flyToBlueIce");
                lookInRandomDirection();
                state = "flyToBlueIce";

                //If the current iceberg exists, path to it.
            } else {
                if (scanningWorld) {
                    getBlockGroups(Blocks.BLUE_ICE);
                    scanningWorld = false;
                }
                BlockPos goal = nearestGroupCoords(true);
                goal = (slice_minY == 0 ? goal : goal.withY(slice_minY));
                if (PlayerUtils.isWithin(goal, 2.0)) {
                    cancelBaritone();
                    error("cancelBaritone 1746");
                } else {
                    createCustomGoalProcess(goal);
                }
            }

            //Once there is blue ice in range, begin mining.
        } else {
            wasGathering = false;
            wasBreathing = false;
            state = "mineBlueIceInRange";
        }
    }

    private void createCustomGoalProcess(BlockPos goal) {
        ICustomGoalProcess customGoalProcess = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess();
        if (!customGoalProcess.isActive() || customGoalProcess.getGoal() == null) {
            error("customGoalProcess.getGoal(): " + customGoalProcess.getGoal() + " real goal: " + goal);
            customGoalProcess.setGoalAndPath(new GoalBlock(goal));
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

    private BlockPos getLookingAtBlockPos() {
        assert mc.world != null;
        assert mc.crosshairTarget != null;
        HitResult target = mc.crosshairTarget;
        if (target.getType() == HitResult.Type.BLOCK) {
            return ((BlockHitResult) target).getBlockPos();
        } else {
            return null;
        }
    }

    private void gatherBlueIce() {
        assert mc.player != null;
        getItemInInventory(Items.DIAMOND_PICKAXE, toolSlot);
        mc.options.attackKey.setPressed(true);
        mc.options.sneakKey.setPressed(false);
        mc.options.forwardKey.setPressed(true);
        mc.options.jumpKey.setPressed(mc.player.getY() < 60);
        if (!isGatheringItems()) {
            IceRailGatherItem(Items.BLUE_ICE);
        }
    }

    private void centerPlayer() {
        if (tick % 3 > 0) return;

        assert mc.player != null;
        mc.player.setYaw(-90.0f);
        GameOptions ops = mc.options;

        boolean choice = Math.abs(mc.player.getPos().x % 1) > 0.5;
        if (mc.player.getPos().x > 0) {
            ops.backKey.setPressed(choice);
            ops.forwardKey.setPressed(!choice);
        } else {
            ops.backKey.setPressed(!choice);
            ops.forwardKey.setPressed(choice);
        }
        choice = Math.abs(mc.player.getPos().z % 1) > 0.5;
        if (mc.player.getPos().z > 0) {
            ops.leftKey.setPressed(choice);
            ops.rightKey.setPressed(!choice);
        } else {
            ops.leftKey.setPressed(!choice);
            ops.rightKey.setPressed(choice);
        }
    }
    private ArrayList<BlockPos> range = null;
    private ArrayList<BlockPos> slice = null;
    private void handleMineBlueIceInRange() {
        assert mc.player != null;
        assert mc.world != null;
        range = getBlueIceInRange();
        slice = getIcebergSlice();

        //Air is on a scale of 0-300
        if (mc.player.getAir() < 30 || wasBreathing) {
            wasBreathing = mc.player.getAir() < 300;
            mc.options.jumpKey.setPressed(true);
            mc.options.sneakKey.setPressed(false);
            mc.options.attackKey.setPressed(true);
            mc.player.setPitch(-90.0f);
            centerPlayer();
            error("getting air");
            return;
        }
        int blueIceInRange = countDroppedBlueIce(mc.player, 10);
        if (blueIceInRange > 60 || wasGathering) {
            wasGathering = blueIceInRange > 0;
            gatherBlueIce();
            error("gatherBlueIce");
            return;
        }
        cancelBaritone();
        error("cancelBaritone 1865");

        boolean walkForward = true;

        boolean isUnderWater = mc.world.getFluidState(mc.player.getBlockPos()).getFluid() == Fluids.WATER;
        boolean jump = mc.player.getY() < slice_minY && mc.player.getY() < 60;
        if (range.isEmpty() || wasTowering) {
            if (slice.isEmpty() && !wasTowering && !(mc.player.getY() < slice_minY)) {
                if (tick % 80 == 0) getBlockGroups(Blocks.BLUE_ICE);
                if (groups.size() > currentIceberg && (int) groups.get(currentIceberg + 1) > 0 && slice_minY > 46) {
                    error("Decreasing slice_minY and slice_maxY");
                    slice_minY -= sliceHeight.get();
                    slice_maxY -= sliceHeight.get();
                } else {
                    error("switching to goToBlueIce");
                    state = "goToBlueIce";
                    scanningWorld = true;
                    mc.options.attackKey.setPressed(false);
                    mc.options.sneakKey.setPressed(false);
                    mc.options.forwardKey.setPressed(false);
                    mc.options.backKey.setPressed(false);
                }
            } else {
                error("walking forwards to get more blue ice");
                swapToPickaxe();
                if (mineObstructingBlocks()) {
                    mc.options.jumpKey.setPressed(false);
                    return;
                }
                if (mc.player.getY() < slice_minY) {
                    wasTowering = true;
                    mc.options.sneakKey.setPressed(false);
                    mc.options.forwardKey.setPressed(false);
                    mc.options.backKey.setPressed(false);
                    if (mc.world.getBlockState(mc.player.getBlockPos().up(2)).isAir()) {
                        mc.player.setPitch(90.0f);
                        mc.options.jumpKey.setPressed(true);
                        mc.options.attackKey.setPressed(false);
                    } else {
                        mc.player.setPitch(-90.0f);
                        mc.options.jumpKey.setPressed(false);
                        mc.options.attackKey.setPressed(true);
                    }
                } else {
                    wasTowering = false;
                    lookAtBlock(slice.getFirst());
                    mc.options.forwardKey.setPressed(true);
                    mc.options.jumpKey.setPressed(false);
                    mc.options.sneakKey.setPressed(tick % 40 < 35);
                    mc.options.backKey.setPressed(false);
                    if (mc.player.getY() > slice_minY + 1) {
                        return;
                    }
                }
                getItemInInventory(Items.BLUE_ICE, placingSlot);
                airPlace(Items.BLUE_ICE, mc.player.getBlockPos().up(-1), Direction.DOWN);
            }
        } else {
            error("Mining. slice_minY: " + slice_minY + ", slice_maxY: " + slice_maxY + ", mining block: " + range.getFirst() + ", previous block: " + previousRangeFirst + ", mineDuration:" + mineDuration);
            BlockPos playerPos = mc.player.getBlockPos();

            if (previousRangeFirst == null || !previousRangeFirst.equals(range.getFirst())) {
                if (mineDuration > 2 || previousRangeFirst == null) {
                    mineDuration = 0;
                    previousRangeFirst = range.getFirst();
                }
                error("caught 2 tick delay between switching target block");
            } else {
                List<Integer> i = Arrays.asList(0,-1,1,0);
                for (Integer j : i) {
                    BlockPos target = range.getFirst().up(j);
                    mc.player.setYaw((float) Rotations.getYaw(target));
                    mc.player.setPitch((float) Rotations.getPitch(target));
                    if (j != 0) error("looked up by " + j + " blocks");
                    if (getLookingAtBlockPos() != null) {
                        break;
                    }
                }
            }
            mineDuration++;

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
            swapToPickaxe();
            if (isOverBlueIce) {
                mc.options.jumpKey.setPressed(jump);
            } else if (isUnderWater) {
                mc.options.jumpKey.setPressed(true);
            }
            mc.options.sneakKey.setPressed(!jump);
            mc.options.forwardKey.setPressed(walkForward);
            boolean attack = (isOverBlueIce ? (mineDuration % 35 < 30) : (mineDuration % 150 < 125));
            mc.options.attackKey.setPressed(attack);
            mc.options.backKey.setPressed(false);
        }
    }

    private void swapToPickaxe() {
        getItemInInventory(Items.DIAMOND_PICKAXE, toolSlot);
        InvUtils.swap(toolSlot, false);
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

        boolean inOverworld = mc.world.getDimension().bedWorks();
        List<Boolean> conditions;
        if (inOverworld) {
            conditions = Arrays.asList(
                    buildTimer % 20 < 9,
                    buildTimer % 20 < 18,
                    buildTimer % 20 == 18
            );
        } else {
            conditions = Arrays.asList(
                    buildTimer % 10 < 3,
                    buildTimer % 10 < 6,
                    buildTimer % 10 == 8
            );
        }
        if (conditions.get(0)) {
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
        } else if (conditions.get(1)) {
            if (!mc.world.getBlockState(target).isAir() && mc.world.getBlockState(target).getBlock() != Blocks.OBSIDIAN) {
                packetMine(target);
            }
        } else if (conditions.get(2)) {
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
        assert mc.player != null;
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

    private void openShulker() {
        mc.setScreen(null);
        // Open the shulker
        assert mc.player != null;
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(shulkerBlockPos), Direction.DOWN,
                        shulkerBlockPos, false), 0));


        mc.setScreen(null);
        hasOpenedShulker = true;
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
            createCustomGoalProcess(restockingStartPosition);
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
            openShulker();
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
