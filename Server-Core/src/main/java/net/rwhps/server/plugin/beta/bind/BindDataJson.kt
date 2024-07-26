package net.rwhps.server.plugin.beta.bind

/**
 *
 *
 * @date 2024/7/24 上午11:41
 * @author Dr (dr@der.kim)
 */
data class BindDataJson(
    val bindCode: String,
    val qq: String,
    var hex: String = "",
    var name: String = "",
    var ip: String = ""
)