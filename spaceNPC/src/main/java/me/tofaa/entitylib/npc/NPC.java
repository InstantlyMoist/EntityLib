package me.tofaa.entitylib.npc;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.world.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.viaversion.ViaVersionUtil;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.meta.EntityMeta;
import me.tofaa.entitylib.meta.other.ArmorStandMeta;
import me.tofaa.entitylib.meta.types.PlayerMeta;
import me.tofaa.entitylib.npc.path.NPCPath;
import me.tofaa.entitylib.npc.skin.NPCSkin;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import me.tofaa.entitylib.wrapper.WrapperEntityEquipment;
import me.tofaa.entitylib.wrapper.WrapperLivingEntity;
import me.tofaa.entitylib.wrapper.WrapperPlayer;
import me.tofaa.entitylib.npc.placeholder.PlaceholderAPIHook;
import me.tofaa.entitylib.wrapper.hologram.Hologram;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NPC {

    private static final String TEAM_NAME = "spacenpcs";
    private final String id;
    private final EntityType entityType;
    private final Map<UUID, PerPlayerData> perPlayerData;
    private final NPCOptions options;
    private final NPCPath path;
    private final Map<String, ItemStack> equipment = new LinkedHashMap<>();
    private WrapperEntity entity;
    private WrapperEntity sittingEntity;
    private Hologram hologram;
    private String name;
    private NPCSkin skin;
    private boolean spawned;
    private boolean hologramPassenger;
    private Location spawnLocation;
    private String worldName;
    private String teamEntry;

    public NPC(String id, EntityType entityType) {
        this.id = id;
        this.entityType = entityType;
        this.perPlayerData = new ConcurrentHashMap<>();
        this.options = new NPCOptions();
        this.path = new NPCPath();
        this.name = "";
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull EntityType getEntityType() {
        return entityType;
    }

    public void setName(@NotNull String name) {
        this.name = name;
        if (hologram != null) {
            updateHologram();
        }
    }

    public @NotNull String getName() {
        return name;
    }

    public void setSkin(@Nullable NPCSkin skin) {
        this.skin = skin;
        getEntity().ifPresent(e -> {
            e.consumeEntityMeta(PlayerMeta.class, meta -> {
                meta.setLeftSleeveEnabled(true);
                meta.setRightSleeveEnabled(true);
                meta.setCapeEnabled(true);
            });
        });
    }

    public @Nullable NPCSkin getSkin() {
        return skin;
    }

    /**
     * Slot keys: helmet, chestplate, leggings, boots, mainhand, offhand.
     */
    public @NotNull Map<String, ItemStack> getEquipment() {
        return equipment;
    }

    public void setEquipment(@NotNull String slot, @Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            equipment.remove(slot);
        } else {
            equipment.put(slot, item.clone());
        }
        applyEquipment();
    }

    public void applyEquipment() {
        if (!(entity instanceof WrapperLivingEntity)) return;
        WrapperEntityEquipment eq = ((WrapperLivingEntity) entity).getEquipment();
        for (Map.Entry<String, ItemStack> entry : equipment.entrySet()) {
            EquipmentSlot slot = equipmentSlot(entry.getKey());
            if (slot != null) {
                eq.setItem(slot, SpigotConversionUtil.fromBukkitItemStack(entry.getValue()));
            }
        }
    }

    private static @Nullable EquipmentSlot equipmentSlot(String slot) {
        switch (slot) {
            case "helmet":
                return EquipmentSlot.HELMET;
            case "chestplate":
                return EquipmentSlot.CHEST_PLATE;
            case "leggings":
                return EquipmentSlot.LEGGINGS;
            case "boots":
                return EquipmentSlot.BOOTS;
            case "mainhand":
                return EquipmentSlot.MAIN_HAND;
            case "offhand":
                return EquipmentSlot.OFF_HAND;
            default:
                return null;
        }
    }

    public void setPosition(@NotNull Location location) {
        if (spawned) {
            entity.teleport(location);
        }
        this.spawnLocation = location;
    }

    public @NotNull World getWorld() {
        return Objects.requireNonNull(worldName != null ? Bukkit.getWorld(worldName) : null);
    }

    public void setWorld(@NotNull World world) {
        this.worldName = world.getName();
    }

    public @NotNull String getWorldName() {
        return worldName;
    }

    public void setWorldName(@NotNull String name) {
        this.worldName = name;
    }

    public @NotNull Location getPosition() {
        if (spawned && entity != null) {
            return entity.getLocation();
        }
        return spawnLocation;
    }

    public void setHologramVisible(boolean visible) {
        if (hologram != null) {
            if (visible) {
                hologram.show();
            } else {
                hologram.hide();
            }
        }
    }

    public void spawn(@NotNull Location location) {
        if (spawned) return;

        this.spawnLocation = location;

        if (
            entityType ==
            EntityTypes.PLAYER
        ) {
            UserProfile profile = new UserProfile(
                java.util.UUID.randomUUID(),
                name
            );
            if (skin != null) {
                profile.setTextureProperties(skin.getTextureProperties());
            }
            WrapperPlayer wrapperPlayer = new WrapperPlayer(
                profile,
                EntityLib.getPlatform()
                    .getEntityIdProvider()
                    .provide(profile.getUUID(), entityType)
            );
            wrapperPlayer.setInTablist(false);
            wrapperPlayer.setGameMode(
                GameMode.SURVIVAL
            );
            entity = wrapperPlayer;
            Bukkit.getLogger().info(
                "[NPC] Created WrapperPlayer with UUID: " +
                    profile.getUUID() +
                    ", name: " +
                    name
            );
        } else {
            if (EntityMeta.isLivingEntity(entityType)) {
                entity = new WrapperLivingEntity(entityType);
            }
            else {
                entity = new WrapperEntity(entityType);
            }
            Bukkit.getLogger().info(
                "[NPC] Created WrapperEntity with type: " + entityType
            );
        }
        entity.setLocation(location);

        if (entityType == EntityTypes.PLAYER) {
            entity.getEntityMeta().setCustomNameVisible(false);
            registerScoreboardTeam();
        } else if (options.hasDisplayName()) {
            String raw = MiniMessage.miniMessage().serialize(options.getDisplayName());
            String parsed = PlaceholderAPIHook.setPlaceholders(raw);
            entity.getEntityMeta().setCustomName(MiniMessage.miniMessage().deserialize(parsed));
            entity.getEntityMeta().setCustomNameVisible(true);
        }

        entity.spawn(location);

        applyEquipment();

        if (options.isSwimming()) {
            entity.getEntityMeta().setSwimming(true);
        }
        if (options.isCrouching()) {
            entity.getEntityMeta().setSneaking(true);
        }

        World npcWorld = getWorld();

        if (options.isSitting()) {
            Location sittingLoc = new Location(
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
            );
            sittingEntity = new WrapperEntity(EntityTypes.ARMOR_STAND);
            sittingEntity.setLocation(sittingLoc);
            sittingEntity.spawn(sittingLoc);

            ArmorStandMeta meta = (ArmorStandMeta) sittingEntity.getEntityMeta();
            meta.setSmall(true);
            meta.setInvisible(true);

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld() != npcWorld) continue;
                sittingEntity.addViewer(player.getUniqueId());
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != npcWorld) continue;
            entity.addViewer(player.getUniqueId());
            if (entity instanceof me.tofaa.entitylib.wrapper.WrapperPlayer) {
                doStupidDogshitForOldClients(player);
            }
        }

        if (options.isSitting()) {
            sittingEntity.addPassenger(entity);
        }

        NPCRegistry.registerEntityId(this);

        if (options.isShowNameTag()) {
            createHologramWithViewers();
        }

        spawned = true;
    }

    private void registerScoreboardTeam() {
        if (entity == null) return;
        
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(TEAM_NAME);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            team.setOption(Team.Option.DEATH_MESSAGE_VISIBILITY, Team.OptionStatus.NEVER);
        }
        
        if (!team.hasEntry(name)) {
            team.addEntry(name);
        }
        teamEntry = name;
    }

    private void createHologramWithViewers() {
        createHologram();
        if (hologram != null) {
            World npcWorld = getWorld();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld() != npcWorld) continue;
                hologram.addViewer(player.getUniqueId());
            }
        }
    }

    public void despawn() {
        if (!spawned) return;

        if (teamEntry != null) {
            removeScoreboardTeamEntry();
        }

        if (hologram != null) {
            hologram.hide();
            hologram = null;
        }

        if (entity != null) {
            for (UUID viewerId : entity.getViewers()) {
                org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(viewerId);
                if (player != null && entity instanceof me.tofaa.entitylib.wrapper.WrapperPlayer) {
                    doStupidDogshitForOldClients(player);
                }
            }
            entity.remove();
            entity = null;
        }

        if (sittingEntity != null) {
            sittingEntity.remove();
            sittingEntity = null;
        }

        spawned = false;
    }

    private void removeScoreboardTeamEntry() {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = scoreboard.getTeam(TEAM_NAME);
            if (team != null && teamEntry != null) {
                team.removeEntry(teamEntry);
            }
    }

    public void remove() {
        despawn();
        NPCRegistry.unregister(this);
    }

    /**
     * Splits a Component into one Component per line, breaking on newline characters.
     * <p>
     * Every way of asking for a line break ends up as a newline character in the deserialized
     * component: MiniMessage's {@code <br>} and {@code <newline>} tags both emit one, and so does
     * a literal {@code \n} written in a config. Splitting on that character therefore covers all
     * of them — and it is the only thing that can work, because MiniMessage does NOT round-trip
     * {@code <br>} as a tag. {@code serialize()} writes the newline back out as a raw newline
     * character, so splitting re-serialized text on the literal string {@code <br>} never matched
     * and the name tag stayed one line with a stray control character in it.
     * <p>
     * The split carries inherited styles onto each fragment, so colours and decorations opened
     * before the break survive it.
     */
    private List<Component> splitDisplayNameLines(Component component) {
        List<Component> lines = new ArrayList<>(3);
        TextComponent.Builder current = splitInto(component, Style.empty(), Component.text(), lines);
        lines.add(current.build());
        if (lines.size() == 1) {
            // Nothing was split — hand back the original component untouched.
            return Collections.singletonList(component);
        }
        return lines;
    }

    /**
     * Walks a component tree, appending it to {@code current} and starting a new line every time a
     * newline character is met. Returns the builder that following content must append to.
     */
    private TextComponent.Builder splitInto(Component component, Style inherited,
                                            TextComponent.Builder current, List<Component> lines) {
        // Inherited style first, then the component's own on top: Style#merge overwrites the
        // target with whatever the argument actually sets, so this order lets a child's colour
        // win while still inheriting decorations opened by its parents.
        Style style = inherited.merge(component.style());

        if (component instanceof TextComponent) {
            String content = ((TextComponent) component).content();
            if (!content.isEmpty()) {
                String[] parts = content.split("\n", -1);
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) {
                        lines.add(current.build());
                        current = Component.text();
                    }
                    if (!parts[i].isEmpty()) {
                        current.append(Component.text(parts[i]).style(style));
                    }
                }
            }
        } else {
            // Non-text components (translatable, keybind, score...) hold no raw newline of their own.
            current.append(component.children(Collections.<Component>emptyList()).style(style));
        }

        for (Component child : component.children()) {
            current = splitInto(child, style, current, lines);
        }
        return current;
    }

    private void createHologram() {
        Location loc = getPosition();
        double yOffset = options.isSitting() ? 1.1 : 1.0;
        Location hologramLoc = new Location(
            loc.getX(),
            loc.getY() + yOffset,
            loc.getZ(),
            loc.getYaw(),
            loc.getPitch()
        );

        Hologram.Legacy hologram = Hologram.legacy(hologramLoc);
        hologram.setLineOffset(-0.28f);

        String raw = options.getDisplayName() != null
            ? MiniMessage.miniMessage().serialize(options.getDisplayName())
            : name;
        String parsed = PlaceholderAPIHook.setPlaceholders(raw);
        Component displayComponent = MiniMessage.miniMessage().deserialize(parsed);
        for (Component line : splitDisplayNameLines(displayComponent)) {
            hologram.addLine(line);
        }
        hologram.show();

        this.hologram = hologram;
    }

    public void updateHologram() {
        updateHologram(null);
    }

    public void updateHologram(@Nullable Player viewer) {
        if (hologram != null) {
            String raw = options.getDisplayName() != null
                ? MiniMessage.miniMessage().serialize(options.getDisplayName())
                : name;
            String parsed = PlaceholderAPIHook.setPlaceholders(viewer, raw);
            Component displayComponent = MiniMessage.miniMessage().deserialize(parsed);
            hologram.setLines(splitDisplayNameLines(displayComponent));
        }
    }

    /**
     * Very important, fixes legacy garbage clients from
     * Seeing the npc name in tab
     * @param player
     */
    public void doStupidDogshitForOldClients(
            Player player
    ) {

        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        SpaceNPC.getInstance().getServer().getScheduler().runTaskLater(SpaceNPC.getInstance(), () -> {
            if (!checkIfStupidDogshitPlayerIsOldClient(player ) || !getEntity().isPresent()) {
                return;
            }
            WrapperPlayer p = (WrapperPlayer) getEntity().get();

            PacketWrapper<?> packet = new WrapperPlayServerPlayerInfo(
                    WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER,
                    new WrapperPlayServerPlayerInfo.PlayerData(
                        p.getDisplayName(),
                           new UserProfile(p.getUuid(), p.getUsername()),
                            p.getGameMode(),
                            null, 0
                    )
            );
//             packet = new WrapperPlayServerPlayerInfoRemove(
//                    getEntity().get().getUuid()
//            );
//            user.sendPacket(packet);
            sendPacketSilentlySkipTranslation(user, packet);
        }, 5);

    }

    public boolean checkIfStupidDogshitPlayerIsOldClient(
            Player player
    ) {
        ViaVersionUtil.checkIfViaIsPresent();
        if (ViaVersionUtil.isAvailable()) {
            int pv = ViaVersionUtil.getProtocolVersion(player);
            if (pv < ClientVersion.V_1_18_2.getProtocolVersion()) {
                return true;
            }
        }

        return false;
    }

    public static void sendPacketSilentlySkipTranslation(User user, Object byteBuf) {
        if (ChannelHelper.isOpen(user.getChannel())) {
            ChannelHelper.writeAndFlushInContext(user.getChannel(), "via-encoder", byteBuf);
        }
        else {
//            ((ByteBuf)byteBuf).release()
            // W memory leak
        }
    }

    public static void sendPacketSilentlySkipTranslation(User user, PacketWrapper<?> wrapper) {
        wrapper.prepareForSend(user.getChannel(), true, true);
        sendPacketSilentlySkipTranslation(user, wrapper.buffer);
    }


    public void updateHeadRotationForViewers(Location npcLocation) {
        if (entity == null || !spawned) return;

        PacketEventsAPI<?> api = EntityLib.getApi().getPacketEvents();

        for (UUID viewerId : entity.getViewers()) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(
                viewerId
            );
            if (player == null) continue;

            double dx = player.getLocation().getX() - npcLocation.getX();
            double dz = player.getLocation().getZ() - npcLocation.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));

            WrapperPlayServerEntityHeadLook headPacket =
                new WrapperPlayServerEntityHeadLook(
                    entity.getEntityId(),
                    yaw
                );

            Object channel = api.getProtocolManager().getChannel(viewerId);
            if (channel != null) {
                api.getProtocolManager().sendPacket(channel, headPacket);
            }
        }
    }

    public @NotNull Collection<UUID> getViewers() {
        if (entity == null) return Collections.emptyList();
        return entity.getViewers();
    }

    public void addViewer(@NotNull UUID playerId) {
        if (entity != null) {
            entity.addViewer(playerId);
        }
    }

    public void removeViewer(@NotNull UUID playerId) {
        if (entity != null) {
            entity.removeViewer(playerId);
        }
    }

    public @NotNull Optional<WrapperEntity> getEntity() {
        return Optional.ofNullable(entity);
    }

    public @NotNull Optional<Hologram> getHologram() {
        return Optional.ofNullable(hologram);
    }

    public @NotNull Optional<WrapperEntity> getSittingEntity() {
        return Optional.ofNullable(sittingEntity);
    }

    public @NotNull NPCOptions getOptions() {
        return options;
    }

    public @NotNull NPCPath getPath() {
        return path;
    }

    public boolean isHologramPassenger() {
        return hologramPassenger;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public @NotNull Map<UUID, PerPlayerData> getPerPlayerData() {
        return perPlayerData;
    }

    public @Nullable PerPlayerData getPerPlayerData(UUID playerId) {
        return perPlayerData.get(playerId);
    }

    public void setPerPlayerData(UUID playerId, PerPlayerData data) {
        perPlayerData.put(playerId, data);
    }

    public void removePerPlayerData(UUID playerId) {
        perPlayerData.remove(playerId);
    }

    public void moveTo(@NotNull Location target) {
        NPCPath path = getPath();
        path.clearWaypoints();
        path.addWaypoint(target);
        NPCMovement.startPathFollowing(this);
    }

    public void stopMoving() {
        if (entity != null) {
            NPCMovement.stop(this);
            getPath().reset();
        }
    }

    public boolean isMoving() {
        return NPCMovement.isMoving(this);
    }

    public static class PerPlayerData {

        private final UUID playerId;
        private Component customName;
        private boolean hidden;

        public PerPlayerData(UUID playerId) {
            this.playerId = playerId;
        }

        public @NotNull UUID getPlayerId() {
            return playerId;
        }

        public void setCustomName(@Nullable Component name) {
            this.customName = name;
        }

        public @Nullable Component getCustomName() {
            return customName;
        }

        public void setHidden(boolean hidden) {
            this.hidden = hidden;
        }

        public boolean isHidden() {
            return hidden;
        }
    }


}

