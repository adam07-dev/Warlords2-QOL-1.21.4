package net.adam.warlords2qol.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(InGameHud.class)
public interface InGameHudAccessor {

    // === TITLE TEXT ===
    @Accessor("title")
    Text warlords2qol$getTitle();

    @Accessor("subtitle")
    Text warlords2qol$getSubtitle();

    // === TITLE TIMERS ===
    @Accessor("titleFadeInTicks")
    int warlords2qol$getTitleFadeInTicks();

    @Accessor("titleStayTicks")
    int warlords2qol$getTitleStayTicks();

    @Accessor("titleFadeOutTicks")
    int warlords2qol$getTitleFadeOutTicks();
}
