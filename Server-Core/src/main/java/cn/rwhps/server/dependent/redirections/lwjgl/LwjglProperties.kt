/*
 * Copyright 2020-2022 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package cn.rwhps.server.dependent.redirections.lwjgl

interface LwjglProperties {
    companion object {
        const val DISPLAY_UPDATE = "cn.rwhps.lwjgl.update_sleep"
        const val AppGameContainer_UPDATE = "cn.rwhps.lwjgl.game_update_sleep"
        const val TEXTURE_SIZE = "cn.rwhps.lwjgl.texturesize"
        const val FULLSCREEN = "cn.rwhps.lwjgl.fullscreen"
        const val SCREEN_WIDTH = "cn.rwhps.lwjgl.screenwidth"
        const val SCREEN_HEIGHT = "cn.rwhps.lwjgl.screenheight"
        const val REFRESH_RATE = "cn.rwhps.lwjgl.refreshrate"
        const val BITS_PER_PIXEL = "cn.rwhps.lwjgl.bitsperpixel"
        const val JNI_VERSION = "cn.rwhps.lwjgl.nativejniversion"
    }
}