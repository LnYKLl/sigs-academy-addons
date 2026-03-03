package com.siguha.sigsacademyaddons.feature.wondertrade;

import com.siguha.sigsacademyaddons.config.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.Deque;

public class WondertradeSoundPlayer {

    private final HudConfig hudConfig;
    private final Deque<ScheduledNote> noteQueue = new ArrayDeque<>();
    private int tickCounter = 0;

    public WondertradeSoundPlayer(HudConfig hudConfig) {
        this.hudConfig = hudConfig;
    }

    public void playCooldownCompleteSound() {
        if (!hudConfig.isWtSoundsEnabled()) return;

        int baseTick = tickCounter;
        noteQueue.add(new ScheduledNote(baseTick, 1.5f, 0.9f));
        noteQueue.add(new ScheduledNote(baseTick + 3, 1.25f, 0.85f));
        noteQueue.add(new ScheduledNote(baseTick + 6, 1.0f, 0.9f));
    }

    public void tick() {
        tickCounter++;

        if (noteQueue.isEmpty()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        while (!noteQueue.isEmpty() && noteQueue.peek().scheduledTick <= tickCounter) {
            ScheduledNote note = noteQueue.poll();
            player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), note.volume, note.pitch);
        }
    }

    private record ScheduledNote(int scheduledTick, float pitch, float volume) {}
}
