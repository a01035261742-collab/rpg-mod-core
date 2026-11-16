package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;

/** 보스 BGM: 경기장 안에서 반복 재생 (음악 볼륨), 바뀌거나 끝나면 천천히 꺼짐. 그동안 바닐라 음악 멈춤 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
public final class BossMusic {
    private BossMusic() {}

    private static String current = "";
    private static Track playing;

    /** id = sounds.json 이름 (예: music.boss_guardian_1), 빈칸 = 끄기 */
    public static void set(String id) {
        if (id.equals(current)) return;
        current = id;
        if (playing != null) playing.fade = true;
        playing = null;
        if (id.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        mc.getMusicManager().stopPlaying();
        playing = new Track(SoundEvent.createVariableRangeEvent(RpgCore.id(id)));
        mc.getSoundManager().play(playing);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { if (!current.isEmpty()) set(""); return; }
        if (!current.isEmpty()) mc.getMusicManager().stopPlaying();   // 보스 곡 도중엔 바닐라 음악 안 나오게
    }

    static final class Track extends AbstractTickableSoundInstance {
        boolean fade;
        Track(SoundEvent ev) {
            super(ev, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            volume = 0.05f;
            relative = true;
            attenuation = Attenuation.NONE;
        }

        @Override
        public void tick() {
            if (fade) {   // 2초에 걸쳐 꺼짐
                volume -= 0.025f;
                if (volume <= 0) stop();
            } else if (volume < 1f) volume = Math.min(1f, volume + 0.05f);   // 1초에 걸쳐 커짐
        }
    }
}
