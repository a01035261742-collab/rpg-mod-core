package rpg.core.item;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import rpg.core.RpgCore;
import rpg.core.combat.Boss;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;
import rpg.core.net.Net;

/**
 * 보스 보물: 우클릭으로 열면 그 보스 드랍템 중 하나 (mobs.json 보스 reward.treasure 표). 안 열고 상점에 팔아도 됨.
 * 장비가 나오면 서버 전체 공지
 */
public class Treasure extends Material {
    public Treasure(Properties p, String desc) { super(p, desc, false); }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(s, level, tip, flag);
        tip.add(Component.literal("우클릭: 열어서 보스 드랍템 1개 받기").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer p)) return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
        String id = ItemIds.sellKey(s);
        if (id.contains(":")) id = id.substring(id.indexOf(':') + 1);
        Boss.TreasureTable t = Boss.TREASURE.get(id);
        if (t == null || t.rows().isEmpty()) {
            p.sendSystemMessage(Component.literal("[RPG] 이 보물은 아직 열 수 없어요 (상점에 팔 수 있음)").withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(s);
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double sum = 0;
        for (Boss.TreasureRow row : t.rows()) sum += Math.max(0, row.weight());
        double x = r.nextDouble() * sum;
        Boss.TreasureRow pick = t.rows().get(t.rows().size() - 1);
        for (Boss.TreasureRow row : t.rows()) { x -= Math.max(0, row.weight()); if (x < 0) { pick = row; break; } }

        String boxName = s.getHoverName().getString();
        s.shrink(1);
        level.playSound(null, p.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 1f, 1f);
        level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);

        if (pick.id().equals("gold")) {
            PlayerData d = RpgData.get(p);
            d.gold += pick.count();
            RpgData.sync(p);
            Net.reward(p, 0, pick.count());
            p.sendSystemMessage(Component.literal("[보물] " + String.format("%,d", pick.count()) + " G 획득!").withStyle(ChatFormatting.GOLD));
            return InteractionResultHolder.consume(s);
        }
        ItemStack got;
        if (pick.id().equals("scroll")) {
            got = rpg.core.equip.Enhance.randomScroll(Boss.level(t.boss()), r);
        } else {
            String gid = pick.id();
            if (gid.startsWith("job:")) {   // 내 직업 것 (초보자는 무작위)
                String[] js = gid.substring(4).split("\\|");
                int job = RpgData.get(p).job;
                gid = job >= 1 && job <= js.length ? js[job - 1] : js[r.nextInt(js.length)];
            }
            got = ItemIds.stack(RpgCore.MODID + ":" + gid, Math.max(1, pick.count()));
        }
        if (got.isEmpty()) return InteractionResultHolder.consume(s);
        boolean gear = got.getItem() instanceof rpg.core.equip.Gear.GearItem;
        if (gear) rpg.core.equip.Gear.roll(got);
        String nm = got.getHoverName().getString() + (got.getCount() > 1 ? " ×" + got.getCount() : "");
        if (!p.getInventory().add(got)) p.drop(got, false);
        if (gear && p.getServer() != null) {
            p.getServer().getPlayerList().broadcastSystemMessage(Component.literal("✦ " + p.getGameProfile().getName() + " 님이 " + boxName + "에서 " + nm + " 획득!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        } else {
            p.sendSystemMessage(Component.literal("[보물] " + nm + " 획득!").withStyle(ChatFormatting.AQUA));
        }
        return InteractionResultHolder.consume(s);
    }
}
