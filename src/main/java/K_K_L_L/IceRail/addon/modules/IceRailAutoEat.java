/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Edited by K-K-L-L (Discord:theorangedot).
 */

package K_K_L_L.IceRail.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.ItemUseCrosshairTargetEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AnchorAura;
import meteordevelopment.meteorclient.systems.modules.combat.BedAura;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import K_K_L_L.IceRail.addon.IceRail;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

import static K_K_L_L.IceRail.addon.modules.IceHighwayBuilder.needsToScaffold;

public class IceRailAutoEat extends Module {
    private static final Class<? extends Module>[] AURAS = new Class[]{KillAura.class, CrystalAura.class, AnchorAura.class, BedAura.class};

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgThreshold = settings.createGroup("Threshold");

    // General
    public final Setting<List<Item>> blacklist = sgGeneral.add(new ItemListSetting.Builder()
            .name("blacklist")
            .description("Which items to not eat.")
            .defaultValue(
                    Items.CHORUS_FRUIT,
                    Items.POISONOUS_POTATO,
                    Items.PUFFERFISH,
                    Items.CHICKEN,
                    Items.ROTTEN_FLESH,
                    Items.SPIDER_EYE,
                    Items.SUSPICIOUS_STEW
            )
            .filter(item -> item.getComponents().get(DataComponentTypes.FOOD) != null)
            .build()
    );

    private final Setting<Boolean> pauseAuras = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-auras")
            .description("Pauses all auras when eating.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> eatEGaps = sgGeneral.add(new BoolSetting.Builder()
            .name("eat-egap-when-burning")
            .description("Eats an enchanted golden apple if the player is burning.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> pauseBaritone = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-baritone")
            .description("Pause baritone when eating.")
            .defaultValue(true)
            .build()
    );

    // Threshold
    private final Setting<ThresholdMode> thresholdMode = sgThreshold.add(new EnumSetting.Builder<ThresholdMode>()
            .name("threshold-mode")
            .description("The threshold mode to trigger auto eat.")
            .defaultValue(ThresholdMode.Hunger)
            .build()
    );

    private final Setting<Double> healthThreshold = sgThreshold.add(new DoubleSetting.Builder()
            .name("health-threshold")
            .description("The level of health you eat at.")
            .defaultValue(12)
            .range(1, 19)
            .sliderRange(1, 19)
            .visible(() -> thresholdMode.get() != ThresholdMode.Hunger)
            .build()
    );

    private final Setting<Integer> hungerThreshold = sgThreshold.add(new IntSetting.Builder()
            .name("hunger-threshold")
            .description("The level of hunger you eat at.")
            .defaultValue(12)
            .range(1, 19)
            .sliderRange(1, 19)
            .visible(() -> thresholdMode.get() != ThresholdMode.Health)
            .build()
    );

    public static boolean eating;
    public static boolean getIsEating() {
        return eating;
    }

    private int slot, prevSlot;

    private final List<Class<? extends Module>> wasAura = new ArrayList<>();
    private boolean wasBaritone = false;

    public IceRailAutoEat() {
        super(IceRail.CATEGORY, "ice-rail-auto-eat", "Automatically eats food.");
    }

