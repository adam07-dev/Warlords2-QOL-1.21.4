package net.adam.warlords2qol;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.text.Text;




public class LastStandDetection implements ClientModInitializer {

    private static int lastStandTimer = -1;
    private static boolean active = false;
    private static int lastStandTickCounter = 0;

    private static int interveneTimer = -1;
    private static boolean interveneActive = false;
    private static int interveneTickCounter = 0;

    private static float hudScale = 1.0f;

    @Override
    public void onInitializeClient() {

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("cdscale")
                            .then(argument("scale", StringArgumentType.string())
                                    .executes(context -> {
                                        String input = context.getArgument("scale", String.class);

                                        float value;
                                        try {
                                            value = Float.parseFloat(input);
                                        } catch (NumberFormatException e) {
                                            context.getSource().sendFeedback(
                                                    Text.literal("§cInvalid number. Example: /cdscale 0.8")
                                            );
                                            return 0;
                                        }

                                        if (value < 0.3f || value > 3.0f) {
                                            context.getSource().sendFeedback(
                                                    Text.literal("§cScale must be between 0.3 and 3.0")
                                            );
                                            return 0;
                                        }

                                        hudScale = value;

                                        context.getSource().sendFeedback(
                                                Text.literal("§aCooldown HUD scale set to " + value)
                                        );
                                        return 1;
                                    })
                            )
            );
        });


        // === CHAT DETECTION ===
        ClientReceiveMessageEvents.CHAT.register((message, signed, type, senderUuid, senderProfile) -> {
            String text = stripFormatting(message.getString());

            if (text.contains("Last Stand is now protecting you for")) {
                lastStandTimer = 58;
                lastStandTickCounter = 0;
                active = true;
            }

            if (text.contains("is shielding you with their Intervene!")) {
                interveneTimer = 15;
                interveneTickCounter = 0;
                interveneActive = true;
            }

        });

        // === COUNTDOWN (real seconds) ===
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // === LAST STAND ===
            if (active && lastStandTimer > 0) {
                lastStandTickCounter++;

                if (lastStandTickCounter >= 20) {
                    lastStandTimer--;
                    lastStandTickCounter = 0;
                }
            }

            // === INTERVENE ===
            if (interveneActive && interveneTimer > 0) {
                interveneTickCounter++;

                if (interveneTickCounter >= 20) {
                    interveneTimer--;
                    interveneTickCounter = 0;
                }
            }
        });




        // === HUD LAYER (same format as your respawn timer) ===
        HudLayerRegistrationCallback.EVENT.register(layeredHud -> {
            layeredHud.attachLayerAfter(
                    IdentifiedLayer.HOTBAR_AND_BARS,
                    Identifier.of(Warlords2QOL.MOD_ID, "last_stand_timer"),
                    (DrawContext context, RenderTickCounter tickCounter) -> {

                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.player == null) return;

                        int x = 6;
                        int y = 6;

                        // ===== LAST STAND =====
                        if (active) {
                            drawTimer(context, client, "LS: ", lastStandTimer, x, y, 24, 10);
                            y += 10;
                        }

                        // ===== INTERVENE =====
                        if (interveneActive) {
                            drawTimer(context, client, "VENE: ", interveneTimer, x, y, 8, 4);
                        }
                    }
            );
        });

    }

    private void drawTimer(DrawContext context, MinecraftClient client, String prefix,
                           int timer, int x, int y, int yellow, int red) {

        String value = timer > 0 ? String.valueOf(timer) : "AVAILABLE";

        int colour;
        if (timer <= red && timer > 0) {
            colour = 0xFF5555; // red
        } else if (timer <= yellow && timer > 0) {
            colour = 0xFFFF55; // yellow
        } else {
            colour = 0x55FF55; // green
        }

        // === APPLY SCALE ===
        context.getMatrices().push();
        context.getMatrices().scale(hudScale, hudScale, 1.0f);

        int drawX = (int) (x / hudScale);
        int drawY = (int) (y / hudScale);

        // Prefix in white
        context.drawText(
                client.textRenderer,
                prefix,
                drawX,
                drawY,
                0xFFFFFF,
                true
        );

        int prefixWidth = client.textRenderer.getWidth(prefix);

        // Value in color
        context.drawText(
                client.textRenderer,
                value,
                drawX + prefixWidth,
                drawY,
                colour,
                true
        );

        context.getMatrices().pop();
    }



    private static String stripFormatting(String text) {
        return text.replaceAll("§.", "");
    }

}
