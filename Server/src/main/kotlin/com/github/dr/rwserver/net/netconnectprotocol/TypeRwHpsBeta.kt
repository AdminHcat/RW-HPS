/*
 * Copyright 2020-2022 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package com.github.dr.rwserver.net.netconnectprotocol

import com.github.dr.rwserver.data.global.Data
import com.github.dr.rwserver.io.packet.GameSavePacket
import com.github.dr.rwserver.io.packet.Packet
import com.github.dr.rwserver.net.core.ConnectionAgreement
import com.github.dr.rwserver.net.core.TypeConnect
import com.github.dr.rwserver.net.netconnectprotocol.realize.GameVersionServerBeta
import com.github.dr.rwserver.util.PacketType
import com.github.dr.rwserver.util.Time.concurrentSecond

class TypeRwHpsBeta(val con: GameVersionServerBeta) : TypeConnect(con) {
    override fun getTypeConnect(connectionAgreement: ConnectionAgreement): TypeConnect {
        return TypeRwHpsBeta(GameVersionServerBeta(connectionAgreement))
    }

    @Throws(Exception::class)
    override fun typeConnect(packet: Packet) {
        con.lastReceivedTime()

        if (packet.type == PacketType.PACKET_ADD_GAMECOMMAND) {
            con.receiveCommand(packet)
            con.player.lastMoveTime = concurrentSecond()
        } else {
            when (packet.type) {
                PacketType.PACKET_PREREGISTER_CONNECTION -> con.registerConnection(packet)
                PacketType.PACKET_PLAYER_INFO -> if (!con.getPlayerInfo(packet)) {
                    con.disconnect()
                }
                PacketType.PACKET_HEART_BEAT_RESPONSE -> {
                    val player = con.player
                    player.ping = (System.currentTimeMillis() - player.timeTemp).toInt() shr 1
                }
                PacketType.PACKET_ADD_CHAT -> con.receiveChat(packet)
                PacketType.PACKET_DISCONNECT -> con.disconnect()
                PacketType.PACKET_ACCEPT_START_GAME -> con.player.start = true
                PacketType.PACKET_SERVER_DEBUG -> con.debug(packet)
                // 竞争 谁先到就用谁
                PacketType.PACKET_SYNC -> if (Data.game.gameSaveCache == null) Data.game.gameSaveCache = GameSavePacket(packet)
                else -> {
                }
            }
        }
    }

    override val version: String
        get() = "2.0.0"
}