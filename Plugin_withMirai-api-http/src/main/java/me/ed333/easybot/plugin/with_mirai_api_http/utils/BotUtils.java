package me.ed333.easybot.plugin.with_mirai_api_http.utils;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.ed333.easybot.api.BotAPI;
import me.ed333.easybot.api.EConfigKeys;
import me.ed333.easybot.api.Vars;
import me.ed333.easybot.api.contacts.IGroup;
import me.ed333.easybot.api.messages.DEBUG;
import me.ed333.easybot.api.utils.IBotMiraiHttpUtils;
import me.ed333.easybot.api.utils.ILanguageUtils;
import me.ed333.easybot.plugin.with_mirai_api_http.Configs;
import me.ed333.easybot.plugin.with_mirai_api_http.SocketClient;
import me.ed333.easybot.plugin.with_mirai_api_http.contactors.Group;
import me.ed333.easybot.plugin.with_mirai_api_http.contactors.GroupMember;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.*;

import static me.ed333.easybot.plugin.with_mirai_api_http.utils.HttpRequestUtils.*;

public class BotUtils implements IBotMiraiHttpUtils {
    private static String apiVer;
    private static String session;
    private static SocketClient client;
    private static boolean initialized = false;
    private static final Long botID = EConfigKeys.BOTID.getLong();
    private static final List<Long> groupIDs = EConfigKeys.GROUPID.getLongList();
    private static final String url = Configs.HOST.getString();
    private static final String key = Configs.KEY.getString();
    private static final JsonParser jp = new JsonParser();
    private static final HashMap<VerifyingPlayer, String> verifyingPlayer = new HashMap<>();
    private static final Set<Player> playerInCool = new HashSet<>();
    private static final Set<Player> enableBotPlayer = new HashSet<>();
    private static final HashMap<Long, IGroup> groups = new HashMap<>();
    private static final HashMap<Long,String> bindQQ = new HashMap<>();
    private static final ConsoleCommandSender sender = Bukkit.getConsoleSender();

    private static void apiVer() {
        apiVer = jp.parse(doGet(String.format("http://%s/about", url))).getAsJsonObject().get("data").getAsJsonObject().get("version").getAsString();
    }

    @Override
    public IGroup getGroup(Long id) {
        return groups.get(id);
    }

    @Override
    public IGroup getSenderGroup() {
        return getGroup(Vars.groupID);
    }

    @Override
    public HashMap<Long, IGroup> getAllGroups() {
        return groups;
    }

    @Override
    public String getSession() {
        return session;
    }

    @Override
    public Boolean isInitialized() {
        return initialized;
    }

    @Override
    public String getApiVer() {
        return apiVer;
    }

    @Override
    public String getGameName(long qqNum) {
        String result = bindQQ.getOrDefault(qqNum, "null");
        DEBUG.debugInfo(String.format("Get game name by qq number: %s, the result is %s", qqNum, result));
        return result;
    }

