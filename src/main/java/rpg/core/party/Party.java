package rpg.core.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 파티 하나 (서버 메모리, 재시작하면 풀림) */
public class Party {
    public UUID leader;
    public final List<UUID> members = new ArrayList<>();

    Party(UUID leader) {
        this.leader = leader;
        members.add(leader);
    }
}
