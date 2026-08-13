package me.tofaa.entitylib.npc.command;

import static me.tofaa.entitylib.npc.command.CommandSystem.mm;

import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.google.common.collect.Lists;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import me.tofaa.entitylib.extras.skin.SkinFetcher;
import me.tofaa.entitylib.npc.NPC;
import me.tofaa.entitylib.npc.NPCMovement;
import me.tofaa.entitylib.npc.NPCOptions;
import me.tofaa.entitylib.npc.NPCRegistry;
import me.tofaa.entitylib.npc.interactions.InteractionAction;
import me.tofaa.entitylib.npc.interactions.InteractionHandler;
import me.tofaa.entitylib.npc.interactions.InteractionType;
import me.tofaa.entitylib.npc.path.NPCPath;
import me.tofaa.entitylib.npc.skin.NPCSkin;
import me.tofaa.entitylib.npc.storage.NPCStorage;
import me.tofaa.entitylib.wrapper.WrapperLivingEntity;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class NPCCommand extends CommandSystem.BaseCommand {

    private final NPCStorage storage;
    private final SkinFetcher skinFetcher;

    public NPCCommand(NPCStorage storage, SkinFetcher skinFetcher) {
        super(
            "spacenpc",
            "SpaceNPC main command",
            "/spacenpc [args]",
            Lists.newArrayList("npc", "npcs", "spacenpcs")
        );
        setPermission("spacenpcs.admin");
        this.storage = storage;
        this.skinFetcher = skinFetcher;
        registerSubCommands();
    }

    private void registerSubCommands() {
        addSubCommand(new Create());
        addSubCommand(new Remove());
        addSubCommand(new ListCommand());
        addSubCommand(new Teleport());
        addSubCommand(new Name());
        addSubCommand(new Skin());
        addSubCommand(new Type());
        addSubCommand(new Move());
        addSubCommand(new Walk());
        addSubCommand(new Stop());
        addSubCommand(new Path());
        addSubCommand(new Options());
        addSubCommand(new Interaction());
        addSubCommand(new CopySkin());
        addSubCommand(new Inventory());
        addSubCommand(new Reload());
    }

    private NPC getNPC(String id, CommandSender sender) {
        NPC npc = NPCRegistry.get(id);
        if (npc == null) {
            sender.sendMessage(
                mm("<red>NPC '<yellow>" + id + "<red>' not found")
            );
        }
        return npc;
    }

    private Location playerLocation(Player player) {
        return new Location(
            player.getLocation().getX(),
            player.getLocation().getY(),
            player.getLocation().getZ(),
            player.getLocation().getYaw(),
            player.getLocation().getPitch()
        );
    }

    private EntityType getEntityType(String name) {
        return EntityTypes.getByName(name.toLowerCase());
    }

    private List<String> getEntityTypeCompletions() {
        return Lists.newArrayList(
            "PLAYER",
            "ZOMBIE",
            "SKELETON",
            "PIG",
            "COW",
            "CHICKEN",
            "SHEEP",
            "HORSE",
            "ARMOR_STAND",
            "TEXT_DISPLAY"
        );
    }

    public class Create extends CommandSystem.SubCommand {

        Create() {
            super("create", "spacenpcs.admin");
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(
                    mm("<red>Only players can use this command")
                );
                return true;
            }
            Player player = (Player) sender;

            CommandSystem.Args.Validation validation = args
                .validate()
                .minArgs(2);

            if (!validation.passes()) {
                validation.sendError(sender);
                sender.sendMessage(
                    mm(
                        "<red>Usage: <yellow>/spacenpc create <id> <type> [name]"
                    )
                );
                return true;
            }

            String id = args.get(0);
            String typeStr = args.get(1).toLowerCase();
            String name = args.has(2) ? args.get(2) : id;

            if (NPCRegistry.get(id) != null) {
                sender.sendMessage(
                    mm(
                        "<red>NPC with id '<yellow>" +
                            id +
                            "<red>' already exists"
                    )
                );
                return true;
            }

            EntityType entityType = NPCCommand.this.getEntityType(typeStr);
            if (entityType == null) {
                sender.sendMessage(
                    mm("<red>Invalid entity type: <yellow>" + typeStr)
                );
                return true;
            }

            NPC npc = new NPC(id, entityType);
            npc.setName(name);
            npc.setWorldName(player.getLocation().getWorld().getName());
            Location loc = NPCCommand.this.playerLocation(player);

            NPCRegistry.register(npc);
            npc.spawn(loc);
            storage.saveNPC(npc);

            sender.sendMessage(
                mm(
                    "<green>Created NPC '<yellow>" +
                        id +
                        "<green>' with entity type <yellow>" +
                        typeStr
                )
            );
            return true;
        }

        @Override
        public List<String> tabComplete(
            CommandSender sender,
            CommandSystem.Args args
        ) {
            if (args.length() == 1) {
                return new ArrayList<>(
                    NPCRegistry.getAll()
                        .stream()
                        .map(NPC::getId)
                        .filter(id -> NPCRegistry.get(id) == null)
                        .collect(Collectors.toList())
                );
            } else if (args.length() == 2) {
                return NPCCommand.this.getEntityTypeCompletions()
                    .stream()
                    .filter(t ->
                        t
                            .toLowerCase()
                            .startsWith(args.get(1, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            } else if (args.length() == 3) {
                return Lists.newArrayList("<name>");
            }
            return Collections.emptyList();
        }

        @Override
        public String getDescription() {
            return "Create an NPC";
        }

        @Override
        public String getUsage() {
            return "create <id> <type> [name]";
        }
    }

    public class Remove extends CommandSystem.SubCommand {

        Remove() {
            super("remove", "spacenpcs.admin");
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            CommandSystem.Args.Validation validation = args
                .validate()
                .minArgs(1);
            if (!validation.passes()) {
                validation.sendError(sender);
                sender.sendMessage(
                    mm("<red>Usage: <yellow>/spacenpc remove <id>")
                );
                return true;
            }

            String id = args.get(0);
            NPC npc = NPCCommand.this.getNPC(id, sender);
            if (npc == null) return true;

            npc.remove();
            storage.deleteNPC(id);
            sender.sendMessage(
                mm("<green>Removed NPC '<yellow>" + id + "<green>'")
            );
            return true;
        }

        @Override
        public List<String> tabComplete(
            CommandSender sender,
            CommandSystem.Args args
        ) {
            if (args.length() == 1) {
                return NPCRegistry.getAll()
                    .stream()
                    .map(NPC::getId)
                    .filter(id ->
                        id
                            .toLowerCase()
                            .startsWith(args.get(0, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        @Override
        public String getDescription() {
            return "Remove an NPC";
        }

        @Override
        public String getUsage() {
            return "remove <id>";
        }
    }

    public class ListCommand extends CommandSystem.SubCommand {

        ListCommand() {
            super("list", "spacenpcs.admin");
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            List<NPC> npcs = new ArrayList<>(NPCRegistry.getAll());

            if (npcs.isEmpty()) {
                sender.sendMessage(mm("<yellow>No NPCs exist"));
                return true;
            }

            sender.sendMessage(
                mm("<yellow>NPCs (<white>" + npcs.size() + "<yellow>):")
            );
            for (NPC npc : npcs) {
                sender.sendMessage(
                    mm(
                        "<white>  - <yellow>" +
                            npc.getId() +
                            " <gray>(" +
                            npc.getEntityType() +
                            ") <white>at " +
                            (int) npc.getPosition().getX() +
                            ", " +
                            (int) npc.getPosition().getY() +
                            ", " +
                            (int) npc.getPosition().getZ()
                    )
                );
            }
            return true;
        }

        @Override
        public String getDescription() {
            return "List all NPCs";
        }

        @Override
        public String getUsage() {
            return "list";
        }
    }

    public class Teleport extends CommandSystem.SubCommand {

        Teleport() {
            super("teleport", "spacenpcs.admin", "tp");
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(
                    mm("<red>Only players can use this command")
                );
                return true;
            }
            Player player = (Player) sender;

            CommandSystem.Args.Validation validation = args
                .validate()
                .minArgs(1);
            if (!validation.passes()) {
                validation.sendError(sender);
                sender.sendMessage(
                    mm("<red>Usage: <yellow>/spacenpc teleport <id>")
                );
                return true;
            }

            String id = args.get(0);
            NPC npc = NPCCommand.this.getNPC(id, sender);
            if (npc == null) return true;

            Location loc = npc.getPosition();
            player.teleport(
                new org.bukkit.Location(
                    player.getWorld(),
                    loc.getX(),
                    loc.getY(),
                    loc.getZ(),
                    loc.getYaw(),
                    loc.getPitch()
                )
            );

            sender.sendMessage(
                mm("<green>Teleported to NPC '<yellow>" + id + "<green>'")
            );
            return true;
        }

        @Override
        public List<String> tabComplete(
            CommandSender sender,
            CommandSystem.Args args
        ) {
            if (args.length() == 1) {
                return NPCRegistry.getAll()
                    .stream()
                    .map(NPC::getId)
                    .filter(id ->
                        id
                            .toLowerCase()
                            .startsWith(args.get(0, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        @Override
        public String getDescription() {
            return "Teleport to an NPC";
        }

        @Override
        public String getUsage() {
            return "teleport <id>";
        }
    }

    public class Name extends CommandSystem.SubCommand {

        Name() {
            super("name", "spacenpcs.admin");
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            CommandSystem.Args.Validation validation = args
                .validate()
                .minArgs(2);
            if (!validation.passes()) {
                validation.sendError(sender);
                sender.sendMessage(
                    mm("<red>Usage: <yellow>/spacenpc name <id> <name>")
                );
                return true;
            }

            String id = args.get(0);
            String name = args.get(1);
            NPC npc = NPCCommand.this.getNPC(id, sender);
            if (npc == null) return true;

            npc.setName(name);
            storage.saveNPC(npc);
            sender.sendMessage(
                mm(
                    "<green>Set name of NPC '<yellow>" +
                        id +
                        "<green>' to '<yellow>" +
                        name +
                        "<green>'"
                )
            );
            return true;
        }

        @Override
        public List<String> tabComplete(
            CommandSender sender,
            CommandSystem.Args args
        ) {
            if (args.length() == 1) {
                return NPCRegistry.getAll()
                    .stream()
                    .map(NPC::getId)
                    .filter(id ->
                        id
                            .toLowerCase()
                            .startsWith(args.get(0, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        @Override
        public String getDescription() {
            return "Set NPC name";
        }

        @Override
        public String getUsage() {
            return "name <id> <name>";
        }
    }

    public class Skin extends CommandSystem.SubCommand {

        Skin() {
            super("skin", "spacenpcs.admin");
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            CommandSystem.Args.Validation validation = args
                .validate()
                .minArgs(2);
            if (!validation.passes()) {
                validation.sendError(sender);
                sender.sendMessage(
                    mm("<red>Usage: <yellow>/spacenpc skin <id> <player>")
                );
                return true;
            }

            String id = args.get(0);
            String playerName = args.get(1);
            NPC npc = NPCCommand.this.getNPC(id, sender);
            if (npc == null) return true;

            sender.sendMessage(
                mm(
                    "<yellow>Fetching skin for <white>" +
                        playerName +
                        "<yellow>..."
                )
            );

            List<TextureProperty> skins = skinFetcher.getSkin(playerName);
            if (skins.isEmpty()) {
                sender.sendMessage(
                    mm("<red>Failed to fetch skin for <yellow>" + playerName)
                );
                return true;
            }

            TextureProperty skin = skins.get(0);
            npc.setSkin(new NPCSkin(skin.getValue(), skin.getSignature()));

            if (npc.isSpawned()) {
                npc.despawn();
                npc.spawn(npc.getPosition());
            }

            storage.saveNPC(npc);
            sender.sendMessage(
                mm(
                    "<green>Set skin of NPC '<yellow>" +
                        id +
                        "<green>' to <white>" +
                        playerName
                )
            );
            return true;
        }

        @Override
        public List<String> tabComplete(
            CommandSender sender,
            CommandSystem.Args args
        ) {
            if (args.length() == 1) {
                return NPCRegistry.getAll()
                    .stream()
                    .map(NPC::getId)
                    .filter(id ->
                        id
                            .toLowerCase()
                            .startsWith(args.get(0, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            } else if (args.length() == 2) {
                return CommandSystem.onlinePlayers()
                    .stream()
                    .filter(p ->
                        p
                            .toLowerCase()
                            .startsWith(args.get(1, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        @Override
        public String getDescription() {
            return "Set NPC skin";
        }

        @Override
        public String getUsage() {
            return "skin <id> <player>";
        }
    }

    public class Type extends CommandSystem.SubCommand {

        Type() {
            super("type", "spacenpcs.admin");
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            CommandSystem.Args.Validation validation = args
                .validate()
                .minArgs(2);
            if (!validation.passes()) {
                validation.sendError(sender);
                sender.sendMessage(
                    mm("<red>Usage: <yellow>/spacenpc type <id> <type>")
                );
                return true;
            }

            String id = args.get(0);
            String typeStr = args.get(1).toLowerCase();
            NPC npc = NPCCommand.this.getNPC(id, sender);
            if (npc == null) return true;

            EntityType entityType = NPCCommand.this.getEntityType(typeStr);
            if (entityType == null) {
                sender.sendMessage(
                    mm("<red>Invalid entity type: <yellow>" + typeStr)
                );
                return true;
            }

            String oldName = npc.getName();
            if (npc.isSpawned()) {
                npc.despawn();
            }

            NPC newNpc = new NPC(id, entityType);
            newNpc.setName(oldName);

            NPCRegistry.unregister(id);
            NPCRegistry.register(newNpc);
            newNpc.spawn(npc.getPosition());
            storage.saveNPC(newNpc);

            sender.sendMessage(
                mm(
                    "<green>Set entity type of NPC '<yellow>" +
                        id +
                        "<green>' to <yellow>" +
                        typeStr
                )
            );
            return true;
        }

        @Override
        public List<String> tabComplete(
            CommandSender sender,
            CommandSystem.Args args
        ) {
            if (args.length() == 1) {
                return NPCRegistry.getAll()
                    .stream()
                    .map(NPC::getId)
                    .filter(id ->
                        id
                            .toLowerCase()
                            .startsWith(args.get(0, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            } else if (args.length() == 2) {
                return NPCCommand.this.getEntityTypeCompletions()
                    .stream()
                    .filter(t ->
                        t
                            .toLowerCase()
                            .startsWith(args.get(1, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        @Override
        public String getDescription() {
            return "Change entity type";
        }

        @Override
        public String getUsage() {
            return "type <id> <type>";
        }
    }

    public class Move extends CommandSystem.SubCommand {

        Move() {
            super("move", "spacenpcs.admin");
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(
                    mm("<red>Only players can use this command")
                );
                return true;
            }
            Player player = (Player) sender;

            CommandSystem.Args.Validation validation = args
                .validate()
                .minArgs(1);
            if (!validation.passes()) {
                validation.sendError(sender);
                sender.sendMessage(
                    mm("<red>Usage: <yellow>/spacenpc move <id>")
                );
                return true;
            }

            String id = args.get(0);
            NPC npc = NPCCommand.this.getNPC(id, sender);
            if (npc == null) return true;

            Location loc = NPCCommand.this.playerLocation(player);
            npc.setPosition(loc);

            if (npc.isSpawned()) {
                npc.despawn();
                npc.spawn(loc);
            }

            storage.saveNPC(npc);
            sender.sendMessage(
                mm(
                    "<green>Moved NPC '<yellow>" +
                        id +
                        "<green>' to your location"
                )
            );
            return true;
        }

        @Override
        public List<String> tabComplete(
            CommandSender sender,
            CommandSystem.Args args
        ) {
            if (args.length() == 1) {
                return NPCRegistry.getAll()
                    .stream()
                    .map(NPC::getId)
                    .filter(id ->
                        id
                            .toLowerCase()
                            .startsWith(args.get(0, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        @Override
        public String getDescription() {
            return "Instantly move NPC to you";
        }

        @Override
        public String getUsage() {
            return "move <id>";
        }
    }

    public class Walk extends CommandSystem.SubCommand {

        Walk() {
            super("walk", "spacenpcs.admin");
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(
                    mm("<red>Only players can use this command")
                );
                return true;
            }
            Player player = (Player) sender;

            CommandSystem.Args.Validation validation = args
                .validate()
                .minArgs(1);
            if (!validation.passes()) {
                validation.sendError(sender);
                sender.sendMessage(
                    mm("<red>Usage: <yellow>/spacenpc walk <id> [speed]")
                );
                return true;
            }

            String id = args.get(0);
            NPC npc = NPCCommand.this.getNPC(id, sender);
            if (npc == null) return true;

            double speed = args.getDouble(1, 4.0);
            Location target = NPCCommand.this.playerLocation(player);

            npc.getPath().clearWaypoints();
            npc.getPath().addWaypoint(target);
            npc.getPath().setSpeed(speed);
            NPCMovement.startPathFollowing(npc);

            sender.sendMessage(
                mm(
                    "<green>NPC '<yellow>" +
                        id +
                        "<green>' is now walking to your location"
                )
            );
            return true;
        }

        @Override
        public List<String> tabComplete(
            CommandSender sender,
            CommandSystem.Args args
        ) {
            if (args.length() == 1) {
                return NPCRegistry.getAll()
                    .stream()
                    .map(NPC::getId)
                    .filter(id ->
                        id
                            .toLowerCase()
                            .startsWith(args.get(0, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            } else if (args.length() == 2) {
                return Lists.newArrayList("4.0");
            }
            return Collections.emptyList();
        }

        @Override
        public String getDescription() {
            return "Walk NPC to you";
        }

        @Override
        public String getUsage() {
            return "walk <id> [speed]";
        }
    }

    public class Stop extends CommandSystem.SubCommand {

        Stop() {
            super("stop", "spacenpcs.admin");
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            CommandSystem.Args.Validation validation = args
                .validate()
                .minArgs(1);
            if (!validation.passes()) {
                validation.sendError(sender);
                sender.sendMessage(
                    mm("<red>Usage: <yellow>/spacenpc stop <id>")
                );
                return true;
            }

            String id = args.get(0);
            NPC npc = NPCCommand.this.getNPC(id, sender);
            if (npc == null) return true;

            npc.stopMoving();
            sender.sendMessage(
                mm("<green>Stopped NPC '<yellow>" + id + "<green>'")
            );
            return true;
        }

        @Override
        public List<String> tabComplete(
            CommandSender sender,
            CommandSystem.Args args
        ) {
            if (args.length() == 1) {
                return NPCRegistry.getAll()
                    .stream()
                    .map(NPC::getId)
                    .filter(id ->
                        id
                            .toLowerCase()
                            .startsWith(args.get(0, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        @Override
        public String getDescription() {
            return "Stop NPC movement";
        }

        @Override
        public String getUsage() {
            return "stop <id>";
        }
    }

    public class Path extends CommandSystem.SubCommand {

        Path() {
            super("path", "spacenpcs.admin");
            setArgs(2, Integer.MAX_VALUE);
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(
                    mm("<red>Only players can use this command")
                );
                return true;
            }
            Player player = (Player) sender;

            if (args.length() < 2) {
                sender.sendMessage(
                    mm(
                        "<red>Usage: <yellow>/spacenpc path <id> <add|remove|clear|list|start|loop|speed> [args]"
                    )
                );
                return true;
            }

            String id = args.get(0);
            String action = args.get(1).toLowerCase();
            NPC npc = NPCCommand.this.getNPC(id, sender);
            if (npc == null) return true;

            NPCPath path = npc.getPath();

            switch (action) {
                case "add": {
                    path.addWaypoint(NPCCommand.this.playerLocation(player));
                    sender.sendMessage(
                        mm(
                            "<green>Added waypoint to NPC '<yellow>" +
                                id +
                                "<green>' <gray>(total: <white>" +
                                path.getWaypointCount() +
                                "<gray>)"
                        )
                    );
                    storage.saveNPC(npc);
                    break;
                }
                case "clear": {
                    path.clearWaypoints();
                    NPCMovement.stop(npc);
                    sender.sendMessage(
                        mm(
                            "<green>Cleared path for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    storage.saveNPC(npc);
                    break;
                }
                case "list": {
                    if (path.getWaypointCount() == 0) {
                        sender.sendMessage(
                            mm(
                                "<yellow>No waypoints for NPC '<yellow>" +
                                    id +
                                    "<yellow>'"
                            )
                        );
                    } else {
                        sender.sendMessage(
                            mm(
                                "<yellow>Waypoints for NPC '<yellow>" +
                                    id +
                                    "<yellow>':"
                            )
                        );
                        for (int i = 0; i < path.getWaypoints().size(); i++) {
                            Location loc = path.getWaypoints().get(i);
                            sender.sendMessage(
                                mm(
                                    "<white>  " +
                                        i +
                                        ": <gray>" +
                                        (int) loc.getX() +
                                        ", " +
                                        (int) loc.getY() +
                                        ", " +
                                        (int) loc.getZ()
                                )
                            );
                        }
                    }
                    break;
                }
                case "start": {
                    if (path.getWaypointCount() == 0) {
                        sender.sendMessage(mm("<red>No waypoints to follow"));
                        return true;
                    }
                    path.reset();
                    NPCMovement.startPathFollowing(npc);
                    sender.sendMessage(
                        mm(
                            "<green>NPC '<yellow>" +
                                id +
                                "<green>' started following path"
                        )
                    );
                    storage.saveNPC(npc);
                    break;
                }
                case "loop": {
                    boolean loop = args.getBool(2, false);
                    path.setLooping(loop);
                    sender.sendMessage(
                        mm(
                            "<green>Looping " +
                                (loop ? "enabled" : "disabled") +
                                " for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    storage.saveNPC(npc);
                    break;
                }
                case "speed": {
                    double speed = args.getDouble(2, 4.0);
                    path.setSpeed(speed);
                    sender.sendMessage(
                        mm(
                            "<green>Speed set to <yellow>" +
                                speed +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    storage.saveNPC(npc);
                    break;
                }
                default: {
                    sender.sendMessage(
                        mm("<red>Unknown action: <yellow>" + action)
                    );
                    break;
                }
            }
            return true;
        }

        @Override
        public List<String> tabComplete(
            CommandSender sender,
            CommandSystem.Args args
        ) {
            if (args.length() == 1) {
                return NPCRegistry.getAll()
                    .stream()
                    .map(NPC::getId)
                    .filter(id ->
                        id
                            .toLowerCase()
                            .startsWith(args.get(0, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            } else if (args.length() == 2) {
                return Lists.newArrayList(
                    "add",
                    "clear",
                    "list",
                    "start",
                    "loop",
                    "speed"
                )
                    .stream()
                    .filter(a -> a.startsWith(args.get(1, "").toLowerCase()))
                    .collect(Collectors.toList());
            } else if (args.length() == 3) {
                String action = args.get(1).toLowerCase();
                if (action.equals("loop")) {
                    return Lists.newArrayList("true", "false")
                        .stream()
                        .filter(v ->
                            v.startsWith(args.get(2, "").toLowerCase())
                        )
                        .collect(Collectors.toList());
                } else if (action.equals("speed")) {
                    return Lists.newArrayList("4.0");
                }
            }
            return Collections.emptyList();
        }

        @Override
        public String getDescription() {
            return "Path waypoints";
        }

        @Override
        public String getUsage() {
            return "path <id> <add|clear|list|start|loop|speed> [args]";
        }
    }

    public class Options extends CommandSystem.SubCommand {

        Options() {
            super("options", "spacenpcs.admin");
            setArgs(2, 3);
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            if (args.length() < 2) {
                sender.sendMessage(
                    mm(
                        "<red>Usage: <yellow>/spacenpc options <id> <option> [value]"
                    )
                );
                sender.sendMessage(
                    mm(
                        "<gray>Options: <white>nametag, gravity, collision, silent, invulnerable, lookatplayers, lookatplayersperplayer, lookatpath, lookforward, clampground, permanentlyvisible, viewdistance, speed, sitting"
                    )
                );
                return true;
            }

            String id = args.get(0);
            String option = args.get(1).toLowerCase();
            String valueStr = args.get(2);
            NPC npc = NPCCommand.this.getNPC(id, sender);
            if (npc == null) return true;

            NPCOptions opts = npc.getOptions();
            boolean boolValue = parseBool(valueStr, true);

            switch (option) {
                case "nametag": {
                    opts.showNameTag(boolValue);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>nametag <green>to <white>" +
                                boolValue +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    break;
                }
                case "gravity": {
                    opts.gravity(boolValue);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>gravity <green>to <white>" +
                                boolValue +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    break;
                }
                case "collision":
                case "collides": {
                    opts.collides(boolValue);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>collision <green>to <white>" +
                                boolValue +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    break;
                }
                case "silent": {
                    opts.silent(boolValue);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>silent <green>to <white>" +
                                boolValue +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    break;
                }
                case "invulnerable": {
                    opts.invulnerable(boolValue);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>invulnerable <green>to <white>" +
                                boolValue +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    break;
                }
                case "lookatplayers": {
                    opts.lookAtPlayers(boolValue);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>lookAtPlayers <green>to <white>" +
                                boolValue +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    break;
                }
                case "lookatplayersperplayer": {
                    opts.lookAtPlayersPerPlayer(boolValue);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>lookAtPlayersPerPlayer <green>to <white>" +
                                boolValue +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    break;
                }
                case "lookatpath": {
                    opts.lookAtPath(boolValue);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>lookAtPath <green>to <white>" +
                                boolValue +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    break;
                }
                case "lookforward": {
                    opts.lookForward(boolValue);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>lookForward <green>to <white>" +
                                boolValue +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    break;
                }
                case "clampground": {
                    opts.clampToGround(boolValue);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>clampToGround <green>to <white>" +
                                boolValue +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    break;
                }
                case "permanentlyvisible": {
                    opts.permanentlyVisible(boolValue);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>permanentlyVisible <green>to <white>" +
                                boolValue +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    break;
                }
                case "viewdistance": {
                    double value = args.getDouble(2, 100.0);
                    opts.viewDistance(value);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>viewDistance <green>to <white>" +
                                value +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    break;
                }
                case "speed": {
                    double value = args.getDouble(2, 4.0);
                    opts.movementSpeed(value);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>movementSpeed <green>to <white>" +
                                value +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    break;
                }
                case "sitting": {
                    opts.sitting(boolValue);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>sitting <green>to <white>" +
                                boolValue +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    if (npc.isSpawned()) {
                        npc.despawn();
                        npc.spawn(npc.getPosition());
                    }
                    break;
                }
                case "swimming": {
                    opts.swimming(boolValue);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>swimming <green>to <white>" +
                                boolValue +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    if (npc.isSpawned() && npc.getEntity().isPresent()) {
                        npc.getEntity().get().getEntityMeta().setSwimming(boolValue);
                    }
                    break;
                }
                case "crouching": {
                    opts.crouching(boolValue);
                    sender.sendMessage(
                        mm(
                            "<green>Set <yellow>crouching <green>to <white>" +
                                boolValue +
                                " <green>for NPC '<yellow>" +
                                id +
                                "<green>'"
                        )
                    );
                    if (npc.isSpawned() && npc.getEntity().isPresent()) {
                        npc.getEntity().get().getEntityMeta().setSneaking(boolValue);
                    }
                    break;
                }
                default: {
                    sender.sendMessage(
                        mm("<red>Unknown option: <yellow>" + option)
                    );
                    sender.sendMessage(
                        mm(
                        "<gray>Options: <white>nametag, gravity, collision, silent, invulnerable, lookatplayers, lookatplayersperplayer, lookatpath, lookforward, clampground, permanentlyvisible, viewdistance, speed, sitting, swimming, crouching"
                        )
                    );
                    return true;
                }
            }

            storage.saveNPC(npc);
            return true;
        }

        private boolean parseBool(String valueStr, boolean defaultValue) {
            if (valueStr == null) return defaultValue;
            switch (valueStr.toLowerCase()) {
                case "true":
                case "yes":
                case "1":
                case "on":
                case "enable":
                case "enabled":
                    return true;
                case "false":
                case "no":
                case "0":
                case "off":
                case "disable":
                case "disabled":
                    return false;
                default:
                    return defaultValue;
            }
        }

        @Override
        public List<String> tabComplete(
            CommandSender sender,
            CommandSystem.Args args
        ) {
            List<String> optionNames = Lists.newArrayList(
                "nametag",
                "gravity",
                "collision",
                "silent",
                "invulnerable",
                "lookatplayers",
                "lookatplayersperplayer",
                "lookatpath",
                "lookforward",
                "clampground",
                "permanentlyvisible",
                "viewdistance",
                "speed",
                "sitting"
            );

            if (args.length() == 1) {
                return NPCRegistry.getAll()
                    .stream()
                    .map(NPC::getId)
                    .filter(id ->
                        id
                            .toLowerCase()
                            .startsWith(args.get(0, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            } else if (args.length() == 2) {
                return optionNames
                    .stream()
                    .filter(o -> o.startsWith(args.get(1, "").toLowerCase()))
                    .collect(Collectors.toList());
            } else if (args.length() == 3) {
                String option = args.get(1).toLowerCase();
                if (option.equals("viewdistance") || option.equals("speed")) {
                    return Lists.newArrayList("4.0");
                } else {
                    return Lists.newArrayList("true", "false")
                        .stream()
                        .filter(v ->
                            v.startsWith(args.get(2, "").toLowerCase())
                        )
                        .collect(Collectors.toList());
                }
            }
            return Collections.emptyList();
        }

        @Override
        public String getDescription() {
            return "Toggle options";
        }

        @Override
        public String getUsage() {
            return "options <id> <option> [value]";
        }
    }

    // ===== INTERACTION =====
    public class Interaction extends CommandSystem.SubCommand {

        private static final String USAGE_HINT =
            "<gray>Use <white>/spacenpc interaction <id> help <gray>for all options";

        Interaction() {
            super("interaction", "spacenpcs.admin", "interact", "interactions");
            setArgs(1, Integer.MAX_VALUE);
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            String id = args.get(0);
            NPC npc = NPCCommand.this.getNPC(id, sender);
            if (npc == null) return true;

            String action = args.get(1, "list").toLowerCase();

            switch (action) {
                case "list":
                case "show":
                    return list(sender, npc, args.get(2));
                case "add":
                case "append":
                    return add(sender, npc, args);
                case "insert":
                    return insert(sender, npc, args);
                case "set":
                case "edit":
                case "replace":
                    return set(sender, npc, args);
                case "remove":
                case "delete":
                case "del":
                    return remove(sender, npc, args);
                case "clear":
                case "reset":
                    return clear(sender, npc, args.get(2));
                case "test":
                case "preview":
                case "run":
                    return test(sender, npc, args.get(2));
                case "help":
                    return help(sender, npc);
                default:
                    sender.sendMessage(
                        mm("<red>Unknown action: <yellow>" + action)
                    );
                    sender.sendMessage(mm(USAGE_HINT));
                    return true;
            }
        }

        // ===== ACTIONS =====

        private boolean list(
            CommandSender sender,
            NPC npc,
            String typeFilter
        ) {
            InteractionType only = null;
            if (typeFilter != null) {
                only = InteractionType.fromString(typeFilter);
                if (only == null) return invalidType(sender, typeFilter);
            }

            NPCOptions opts = npc.getOptions();
            sender.sendMessage(
                mm("<gold><bold>Interactions <dark_gray>| <yellow>" + npc.getId())
            );

            boolean any = false;
            for (InteractionType type : InteractionType.values()) {
                if (only != null && type != only) continue;
                List<InteractionAction> actions = opts.getInteractions(type);
                if (actions.isEmpty()) continue;

                any = true;
                sender.sendMessage(
                    mm(
                        "<yellow>" +
                            type.name().toLowerCase() +
                            " <dark_gray>(" +
                            actions.size() +
                            ")"
                    )
                );
                for (int i = 0; i < actions.size(); i++) {
                    InteractionAction action = actions.get(i);
                    if (action == null) continue;
                    sender.sendMessage(
                        mm(
                            "<dark_gray> [<white>" +
                                i +
                                "<dark_gray>] <aqua>" +
                                action.getActionType().toLowerCase() +
                                " <gray>" +
                                MiniMessage.miniMessage().escapeTags(action.getValue())
                        )
                    );
                }
            }

            if (!any) {
                sender.sendMessage(mm("<gray>Nothing set yet."));
                sender.sendMessage(
                    mm(
                        "<gray>Try <white>/spacenpc interaction " +
                            npc.getId() +
                            " add right_click <red>Hello %player%!"
                    )
                );
            } else {
                sender.sendMessage(mm(USAGE_HINT));
            }
            return true;
        }

        private boolean add(
            CommandSender sender,
            NPC npc,
            CommandSystem.Args args
        ) {
            List<InteractionType> types = parseTypes(sender, args.get(2));
            if (types == null) return true;

            InteractionAction template = parseAction(sender, args, 3);
            if (template == null) return true;

            NPCOptions opts = npc.getOptions();
            for (InteractionType type : types) {
                List<InteractionAction> actions = opts
                    .getAllInteractions()
                    .computeIfAbsent(type, k -> new ArrayList<>());
                actions.add(
                    new InteractionAction(
                        type,
                        template.getActionType(),
                        template.getValue()
                    )
                );
                sender.sendMessage(
                    mm(
                        "<green>Added <aqua>" +
                            template.getActionType().toLowerCase() +
                            " <green>to <yellow>" +
                            type.name().toLowerCase() +
                            " <dark_gray>[<white>" +
                            (actions.size() - 1) +
                            "<dark_gray>]"
                    )
                );
            }
            storage.saveNPC(npc);
            preview(sender, template);
            return true;
        }

        private boolean insert(
            CommandSender sender,
            NPC npc,
            CommandSystem.Args args
        ) {
            InteractionType type = parseType(sender, args.get(2));
            if (type == null) return true;

            NPCOptions opts = npc.getOptions();
            List<InteractionAction> actions = opts
                .getAllInteractions()
                .computeIfAbsent(type, k -> new ArrayList<>());

            int index = parseIndex(sender, args.get(3), actions.size());
            if (index < 0) return true;

            InteractionAction action = parseAction(sender, args, 4);
            if (action == null) return true;

            action.setType(type);
            actions.add(Math.min(index, actions.size()), action);
            storage.saveNPC(npc);

            sender.sendMessage(
                mm(
                    "<green>Inserted <aqua>" +
                        action.getActionType().toLowerCase() +
                        " <green>at <yellow>" +
                        type.name().toLowerCase() +
                        " <dark_gray>[<white>" +
                        index +
                        "<dark_gray>]"
                )
            );
            preview(sender, action);
            return true;
        }

        private boolean set(
            CommandSender sender,
            NPC npc,
            CommandSystem.Args args
        ) {
            InteractionType type = parseType(sender, args.get(2));
            if (type == null) return true;

            List<InteractionAction> actions = npc
                .getOptions()
                .getInteractions(type);
            if (actions.isEmpty()) {
                sender.sendMessage(
                    mm(
                        "<red>No actions on <yellow>" +
                            type.name().toLowerCase()
                    )
                );
                return true;
            }

            int index = parseIndex(sender, args.get(3), actions.size() - 1);
            if (index < 0) return true;

            InteractionAction action = parseAction(sender, args, 4);
            if (action == null) return true;

            action.setType(type);
            actions.set(index, action);
            storage.saveNPC(npc);

            sender.sendMessage(
                mm(
                    "<green>Replaced <yellow>" +
                        type.name().toLowerCase() +
                        " <dark_gray>[<white>" +
                        index +
                        "<dark_gray>] <green>with <aqua>" +
                        action.getActionType().toLowerCase()
                )
            );
            preview(sender, action);
            return true;
        }

        private boolean remove(
            CommandSender sender,
            NPC npc,
            CommandSystem.Args args
        ) {
            InteractionType type = parseType(sender, args.get(2));
            if (type == null) return true;

            NPCOptions opts = npc.getOptions();
            List<InteractionAction> actions = opts.getInteractions(type);
            if (actions.isEmpty()) {
                sender.sendMessage(
                    mm(
                        "<red>No actions on <yellow>" +
                            type.name().toLowerCase()
                    )
                );
                return true;
            }

            if (!args.has(3)) {
                sender.sendMessage(
                    mm(
                        "<red>Usage: <yellow>/spacenpc interaction " +
                            npc.getId() +
                            " remove " +
                            type.name().toLowerCase() +
                            " <index>"
                    )
                );
                return list(sender, npc, type.name());
            }

            int index = parseIndex(sender, args.get(3), actions.size() - 1);
            if (index < 0) return true;

            InteractionAction removed = actions.get(index);
            opts.removeInteraction(type, index);
            storage.saveNPC(npc);

            sender.sendMessage(
                mm(
                    "<green>Removed <yellow>" +
                        type.name().toLowerCase() +
                        " <dark_gray>[<white>" +
                        index +
                        "<dark_gray>] <aqua>" +
                        removed.getActionType().toLowerCase() +
                        " <gray>" +
                        MiniMessage.miniMessage().escapeTags(removed.getValue())
                )
            );
            return true;
        }

        private boolean clear(CommandSender sender, NPC npc, String typeStr) {
            NPCOptions opts = npc.getOptions();

            if (typeStr == null) {
                int total = 0;
                for (List<InteractionAction> actions : opts
                    .getAllInteractions()
                    .values()) {
                    total += actions.size();
                }
                opts.clearAllInteractions();
                storage.saveNPC(npc);
                sender.sendMessage(
                    mm(
                        "<green>Cleared <yellow>" +
                            total +
                            " <green>action(s) on <yellow>" +
                            npc.getId()
                    )
                );
                return true;
            }

            InteractionType type = parseType(sender, typeStr);
            if (type == null) return true;

            int count = opts.getInteractions(type).size();
            opts.clearInteractions(type);
            storage.saveNPC(npc);
            sender.sendMessage(
                mm(
                    "<green>Cleared <yellow>" +
                        count +
                        " <green>action(s) on <yellow>" +
                        type.name().toLowerCase()
                )
            );
            return true;
        }

        private boolean test(CommandSender sender, NPC npc, String typeStr) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(
                    mm("<red>Only players can test interactions")
                );
                return true;
            }

            InteractionType type = typeStr == null
                ? InteractionType.RIGHT_CLICK
                : parseType(sender, typeStr);
            if (type == null) return true;

            sender.sendMessage(
                mm(
                    "<gray>Running <yellow>" +
                        type.name().toLowerCase() +
                        " <gray>on <yellow>" +
                        npc.getId() +
                        " <gray>(commands included)"
                )
            );
            InteractionHandler.handleInteraction(npc, (Player) sender, type);
            return true;
        }

        private boolean help(CommandSender sender, NPC npc) {
            String id = npc.getId();
            sender.sendMessage(
                mm("<gold><bold>Interactions <dark_gray>| <yellow>" + id)
            );
            sender.sendMessage(
                mm(
                    "<yellow>/spacenpc interaction " +
                        id +
                        " add <type> [action] <value>"
                )
            );
            sender.sendMessage(
                mm("<gray>  action defaults to <white>message<gray> when omitted")
            );
            sender.sendMessage(
                mm(
                    "<gray>  types: <white>right_click, left_click, shift_right_click, shift_left_click, any"
                )
            );
            sender.sendMessage(
                mm(
                    "<gray>  several at once: <white>left_click,right_click"
                )
            );
            sender.sendMessage(
                mm(
                    "<gray>  actions: <white>message, run_command, run_command_player, player_chat"
                )
            );
            sender.sendMessage(
                mm(
                    "<gray>  messages use MiniMessage: <white><red>hi <gray>and <white>\\n <gray>for a new line"
                )
            );
            sender.sendMessage(
                mm("<gray>  placeholders: <white>%player%<gray>, <white>%npc%")
            );
            sender.sendMessage(mm("<yellow>Other actions:"));
            sender.sendMessage(
                mm("<gray>  <white>list [type] <gray>- show what is set")
            );
            sender.sendMessage(
                mm(
                    "<gray>  <white>insert <type> <index> [action] <value> <gray>- add in the middle"
                )
            );
            sender.sendMessage(
                mm(
                    "<gray>  <white>set <type> <index> [action] <value> <gray>- rewrite one entry"
                )
            );
            sender.sendMessage(
                mm("<gray>  <white>remove <type> <index> <gray>- delete one entry")
            );
            sender.sendMessage(
                mm(
                    "<gray>  <white>clear [type] <gray>- wipe a type, or everything"
                )
            );
            sender.sendMessage(
                mm("<gray>  <white>test [type] <gray>- fire it on yourself")
            );
            sender.sendMessage(
                mm(
                    "<dark_gray>Example: <white>/spacenpc interaction " +
                        id +
                        " add left_click <red>Line one\\n<gray>Line two"
                )
            );
            return true;
        }

        // ===== PARSING =====

        private InteractionType parseType(CommandSender sender, String raw) {
            if (raw == null) {
                sender.sendMessage(mm("<red>Missing interaction type"));
                sender.sendMessage(
                    mm(
                        "<gray>Types: <white>right_click, left_click, shift_right_click, shift_left_click, any"
                    )
                );
                return null;
            }
            InteractionType type = InteractionType.fromString(raw);
            if (type == null) {
                invalidType(sender, raw);
                return null;
            }
            return type;
        }

        private List<InteractionType> parseTypes(
            CommandSender sender,
            String raw
        ) {
            if (raw == null) {
                parseType(sender, null);
                return null;
            }
            List<InteractionType> types = new ArrayList<>();
            for (String part : raw.split(",")) {
                if (part.isEmpty()) continue;
                InteractionType type = InteractionType.fromString(part);
                if (type == null) {
                    invalidType(sender, part);
                    return null;
                }
                if (!types.contains(type)) types.add(type);
            }
            if (types.isEmpty()) {
                parseType(sender, null);
                return null;
            }
            return types;
        }

        private boolean invalidType(CommandSender sender, String raw) {
            sender.sendMessage(mm("<red>Unknown type: <yellow>" + raw));
            sender.sendMessage(
                mm(
                    "<gray>Types: <white>right_click, left_click, shift_right_click, shift_left_click, any"
                )
            );
            return true;
        }

        private int parseIndex(CommandSender sender, String raw, int max) {
            if (raw == null) {
                sender.sendMessage(mm("<red>Missing index"));
                return -1;
            }
            int index;
            try {
                index = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                sender.sendMessage(mm("<red>Not a number: <yellow>" + raw));
                return -1;
            }
            if (index < 0 || index > max) {
                sender.sendMessage(
                    mm("<red>Index must be between <yellow>0 <red>and <yellow>" + max)
                );
                return -1;
            }
            return index;
        }

        /**
         * Reads an optional action type followed by the rest of the arguments as
         * its value. When the token at {@code start} is not a known action type the
         * whole remainder is treated as a message, so
         * {@code add right_click <red>Hi} works without spelling out "message".
         */
        private InteractionAction parseAction(
            CommandSender sender,
            CommandSystem.Args args,
            int start
        ) {
            String actionType = actionTypeOf(args.get(start));
            String value = actionType == null
                ? args.join(start)
                : args.join(start + 1);

            if (actionType == null) actionType = InteractionAction.MESSAGE;

            if (value.isEmpty()) {
                sender.sendMessage(
                    mm(
                        "<red>Missing value for <yellow>" +
                            actionType.toLowerCase()
                    )
                );
                return null;
            }

            if (
                InteractionAction.RUN_COMMAND.equals(actionType) ||
                InteractionAction.RUN_COMMAND_PLAYER.equals(actionType)
            ) {
                if (value.startsWith("/")) value = value.substring(1);
            } else if (InteractionAction.MESSAGE.equals(actionType)) {
                value = value.replace("\\n", "<newline>");
                if (!isValidMiniMessage(sender, value)) return null;
            }

            return new InteractionAction(null, actionType, value);
        }

        private String actionTypeOf(String raw) {
            if (raw == null) return null;
            switch (raw.toLowerCase()) {
                case "message":
                case "msg":
                case "say":
                    return InteractionAction.MESSAGE;
                case "run_command":
                case "command":
                case "cmd":
                case "console":
                    return InteractionAction.RUN_COMMAND;
                case "run_command_player":
                case "player_command":
                case "player_cmd":
                    return InteractionAction.RUN_COMMAND_PLAYER;
                case "player_chat":
                case "chat":
                    return InteractionAction.PLAYER_CHAT;
                default:
                    return null;
            }
        }

        private boolean isValidMiniMessage(CommandSender sender, String value) {
            try {
                MiniMessage.miniMessage().deserialize(value);
                return true;
            } catch (RuntimeException e) {
                sender.sendMessage(
                    mm("<red>Invalid MiniMessage: <yellow>" + e.getMessage())
                );
                return false;
            }
        }

        private void preview(CommandSender sender, InteractionAction action) {
            if (!InteractionAction.MESSAGE.equals(action.getActionType())) {
                return;
            }
            sender.sendMessage(mm("<dark_gray>Preview:"));
            sender.sendMessage(
                MiniMessage.miniMessage()
                    .deserialize(
                        action.getValue().replace("%player%", sender.getName())
                    )
            );
        }

        // ===== TAB COMPLETE =====

        @Override
        public List<String> tabComplete(
            CommandSender sender,
            CommandSystem.Args args
        ) {
            int length = args.length();

            if (length <= 1) {
                return NPCRegistry.getAll()
                    .stream()
                    .map(NPC::getId)
                    .collect(Collectors.toList());
            }

            if (length == 2) {
                return Lists.newArrayList(
                    "add",
                    "list",
                    "insert",
                    "set",
                    "remove",
                    "clear",
                    "test",
                    "help"
                );
            }

            String action = args.get(1).toLowerCase();

            if (length == 3) {
                return typeNames();
            }

            if (length == 4) {
                if (action.equals("add") || action.equals("append")) {
                    return actionNames();
                }
                return indices(args.get(0), args.get(2));
            }

            if (
                length == 5 &&
                (action.equals("insert") ||
                    action.equals("set") ||
                    action.equals("edit") ||
                    action.equals("replace"))
            ) {
                return actionNames();
            }

            return Collections.emptyList();
        }

        private List<String> typeNames() {
            List<String> names = new ArrayList<>();
            for (InteractionType type : InteractionType.values()) {
                names.add(type.name().toLowerCase());
            }
            return names;
        }

        private List<String> actionNames() {
            return Lists.newArrayList(
                "message",
                "run_command",
                "run_command_player",
                "player_chat"
            );
        }

        private List<String> indices(String id, String typeStr) {
            NPC npc = NPCRegistry.get(id);
            if (npc == null) return Collections.emptyList();
            InteractionType type = InteractionType.fromString(typeStr);
            if (type == null) return Collections.emptyList();

            List<String> indices = new ArrayList<>();
            int size = npc.getOptions().getInteractions(type).size();
            for (int i = 0; i < size; i++) {
                indices.add(String.valueOf(i));
            }
            return indices;
        }

        @Override
        public String getDescription() {
            return "Manage NPC interactions";
        }

        @Override
        public String getUsage() {
            return "interaction <id> <add|list|insert|set|remove|clear|test> [args]";
        }
    }

    // ===== RELOAD =====
    public class Reload extends CommandSystem.SubCommand {

        Reload() {
            super("reload", "spacenpcs.admin");
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            sender.sendMessage(mm("<yellow>Reloading SpaceNPC..."));

            for (NPC npc : NPCRegistry.getAll()) {
                npc.despawn();
            }
            NPCRegistry.clear();

            storage.saveAll();

            storage.loadAllNPCs();

            sender.sendMessage(mm("<green>SpaceNPC reloaded successfully!"));
            sender.sendMessage(
                mm("<gray>NPCs loaded: <white>" + NPCRegistry.count())
            );
            return true;
        }

        @Override
        public String getDescription() {
            return "Reload NPC configurations";
        }

        @Override
        public String getUsage() {
            return "reload";
        }
    }

    // ===== COPYSKIN =====
    public class CopySkin extends CommandSystem.SubCommand {

        CopySkin() {
            super("copyskin", "spacenpcs.admin", "copySkin");
            setArgs(1, 1);
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(
                    mm("<red>Only players can use this command")
                );
                return true;
            }
            Player player = (Player) sender;
            String id = args.get(0);
            NPC npc = NPCCommand.this.getNPC(id, sender);
            if (npc == null) return true;

            sender.sendMessage(
                mm(
                    "<yellow>Copying your skin to NPC '<yellow>" +
                        id +
                        "<yellow>'..."
                )
            );

            List<TextureProperty> skins = skinFetcher.getSkin(player.getName());
            if (skins.isEmpty()) {
                sender.sendMessage(mm("<red>Failed to fetch your skin"));
                return true;
            }

            TextureProperty skin = skins.get(0);
            npc.setSkin(new NPCSkin(skin.getValue(), skin.getSignature()));


            if (npc.isSpawned()) {
                npc.despawn();
                npc.spawn(npc.getPosition());
            }

            storage.saveNPC(npc);
            sender.sendMessage(
                mm(
                    "<green>Successfully copied your skin to NPC '<yellow>" +
                        id +
                        "<green>'"
                )
            );
            return true;
        }

        @Override
        public List<String> tabComplete(
            CommandSender sender,
            CommandSystem.Args args
        ) {
            if (args.length() == 1) {
                return NPCRegistry.getAll()
                    .stream()
                    .map(NPC::getId)
                    .filter(id ->
                        id
                            .toLowerCase()
                            .startsWith(args.get(0, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        @Override
        public String getDescription() {
            return "Copy your skin to an NPC";
        }

        @Override
        public String getUsage() {
            return "copyskin <id>";
        }
    }

    // ===== INVENTORY =====
    public class Inventory extends CommandSystem.SubCommand {

        Inventory() {
            super("inventory", "spacenpcs.admin", "inv");
            setArgs(2, 2);
        }

        @Override
        public boolean execute(CommandSender sender, CommandSystem.Args args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(
                    mm("<red>Only players can use this command")
                );
                return true;
            }
            Player player = (Player) sender;

            String id = args.get(0);
            String slotStr = args.get(1).toLowerCase();

            NPC npc = NPCCommand.this.getNPC(id, sender);
            if (npc == null) return true;

            Optional<?> entityOpt = npc.getEntity();
            if (
                !entityOpt.isPresent() ||
                !(entityOpt.get() instanceof WrapperLivingEntity)
            ) {
                sender.sendMessage(
                    mm(
                        "<red>NPC entity is not a living entity, cannot set equipment"
                    )
                );
                return true;
            }
            String canonicalSlot = canonicalSlot(slotStr.toLowerCase());
            if (canonicalSlot == null) {
                sender.sendMessage(
                    mm("<red>Invalid slot: <yellow>" + slotStr)
                );
                sender.sendMessage(
                    mm(
                        "<gray>Valid slots: <white>helmet, chestplate, leggings, boots, main, off"
                    )
                );
                return true;
            }

            ItemStack bukkitItem = player.getInventory().getItemInMainHand();

            if (bukkitItem == null || bukkitItem.getType().isAir()) {
                sender.sendMessage(
                    mm("<red>You don't have an item in your main hand")
                );
                return true;
            }

            npc.setEquipment(canonicalSlot, bukkitItem);
            storage.saveNPC(npc);
            sender.sendMessage(
                mm(
                    "<green>Set <yellow>" +
                        slotStr +
                        " <green>for NPC '<yellow>" +
                        id +
                        "<green>' to: <white>" +
                        bukkitItem.getType().name()
                )
            );
            return true;
        }

        @Override
        public List<String> tabComplete(
            CommandSender sender,
            CommandSystem.Args args
        ) {
            if (args.length() == 1) {
                return NPCRegistry.getAll()
                    .stream()
                    .map(NPC::getId)
                    .filter(id ->
                        id
                            .toLowerCase()
                            .startsWith(args.get(0, "").toLowerCase())
                    )
                    .collect(Collectors.toList());
            } else if (args.length() == 2) {
                List<String> slots = Lists.newArrayList(
                    "helmet",
                    "chestplate",
                    "leggings",
                    "boots",
                    "main",
                    "off"
                );
                return slots
                    .stream()
                    .filter(s -> s.startsWith(args.get(1, "").toLowerCase()))
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        private String canonicalSlot(String slot) {
            switch (slot) {
                case "helmet":
                case "head":
                case "hat":
                    return "helmet";
                case "chestplate":
                case "chest":
                case "body":
                    return "chestplate";
                case "leggings":
                case "legs":
                    return "leggings";
                case "boots":
                case "feet":
                    return "boots";
                case "main":
                case "mainhand":
                case "hand":
                    return "mainhand";
                case "off":
                case "offhand":
                case "off_hand":
                    return "offhand";
                default:
                    return null;
            }
        }

        @Override
        public String getDescription() {
            return "Set NPC equipment from your inventory";
        }

        @Override
        public String getUsage() {
            return "inventory <id> <slot>";
        }
    }
}
