/*
 * Copyright 2020-2021 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package com.github.dr.rwserver.plugin

import com.github.dr.rwserver.data.global.Data
import com.github.dr.rwserver.data.json.Json
import com.github.dr.rwserver.dependent.LibraryManager
import com.github.dr.rwserver.struct.Seq
import com.github.dr.rwserver.util.IsUtil
import com.github.dr.rwserver.util.file.FileUtil
import com.github.dr.rwserver.util.log.Log
import com.github.dr.rwserver.util.log.Log.error
import com.github.dr.rwserver.util.log.Log.warn
import com.github.dr.rwserver.util.zip.zip.ZipDecoder
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.util.*

/**
 * @author Dr
 */
class PluginsLoad {
    private fun loadJar(jarFileList: Seq<File>): Seq<PluginLoadData> {
        val data = Seq<PluginLoadData>()
        val dataName = Seq<String>()
        val dataImport = Seq<PluginImportData>()
        for (file in jarFileList) {
            try {
                val imp = ZipDecoder(file).getZipNameInputStream("plugin.json")
                if (IsUtil.isBlank(imp)) {
                    error("Invalid jar file", file.name)
                    continue
                }
                val imports = ZipDecoder(file).getZipNameInputStream("imports.json")
                if (IsUtil.notIsBlank(imports)) {
                    val importsJson = Json(FileUtil.readFileString(imports!!)).getArraySeqData("imports")
                    val lib = LibraryManager(Data.Plugin_Lib_Path)
                    importsJson.each {
                        lib.importLib(it.getData("group"), it.getData("name"), it.getData("version"))
                    }
                    lib.loadToClassLoader()
                }

                val json = Json(FileUtil.readFileString(imp!!))
                if (!GetVersion(Data.SERVER_CORE_VERSION).getIfVersion(json.getData("supportedVersions"))) {
                    warn("Plugin版本不兼容 Plugin名字为: ", json.getData("name"))
                    continue
                }
                if (IsUtil.isBlank(json.getDataNull("import"))) {
                    val mainPlugin = loadClass(file, json.getData("main"))
                    data.add(PluginLoadData(
                        json.getData("name"),
                        json.getData("author"),
                        json.getData("description"),
                        json.getData("version"),
                        mainPlugin
                    ))
                    dataName.add(json.getData("name"))
                } else {
                    dataImport.add(PluginImportData(json, file))
                }
            } catch (e: Exception) {
                error("Failed to load", e)
            }
        }
        var i = 0
        val count = dataImport.size()
        while (i < count) {
            dataImport.each { e: PluginImportData ->
                if (dataName.contains(e.pluginData.getDataNull("import"))) {
                    try {
                        val mainPlugin = loadClass(e.file, e.pluginData.getData("main"))
                        data.add(PluginLoadData(
                            e.pluginData.getData("name"),
                            e.pluginData.getData("author"),
                            e.pluginData.getData("description"),
                            e.pluginData.getData("version"),
                            mainPlugin
                        ))
                        dataName.add(e.pluginData.getData("name"))
                        dataImport.remove(e)
                    } catch (err: Exception) {
                        error("Failed to load", e)
                    }
                }
            }
            i++
        }
        return data
    }

    /**
     * 根据包名加载实例
     * @param file Jar文件
     * @param main 主类的包名
     * @return Plugin实例
     * @throws InstantiationException 如果声明基础构造函数的类表示抽象类
     * @throws InvocationTargetException 如果基础构造函数引发异常
     * @throws ExceptionInInitializerError 如果此方法引发的初始化失败
     */
    @Throws(Exception::class)
    private fun loadClass(file: File, main: String): Plugin {
        val classLoader = URLClassLoader(arrayOf(file.toURI().toURL()), ClassLoader.getSystemClassLoader())
        Log.info(file.name)
        val classMain = classLoader.loadClass(main)
        //mainPlugin.classLoader = classLoader
        return classMain.getDeclaredConstructor().newInstance() as Plugin
    }

    /**
     * Plugin导入的数据
     * @property pluginData Json示例
     * @property file Jar的文件
     * @constructor PluginImportData
     */
    private class PluginImportData(@JvmField val pluginData: Json, @JvmField val file: File)

    class PluginLoadData(
        @JvmField val name: String,
        @JvmField val author: String,
        @JvmField val description: String,
        @JvmField val version: String,
        @JvmField val main: Plugin
        ) {
        init {
            main.pluginData.setFileUtil(FileUtil.getFolder(Data.Plugin_Plugins_Path).toFolder(this.name).toFile(this.name + ".bin"))
            main.pluginData.read()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            return if (other == null || javaClass != other.javaClass) {
                false
            } else name == (other as PluginLoadData).name
        }

        override fun hashCode(): Int {
            return Objects.hash(name)
        }
    }

    companion object {
        /**
         * 返回文件夹内指定后辍的文件并包装为PluginLoadData
         * @param f FileUtil
         * @return Seq<PluginLoadData>
         */
        @JvmStatic
        fun resultPluginData(f: FileUtil): Seq<PluginLoadData> {
            val jarFileList = Seq<File>()
            val list = f.fileList
            for (file in list) {
                if (file.name.endsWith("jar")) {
                    jarFileList.add(file)
                }
            }
            return PluginsLoad().loadJar(jarFileList)
        }
    }
}