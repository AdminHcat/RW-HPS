/*
 * Copyright 2020-2024 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.plugin.beta.bind

import net.rwhps.server.data.bean.AbstractBeanConfig
import net.rwhps.server.util.file.FileUtils
import net.rwhps.server.util.inline.toGson

/**
 * 简单的绑定服务
 *
 * @date 2024/7/2 上午11:12
 * @author Dr (dr@der.kim)
 */
data class BaseBindData(
    val use: Boolean = false,
    val force: Boolean = false,
    val apiPort: Int = 0,
    val apiConsole:String = "",
): AbstractBeanConfig(
        this::class.java, ""
) {
    companion object {
        fun get(fileUtils: FileUtils): BaseBindData {
            val config: BaseBindData = BaseBindData::class.java.toGson(fileUtils.readFileStringData())
            config.bindFile(fileUtils)
            config.readProperty()
            return config
        }
    }
}