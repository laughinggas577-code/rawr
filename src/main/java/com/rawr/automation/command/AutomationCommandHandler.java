package com.rawr.automation.command;

import com.rawr.automation.config.ModConfig;
import com.rawr.automation.modules.AutoAttack;
import com.rawr.automation.modules.Module;
import com.rawr.automation.modules.ModuleManager;
import com.rawr.automation.modules.PathWalker;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Command handler for /rawr command.
 *
 * Usage:
 *   /rawr                     - Show help and module status
 *   /rawr <module>            - Toggle a module on/off
 *   /rawr walkto <x> <y> <z> - Walk to coordinates
 *   /rawr attack animals      - Toggle animal targeting for AutoAttack
 *   /rawr list                - List all modules and their status
 */
public class AutomationCommandHandler extends CommandBase {

    private static final String PREFIX = "\u00a76[Rawr] \u00a7f";
    private static final String ENABLED = "\u00a7a\u2588 ON";
    private static final String DISABLED = "\u00a7c\u2588 OFF";

    private final ModuleManager moduleManager;
    private final ModConfig config;

    public AutomationCommandHandler(ModuleManager moduleManager, ModConfig config) {
        this.moduleManager = moduleManager;
        this.config = config;
    }

    @Override
    public String getCommandName() {
        return "rawr";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/rawr [module|list|walkto|help]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // No permission needed (client-side)
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
                showHelp(sender);
                break;
            case "list":
                showList(sender);
                break;
            case "walkto":
                handleWalkTo(sender, args);
                break;
            case "attack":
                handleAttackSettings(sender, args);
                break;
            default:
                toggleModule(sender, sub);
                break;
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("help");
            options.add("list");
            options.add("walkto");
            options.add("attack");
            for (String name : moduleManager.getModules().keySet()) {
                options.add(name);
            }
            return getListOfStringsMatchingLastWord(args, options.toArray(new String[0]));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("attack")) {
            options.add("animals");
            return getListOfStringsMatchingLastWord(args, options.toArray(new String[0]));
        }
        return options;
    }

    private void showHelp(ICommandSender sender) {
        msg(sender, "\u00a76\u00a7l--- Rawr Automation ---");
        msg(sender, "\u00a7e/rawr <module>\u00a7f - Toggle a module");
        msg(sender, "\u00a7e/rawr list\u00a7f - List all modules");
        msg(sender, "\u00a7e/rawr walkto <x> <y> <z>\u00a7f - Walk to coords");
        msg(sender, "\u00a7e/rawr attack animals\u00a7f - Toggle animal targeting");
        msg(sender, "");
        showList(sender);
    }

    private void showList(ICommandSender sender) {
        msg(sender, "\u00a76Modules:");
        for (Map.Entry<String, Module> entry : moduleManager.getModules().entrySet()) {
            Module mod = entry.getValue();
            String status = mod.isEnabled() ? ENABLED : DISABLED;
            msg(sender, " " + status + " \u00a7f" + mod.getName() + " \u00a77- " + mod.getDescription());
        }
    }

    private void toggleModule(ICommandSender sender, String name) {
        Module module = moduleManager.getModule(name);
        if (module == null) {
            msg(sender, PREFIX + "\u00a7cUnknown module: " + name);
            msg(sender, PREFIX + "Use \u00a7e/rawr list\u00a7f to see available modules.");
            return;
        }

        module.toggle();
        moduleManager.saveSettings(config);

        String status = module.isEnabled()
                ? "\u00a7aenabled"
                : "\u00a7cdisabled";
        msg(sender, PREFIX + module.getName() + " " + status);
    }

    private void handleWalkTo(ICommandSender sender, String[] args) {
        if (args.length < 4) {
            msg(sender, PREFIX + "\u00a7cUsage: /rawr walkto <x> <y> <z>");
            return;
        }

        try {
            int x = Integer.parseInt(args[1]);
            int y = Integer.parseInt(args[2]);
            int z = Integer.parseInt(args[3]);

            PathWalker walker = (PathWalker) moduleManager.getModule("pathwalker");
            if (walker == null) {
                msg(sender, PREFIX + "\u00a7cPathWalker module not found!");
                return;
            }

            walker.setTarget(x, y, z);
            if (!walker.isEnabled()) {
                walker.setEnabled(true);
            }

            msg(sender, PREFIX + "Walking to \u00a7e" + x + ", " + y + ", " + z);
        } catch (NumberFormatException e) {
            msg(sender, PREFIX + "\u00a7cInvalid coordinates. Use whole numbers.");
        }
    }

    private void handleAttackSettings(ICommandSender sender, String[] args) {
        AutoAttack attack = (AutoAttack) moduleManager.getModule("autoattack");
        if (attack == null) {
            msg(sender, PREFIX + "\u00a7cAutoAttack module not found!");
            return;
        }

        if (args.length < 2) {
            // Just toggle AutoAttack
            attack.toggle();
            moduleManager.saveSettings(config);
            String status = attack.isEnabled() ? "\u00a7aenabled" : "\u00a7cdisabled";
            msg(sender, PREFIX + "AutoAttack " + status);
            return;
        }

        if (args[1].equalsIgnoreCase("animals")) {
            attack.setTargetAnimals(!attack.isTargetingAnimals());
            String status = attack.isTargetingAnimals() ? "\u00a7aenabled" : "\u00a7cdisabled";
            msg(sender, PREFIX + "Animal targeting " + status);
        } else {
            msg(sender, PREFIX + "\u00a7cUnknown attack setting: " + args[1]);
        }
    }

    private void msg(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }
}
