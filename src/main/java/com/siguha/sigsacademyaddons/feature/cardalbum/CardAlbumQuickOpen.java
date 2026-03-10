package com.siguha.sigsacademyaddons.feature.cardalbum;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CardAlbumQuickOpen {

    private static final String CARD_ALBUM_ID = "academy:card_album";
    private static final int OFFHAND_SLOT = 45;
    private static final int MAX_TIMEOUT_TICKS = 300;
    private static final int SYNC_TICKS = 2;
    private static final int SCREEN_OPEN_TIMEOUT = 40;

    private enum State {
        IDLE,
        SAVE_PICKUP_MAINHAND,
        SAVE_PLACE_OFFHAND,
        STASH_OLD_OFFHAND,
        ALBUM_PICKUP,
        ALBUM_PLACE_MAINHAND,
        USE_MAINHAND,
        WAIT_SCREEN_OPEN,
        WAIT_SCREEN_CLOSE,
        RESTORE_PICKUP_MAINHAND,
        RESTORE_PLACE_TRINKET,
        RESTORE_UNSTASH_PICKUP,
        RESTORE_UNSTASH_OFFHAND,
        RESTORE_PICKUP_OFFHAND,
        RESTORE_PLACE_MAINHAND
    }

    private State state = State.IDLE;
    private int waitTicks = 0;
    private int timeoutTicks = 0;
    private int trinketSlotIndex = -1;
    private int hotbarSlotIndex = -1;
    private int stashSlotIndex = -1;
    private boolean hadMainHandItem = false;
    private boolean hadOffhandItem = false;
    private int screenOpenWaitTicks = 0;

    public boolean isActive() {
        return state != State.IDLE;
    }

    public void start() {
        if (state != State.IDLE) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isDeadOrDying() || mc.screen != null || mc.gameMode == null) return;

        trinketSlotIndex = findCardAlbumSlot(player);
        if (trinketSlotIndex < 0) {
            player.displayClientMessage(
                    Component.literal("§cNo card album found in trinket slot"), true);
            return;
        }

        hotbarSlotIndex = 36 + player.getInventory().selected;
        hadMainHandItem = !player.getMainHandItem().isEmpty();
        hadOffhandItem = !player.getOffhandItem().isEmpty();
        state = hadMainHandItem ? State.SAVE_PICKUP_MAINHAND : State.ALBUM_PICKUP;
        waitTicks = 0;
        timeoutTicks = 0;
        stashSlotIndex = -1;
        screenOpenWaitTicks = 0;

        SigsAcademyAddons.LOGGER.info("[SAA CardAlbum] Starting quick-open (trinket={}, hotbar={}, mainhand={}, offhand={})",
                trinketSlotIndex, hotbarSlotIndex, hadMainHandItem, hadOffhandItem);
    }

    public void tick() {
        if (state == State.IDLE) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isDeadOrDying() || mc.gameMode == null) {
            SigsAcademyAddons.LOGGER.warn("[SAA CardAlbum] Player unavailable, aborting");
            reset();
            return;
        }

        timeoutTicks++;
        if (timeoutTicks > MAX_TIMEOUT_TICKS) {
            SigsAcademyAddons.LOGGER.warn("[SAA CardAlbum] Timeout reached, emergency restore");
            emergencyRestore(mc, player);
            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        AbstractContainerMenu menu = player.inventoryMenu;

        switch (state) {
            case SAVE_PICKUP_MAINHAND -> {
                click(mc, menu, hotbarSlotIndex, 0, ClickType.PICKUP);
                state = State.SAVE_PLACE_OFFHAND;
                waitTicks = SYNC_TICKS;
            }

            case SAVE_PLACE_OFFHAND -> {
                click(mc, menu, OFFHAND_SLOT, 0, ClickType.PICKUP);
                if (hadOffhandItem) {
                    state = State.STASH_OLD_OFFHAND;
                } else {
                    state = State.ALBUM_PICKUP;
                }
                waitTicks = SYNC_TICKS;
            }

            case STASH_OLD_OFFHAND -> {
                stashSlotIndex = findEmptySlot(player);
                if (stashSlotIndex < 0) {
                    SigsAcademyAddons.LOGGER.warn("[SAA CardAlbum] No empty slot to stash offhand item");
                    emergencyRestore(mc, player);
                    return;
                }
                click(mc, menu, stashSlotIndex, 0, ClickType.PICKUP);
                state = State.ALBUM_PICKUP;
                waitTicks = SYNC_TICKS;
            }

            case ALBUM_PICKUP -> {
                click(mc, menu, trinketSlotIndex, 0, ClickType.PICKUP);
                state = State.ALBUM_PLACE_MAINHAND;
                waitTicks = SYNC_TICKS;
            }

            case ALBUM_PLACE_MAINHAND -> {
                click(mc, menu, hotbarSlotIndex, 0, ClickType.PICKUP);
                state = State.USE_MAINHAND;
                waitTicks = SYNC_TICKS;
            }

            case USE_MAINHAND -> {
                if (player.isUsingItem()) return;

                String mainHandId = getItemId(player.getMainHandItem());
                if (!mainHandId.equals(CARD_ALBUM_ID)) {
                    SigsAcademyAddons.LOGGER.warn("[SAA CardAlbum] Album not in main hand (found: {}), aborting", mainHandId);
                    emergencyRestore(mc, player);
                    return;
                }

                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                state = State.WAIT_SCREEN_OPEN;
                screenOpenWaitTicks = 0;
                waitTicks = 2;
                SigsAcademyAddons.LOGGER.info("[SAA CardAlbum] Used album from main hand");
            }

            case WAIT_SCREEN_OPEN -> {
                if (mc.screen != null) {
                    state = State.WAIT_SCREEN_CLOSE;
                } else {
                    screenOpenWaitTicks++;
                    if (screenOpenWaitTicks > SCREEN_OPEN_TIMEOUT) {
                        SigsAcademyAddons.LOGGER.warn("[SAA CardAlbum] Screen never opened, restoring");
                        state = State.RESTORE_PICKUP_MAINHAND;
                        waitTicks = SYNC_TICKS;
                    }
                }
            }

            case WAIT_SCREEN_CLOSE -> {
                if (mc.screen == null) {
                    state = State.RESTORE_PICKUP_MAINHAND;
                    waitTicks = 3;
                    SigsAcademyAddons.LOGGER.info("[SAA CardAlbum] Screen closed, restoring inventory");
                }
            }

            case RESTORE_PICKUP_MAINHAND -> {
                click(mc, menu, hotbarSlotIndex, 0, ClickType.PICKUP);
                state = State.RESTORE_PLACE_TRINKET;
                waitTicks = SYNC_TICKS;
            }

            case RESTORE_PLACE_TRINKET -> {
                click(mc, menu, trinketSlotIndex, 0, ClickType.PICKUP);
                waitTicks = SYNC_TICKS;
                if (stashSlotIndex >= 0) {
                    state = State.RESTORE_UNSTASH_PICKUP;
                } else if (hadMainHandItem) {
                    state = State.RESTORE_PICKUP_OFFHAND;
                } else {
                    SigsAcademyAddons.LOGGER.info("[SAA CardAlbum] Restore complete");
                    reset();
                }
            }

            case RESTORE_UNSTASH_PICKUP -> {
                click(mc, menu, stashSlotIndex, 0, ClickType.PICKUP);
                state = State.RESTORE_UNSTASH_OFFHAND;
                waitTicks = SYNC_TICKS;
            }

            case RESTORE_UNSTASH_OFFHAND -> {
                click(mc, menu, OFFHAND_SLOT, 0, ClickType.PICKUP);
                state = State.RESTORE_PLACE_MAINHAND;
                waitTicks = SYNC_TICKS;
            }

            case RESTORE_PICKUP_OFFHAND -> {
                click(mc, menu, OFFHAND_SLOT, 0, ClickType.PICKUP);
                state = State.RESTORE_PLACE_MAINHAND;
                waitTicks = SYNC_TICKS;
            }

            case RESTORE_PLACE_MAINHAND -> {
                click(mc, menu, hotbarSlotIndex, 0, ClickType.PICKUP);
                SigsAcademyAddons.LOGGER.info("[SAA CardAlbum] Restore complete");
                reset();
            }
        }
    }

    private void click(Minecraft mc, AbstractContainerMenu menu, int slotId, int button, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slotId, button, type, mc.player);
    }

    private int findCardAlbumSlot(LocalPlayer player) {
        for (Slot slot : player.inventoryMenu.slots) {
            if (slot.getItem().isEmpty()) continue;
            if (getItemId(slot.getItem()).equals(CARD_ALBUM_ID)) {
                return slot.index;
            }
        }
        return -1;
    }

    private int findEmptySlot(LocalPlayer player) {
        for (int i = 9; i <= 35; i++) {
            if (i < player.inventoryMenu.slots.size()
                    && player.inventoryMenu.slots.get(i).getItem().isEmpty()) {
                return player.inventoryMenu.slots.get(i).index;
            }
        }
        return -1;
    }

    private String getItemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private void emergencyRestore(Minecraft mc, LocalPlayer player) {
        SigsAcademyAddons.LOGGER.warn("[SAA CardAlbum] Emergency restore");
        AbstractContainerMenu menu = player.inventoryMenu;

        if (getItemId(player.getMainHandItem()).equals(CARD_ALBUM_ID) && trinketSlotIndex >= 0) {
            click(mc, menu, hotbarSlotIndex, 0, ClickType.PICKUP);
            click(mc, menu, trinketSlotIndex, 0, ClickType.PICKUP);
        }

        if (stashSlotIndex >= 0 && stashSlotIndex < menu.slots.size()
                && !menu.slots.get(stashSlotIndex).getItem().isEmpty()) {
            click(mc, menu, stashSlotIndex, 0, ClickType.PICKUP);
            click(mc, menu, OFFHAND_SLOT, 0, ClickType.PICKUP);
        }

        if (hadMainHandItem && !player.getOffhandItem().isEmpty()) {
            click(mc, menu, OFFHAND_SLOT, 0, ClickType.PICKUP);
            click(mc, menu, hotbarSlotIndex, 0, ClickType.PICKUP);
        }

        reset();
    }

    private void reset() {
        state = State.IDLE;
        waitTicks = 0;
        timeoutTicks = 0;
        trinketSlotIndex = -1;
        hotbarSlotIndex = -1;
        stashSlotIndex = -1;
        hadMainHandItem = false;
        hadOffhandItem = false;
        screenOpenWaitTicks = 0;
    }
}
