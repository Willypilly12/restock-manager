package dev.restockmanager.client;

import dev.restockmanager.core.*;
import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;

/** Client glue: scans inventory every 5 ticks, runs AlertEngine, shows coloured notification cards + sound. */
public class RestockManagerClient implements ClientModInitializer {
    private final AlertEngine engine = new AlertEngine();
    private int tick;
    private volatile boolean openMenu;
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("restockmanager", "main"));
    private KeyBinding openKey, toggleKey;

    @Override
    public void onInitializeClient() {
        // Shows up in Options > Controls > Key Binds. Unbound by default so it never clashes with another key.
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.restockmanager.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.restockmanager.toggle_alerts", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        Defaults.populate(engine);
        ConfigStore.load(engine);
        // Client-side command: never sent to the server.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("rsm").executes(ctx -> { openMenu = true; return Command.SINGLE_SUCCESS; }));
            dispatcher.register(ClientCommandManager.literal("restockmanager").executes(ctx -> { openMenu = true; return Command.SINGLE_SUCCESS; }));
        });
        // Coloured notification cards, drawn above the chat layer of the HUD.
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.of("restockmanager", "notifications"),
                (context, tickCounter) -> {
                    try { Notifications.render(context, engine); } catch (Throwable ignored) { }
                });
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient mc) {
        try {
            while (openKey.wasPressed()) {
                if (mc.player != null && mc.currentScreen == null) openMenu = true;
            }
            while (toggleKey.wasPressed()) {
                if (mc.player == null) continue;
                engine.globalEnabled = !engine.globalEnabled;
                ConfigStore.save(engine);
                Notifications.push(engine.globalEnabled ? "Alerts on" : "Alerts off",
                        engine.globalEnabled ? "Restock Manager reminders are active." : "Restock Manager reminders are muted.",
                        Notifications.accent(engine, engine.globalEnabled ? Notifications.KIND_OK : Notifications.KIND_LOW), null);
            }
            if (mc.player == null || mc.world == null) { engine.reset(); Notifications.clear(); return; }
            if (openMenu) { openMenu = false; mc.setScreen(new SettingsScreen(engine)); return; }
            if (++tick % 5 != 0) return;
            Inventory snap = snapshot(mc.player.getInventory(), mc.player.getOffHandStack());
            for (Alert a : engine.update(snap, System.currentTimeMillis())) {
                TrackedRule r = a.rule();
                String title, label; int kind, channel;
                switch (a.type()) {
                    case HOTBAR_RESTOCK -> { title = "Restock your hotbar"; label = "Restock hotbar"; kind = Notifications.KIND_HOTBAR; channel = 0; }
                    case LOW_KIT -> { title = "Low on supplies"; label = "/rg reminder"; kind = Notifications.KIND_LOW; channel = 1; }
                    default -> { title = "Kit restocked"; label = "Kit restocked"; kind = Notifications.KIND_OK; channel = 1; }
                }
                String count = r.countStacks && a.type() != Alert.Type.RESTOCKED
                        ? a.quantity() + (a.quantity() == 1 ? " stack" : " stacks") : "x" + a.quantity();
                if (Notifications.channelOf(engine, channel).enabled)
                    Notifications.push(title, a.message(), Notifications.accent(engine, kind), Icons.of(r), channel, count, label);
                String soundId = engine.useGlobalSound ? engine.globalSound : r.sound;
                if (r.soundEnabled && Notifications.channelOf(engine, channel).soundOn && soundId != null) {
                    Identifier sid = Identifier.tryParse(soundId);
                    SoundEvent se = sid == null ? null : Registries.SOUND_EVENT.get(sid);
                    if (se != null) mc.player.playSound(se, Math.max(0f, Math.min(1f, r.volume)), 1.0f);
                }
            }
        } catch (Throwable t) { /* never crash the game from this mod */ }
    }

    private static Inventory snapshot(PlayerInventory inv, ItemStack off) {
        List<StackInfo> hot = new ArrayList<>(9), main = new ArrayList<>(27);
        for (int i = 0; i < 9; i++) hot.add(info(inv.getStack(i)));
        for (int i = 9; i < 36; i++) main.add(info(inv.getStack(i)));
        return new Inventory(hot, main, info(off));
    }

    private static StackInfo info(ItemStack s) {
        if (s == null || s.isEmpty()) return null;
        String id = Registries.ITEM.getId(s.getItem()).toString();
        String effect = null;
        PotionContentsComponent pc = s.get(DataComponentTypes.POTION_CONTENTS);
        if (pc != null && pc.potion().isPresent())
            effect = pc.potion().get().getKey().map(k -> k.getValue().toString()).orElse(null);
        Text name = s.get(DataComponentTypes.CUSTOM_NAME);
        return new StackInfo(id, effect, name == null ? null : name.getString(), s.getCount(), s.getMaxCount());
    }
}
