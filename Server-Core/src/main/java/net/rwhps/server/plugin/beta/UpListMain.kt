/*
 * Copyright 2020-2024 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.plugin.beta

import net.rwhps.server.core.thread.CallTimeTask
import net.rwhps.server.core.thread.Threads
import net.rwhps.server.data.global.Data
import net.rwhps.server.data.global.NetStaticData
import net.rwhps.server.func.StrCons
import net.rwhps.server.game.event.EventManage
import net.rwhps.server.game.event.core.EventListenerHost
import net.rwhps.server.game.event.game.ServerGameOverEvent
import net.rwhps.server.game.manage.HeadlessModuleManage
import net.rwhps.server.game.player.PlayerHess
import net.rwhps.server.net.NetService
import net.rwhps.server.net.core.IRwHps
import net.rwhps.server.net.core.server.AbstractNetConnectServer
import net.rwhps.server.plugin.Plugin
import net.rwhps.server.util.IpUtils
import net.rwhps.server.util.algorithms.Base64
import net.rwhps.server.util.annotations.core.EventListenerHandler
import net.rwhps.server.util.file.json.Json
import net.rwhps.server.util.game.command.CommandHandler
import net.rwhps.server.util.inline.ifEmptyResult
import net.rwhps.server.util.log.Log
import net.rwhps.server.util.str.StringFilteringUtil.cutting
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread


internal class UpListMain: Plugin() {
    private val version = "Version=HPS#1"
    private val privateIp: String
        get() {
            var privateIpTemp = IpUtils.getPrivateIp()
            if (privateIpTemp.isNullOrBlank()) {
                privateIpTemp = "10.0.0.1"
            }
            return privateIpTemp
        }
    private var port = Data.config.port.toString()

    private var versionBeta = false
    private var versionGame = "1.15"
    private var versionGameInt = 176

    private var upServerList = false

    /* DATA Cache */
    private lateinit var serverID: String
    private lateinit var addData: String
    private lateinit var openData: String
    private lateinit var updateData: String
    private lateinit var removeData: String

    private var uplistFlag = 0
    private var over = false

    override fun init() {
        AddLang(this)
    }

    override fun registerEvents(eventManage: EventManage) {
        eventManage.registerListener(object: EventListenerHost {
            @EventListenerHandler
            fun gameover(gameOverEvent: ServerGameOverEvent) {
                if (over) {
                    remove({})
                }
                over = false
            }
        })
    }

    override fun registerCoreCommands(handler: CommandHandler) {
        handler.removeCommand("upserverlist")
        handler.removeCommand("upserverlistnew")

        handler.register("uplist", "[command...]", "serverCommands.upserverlist") { args: Array<String>?, log: StrCons ->
            if (!args.isNullOrEmpty()) {
                when (args[0]) {
                    "add" -> NetStaticData.checkServerStartNet { if (args.size > 1) add(log, args[1]) else add(log) }
                    "update" -> NetStaticData.checkServerStartNet { update() }
                    "remove" -> NetStaticData.checkServerStartNet { remove(log) }
                    "help" -> log(Data.i18NBundle.getinput("uplist.help"))
                    else -> log("Check UpList Command ! use 'uplist help'")
                }
            } else {
                log("Check UpList Command ! use 'uplist help'")
            }
        }
    }

    override fun registerServerClientCommands(handler: CommandHandler) {
        fun isAdmin(player: PlayerHess): Boolean {
            if (player.isAdmin) {
                return true
            }
            player.sendSystemMessage(player.i18NBundle.getinput("err.noAdmin"))
            return false
        }

        handler.register("toup", "#up") { _: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                when (uplistFlag) {
                    0 -> {
                        thread {
                            player.sendSystemMessage("耐心等待, 错误信息不打印, 请自行在控制台提前实验")
                            if (initUpListData()) {
                                over = true
                                this.port = port.ifBlank { Data.config.port.toString() }
                                Threads.newThreadCore { upServerList = true; uplistFlag = 1;uplist(player::sendSystemMessage) }
                            }
                        }
                    }
                    else -> player.sendSystemMessage("Already on the list")
                }

            }
        }
        handler.register("tonp", "#up") { _: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                when (uplistFlag) {
                    1 -> {
                        thread {
                            player.sendSystemMessage("耐心等待")
                            remove(player::sendSystemMessage)
                        }
                    }
                    else -> player.sendSystemMessage("no uplist")
                }
            }
        }
    }

    private fun initUpListData(urlIn: String = ""): Boolean {
        if (NetStaticData.RwHps.netType.ordinal in IRwHps.NetType.ServerProtocol.ordinal .. IRwHps.NetType.ServerTestProtocol.ordinal) {
            (NetStaticData.RwHps.typeConnect.abstractNetConnect as AbstractNetConnectServer).run {
                versionBeta = supportedversionBeta
                versionGame = supportedversionGame
                versionGameInt = supportedVersionInt
            }

        } else {
            versionBeta = false
            versionGame = "1.15-Other"
            versionGameInt = 176
        }


        val url = urlIn.ifBlank { Data.urlData.readString("Get.Api.UpListData.Bak") }

        var resultUpList = Data.core.http.doPost(url, version)

        if (resultUpList.isBlank() && urlIn.isBlank()) {
            resultUpList = Data.core.http.doPost(Data.urlData.readString("Get.Api.UpListData"), version)
        }

        if (resultUpList.isBlank()) {
            Log.error("[Get UPLIST Data Error] Unexpected error Failed to initialize")
            return false
        }

        if (resultUpList.startsWith("[-1]")) {
            Log.error("[Get UPLIST Data Error] Please Check API")
            return false
        } else if (resultUpList.startsWith("[-2]")) {
            Log.error("[Get UPLIST Data Error] IP prohibited")
            return false
        } else if (resultUpList.startsWith("[-4]")) {
            Log.error("[Get UPLIST Data Error] Version Error")
            val newUrl = resultUpList.substring(8, resultUpList.length)
            return if (newUrl == "Error") {
                Log.error("[Get UPLIST Data Error] Version Error & New Error")
                false
            } else {
                initUpListData(newUrl)
            }
        } else if (resultUpList.startsWith("[-0]")) {
            Log.error("[UPLIST Info] ${resultUpList.removePrefix("[-0]")}")
        } else if (resultUpList.startsWith("[-5]")) {
            Log.error(resultUpList)
        }

        val json = Json(resultUpList)

        serverID = Base64.decodeString(json.getString("id"))
        addData = Base64.decodeString(json.getString("add"))
        openData = Base64.decodeString(json.getString("open"))
        updateData = Base64.decodeString(json.getString("update"))
        removeData = Base64.decodeString(json.getString("remove"))
        return true
    }

    private fun add(log: StrCons, port: String = "") {
        if (!upServerList) {
            if (initUpListData()) {
                this.port = port.ifBlank { Data.config.port.toString() }
                Threads.newThreadCore { upServerList = true; uplistFlag = 1;uplist(Log::clog) }
            }
        } else {
            log("Already on the list")
        }
    }

    private fun uplist(print: (String)->Unit) {
        var addData0 = addData.replace("{RW-HPS.RW.VERSION}", versionGame)
        addData0 = addData0.replace("{RW-HPS.RW.VERSION.INT}", versionGameInt.toString())
        addData0 = addData0.replace("{RW-HPS.RW.IS.VERSION}", versionBeta.toString())
        addData0 = addData0.replace("{RW-HPS.RW.IS.PASSWD}", Data.configServer.passwd.isNotBlank().toString())
        addData0 = addData0.replace("{RW-HPS.S.NAME}", cutting(Data.config.serverName, 15))
        addData0 = addData0.replace("{RW-HPS.S.PRIVATE.IP}", privateIp)
        addData0 = addData0.replace("{RW-HPS.S.PORT}", port)


        addData0 = addData0.replace("{RW-HPS.RW.MAP.NAME}", getMapName)
        addData0 = addData0.replace("{RW-HPS.PLAYER.SIZE}", serverPlayerSize.toString())

        addData0 = addData0.replace("{RW-HPS.PLAYER.SIZE.MAX}", Data.configServer.maxPlayer.toString())


        Log.debug(addData0)

        val addGs1 = Data.core.rwHttp.doPost("http://gs1.corrodinggames.com/masterserver/1.4/interface", addData0).contains(serverID)
        val addGs4 = Data.core.rwHttp.doPost("http://gs4.corrodinggames.net/masterserver/1.4/interface", addData0).contains(serverID)
        if (addGs1 || addGs4) {
            if (addGs1 && addGs4) {
                print(Data.i18NBundle.getinput("err.yesList"))
            } else {
                print(Data.i18NBundle.getinput("err.ynList"))
            }
        } else {
            print(Data.i18NBundle.getinput("err.noList"))
        }

        val openData0 = openData.replace("{RW-HPS.S.PORT}", port)

        val checkPortGs1 = Data.core.rwHttp.doPost("http://gs1.corrodinggames.com/masterserver/1.4/interface", openData0)
            .contains("true")
        val checkPortGs4 = Data.core.rwHttp.doPost("http://gs4.corrodinggames.net/masterserver/1.4/interface", openData0)
            .contains("true")
        if (checkPortGs1 || checkPortGs4) {
            print(Data.i18NBundle.getinput("err.yesOpen"))
        } else {
            print(Data.i18NBundle.getinput("err.noOpen"))
        }

        Threads.newTimedTask(CallTimeTask.CustomUpServerListTask, 50, 50, TimeUnit.SECONDS) { update() }
    }

    private fun update() {
        var updateData0 = updateData.replace("{RW-HPS.RW.IS.PASSWD}", Data.configServer.passwd.isNotBlank().toString())
        updateData0 = updateData0.replace("{RW-HPS.S.NAME}", cutting(Data.config.serverName, 15))
        updateData0 = updateData0.replace("{RW-HPS.S.PRIVATE.IP}", privateIp)
        updateData0 = updateData0.replace("{RW-HPS.S.PORT}", port)


        updateData0 = updateData0.replace("{RW-HPS.RW.MAP.NAME}", getMapName)
        updateData0 = updateData0.replace("{RW-HPS.S.STATUS}", if (isRelay || HeadlessModuleManage.hps.room.isStartGame) "ingame" else "battleroom")
        updateData0 = updateData0.replace("{RW-HPS.PLAYER.SIZE}", serverPlayerSize.toString())


        updateData0 = updateData0.replace("{RW-HPS.PLAYER.SIZE.MAX}", Data.configServer.maxPlayer.toString())


        Data.core.rwHttp.doPost("http://gs1.corrodinggames.com/masterserver/1.4/interface", updateData0)
        Data.core.rwHttp.doPost("http://gs4.corrodinggames.net/masterserver/1.4/interface", updateData0)
    }

    private fun remove(log: StrCons) {
        if (upServerList) {
            if (Threads.closeTimeTask(CallTimeTask.CustomUpServerListTask) {
                    Data.core.rwHttp.doPost("http://gs1.corrodinggames.com/masterserver/1.4/interface", removeData)
                    Data.core.rwHttp.doPost("http://gs4.corrodinggames.net/masterserver/1.4/interface", removeData)
                }) {
                upServerList = false
                uplistFlag = 0
                log("Deleted UPLIST")
                return
            }
            log("Delete failed, unable to stop thread")
        } else {
            log("Not uploaded No deletion is required")
        }
    }

    private val isRelay get() = (NetStaticData.RwHps.netType == IRwHps.NetType.RelayProtocol || NetStaticData.RwHps.netType == IRwHps.NetType.RelayMulticastProtocol)

    private val getMapName get() = Data.config.subtitle.ifEmptyResult({ if (isRelay) "RELAY" else HeadlessModuleManage.hps.room.maps.mapName }) { cutting(it, 15) }

    private val serverPlayerSize get() = AtomicInteger().apply {
        if (isRelay) {
            NetStaticData.netService.eachAllFind( { it.netType == NetService.Companion.NetTypeEnum.HeadlessNet }) { addAndGet(it.getConnectSize()) }
        } else {
            addAndGet(HeadlessModuleManage.hps.room.playerManage.playerGroup.size)
        }
    } .get()

    /**
     * Inject multiple languages into the server
     * @author Dr (dr@der.kim)
     */
    private class AddLang(val plugin: Plugin) {
        init {
            help()
        }

        private fun help() {
            loadCN(
                    "uplist.help", """       
        [uplist add] 服务器上传到列表 显示配置文件端口
        [uplist add (port)] 服务器上传到列表 服务器运行配置文件端口 显示自定义端口
        [uplist update] 立刻更新列表服务器信息
        [uplist remove] 取消服务器上传列表
        [uplist help] 获取帮助
        """.trimIndent()
            )
            loadEN(
                    "uplist.help", """
        [uplist add] Server upload to list Show profile port
        [uplist add (port)] Server upload to list Server running profile port Display custom port
        [uplist update] Update list server information immediately
        [uplist remove] Cancel server upload list
        [uplist help] Get Help
        """.trimIndent()
            )
            loadHK(
                    "uplist.help", """        
        [uplist add] 服务器上传到列表 显示配置文件端口
        [uplist add (port)] 服务器上传到列表 服务器运行配置文件端口 显示自定义端口
        [uplist update] 立刻更新列表服务器信息
        [uplist remove] 取消服务器上传列表
        [uplist help] 获取帮助
        """.trimIndent()
            )
            loadRU(
                    "uplist.help", """
        [uplist add] Загрузка сервера в список Показать порт профиля
        [uplist add (port)] Загрузка сервера в список Порт запущенного профиля сервера Показать пользовательские порты
        [uplist update] Немедленное обновление информации сервера списка
        [uplist remove] Отмена загрузки сервера в список
        [uplist help] Получить помощь
        """.trimIndent()
            )
        }

        private fun loadCN(k: String, v: String) {
            plugin.loadLang("CN", k, v)
        }

        private fun loadEN(k: String, v: String) {
            plugin.loadLang("EN", k, v)
        }

        private fun loadHK(k: String, v: String) {
            plugin.loadLang("HK", k, v)
        }

        private fun loadRU(k: String, v: String) {
            plugin.loadLang("RU", k, v)
        }
    }
}
// 给岁月以文明，而不是给文明以岁月