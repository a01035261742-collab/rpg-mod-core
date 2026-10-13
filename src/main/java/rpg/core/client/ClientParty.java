package rpg.core.client;

import java.util.ArrayList;
import java.util.List;
import rpg.core.net.Net;

/** 서버에서 받은 파티 정보 */
public final class ClientParty {
    private ClientParty() {}

    public static final List<Net.Member> MEMBERS = new ArrayList<>();
    public static String inviter = "";

    public static void set(List<Net.Member> members, String inv) {
        MEMBERS.clear();
        MEMBERS.addAll(members);
        inviter = inv == null ? "" : inv;
    }
}
