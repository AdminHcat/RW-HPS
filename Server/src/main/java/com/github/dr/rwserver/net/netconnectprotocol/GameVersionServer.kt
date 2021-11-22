/*
 * Copyright 2020-2021 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package com.github.dr.rwserver.net.netconnectprotocol

import com.github.dr.rwserver.Main
import com.github.dr.rwserver.core.Call
import com.github.dr.rwserver.core.thread.Threads.getIfScheduledFutureData
import com.github.dr.rwserver.core.thread.Threads.removeScheduledFutureData
import com.github.dr.rwserver.data.Player
import com.github.dr.rwserver.data.global.Data
import com.github.dr.rwserver.data.global.NetStaticData
import com.github.dr.rwserver.game.EventType.*
import com.github.dr.rwserver.game.GameCommand
import com.github.dr.rwserver.io.GameInputStream
import com.github.dr.rwserver.io.GameOutputStream
import com.github.dr.rwserver.io.Packet
import com.github.dr.rwserver.net.core.server.AbstractNetConnect
import com.github.dr.rwserver.net.game.ConnectionAgreement
import com.github.dr.rwserver.util.ExtractUtil
import com.github.dr.rwserver.util.IsUtil
import com.github.dr.rwserver.util.PacketType
import com.github.dr.rwserver.util.RandomUtil
import com.github.dr.rwserver.util.alone.annotations.MainProtocolImplementation
import com.github.dr.rwserver.util.encryption.Game
import com.github.dr.rwserver.util.game.CommandHandler
import com.github.dr.rwserver.util.game.CommandHandler.CommandResponse
import com.github.dr.rwserver.util.game.Events
import com.github.dr.rwserver.util.log.Log
import com.github.dr.rwserver.util.zip.CompressOutputStream
import java.io.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.min

/**
 * @author Dr
 * @date 2020/9/5 17:02:33
 */

@MainProtocolImplementation
open class GameVersionServer(connectionAgreement: ConnectionAgreement) : AbstractGameVersion(connectionAgreement) {
    private val sync = ReentrantLock(true)

    /** 玩家  */
    override lateinit var player: Player

    override fun getVersionNet(connectionAgreement: ConnectionAgreement): AbstractNetConnect {
        return GameVersionServer(connectionAgreement)
    }

    override val version: String
        get() = "1.14 RW-HPS"

    override fun sendSystemMessage(msg: String) {
        if (!player.noSay) {
            super.sendSystemMessage(msg)
        }
    }

    @Throws(IOException::class)
    override fun sendServerInfo(utilData: Boolean) {
        val o = GameOutputStream()
        o.writeString(Data.SERVER_ID)
        o.writeInt(NetStaticData.protocolData.gameNetVersion)
        /* 地图 */
        o.writeInt(Data.game.maps.mapType.ordinal)
        o.writeString(Data.game.maps.mapPlayer + Data.game.maps.mapName)
        o.writeInt(Data.game.credits)
        o.writeInt(Data.game.mist)
        o.writeBoolean(true)
        o.writeInt(1)
        o.writeByte(7)
        o.writeBoolean(false)
        /* Admin Ui */
        o.writeBoolean(player.isAdmin)
        o.writeInt(Data.config.MaxUnit)
        o.writeInt(Data.config.MaxUnit)
        o.writeInt(Data.game.initUnit)
        o.writeFloat(Data.game.income)
        /* NO Nukes */
        o.writeBoolean(Data.game.noNukes)
        o.writeBoolean(false)
        o.writeBoolean(utilData)
        if (utilData) {
            o.flushEncodeData(Data.utilData)
        }

        /* 共享控制 */
        o.writeBoolean(Data.game.sharedControl)
        o.writeBoolean(false)
        o.writeBoolean(false)
        // 允许观众
        o.writeBoolean(true)
        o.writeBoolean(false)
        sendPacket(o.createPacket(PacketType.PACKET_SERVER_INFO))
    }

