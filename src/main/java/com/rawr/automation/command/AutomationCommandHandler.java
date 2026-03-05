package com.rawr.automation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rawr.automation.config.ModConfig;
import com.rawr.automation.modules.AutoAttack;
import com.rawr.automation.modules.Module;
import com.rawr.automation.modules.ModuleManager;
import com.rawr.automation.modules.PathWalker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * Command handler for /rawr command using Brigadier.
 *
 * Usage:
 *   /rawr                     - Show help and module status
 *   /rawr toggle <module>     - Toggle a module on/off
 *   /rawr walkto <x> <y> <z>  - Walk to coordinates
 *   /rawr attack animals      - Toggle animal targeting for AutoAttack
 *   /rawr list                - List all modules and their status
 */
public class AutomationCommandHandler {

    private static final String PREFIX = "\u00a76[Rawr] \u00a7f";
    private static final String ENABLED = "\u00a7a\u2588 ON";
    private static final String DISABLED = "\u00a7c\u2588 OFF";

    private final ModuleManager moduleManager;
    private final ModConfig config;

    public AutomationCommandHandler(ModuleManager moduleManager, ModConfig config) {
        this.moduleManager = moduleManager;
        this.config = config;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rawr")
                .executes(ctx -> {
                    showHelp(ctx.getSource());
                    return 1;
                })
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            showHelp(ctx.getSource());
                            return 1;
                        })
                )
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            showList(ctx.getSource());
                            return 1;
                        })
                )
                .then(Commands.literal("walkto")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                                    int z = IntegerArgumentType.getInteger(ctx, "z");
                                                    handleWalkTo(ctx.getSource(), x, y, z);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("attack")
                        .executes(ctx -> {
                            handleAttackToggle(ctx.getSource());
                            return 1;
                        })
                        .then(Commands.literal("animals")
                                .executes(ctx -> {
                                    handleAttackAnimals(ctx.getSource());
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("toggle")
                        .then(Commands.argument("module", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (String name : moduleManager.getModules().keySet()) {
                                        builder.suggest(name);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "module");
                                    toggleModule(ctx.getSource(), name);
                                    return 1;
                                })
                        )
                )
        );
    }

    private void showHelp(CommandSourceStack source) {
        msg(source, "\u00a76\u00a7l--- Rawr Automation ---");
        msg(source, "\u00a7e/rawr toggle <module>\u00a7f - Toggle a module");
        msg(source, "\u00a7e/rawr list\u00a7f - List all modules");
        msg(source, "\u00a7e/rawr walkto <x> <y> <z>\u00a7f - Walk to coords");
        msg(source, "\u00a7e/rawr attack animals\u00a7f - Toggle animal targeting");
        msg(source, "");
        showList(source);
    }

    private void showList(CommandSourceStack source) {
        msg(source, "\u00a76Modules:");
        for (Map.Entry<String, Module> entry : moduleManager.getModules().entrySet()) {
            Module mod = entry.getValue();
            String status = mod.isEnabled() ? ENABLED : DISABLED;
            msg(source, " " + status + " \u00a7f" + mod.getName() + " \u00a77- " + mod.getDescription());
        }
    }

    private void toggleModule(CommandSourceStack source, String name) {
        Module module = moduleManager.getModule(name);
        if (module == null) {
            msg(source, PREFIX + "\u00a7cUnknown module: " + name);
            msg(source, PREFIX + "Use \u00a7e/rawr list\u00a7f to see available modules.");
            return;
        }

        module.toggle();
        moduleManager.saveSettings(config);

        String status = module.isEnabled() ? "\u00a7aenabled" : "\u00a7cdisabled";
        msg(source, PREFIX + module.getName() + " " + status);
    }

    private void handleWalkTo(CommandSourceStack source, int x, int y, int z) {
        PathWalker walker = (PathWalker) moduleManager.getModule("pathwalker");
        if (walker == null) {
            msg(source, PREFIX + "\u00a7cPathWalker module not found!");
            return;
        }

        walker.setTarget(x, y, z);
        if (!walker.isEnabled()) {
            walker.setEnabled(true);
        }

        msg(source, PREFIX + "Walking to \u00a7e" + x + ", " + y + ", " + z);
    }

    private void handleAttackToggle(CommandSourceStack source) {
        AutoAttack attack = (AutoAttack) moduleManager.getModule("autoattack");
        if (attack == null) {
            msg(source, PREFIX + "\u00a7cAutoAttack module not found!");
            return;
        }

        attack.toggle();
        moduleManager.saveSettings(config);
        String status = attack.isEnabled() ? "\u00a7aenabled" : "\u00a7cdisabled";
        msg(source, PREFIX + "AutoAttack " + status);
    }

    private void handleAttackAnimals(CommandSourceStack source) {
        AutoAttack attack = (AutoAttack) moduleManager.getModule("autoattack");
        if (attack == null) {
            msg(source, PREFIX + "\u00a7cAutoAttack module not found!");
            return;
        }

        attack.setTargetAnimals(!attack.isTargetingAnimals());
        String status = attack.isTargetingAnimals() ? "\u00a7aenabled" : "\u00a7cdisabled";
        msg(source, PREFIX + "Animal targeting " + status);
    }

    private void msg(CommandSourceStack source, String text) {
        source.sendSystemMessage(Component.literal(text));
    }
}
