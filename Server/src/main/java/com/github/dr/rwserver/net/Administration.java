/*
 * Copyright 2020-2021 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package com.github.dr.rwserver.net;

import com.github.dr.rwserver.core.thread.Threads;
import com.github.dr.rwserver.data.Player;
import com.github.dr.rwserver.data.global.Data;
import com.github.dr.rwserver.data.plugin.PluginData;
import com.github.dr.rwserver.struct.ObjectMap;
import com.github.dr.rwserver.struct.Seq;
import com.github.dr.rwserver.util.Time;

/**
 * @author Dr
 */
public class Administration {

    private final Seq<ChatFilter> chatFilters = new Seq<>();
    public final Seq<String> bannedIPs;
    public final Seq<String> bannedUUIDs;
    public final Seq<String> whitelist;
    public final Seq<String> playerData;
    public final ObjectMap<String,PlayerInfo> playerDataCache = new ObjectMap<>();

    public Administration(PluginData pluginData){
        addChatFilter((player, message) -> {
            if(!player.isAdmin){
                //防止玩家在 30 秒内两次发送相同的消息
                if(message.equals(player.lastSentMessage) && Time.getTimeSinceMillis(player.lastMessageTime) < 1000 * 30){
                    //player.sendSystemMessage("您可能不会两次发送相同的消息.");
                    //player.sendSystemMessageLocal("err.antiSpam");
                    player.sendSystemMessage(player.localeUtil.getinput("err.antiSpam"));
                    return null;
                }
                player.lastSentMessage = message;
                player.lastMessageTime = Time.millis();
            }
            return message;
        });

        bannedIPs = pluginData.getData("bannedIPs", new Seq<>());
        bannedUUIDs = pluginData.getData("bannedUUIDs", new Seq<>());
        whitelist = pluginData.getData("whitelist", new Seq<>());
        playerData = pluginData.getData("playerData", new Seq<>());

        Threads.addSavePool(() -> {
            pluginData.setData("bannedIPs",bannedIPs);
            pluginData.setData("bannedUUIDs",bannedUUIDs);
            pluginData.setData("whitelist",whitelist);
            pluginData.setData("playerData",playerData);
        });

        Threads.addSavePool(() -> Data.config.save());
    }

    /**
     * 添加聊天过滤器。这将改变每个玩家的聊天消息
     * 此功能可用于实现过滤器和特殊命令之类的功能
     * 请注意，未过滤命令
     */
    public void addChatFilter(ChatFilter filter){
        chatFilters.add(filter);
    }

    /** 过滤掉聊天消息 */
    public String filterMessage(Player player, String message){
        String current = message;
        for(ChatFilter f : chatFilters){
            current = f.filter(player, message);
            if(current == null) {
                return null;
            }
        }
        return current;
    }

    public void addAdmin(String uuid) {
        playerData.add(uuid);
    }

    public void removeAdmin(String uuid) {
        playerData.remove(uuid);
    }

    public boolean isAdmin(String uuid) {
        return playerData.contains(uuid);
    }

    public interface ChatFilter{
        /**
         * 过滤消息
         * @param player Player
         * @param message Message
         * @return 过滤后的消息 空字符串表示不应发送该消息
         */
        String filter(Player player, String message);
    }

    public static class PlayerInfo {
        public final String uuid;
        public long timesKicked = 0;
        public long timesJoined = 0;
        public long timeMute = 0;
        public boolean admin = false;

        public PlayerInfo(String uuid){
            this.uuid = uuid;
        }

        public PlayerInfo(String uuid,boolean admin){
            this.uuid = uuid;
            this.admin = admin;
        }

        public PlayerInfo(String uuid,long timesKicked,long timeMute){
            this.uuid = uuid;
            this.timesKicked = timesKicked;
            this.timeMute = timeMute;
        }

        public PlayerInfo(String uuid,long timesKicked,long timeMute,boolean admin){
            this.uuid = uuid;
            this.admin = admin;
            this.timesKicked = timesKicked;
            this.timeMute = timeMute;
        }
    }
}
