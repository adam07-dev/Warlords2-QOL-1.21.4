package net.adam.warlords2qol;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.gui.DrawContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.brigadier.arguments.StringArgumentType;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

import net.adam.warlords2qol.mixin.InGameHudAccessor;


public class Warlords2QOLClient implements ClientModInitializer {

    public static final String MOD_ID = "warlords2qol";
    private static final Logger LOGGER = LoggerFactory.getLogger(Warlords2QOL.MOD_ID);


    private static boolean minigameStarted = false;

    private static boolean timerEnabled = true; // Enabled by default

    private static boolean gameRunning = false;
    private static boolean paused = false;
    private static boolean waitingForRestartEnd = false;

    private static boolean gameStartArmed = false;
    private static int timeRemainingSeconds = 0;
    private static int tickCounter = 0;



    private static float hudScale = 1.0f; // default size

    @Override
    public void onInitializeClient() {
        Warlords2QOL.LOGGER.info("Warlords2QOL client mod loaded!");
        System.out.println("Warlords2QOL client mod loaded!");

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            dispatcher.register(
                    literal("timerstop")
                            .executes(context -> {
                                timerEnabled = false;
                                minigameStarted = false;

                                context.getSource().sendFeedback(
                                        Text.literal("§cHUD timer disabled")
                                );
                                return 1;
                            })
            );

            dispatcher.register(
                    literal("timerstart")
                            .executes(context -> {
                                timerEnabled = true;

                                context.getSource().sendFeedback(
                                        Text.literal("§aHUD timer enabled")
                                );
                                return 1;
                            })
            );

            dispatcher.register(
                    literal("timerset")
                            .then(argument("time", StringArgumentType.greedyString())
                                    .executes(context -> {

                                        String input = context.getArgument("time", String.class).trim();

                                        // Split by space: [MM:SS] [optionalTick]
                                        String[] parts = input.split(" ");

                                        // Validate time part
                                        if (!parts[0].matches("\\d{1,2}:\\d{2}")) {
                                            context.getSource().sendFeedback(
                                                    Text.literal("§cInvalid format. Use MM:SS or MM:SS <tick>")
                                            );
                                            return 0;
                                        }

                                        String[] timeParts = parts[0].split(":");
                                        int minutes = Integer.parseInt(timeParts[0]);
                                        int seconds = Integer.parseInt(timeParts[1]);

                                        if (seconds >= 60) {
                                            context.getSource().sendFeedback(
                                                    Text.literal("§cSeconds must be between 00 and 59")
                                            );
                                            return 0;
                                        }

                                        int totalSeconds = minutes * 60 + seconds;

                                        if (totalSeconds < 1 || totalSeconds > 900) {
                                            context.getSource().sendFeedback(
                                                    Text.literal("§cTime must be between 00:01 and 15:00")
                                            );
                                            return 0;
                                        }

                                        // Optional tickCounter
                                        int newTickCounter = 0;
                                        if (parts.length >= 2) {
                                            try {
                                                newTickCounter = Integer.parseInt(parts[1]);
                                            } catch (NumberFormatException e) {
                                                context.getSource().sendFeedback(
                                                        Text.literal("§cTick must be a number between 0 and 20")
                                                );
                                                return 0;
                                            }

                                            if (newTickCounter < 0 || newTickCounter > 20) {
                                                context.getSource().sendFeedback(
                                                        Text.literal("§cTick must be between 0 and 20")
                                                );
                                                return 0;
                                            }
                                        }

                                        // APPLY TIMER
                                        timeRemainingSeconds = totalSeconds;
                                        tickCounter = newTickCounter;
                                        minigameStarted = true;
                                        gameRunning = true;
                                        paused = false;

                                        context.getSource().sendFeedback(
                                                Text.literal(
                                                        "§aTimer set to " +
                                                                minutes + ":" + String.format("%02d", seconds) +
                                                                " (tick " + tickCounter + ")"
                                                )
                                        );

                                        return 1;
                                    })
                            )
            );


            dispatcher.register(
                    literal("timerscale")
                            .then(argument("scale", StringArgumentType.string())
                                    .executes(context -> {
                                        String input = context.getArgument("scale", String.class);

                                        float value;
                                        try {
                                            value = Float.parseFloat(input);
                                        } catch (NumberFormatException e) {
                                            context.getSource().sendFeedback(
                                                    Text.literal("§cInvalid number. Example: /timerscale 0.8")
                                            );
                                            return 0;
                                        }

                                        // Reasonable limits
                                        if (value < 0.3f || value > 3.0f) {
                                            context.getSource().sendFeedback(
                                                    Text.literal("§cScale must be between 0.3 and 3.0")
                                            );
                                            return 0;
                                        }

                                        hudScale = value;

                                        context.getSource().sendFeedback(
                                                Text.literal("§aHUD timer scale set to " + value)
                                        );
                                        return 1;
                                    })
                            )
            );

            dispatcher.register(
                    literal("timergettick")
                            .executes(context -> {

                                context.getSource().sendFeedback(
                                        Text.literal("§eCurrent tickCounter: §a" + tickCounter)
                                );

                                return 1;
                            })
            );



        });









        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!timerEnabled || client.inGameHud == null) return;

            InGameHudAccessor hud = (InGameHudAccessor) client.inGameHud;
            Text title = hud.warlords2qol$getTitle();
            String titleText = title != null ? title.getString().toLowerCase() : "";

            // === GAME START DETECTION (ALLOWED WHEN NOT RUNNING) ===
            if (!gameRunning && titleText.equals("10")) {
                gameRunning = true;
                minigameStarted = true;
                paused = false;

                timeRemainingSeconds = 900;
                tickCounter = 0; // your offset

                gameStartArmed = true;

                Warlords2QOL.LOGGER.info("Game start detected via title '10'");
                return;
            }

            // Reset arm once title changes
            if (gameStartArmed && !titleText.equals("10")) {
                gameStartArmed = false;
            }

            // === STOP HERE IF GAME IS NOT RUNNING ===
            if (!gameRunning) return;

            // === GAME END DETECTION ===
            if (titleText.contains("victory")
                    || titleText.contains("defeat")
                    || titleText.contains("draw")) {

                gameRunning = false;
                minigameStarted = false;
                paused = false;
                waitingForRestartEnd = false;

                Warlords2QOL.LOGGER.info("Game end detected via title — stopping timer");
                return;
            }

            // === PAUSE DETECTION ===
            if (titleText.contains("game paused")) {
                paused = true;
                waitingForRestartEnd = false;
                return;
            }

            // === RESUME DETECTION ===
            if (paused && titleText.contains("resuming in") && titleText.contains("1")) {
                waitingForRestartEnd = true;
                return;
            }

            if (paused && waitingForRestartEnd && !titleText.contains("resuming in")) {
                paused = false;
                waitingForRestartEnd = false;
                Warlords2QOL.LOGGER.info("Restart finished — resuming timer");
            }

            if (paused) return;

            // === TIMER TICK ===
            tickCounter++;

            if (tickCounter >= 20) {
                tickCounter = 0;

                if (timeRemainingSeconds > 0) {
                    timeRemainingSeconds--;
                } else {
                    gameRunning = false;
                    minigameStarted = false;
                }
            }
        });












        HudLayerRegistrationCallback.EVENT.register(layeredHud -> {
            layeredHud.attachLayerAfter(
                    IdentifiedLayer.HOTBAR_AND_BARS,
                    Identifier.of(Warlords2QOL.MOD_ID, "custom_timer"),
                    (DrawContext context, RenderTickCounter tickCounter) -> {

                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.player == null || !minigameStarted || !timerEnabled) return;

                        // === COMPUTE DISPLAYED TIMER HERE ===
                        int displayedTimer = calculateDisplayedTimer();

                        // Safety check
                        if (displayedTimer <= 0) return;

                        String text = String.valueOf(displayedTimer);

                        int screenWidth = client.getWindow().getScaledWidth();
                        int screenHeight = client.getWindow().getScaledHeight();

                        int centerX = screenWidth / 2;
                        int centerY = screenHeight / 2;

                        float scale = hudScale;
                        int textWidth = client.textRenderer.getWidth(text);

                        context.getMatrices().push();
                        context.getMatrices().scale(scale, scale, 1.0f);

                        int drawX = (int) ((centerX - textWidth * scale / 2) / scale);
                        int drawY = (int) ((centerY + 8) / scale);

                        int colour;
                        if (displayedTimer <= 4) {
                            colour = 0xFF5555; // red
                        } else if (displayedTimer <= 8) {
                            colour = 0xFFFF55; // yellow
                        } else {
                            colour = 0x55FF55; // green
                        }

                        context.drawText(
                                client.textRenderer,
                                text,
                                drawX,
                                drawY,
                                colour,
                                true
                        );

                        context.getMatrices().pop();
                    }
            );
        });





    }

    private static int calculateDisplayedTimer() {
        int elapsed = 900 - timeRemainingSeconds;

        if (elapsed < 0) return 0;

        if (elapsed < 8) {
            return 8 - elapsed;
        } else {
            int cycle = (elapsed - 8) % 12;
            return 12 - cycle;
        }
    }



    private static String stripFormatting(String text) {
        return text.replaceAll("§.", "");
    }

}