    @Override
    public TextComponent getGameName_ToComponent(long qqNum) {
        TextComponent txt;
        ILanguageUtils ilu = BotAPI.getILanguageUtils();
        if (bindQQ.containsKey(qqNum)) {
            txt = new TextComponent(ilu.getLangText("BoundQQ.text"));
            txt.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(
                    ilu.hoverEvent_txt_replace(ilu.getLangText("BoundQQ.hoverEvent"))
            ).create()));
        } else {
            txt = new TextComponent(ilu.getLangText("unBoundQQ.text"));
            txt.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(
                    ilu.hoverEvent_txt_replace(ilu.getLangText("unBoundQQ.hoverEvent"))
            ).create()));
        }

        txt.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, String.format("#reply_%s#", Vars.msgID)));
        DEBUG.debugInfo(String.format("Get game name by qq number: %s (to text component)", qqNum));
        return txt;
    }

    @Override
    public boolean qq_isBound(long qqNum) {
        boolean result = bindQQ.containsKey(qqNum);
        DEBUG.debugInfo(String.format("Check the qq number %b whether is bound, the return value is %b", qqNum, result));
        return result;
    }

    @Override
    public boolean name_isBound(String name) {
        boolean result = false;
        for (Long qq : bindQQ.keySet()) {
            if (bindQQ.get(qq).equals(name) && (qq != 0L)) {
                result = true;
            }
        }
        DEBUG.debugInfo(String.format("Check player name %s whether is bound, the return value is %b", name, result));
        return result;
    }

    @Override
    public void addEnableBotPlayer(Player p) {
        enableBotPlayer.add(p);
        DEBUG.debugInfo("Add an enabled bot player.");
    }

    @Override
    public void addEnableBotPlayers(Set<Player> p) {
        enableBotPlayer.addAll(p);
        DEBUG.debugInfo("Add enabled bot players.");
    }

    @Override
    public void removeEnableBotPlayer(Player p) {
        enableBotPlayer.remove(p);
        DEBUG.debugInfo("Remove an enabled bot player.");
    }

    @Override
    public void removeEnableBotPlayers(Set<Player> p) {
        enableBotPlayer.removeAll(p);
        DEBUG.debugInfo("Remove enabled bot players.");
    }

    @Override
    public void removeAllEnabledBotPlayers() {
        enableBotPlayer.clear();
        DEBUG.debugInfo("Remove all enabled bot players.");
    }

    @Override
    public Set<Player> getEnabledPlayers() {
        DEBUG.debugInfo("Get enabled bot players.");
        return enableBotPlayer;
    }

    @Override
    public Player getEnableBotPlayer(String name) {
        for (Player p : enableBotPlayer) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public Player getEnableBotPlayer(UUID uuid) {
        for (Player p : enableBotPlayer) {
            if (p.getUniqueId().equals(uuid)) {
                return p;
            }
        }
        return null;
    }

    public static void close() {
        client.close();
        sender.sendMessage("§3BOT: §a释放session...");
        JsonObject result = jp.parse(releaseSession(session)).getAsJsonObject();
        if (result.get("code").getAsInt() == 0) {
            sender.sendMessage("§3BOT: §a释放完成");
            initialized = false;
            DEBUG.debugInfo("Close socket client, api-http: " + apiVer + ", result ->" + result);
        } else {
            sender.sendMessage("§3BOT: §c释放失败！原因: " + result);
        }
    }

    public static void initialize() {
        apiVer();
        JsonObject authResult = jp.parse(auth()).getAsJsonObject();
        if (authResult.get("code").getAsInt() == 0) {
            sender.sendMessage("§3BOT: §a验证身份成功!");
            session = authResult.get("session").getAsString();
            JsonObject verifyResult = jp.parse(verify()).getAsJsonObject();
            if (verifyResult.get("code").getAsInt() == 0) {
                sender.sendMessage("§3BOT: §a绑定成功!");
                try {

                    for (int i = 0; i < groupIDs.size(); i++) {
                        long groupID = groupIDs.get(i);
                        sender.sendMessage("§3BOT: 加载第" + (i+1) + "个群信息...");
                        JsonObject groupInfo = jp.parse(getGroupInfo(groupID)).getAsJsonObject();
                        Group group = new Group(groupID, groupInfo.get("name").getAsString());
                        sender.sendMessage("§3BOT: §a加载群成员信息...");

                        JsonArray members = jp.parse(getGroupMembers(groupID)).getAsJsonObject().get("data").getAsJsonArray();

                        for (JsonElement memberInfo : members) {
                            JsonObject j = memberInfo.getAsJsonObject();
                            group.addMember(j.get("id").getAsLong(), new GroupMember(j));
                        }

                        sender.sendMessage("§3BOT: §a已加载" + members.size() + "个群成员信息.");

                        groups.put(groupID, group);
                    }
                    sender.sendMessage("§3BOT: §a初始化完毕.");
                    initialized = true;

                    connect();
                } catch (URISyntaxException e) {
                    sender.sendMessage("§3BOT: §c在连接 SocketServer 时发生错误:");
                    e.printStackTrace();
                }
            } else sender.sendMessage("§3BOT: §c绑定失败！返回结果: §7" + verifyResult);
        } else sender.sendMessage("§3BOT: §c验证失败！返回结果: " + authResult);

    }

    @Override
    public String messageFromId(Integer id) {
        String url = String.format("http://%s/messageFromId?sessionKey=%s&id=%s", BotUtils.url, session, id);
        JsonElement result = new JsonParser().parse(doGet(url)).getAsJsonObject().get("data");
        DEBUG.debugInfo(String.format("Get a message from msgId, result: %s", result));
        return result + "";
    }

    private static void connect() throws URISyntaxException {
        sender.sendMessage("§3BOT: §a连接服务器中...");
        URI uri_api_2 = new URI(String.format("ws://%s/all?verifyKey=%s&qq=%d", url, Configs.KEY.getString(), botID));
        client = new SocketClient(uri_api_2);
        DEBUG.debugInfo("Connect Socket, api-http: " + apiVer);
        client.connect();
    }

    private static String verify() {
        sender.sendMessage("§3BOT: §a绑定BOT...");
        JsonObject request = new JsonObject();
        request.addProperty("sessionKey", session);
        request.addProperty("qq", botID);
        String result = doPost("http://" + url + "/bind", request);
        DEBUG.debugInfo("Verify Session, api-http: " + apiVer + ", request ->" + request + ", result ->" + result);
        return result;
    }

    private static String releaseSession(String session) {
        JsonObject request = new JsonObject();
        String result;
        request.addProperty("sessionKey", session);
        request.addProperty("qq", botID);
        result = doPost("http://" + url + "/release", request);
        DEBUG.debugInfo("Release session, api-http: " + apiVer + ", request ->" + request + ", result ->" + result);
        return result;
    }

    private static String auth() {
        sender.sendMessage("§3BOT: §a注册bot...");
        JsonObject request = new JsonObject();
        request.addProperty("verifyKey", key);
        String result = doPost(String.format("http://%s/verify", url), request);
        DEBUG.debugInfo(String.format("AuthBot, api-http: %s, request: %s result: %s", apiVer, request, result));
        return result;
    }

    private static String getGroupInfo(Long id) {
        String result = doGet(String.format("http://%s/groupConfig?sessionKey=%s&target=%d", url, session, id));
        DEBUG.debugInfo("getGroupInfo, api-http: " + apiVer + ", result ->" + result);
        return result;
    }

    private static String getGroupMembers(Long id) {
        String result = doGet(String.format("http://%s/memberList?sessionKey=%s&target=%d", url, session, id));
        DEBUG.debugInfo("getMemberInfo, api-http: " + apiVer + ", result ->" + result);
        return result;
    }

    public static void setKeySets() {
        YamlConfiguration data = BotAPI.getiConfigManager().getData();
        ConfigurationSection section = data.getConfigurationSection("Player");
        if (section != null) {
            for (String s : section.getKeys(false)) {
                bindQQ.put(section.getLong(s + ".bind"), section.getString(s + ".name"));
            }
        }
    }

    public static HashMap<VerifyingPlayer, String> getVerifyingPlayers() {
        return verifyingPlayer;
    }

    public static void addVerifyingPlayer(VerifyingPlayer p, String code) { verifyingPlayer.put(p, code); }

    public static void remVerifyPlayer(VerifyingPlayer p) { verifyingPlayer.remove(p); }

    public static Set<Player> getPlayersInCool() {
        return playerInCool;
    }

    public static void addPlayerInCool(Player p) {
        playerInCool.add(p);
    }

    public static void remPlayerInCool(Player p) { playerInCool.remove(p); }

    public static void addBindQQ(Long qq, String name) {
        bindQQ.put(qq, name);
    }

    public static @NotNull String genVerifyCode() {
        StringBuilder sb = new StringBuilder();
        SecureRandom rm = new SecureRandom();
        while (sb.length() < 6) {
            sb.append(rm.nextInt(10));
        }
        return sb.toString();
    }
}