/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Edited by K-K-L-L (Discord:theorangedot).
 */

package K_K_L_L.IceRail.addon.modules;

import K_K_L_L.IceRail.addon.IceRail;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.ItemStackAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;

import java.util.Objects;

public class IceRailAutoReplenish extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
            .name("threshold")
            .description("The threshold of items left to trigger replenishment.")
            .defaultValue(16)
            .min(1)
            .sliderRange(1, 63)
            .build()
    );

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Tick delay between replenishment checks.")
            .defaultValue(1)
            .min(0)
            .build()
    );

    //Tool, scaffold block, food, placing slot, trash slot, shulker slot
    public final Setting<Integer> toolSlot = sgGeneral.add(new IntSetting.Builder()
            .name("tool-slot")
            .description("Flint and steel, pickaxes, fireworks, and swords are swapped to this slot. Slot number must be unique.")
            .defaultValue(0)
            .min(0).max(8).build()
    );
    public final Setting<Integer> scaffoldSlot = sgGeneral.add(new IntSetting.Builder()
            .name("scaffold-slot")
            .description("Valid scaffold blocks are swapped to this slot. Slot number must be unique.")
            .defaultValue(8)
            .min(0).max(8).build()
    );
    public final Setting<Integer> foodSlot = sgGeneral.add(new IntSetting.Builder()
            .name("food-slot")
            .description("Foods are swapped to this slot. Slot number must be unique.")
            .defaultValue(2)
            .min(0).max(8).build()
    );
    public final Setting<Integer> placingSlot = sgGeneral.add(new IntSetting.Builder()
            .name("placing-slot")
            .description("Blue ice, echests, and obsidian are swapped to this slot. Slot number must be unique.")
            .defaultValue(1)
            .min(0).max(8).build()
    );
    public final Setting<Integer> trashSlot = sgGeneral.add(new IntSetting.Builder()
            .name("trash-slot")
            .description("Items are thrown from this slot while clearing inventory. Slot number must be unique.")
            .defaultValue(3)
            .min(0).max(8).build()
    );
    public final Setting<Integer> shulkerSlot = sgGeneral.add(new IntSetting.Builder()
            .name("shulker-slot")
            .description("Shulkers are swapped to this slot during shulker restocking. Slot number must be unique.")
            .defaultValue(4)
            .min(0).max(8).build()
    );

    private final ItemStack[] items = new ItemStack[10];
    private int tickDelayLeft;
    public boolean isActive;
    public String toolSlotItem = "pickaxe"; //When pickaxe: Find and swap the best pickaxe using the existing method. Otherwise: skip
    //ScaffoldSlotItem is determined through an inventory scan with IceHighwayBuilder.scaffoldBlacklist
    public String foodSlotItem = "food"; // any type of food not in the blacklist
    public Item placingSlotItem = Items.BLUE_ICE;
    public String shulkerSlotItem = "blue ice";

    public IceRailAutoReplenish() {
        super(IceRail.CATEGORY, "ice-rail-auto-replenish",
                "Automatically refills specific items in each hotbar slot. %nSlot 1 = Pickaxe, Slot 2 = Netherrack, Slot 7 = reserved, Slot 8 = Pickaxe shulker, Slot 9 = Blue Ice Shulker");

        for (int i = 0; i < items.length; i++) items[i] = new ItemStack(Items.AIR);
    }

    @Override
    public void onActivate() {
        tickDelayLeft = tickDelay.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive) return;
        boolean flag = false;
        if (tickDelayLeft <= 0) {
            tickDelayLeft = tickDelay.get();

            handleToolSlot();
            handleShulkerSlot();
            handlePlacingSlot();
            handleFoodSlot();
        }
        else {
            tickDelayLeft--;
            return;
        }

        if (!flag) {
            error("Ice Rail Auto Replenish is not configured correctly, please configure the module and enable the \"Ice Highway Builder\" module once again.");
            Module iceHighwayBuilder = Modules.get().get("ice-highway-builder");
            if (iceHighwayBuilder.isActive()) iceHighwayBuilder.toggle();
            toggle();
        }
    }
    public boolean isActive() {
        return isActive;
    }
    public void toggle() {
        if (isActive) {
            isActive = false;
        } else {
            isActive = true;
        }
    }
    private void handleToolSlot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Inventory inventory = mc.player.getInventory();
        if (toolSlotItem != "pickaxe") return;
        ItemStack toolStack = inventory.getStack(toolSlot.get());

        if (toolStack.getItem() instanceof PickaxeItem
                && toolStack.getMaxDamage() - toolStack.getDamage() > 50) {
            return; // The first slot already has a valid pickaxe
        }

        int bestSlot = -1;

        for (int i = 0; i < inventory.size(); i++) {
            if (i == toolSlot.get()) continue; // Skip the first slot

            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() instanceof PickaxeItem
                    && stack.getMaxDamage() - stack.getDamage() > 50) {
                bestSlot = i;
                break;
            }
        }

        if (bestSlot != -1) {
            InvUtils.move().from(bestSlot).toHotbar(toolSlot.get());
        }
    }

    private void handleShulkerSlot() {
        assert mc.player != null;
        ItemStack currentStack = mc.player.getInventory().getStack(shulkerSlot.get());
        if (!(currentStack.getItem() instanceof BlockItem &&
                ((BlockItem) currentStack.getItem()).getBlock() instanceof ShulkerBoxBlock) ||
                !hasBlueiceInShulker(currentStack)) {
            ItemStack shulkerFound;
            if (shulkerSlotItem == "blue ice") {
                shulkerFound = findBestBlueIceShulker();
            } else if (shulkerSlotItem == "pickaxes") {
                shulkerFound = findBestPicksShulker();
            } else if (shulkerSlotItem == "food") {
                shulkerFound = findFoodShulker();
            } else {
                shulkerFound = null; //convert string to Item
            }
            if (shulkerFound != null) {
                int sourceSlot = findItemStackSlot(shulkerFound);
                if (sourceSlot != -1) {
                    InvUtils.move().from(sourceSlot).to(shulkerSlot.get());
                }
            }
        }
    }

    private static boolean hasFoodInShulker(ItemStack shulkerBox) {
        ItemStack[] containerItems = new ItemStack[27];
        Utils.getItemsInContainerItem(shulkerBox, containerItems);
        IceRailAutoEat iceRailAutoEat = Modules.get().get(IceRailAutoEat.class);
        for (ItemStack stack : containerItems) {
            if (!stack.isEmpty() &&
                    stack.getItem().getComponents().get(DataComponentTypes.FOOD) != null &&
                    !iceRailAutoEat.blacklist.get().contains(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBlueiceInShulker(ItemStack shulkerBox) {
        ItemStack[] containerItems = new ItemStack[27];
        Utils.getItemsInContainerItem(shulkerBox, containerItems);

        for (ItemStack stack : containerItems) {
            if (!stack.isEmpty() && (stack.getItem() == Items.BLUE_ICE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPicksInShulker(ItemStack shulkerBox) {
        ItemStack[] containerItems = new ItemStack[27];
        Utils.getItemsInContainerItem(shulkerBox, containerItems);

        for (ItemStack stack : containerItems) {
            if (!stack.isEmpty() && (stack.getItem() == Items.DIAMOND_PICKAXE || stack.getItem() == Items.NETHERITE_PICKAXE)) {
                if (stack.getDamage() < stack.getMaxDamage() - 50) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int findPickToSwap(ItemStack shulkerBox) {
        ItemStack[] containerItems = new ItemStack[27];
        Utils.getItemsInContainerItem(shulkerBox, containerItems);
        int i;
        i = 0;
        for (ItemStack stack : containerItems) {
            if (!stack.isEmpty() && (stack.getItem() == Items.DIAMOND_PICKAXE || stack.getItem() == Items.NETHERITE_PICKAXE)) {
                if (stack.getDamage() < stack.getMaxDamage() - 50) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    public static ItemStack findBestBlueIceShulker() {
        for (int i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.getItem() instanceof BlockItem &&
                    ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock) {

                if (hasBlueiceInShulker(stack)) {
                    return stack;
                }
            }
        }
        return null;
    }

    public static ItemStack findFoodShulker() {
        for (int i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.getItem() instanceof BlockItem &&
                    ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock) {

                if (hasFoodInShulker(stack)) {
                    return stack;
                }
            }
        }
        return null;
    }

    public static ItemStack findBestPicksShulker() {
        for (int i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.getItem() instanceof BlockItem &&
                    ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock) {

                if (hasPicksInShulker(stack)) {
                    return stack;
                }
            }
        }
        return null;
    }

    private int findItemStackSlot(ItemStack stack) {
        for (int i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).equals(stack)) {
                return i;
            }
        }
        return -1;
    }

    private void handlePlacingSlot() {
        assert mc.player != null;
        int slot = placingSlot.get();
        Item desiredItem = placingSlotItem;
        ItemStack currentStack = mc.player.getInventory().getStack(slot);

        if (desiredItem == Items.AIR) return;

        if (currentStack.isEmpty() || currentStack.getItem() != desiredItem) {
            int foundSlot = findSpecificItem(desiredItem, slot, threshold.get());
            if (foundSlot != -1) {
                addSlots(slot, foundSlot);
            }
        }
        else if (currentStack.isStackable() && currentStack.getCount() <= threshold.get()) {
            int foundSlot = findSpecificItem(desiredItem, slot, threshold.get() - currentStack.getCount() + 1);
            if (foundSlot != -1) {
                addSlots(slot, foundSlot);
            }
        }
    }

    private void handleFoodSlot() {
        assert mc.player != null;
        int slot = foodSlot.get();
        Item desiredItem;
        if (foodSlotItem == "gapple") {
            desiredItem = Items.ENCHANTED_GOLDEN_APPLE;
        } else {
            desiredItem = Items.ENCHANTED_GOLDEN_APPLE;
            for (int i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem().getComponents().get(DataComponentTypes.FOOD) != null &&
                        !Modules.get().get(IceRailAutoEat.class).blacklist.get().contains(stack.getItem())
                ) {
                    desiredItem = stack.getItem();
                    break;
                }
            }
        }
        ItemStack currentStack = mc.player.getInventory().getStack(slot);

        if (desiredItem == Items.AIR) return;

        if (currentStack.isEmpty() || currentStack.getItem() != desiredItem) {
            int foundSlot = findSpecificItem(desiredItem, slot, threshold.get());
            if (foundSlot != -1) {
                addSlots(slot, foundSlot);
            }
        }
        else if (currentStack.isStackable() && currentStack.getCount() <= threshold.get()) {
            int foundSlot = findSpecificItem(desiredItem, slot, threshold.get() - currentStack.getCount() + 1);
            if (foundSlot != -1) {
                addSlots(slot, foundSlot);
            }
        }
    }

    private int findSpecificItem(Item item, int excludedSlot, int goodEnoughCount) {
        int slot = -1;
        int count = 0;

        assert mc.player != null;
        for (int i = 35; i >= 0; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (i != excludedSlot && stack.getItem() == item) {
                if (stack.getCount() > count) {
                    slot = i;
                    count = stack.getCount();

                    if (count >= goodEnoughCount) break;
                }
            }
        }

        return slot;
    }

    private void addSlots(int to, int from) {
        InvUtils.move().from(from).to(to);
    }

    private void setItem(int slot, ItemStack stack) {
        if (slot == SlotUtils.OFFHAND) slot = 9;

        ItemStack s = items[slot];
        ((ItemStackAccessor) (Object) s).setItem(stack.getItem());
        s.setCount(stack.getCount());
        s.applyComponentsFrom(stack.getComponents());
        if (stack.isEmpty()) ((ItemStackAccessor) (Object) s).setItem(Items.AIR);
    }
}