    override fun sendSurrender() {
        try {
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { stream ->
                    stream.writeByte(player.site)
                    val a = ExtractUtil.hexToByteArray("000000ffffffffffffffff0000000000000000ffffffffffffffff00022d3100000001000000000000000000000000640000000000")
                    for (b in a) {
                        stream.writeByte(b.toInt())
                    }
                    val cmd = GameCommand(player.site, buffer.toByteArray())
                    Data.game.gameCommandCache.offer(cmd)
                    Call.sendSystemMessage(Data.localeUtil.getinput("player.surrender", player.name))
                }
            }
        } catch (ignored: Exception) {
        }
    }

    @Throws(IOException::class)
    override fun receiveChat(p: Packet) {
        GameInputStream(p).use { stream ->
            var message: String? = stream.readString()
            var response: CommandResponse? = null
            Log.clog("[{0}]: {1}", player.name, message)
            if (player.isAdmin && getIfScheduledFutureData("AfkCountdown")) {
                removeScheduledFutureData("AfkCountdown")
                Call.sendMessage(player, Data.localeUtil.getinput("afk.clear", player.name))
            }
            if (message!!.startsWith(".") || message.startsWith("-") || message.startsWith("_")) {
                val strEnd = min(message.length, 3)
                response = if ("qc" == message.substring(1, strEnd)) {
                    Data.CLIENT_COMMAND.handleMessage("/" + message.substring(5), player)
                } else {
                    Data.CLIENT_COMMAND.handleMessage("/" + message.substring(1), player)
                }
            }
            if (response == null || response.type == CommandHandler.ResponseType.noCommand) {
                if (message.length > Data.config.MaxMessageLen) {
                    sendSystemMessage(Data.localeUtil.getinput("message.maxLen"))
                    return
                }
                message = Data.core.admin.filterMessage(player, message)
                if (message == null) {
                    return
                }
                Call.sendMessage(player, message)
                Events.fire(PlayerChatEvent(player, message))
            } else {
                if (response.type != CommandHandler.ResponseType.valid) {
                    val text: String =  when (response.type) {
                                            CommandHandler.ResponseType.manyArguments -> {
                                                "Too many arguments. Usage: " + response.command.text + " " + response.command.paramText
                                            }
                                            CommandHandler.ResponseType.fewArguments -> {
                                                "Too few arguments. Usage: " + response.command.text + " " + response.command.paramText
                                            }
                                            else -> {
                                                "Unknown command. Check .help"
                                        }
                    }
                    player.sendSystemMessage(text)
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun receiveCommand(p: Packet) {
        //PlayerOperationUnitEvent
        sync.lock()
        try {
            GameInputStream(GameInputStream(p).getDecodeBytes()).use { inStream ->
                val outStream = GameOutputStream()
                outStream.writeByte(inStream.readByte())
                val boolean1 = inStream.readBoolean()
                outStream.writeBoolean(boolean1)
                if (boolean1) {
                    outStream.writeInt(inStream.readInt())
                    val int1 = inStream.readInt()
                    outStream.writeInt(int1)
                    if (int1 == -2) {
                        outStream.writeString(inStream.readString())
                    }
                    outStream.transferToFixedLength(inStream,28)
                    outStream.writeIsString(inStream)
                }
                outStream.transferToFixedLength(inStream,10)
                val boolean3 = inStream.readBoolean()
                outStream.writeBoolean(boolean3)
                if (boolean3) {
                    outStream.transferToFixedLength(inStream,8)
                }
                outStream.writeBoolean(inStream.readBoolean())
                val int2 = inStream.readInt()
                outStream.writeInt(int2)
                for (i in 0 until int2) {
                    outStream.transferToFixedLength(inStream,8)
                }
                val boolean4 = inStream.readBoolean()
                outStream.writeBoolean(boolean4)
                if (boolean4) {
                    outStream.writeByte(inStream.readByte())
                }
                val boolean5 = inStream.readBoolean()
                outStream.writeBoolean(boolean5)
                if (boolean5) {
                    outStream.transferToFixedLength(inStream,8)
                }
                outStream.transferToFixedLength(inStream,8)
                outStream.writeString(inStream.readString())
                //outStream.writeBoolean(inStream.readBoolean())
                outStream.writeByte(inStream.readByte())
                inStream.readShort()
                outStream.writeShort(Data.game.sharedControlPlayer.toShort())
                outStream.transferTo(inStream)
                Data.game.gameCommandCache.offer(GameCommand(player.site, outStream.getPacketBytes()))
            }
        } catch (e: Exception) {
            Log.error(e)
        } finally {
            sync.unlock()
        }
    }

    @Throws(IOException::class)
    override fun getGameSaveData(packet: Packet): ByteArray {
        GameInputStream(packet).use { stream ->
            val o = GameOutputStream()
            o.writeByte(stream.readByte())
            o.writeInt(stream.readInt())
            o.writeInt(stream.readInt())
            o.writeFloat(stream.readFloat())
            o.writeFloat(stream.readFloat())
            o.writeBoolean(false)
            o.writeBoolean(false)
            stream.readBoolean()
            stream.readBoolean()
            stream.readString()
            return stream.readStreamBytes()
        }
    }

    @Throws(IOException::class)
    override fun sendStartGame() {
        sendServerInfo(true)
        sendPacket(NetStaticData.protocolData.abstractNetPacket.getStartGamePacket())
        if (IsUtil.notIsBlank(Data.config.StartAd)) {
            sendSystemMessage(Data.config.StartAd)
        }
    }

    override fun sendTeamData(gzip: CompressOutputStream) {
        try {
            val o = GameOutputStream()
            /* Player position */
            o.writeInt(player.site)
            o.writeBoolean(Data.game.isStartGame)
            /* Largest player */
            o.writeInt(Data.game.maxPlayer)
            o.flushEncodeData(gzip)
            /* 迷雾 */
            o.writeInt(Data.game.mist)
            o.writeInt(Data.game.credits)
            o.writeBoolean(true)
            /* AI Difficulty ?*/
            o.writeInt(1)
            o.writeByte(5)
            o.writeInt(Data.config.MaxUnit)
            o.writeInt(Data.config.MaxUnit)
            /* 初始单位 */
            o.writeInt(Data.game.initUnit)
            /* 倍速 */
            o.writeFloat(Data.game.income)
            /* NO Nukes */
            o.writeBoolean(Data.game.noNukes)
            o.writeBoolean(false)
            o.writeBoolean(false)
            /* 共享控制 */
            o.writeBoolean(Data.game.sharedControl)
            /* 游戏暂停 */
            o.writeBoolean(false)
            sendPacket(o.createPacket(PacketType.PACKET_TEAM_LIST))
        } catch (e: IOException) {
            Log.error("Team", e)
        }
    }

    //@Throws(IOException::class)
    override fun getPlayerInfo(p: Packet): Boolean {
        try {
            GameInputStream(p).use { stream ->
                stream.readString()
                Log.debug(stream.readInt())
                Log.debug(stream.readInt())
                Log.debug(stream.readInt())
                var name = stream.readString()
                Log.debug("name", name)
                val passwd = stream.isReadString()
                Log.debug("passwd", passwd)
                stream.readString()
                val uuid = stream.readString()
                Log.debug("uuid", uuid)
                Log.debug("?", stream.readInt())
                val token = stream.readString()
                Log.debug("token", token)
                Log.debug(token, connectKey!!)

                /*
                if (!token.equals(playerConnectKey)) {
                    sendKick("You Open Mod?");
                    return false;
                }*/

                val playerConnectPasswdCheck = PlayerConnectPasswdCheckEvent(this, passwd)
                Events.fire(playerConnectPasswdCheck)
                if (playerConnectPasswdCheck.result) {
                    return true
                }
                if (IsUtil.notIsBlank(playerConnectPasswdCheck.name)) {
                    name = playerConnectPasswdCheck.name
                }

                Events.fire(PlayerJoinUuidandNameEvent(uuid,name))

                val playerJoinName = PlayerJoinNameEvent(name)
                Events.fire(playerJoinName)
                if (IsUtil.notIsBlank(playerJoinName.resultName)) {
                    name = playerJoinName.resultName
                }

                inputPassword = false
                val re = AtomicBoolean(false)
                if (Data.game.isStartGame) {
                    Data.playerAll.each({ i: Player -> i.uuid == uuid }) { e: Player ->
                        re.set(true)
                        this.player = e
                        player.con = this
                        Data.playerGroup.add(e)
                    }
                    if (!re.get()) {
                        if (IsUtil.isBlank(Data.config.StartPlayerAd)) {
                            sendKick("游戏已经开局 请等待 # The game has started, please wait")
                        } else {
                            sendKick(Data.config.StartPlayerAd)
                        }
                        return false
                    }
                } else {
                    if (Data.playerGroup.size() >= Data.game.maxPlayer) {
                        if (IsUtil.isBlank(Data.config.MaxPlayerAd)) {
                            sendKick("服务器没有位置 # The server has no free location")
                        } else {
                            sendKick(Data.config.MaxPlayerAd)
                        }
                        return false
                    }
                    val localeUtil = Data.localeUtilMap["CN"]
                    /*
                    if (Data.game.ipCheckMultiLanguageSupport) {
                        val rec = Data.ip2Location.IPQuery(connectionAgreement.ip)
                        if ("OK" != rec.status) {
                            localeUtil = Data.localeUtilMap[rec.countryShort]
                        }
                    }
                     */
                    player = Player.addPlayer(this, uuid, name, localeUtil)
                }
                connectionAgreement.add(NetStaticData.groupNet)
                Call.sendTeamData()
                sendServerInfo(true)
                Events.fire(PlayerJoinEvent(player))
                if (IsUtil.notIsBlank(Data.config.EnterAd)) {
                    sendSystemMessage(Data.config.EnterAd)
                }
                Call.sendSystemMessage(Data.localeUtil.getinput("player.ent", player.name))
                if (re.get()) {
                    reConnect()
                }
                return true
            }
        } finally {
            super.connectKey = null
        }
    }

    @Throws(IOException::class)
    override fun registerConnection(p: Packet) {
        // 生成随机Key;
        val keyLen = 6
        val key = RandomUtil.generateInt(keyLen)
        super.connectKey = Game.connectKey(key)
        GameInputStream(p).use { stream ->
            // Game Pkg Name
            stream.readString()
            // 返回都是1 有啥用
            stream.readInt()
            stream.readInt()
            stream.readInt()
            val o = GameOutputStream()
            o.writeString(Data.SERVER_ID)
            o.writeInt(1)
            o.writeInt(NetStaticData.protocolData.gameNetVersion)
            o.writeInt(NetStaticData.protocolData.gameNetVersion)
            o.writeString("com.corrodinggames.rts.server")
            o.writeString(Data.core.serverConnectUuid)
            o.writeInt(key)
            sendPacket(o.createPacket(PacketType.PACKET_REGISTER_CONNECTION))
        }
    }

    /**
     *
     */
    override fun sendErrorPasswd() {
        try {
            val o = GameOutputStream()
            o.writeInt(0)
            sendPacket(o.createPacket(PacketType.PACKET_PASSWD_ERROR))
            inputPassword = true
        } catch (e: Exception) {
            Log.error("[Player] sendErrorPasswd", e)
        }
    }

    override fun disconnect() {
        if (super.isDis) {
            return
        }
        super.isDis = true
        if (this::player.isInitialized) {
            Data.playerGroup.remove(player)
            if (!Data.game.isStartGame) {
                Data.playerAll.remove(player)
                player.clear()
                Data.game.playerData[player.site] = null
            }
            Events.fire(PlayerLeaveEvent(player))
        }
        super.close(NetStaticData.groupNet)
    }

    override fun getGameSave() {
        try {
            val o = GameOutputStream()
            o.writeByte(0)
            o.writeInt(0)
            o.writeInt(0)
            o.writeFloat(0f)
            o.writeFloat(0f)
            o.writeBoolean(true)
            o.writeBoolean(false)
            val gzipEncoder = CompressOutputStream.getGzipOutputStream("gameSave", false)
            gzipEncoder.writeString("This is RW-HPS!")
            o.flushEncodeData(gzipEncoder)
            sendPacket(o.createPacket(35))
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    override fun sendGameSave(packet: Packet) {
        sendPacket(packet)
    }

    override fun reConnect() {
        try {
            if (!Data.config.ReConnect) {
                sendKick(player.localeUtil.getinput("kick.noReConnect"))
                return
            }
            super.isDis = false
            sendPacket(NetStaticData.protocolData.abstractNetPacket.getStartGamePacket())
            Data.game.reConnectBreak = true
            Call.sendSystemMessage("玩家断线重连中 请耐心等待 请勿退出 期间可能会暂时卡住！！ 预计需要30s-60s")
            val executorService = Executors.newFixedThreadPool(1)
            val future = executorService.submit<String?> {
                Data.playerGroup.each({ e: Player -> e.uuid != this.player.uuid && !e.con!!.tryBoolean }) { p: Player ->
                    p.con!!.getGameSave()
                    while (Data.game.gameSaveCache == null || Data.game.gameSaveCache.type == 0) {
                        if (Thread.interrupted()) {
                            return@each
                        }
                    }
                    try {
                        NetStaticData.groupNet.broadcast(
                            NetStaticData.protocolData.abstractNetPacket.convertGameSaveDataPacket(
                                Data.game.gameSaveCache
                            )
                        )
                    } catch (e: IOException) {
                        Log.error(e)
                    }
                }
                null
            }
            try {
                future[30, TimeUnit.SECONDS]
            } catch (e: Exception) {
                future.cancel(true)
                Log.error(e)
                connectionAgreement.close(NetStaticData.groupNet)
            } finally {
                executorService.shutdown()
                Data.game.gameSaveCache = null
                Data.game.reConnectBreak = false
            }
        } catch (e: Exception) {
            Log.error("[Player] Send GameSave ReConnect Error", e)
        }
    }

    /**
     * (WARN) This part temporarily refuses to provide analysis and needs to wait for the right time
     * @author Dr
     */
    override fun sendRelayServerInfo() {
        try {
            val bytes = ExtractUtil.hexToByteArray("00 00 00 00 01 00 00 00 97 00")
            sendPacket(Packet(163,bytes))
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    /**
     * (WARN) This part temporarily refuses to provide analysis and needs to wait for the right time
     * @author Dr
     */
    override fun sendRelayServerCheck() {
        try {
            val bytes = ExtractUtil.hexToByteArray("00 00 00 01 00 00 00 01 00 00 00 06 52 57 2d 48 50 53 00 06 52 57 2d 48 50 53 00 00 00 01 00")
            sendPacket(Packet(151,bytes))
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    /**
     * (WARN) This part temporarily refuses to provide analysis and needs to wait for the right time
     * @author Dr
     */
    override fun sendRelayServerId() {
        try {
            val bytes = ExtractUtil.hexToByteArray("01 01 01 01 00 24 33 30 34 66 30 34 30 34 2d 63 36 35 38 2d 34 65 33 34 2d 39 35 62 33 2d 39 39 36 66 65 32 62 36 36 61 38 65 01 00 01 00 28 7b 7b 52 57 2d 48 50 53 20 52 65 6c 61 79 7d 7d 2e 52 6f 6f 6d 20 49 44 20 3a 20 41 75 74 6f 20 52 65 61 64 20 4d 6f 64 01")
            sendPacket(Packet(170,bytes))
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    /*

     */

    /**
     * (WARN) This part temporarily refuses to provide analysis and needs to wait for the right time
     * @author Dr
     */
    override fun sendRelayPlayerInfo() {
        try {
            val bytes = ExtractUtil.hexToByteArray("00 00 00 00 02 00 24 62 64 66 30 34 35 35 66 2d 30 62 66 35 2d 34 34 37 31 2d 39 61 65 39 2d 34  64 36 31 35 32 35 31 30 38 37 39 00")
            sendPacket(Packet(172, bytes))
            val bytes2 = ExtractUtil.hexToByteArray("00 00 00 02 00 00 00 34 00 00 00 2c 00 00 00 a0 00 19 63 6f 6d 2e 63 6f 72 72 6f 64 69 6e 67 67  61 6d 65 73 2e 72 74 73 2e 71 7a 00 00 00 03 00  00 00 97 00 00 00 01 00 00 02 00 00")
            sendPacket(Packet(174, bytes2))
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    /**
     * (WARN) This part temporarily refuses to provide analysis and needs to wait for the right time
     * @author Dr
     */
    override fun sendRelayPlayerConnectPacket(packet: Packet) {
        try {
            GameInputStream(packet).use { inStream ->
                val o = GameOutputStream()
                inStream.skip(4)
                val dataOutputStream = inStream.getStream()
                dataOutputStream.readString()
                dataOutputStream.skip(12)
                dataOutputStream.readString()
                dataOutputStream.readString()

                val out = GameOutputStream()
                val bytes = ExtractUtil.hexToByteArray("00 16 63 6f 6d 2e 63 6f 72 72 6f 64 69 6e 67 67 61 6d 65 73 2e 72 74 73 00 00 00 04 00 00 00 97 00 00 00 97 00 0c 4d 6f 64 20 52 65 61 64 20 42 6f 74 00 00 1b 63 6f 6d 2e 63 6f 72 72 6f 64 69 6e 67 67 61 6d 65 73 2e 72 74 73 2e 6a 61 76 61 00 13 52 57 2d 48 50 53 20 4d 6f 64 20 52 65 61 64 20 42 6f 74 47 6e a1 5a")
                out.writeBytes(bytes)
                out.writeString(Game.connectKey(dataOutputStream.readInt()))
                val packet1 = out.createPacket()

                o.writeInt(2)
                o.writeInt(packet1.bytes.size+8)
                o.writeInt(packet1.bytes.size)
                o.writeInt(110)
                o.writeBytes(packet1.bytes)
                sendPacket(o.createPacket(174))
            }
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    /**
     * (WARN) This part temporarily refuses to provide analysis and needs to wait for the right time
     * @author Dr
     */
    override fun getRelayUnitData(packet: Packet) {
        try {
            GameInputStream(packet).use { inStream ->
                inStream.readInt()
                when (inStream.readInt()) {
                    106 -> {
                        inStream.skip(4)
                        inStream.readString()
                        inStream.skip(8)
                        inStream.readString()
                        inStream.skip(8)
                        inStream.readBoolean()
                        inStream.skip(26)
                        inStream.readString()
                        //
                        val stream2 = DataInputStream(ByteArrayInputStream(inStream.readStreamBytes()))
                        stream2.readInt()
                        val count = stream2.readInt()
                        val data = StringBuilder()
                        Data.core.unitBase64.clear()
                        for (i in 0 until count) {
                            data.delete(0, data.length)
                            data.append(stream2.readUTF()).append("%#%").append(stream2.readInt())
                            stream2.readBoolean()
                            if (stream2.readBoolean()) {
                                data.append("%#%").append(stream2.readUTF())
                            }
                            stream2.skip(16)
                            Data.core.unitBase64.add(data.toString())
                        }
                        Data.config.OneReadUnitList = false
                        Data.core.save()
                        sendPacket(NetStaticData.protocolData.abstractNetPacket.getSystemMessagePacket("MOD读取完成，请重新进入"))
                        Main.loadUnitList()
                    }
                    161 -> sendRelayPlayerConnectPacket(packet)
                    else -> return
                }
            }
        } catch (e: Exception) {
            Log.error(e)
        }
    }
}