    @Override
    public void onDeactivate() {
        stopEating();
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onTick(TickEvent.Pre event) {
        assert mc.player != null;
        if (needsToScaffold()) return;
        if (!isActive()) return;
        Module iceHighwayBuilder = Modules.get().get("ice-highway-builder");
        if (iceHighwayBuilder != null) {
            BoolSetting amountSetting = (BoolSetting) iceHighwayBuilder.settings.get("eat-egap-when-burning");
            if (amountSetting != null) amountSetting.set(eatEGaps.get());
        }

        // Skip if Auto Gap is already eating
        if (Modules.get().get(AutoGap.class).isEating()) return;

        if (eating) {
            // If we are eating check if we should still be eating
            if (shouldEat()) {
                // Check if the item in current slot is not food
                if (mc.player.getInventory().getStack(slot).get(DataComponentTypes.FOOD) != null) {
                    // If not try finding a new slot
                    int slot = findSlot();

                    // If no valid slot was found then stop eating
                    if (slot == -1) {
                        stopEating();
                        return;
                    }
                    // Otherwise change to the new slot
                    else {
                        changeSlot(slot);
                    }
                }

                // Continue eating
                eat();
            }
            // If we shouldn't be eating anymore then stop
            else {
                stopEating();
            }
        } else {
            // If we are not eating check if we should start eating
            if (shouldEat()) {
                // Try to find a valid slot
                slot = findSlot();

                // If slot was found then start eating
                if (slot != -1) startEating();
            }
        }
    }

    @EventHandler
    private void onItemUseCrosshairTarget(ItemUseCrosshairTargetEvent event) {
        if (eating) event.target = null;
    }

    private void startEating() {
        assert mc.player != null;
        prevSlot = mc.player.getInventory().selectedSlot;
        eat();

        // Pause auras
        wasAura.clear();
        if (pauseAuras.get()) {
            for (Class<? extends Module> klass : AURAS) {
                Module module = Modules.get().get(klass);

                if (module.isActive()) {
                    wasAura.add(klass);
                    module.toggle();
                }
            }
        }

        // Pause baritone
        if (pauseBaritone.get() && PathManagers.get().isPathing() && !wasBaritone) {
            wasBaritone = true;
            PathManagers.get().pause();
        }
    }

    private void eat() {
        int slot = findSlot();
        if (slot == -1 || mc.player == null) return;

        ItemStack stack = mc.player.getInventory().getStack(slot);
        Item item = stack.getItem();

        // Only eat enchanted golden apple if player is burning
        if (item == Items.ENCHANTED_GOLDEN_APPLE && !mc.player.isOnFire()) return;

        // Normal food eating logic
        if (mc.player.getHungerManager().getFoodLevel() >= 20 && item != Items.ENCHANTED_GOLDEN_APPLE) return;

        changeSlot(slot);
        setPressed(true);
        if (!mc.player.isUsingItem()) Utils.rightClick();

        eating = true;
    }

    private void stopEating() {
        if (prevSlot != -1)
            changeSlot(prevSlot);

        setPressed(false);

        eating = false;

        // Resume auras
        if (pauseAuras.get()) {
            for (Class<? extends Module> klass : AURAS) {
                Module module = Modules.get().get(klass);

                if (wasAura.contains(klass) && !module.isActive()) {
                    module.toggle();
                }
            }
        }

        // Resume baritone
        if (pauseBaritone.get() && wasBaritone) {
            wasBaritone = false;
            PathManagers.get().resume();
        }
    }

    private void setPressed(boolean pressed) {
        mc.options.useKey.setPressed(pressed);
    }

    private void changeSlot(int slot) {
        InvUtils.swap(slot, false);
        this.slot = slot;
    }

    private boolean shouldEat() {
        if (mc.player == null) return false;
        boolean health = mc.player.getHealth() <= healthThreshold.get();
        boolean hunger = mc.player.getHungerManager().getFoodLevel() <= hungerThreshold.get();
        boolean isBurning = mc.player.isOnFire();
        boolean hasFireResistance = mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
        IceRailAutoReplenish autoReplenish = Modules.get().get(IceRailAutoReplenish.class);
        // Special case for enchanted golden apple: eat if burning and no fire resistance
        if (isBurning && !hasFireResistance) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
                    autoReplenish.foodSlotItem = "gapple";
                    return true;
                }
            }
        }

        // Regular eating conditions for other food based on hunger and health thresholds
        autoReplenish.foodSlotItem = "food";
        return thresholdMode.get().test(health, hunger);
    }

    private int findSlot() {
        assert mc.player != null;
        int slot = -1;
        int bestHunger = -1;

        // Prioritize enchanted golden apple if burning and no fire resistance
        if (mc.player.isOnFire() && !mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
            for (int i = 0; i < 9; i++) {
                if (eatEGaps.get() && mc.player.getInventory().getStack(i).getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
                    return i;
                }
            }
        }

        for (int i = 0; i < 9; i++) {
            // Skip if item isn't food
            Item item = mc.player.getInventory().getStack(i).getItem();
            FoodComponent foodComponent = item.getComponents().get(DataComponentTypes.FOOD);
            if (foodComponent == null) continue;

            // Skip enchanted golden apple if not burning or has fire resistance
            if (item == Items.ENCHANTED_GOLDEN_APPLE) continue;

            // Skip if item is in blacklist
            if (blacklist.get().contains(item)) continue;

            // Check if hunger value is better
            int hunger = foodComponent.nutrition();
            if (hunger > bestHunger) {
                // Select the current item
                slot = i;
                bestHunger = hunger;
            }
        }

        // Offhand slot check for normal food, excluding enchanted golden apples if not burning
        Item offHandItem = mc.player.getOffHandStack().getItem();
        if (offHandItem.getComponents().get(DataComponentTypes.FOOD) != null &&
                !blacklist.get().contains(offHandItem) &&
                Objects.requireNonNull(offHandItem.getComponents().get(DataComponentTypes.FOOD)).nutrition() > bestHunger &&
                !(offHandItem == Items.ENCHANTED_GOLDEN_APPLE && !mc.player.isOnFire()))
            slot = SlotUtils.OFFHAND;

        return slot;
    }

    public enum ThresholdMode {
        Health((health, hunger) -> health),
        Hunger((health, hunger) -> hunger);

        private final BiPredicate<Boolean, Boolean> predicate;

        ThresholdMode(BiPredicate<Boolean, Boolean> predicate) {
            this.predicate = predicate;
        }

        public boolean test(boolean health, boolean hunger) {
            return predicate.test(health, hunger);
        }
    }
}
