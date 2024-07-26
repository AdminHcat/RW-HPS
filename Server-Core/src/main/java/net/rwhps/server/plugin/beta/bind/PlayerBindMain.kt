/*
 * Copyright 2020-2024 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.plugin.beta.bind

import net.rwhps.server.game.event.EventGlobalManage
import net.rwhps.server.game.event.EventManage
import net.rwhps.server.game.event.core.EventListenerHost
import net.rwhps.server.game.event.global.ServerLoadEvent
import net.rwhps.server.game.manage.IRwHpsManage
import net.rwhps.server.net.NetService
import net.rwhps.server.net.core.IRwHps
import net.rwhps.server.net.core.web.WebGet
import net.rwhps.server.net.http.AcceptWeb
import net.rwhps.server.net.http.SendWeb
import net.rwhps.server.net.http.WebData
import net.rwhps.server.plugin.Plugin
import net.rwhps.server.struct.map.ObjectMap
import net.rwhps.server.util.annotations.core.EventListenerHandler
import net.rwhps.server.util.file.FileUtils
import net.rwhps.server.util.file.plugin.PluginData
import net.rwhps.server.util.file.plugin.serializer.SerializersEnum
import net.rwhps.server.util.inline.toGson
import net.rwhps.server.util.inline.toPrettyPrintingJson
import net.rwhps.server.util.io.IoRead
import org.jline.utils.Log
import kotlin.concurrent.thread

/**
 * 玩家绑定功能
 *
 * Link: RainTeam
 *
 * @date 2024/7/2 上午11:08
 * @author Dr (dr@der.kim)
 */
class PlayerBindMain : Plugin() {
    private lateinit var config: BaseBindData
    private lateinit var plguinData: PluginData
    private val codeList: ObjectMap<String, BindDataJson> = ObjectMap()

    override fun onEnable() {
        config = BaseBindData.get(pluginDataFileUtils.toFile("BindConfig.json"))
        config.save()

        if (config.use) {
            plguinData = PluginData(SerializersEnum.Bin.create()).apply {
                setFileUtil(pluginDataFileUtils.toFile("BindConfig.bin"))
            }
            val binds = pluginDataFileUtils.toFolder("bind").apply {
                mkdir()
            }
            binds.fileListNotNullSize.forEach {
                BindDataJson::class.java.toGson(FileUtils(it).readFileStringData()).let {
                    codeList[it.bindCode] = it
                }
            }
        }
    }

    override fun registerEvents(eventManage: EventManage) {
        if (config.use) {
            eventManage.registerListener(BindEvent(config, pluginDataFileUtils, codeList))
        }
    }

    override fun registerGlobalEvents(eventManage: EventGlobalManage) {
        if (config.use && config.apiPort != 0) {
            eventManage.registerListener(object: EventListenerHost {
                @EventListenerHandler
                fun loadOver(server: ServerLoadEvent) {
                    thread {
                        WebData().apply {
                            addWebGetInstance("/api/bind", object: WebGet() {
                                override fun get(accept: AcceptWeb, send: SendWeb) {
                                    val json = stringUrlDataResolveToJson(accept)
                                    try {
                                        val data = BindDataJson(json.getString("Code"), json.getString("QQ"))
                                        codeList[json.getString("Code")] = data
                                        pluginDataFileUtils.toFolder("bind").toFile(data.qq+".json").writeFile(data.toPrettyPrintingJson())
                                        send.setData("OK")
                                    } catch (e: Exception) {
                                        net.rwhps.server.util.log.Log.error(e)
                                        send.setData("ERROR Parse")
                                    }
                                    send.send()
                                }
                            })
                            addWebGetInstance("/api/getBindData", object: WebGet() {
                                override fun get(accept: AcceptWeb, send: SendWeb) {
                                    val json = stringUrlDataResolveToJson(accept)
                                    send.setData("")
                                    try {
                                        val hex = json.getString("hex")
                                        codeList.values.forEach {
                                            if (it.hex == hex) {
                                                send.setData(it.qq)
                                                send.send()
                                                return
                                            }
                                        }
                                    } catch (_: Exception) {
                                        send.setData("")
                                    }
                                    send.send()
                                }
                            })
                        }.run {
                            val netServiceTcp1 = NetService(NetService.coreID(), IRwHpsManage.addIRwHps(IRwHps.NetType.HttpProtocol, "BindWebService"))
                            netServiceTcp1.setWebData(this)
                            netServiceTcp1.openPort(config.apiPort)
                        }
                    }
                }
            })
        }
    }
}