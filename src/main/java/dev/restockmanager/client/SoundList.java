package dev.restockmanager.client;

import java.util.List;

/** Vanilla sounds offered in the picker. Ids that don't exist in a given version are skipped when played. */
final class SoundList {
    record S(String id, String name) { }

    static final List<S> ALL = List.of(
        new S("minecraft:block.note_block.pling", "Pling"),
        new S("minecraft:block.note_block.bell", "Note Bell"),
        new S("minecraft:block.note_block.chime", "Chime"),
        new S("minecraft:block.note_block.harp", "Harp"),
        new S("minecraft:block.note_block.bit", "Bit"),
        new S("minecraft:block.note_block.xylophone", "Xylophone"),
        new S("minecraft:block.note_block.iron_xylophone", "Metal Xylophone"),
        new S("minecraft:entity.experience_orb.pickup", "XP Orb"),
        new S("minecraft:entity.player.levelup", "Level Up"),
        new S("minecraft:block.bell.use", "Bell"),
        new S("minecraft:block.amethyst_block.chime", "Amethyst"),
        new S("minecraft:entity.enderman.teleport", "Teleport"),
        new S("minecraft:entity.arrow.hit_player", "Arrow Hit"),
        new S("minecraft:block.anvil.land", "Anvil"),
        new S("minecraft:entity.villager.no", "Villager No"),
        new S("minecraft:item.totem.use", "Totem"),
        new S("minecraft:ui.button.click", "Click")
    );

    static int indexOf(String id) {
        for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).id().equals(id)) return i;
        return -1;
    }

    static String nameOf(String id) {
        int i = indexOf(id);
        if (i >= 0) return ALL.get(i).name();
        if (id == null) return "None";
        int c = id.indexOf(':');
        return c >= 0 ? id.substring(c + 1) : id;
    }
}
