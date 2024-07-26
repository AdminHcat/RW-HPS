/*
 * Copyright 2020-2024 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.plugin.beta.bind

import net.rwhps.server.data.global.Data
import net.rwhps.server.game.event.core.EventListenerHost
import net.rwhps.server.game.event.game.PlayerJoinEvent
import net.rwhps.server.game.player.PlayerHess
import net.rwhps.server.struct.map.ObjectMap
import net.rwhps.server.util.annotations.core.EventListenerHandler
import net.rwhps.server.util.file.FileUtils
import net.rwhps.server.util.file.plugin.PluginData
import net.rwhps.server.util.inline.coverConnect
import net.rwhps.server.util.inline.toPrettyPrintingJson
import net.rwhps.server.util.log.Log

/**
 *
 *
 * @date 2024/7/2 上午11:35
 * @author Dr (dr@der.kim)
 */
class BindEvent(
    private val config: BaseBindData,
    private val pluginDataFileUtils: FileUtils,
    private val codeList: ObjectMap<String, BindDataJson>
): EventListenerHost {
    @EventListenerHandler
    fun playerJoin(event: PlayerJoinEvent){
        if (config.force) {
            var data = ""
            if (config.apiConsole.isNotEmpty()) {
                data = Data.core.http.doGet("http://${config.apiConsole}/api/getBindData?hex=${event.player.connectHexID}")
                if (data.isEmpty()) {
                    event.player.kickPlayer("服务器强制绑定, 您未满足")
                    return
                }
            } else if (config.apiPort != 0) {
                var falsg = true
                codeList.values.forEach {
                    if (it.hex == event.player.connectHexID) {
                        falsg = false
                        return
                    }
                }
                if (falsg) {
                    a(event.player)
                }
            }
        }
    }

    fun a(player: PlayerHess) {
        player.sendPopUps("请输入绑定码") {
            val d = codeList[it]
            if (d == null) {
                player.kickPlayer("错误, 您已被踢出", 0)
            } else {
                if (d.hex.isNotEmpty()) {
                    player.kickPlayer("错误, 绑定码已被使用", 0)
                } else {
                    d.hex = player.connectHexID
                    d.name = player.name
                    d.ip = player.con!!.coverConnect().ip
                    pluginDataFileUtils.toFolder("bind").toFile(d.qq+".json").writeFile(d.toPrettyPrintingJson())
                }
            }
        }
    }
}