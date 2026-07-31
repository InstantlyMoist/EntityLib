package me.tofaa.entitylib.wrapper;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import me.tofaa.entitylib.EntityLib;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static me.tofaa.entitylib.extras.VersionChecker.verifyVersion;

public class WrapperEntityEquipment {

    private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();

    private final WrapperLivingEntity entity;
    private boolean notifyChanges = true;

    private final ItemStack[] equipment = new ItemStack[EQUIPMENT_SLOTS.length];

    public WrapperEntityEquipment(WrapperLivingEntity entity) {
        this.entity = entity;
        Arrays.fill(equipment, ItemStack.EMPTY);
    }


    public void clearSlot(@NotNull EquipmentSlot slot) {
        equipment[slot.ordinal()] = ItemStack.EMPTY;
        refresh();
    }

    public void clearAll() {
        Arrays.fill(equipment, ItemStack.EMPTY);
        refresh();
    }

    public void setHelmet(@Nullable ItemStack itemStack) {
        setItem(EquipmentSlot.HELMET, itemStack);
    }

    public void setChestplate(@Nullable ItemStack itemStack) {
        setItem(EquipmentSlot.CHEST_PLATE, itemStack);
    }

    public void setLeggings(@Nullable ItemStack itemStack) {
        setItem(EquipmentSlot.LEGGINGS, itemStack);
    }

    public void setBoots(@Nullable ItemStack itemStack) {
        setItem(EquipmentSlot.BOOTS, itemStack);
    }

    public void setMainHand(@Nullable ItemStack itemStack) {
        setItem(EquipmentSlot.MAIN_HAND, itemStack);
    }

    public void setOffhand(@Nullable ItemStack itemStack) {
        setItem(EquipmentSlot.OFF_HAND, itemStack);
    }

    public void setItem(@NotNull EquipmentSlot slot, @Nullable ItemStack itemStack) {
        equipment[slot.ordinal()] = itemStack == null ? ItemStack.EMPTY : itemStack;
        refresh();
    }

    public @NotNull ItemStack getItem(@NotNull EquipmentSlot slot) {
        ItemStack itemStack = equipment[slot.ordinal()];
        if (itemStack == null) {
            return ItemStack.EMPTY;
        }
        return itemStack;
    }

    public @NotNull ItemStack getHelmet() {
        return getItem(EquipmentSlot.HELMET);
    }

    public @NotNull ItemStack getChestplate() {
        return getItem(EquipmentSlot.CHEST_PLATE);
    }

    public @NotNull ItemStack getLeggings() {
        return getItem(EquipmentSlot.LEGGINGS);
    }

    public @NotNull ItemStack getBoots() {
        return getItem(EquipmentSlot.BOOTS);
    }

    public @NotNull ItemStack getMainHand() {
        return getItem(EquipmentSlot.MAIN_HAND);
    }

    public @NotNull ItemStack getOffhand() {
        verifyVersion(ServerVersion.V_1_9, "Offhand is only supported on 1.9+");
        return getItem(EquipmentSlot.OFF_HAND);
    }

    /**
     * Whether the running server version knows the given equipment slot.
     *
     * @param slot the slot to check
     * @return true when the slot can be sent to a client of the server version
     */
    public static boolean isSlotSupported(@NotNull EquipmentSlot slot) {
        return isSlotSupported(slot, EntityLib.getApi().getPacketEvents().getServerManager().getVersion());
    }

    /**
     * Whether the given server version knows the given equipment slot.
     * A slot travels in the equipment packet as its ordinal, so a slot that the version does not
     * have yet has no number a client of that version could map back to it.
     *
     * @param slot    the slot to check
     * @param version the server version to check the slot against
     * @return true when the slot can be sent to a client of that version
     */
    public static boolean isSlotSupported(@NotNull EquipmentSlot slot, @NotNull ServerVersion version) {
        switch (slot) {
            case OFF_HAND:
                return version.isNewerThanOrEquals(ServerVersion.V_1_9);
            case BODY:
                return version.isNewerThanOrEquals(ServerVersion.V_1_20_5);
            case SADDLE:
                return version.isNewerThanOrEquals(ServerVersion.V_1_21_5);
            default:
                return true;
        }
    }

    public WrapperPlayServerEntityEquipment createPacket() {
        ServerVersion version = EntityLib.getApi().getPacketEvents().getServerManager().getVersion();
        List<Equipment> equipment = new ArrayList<>(this.equipment.length);
        for (int i = 0; i < this.equipment.length; i++) {
            EquipmentSlot slot = EQUIPMENT_SLOTS[i];
            if (!isSlotSupported(slot, version)) continue;
            equipment.add(new Equipment(slot, this.equipment[i]));
        }
        return new WrapperPlayServerEntityEquipment(
                entity.getEntityId(),
                equipment
        );
    }


    public void refresh() {
        if (notifyChanges) {
            this.entity.sendPacketToViewers(createPacket());
        }
    }

    public boolean isNotifyingChanges() {
        return notifyChanges;
    }

    public void setNotifyChanges(boolean notifyChanges) {
        this.notifyChanges = notifyChanges;
        refresh();
    }
}
