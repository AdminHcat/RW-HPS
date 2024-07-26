# 配置服务器
RW-HPS提供了**3**个配置文件  
[Config.json](#configjson)为共用  
[ServerConfig.json](#serverconfigjson)为Server模式配置  
[ConfigRelay.json](#configrelayjson)为Relay模式配置  
注意: RW-HPS**不支持**热重载配置,每次修改配置请先关闭服务器!

## Config.json

### defStartCommand
#### 服务器默认启动命令(str)
Server模式为**start**  
Relay模式为**startrelay**/**startrelaytest**
> startrelay 直接转发消耗更多带宽，效果与使用 VPN 转发相同  
> startrelaytest 使用多播, 减少宽带消耗

默认配置为**start**

### log
#### log记录等级(str)
决定log记录的级别,通常级别越高输出内容越多  
> 输入错的则默认 ALL  
> OFF FATAL ERROR WARN INFO DEBUG TRACE ALL  

默认配置为**WARN**

### cmdTitle
#### 终端标题(str)
RW-HPS在启动完成时会更改终端标题  
在Windows上,使用native方法  
在Linux上,使用vt100控制符  
若未配置,则显示
> [RW-HPS] Port: 5123, Run Server: ${NetStaticData.ServerNetType.name}

默认**未配置**

### followBetaVersion
#### 使用测试版本更新服务器(bool)
RW-HPS支持在服务器控制台处更新服务器,只需输入`tryupdate`即可  
如果启用,则使用测试版本更新服务器,您会提前体验到新功能~~也会体验到新bug~~  
如果禁用,则使用稳定版更新服务器  
默认配置为**禁用**

### port
#### 服务器端口(int)
指定服务器所使用的端口  
默认配置为**5123**

### serverName
#### 服务器名(str)
服务器在列表处的服务器名  
默认配置为**RW-HPS**

### subtitle
#### 地图名(str)
服务器在列表处的地图名  
未配置则为地图名  
默认**未配置**

### autoUpList
#### 自动上列表(bool)
可以让服务器启动时自动上列表(相当于`uplist add`)  
默认配置为**禁用**

### ipCheckMultiLanguageSupport
#### 基于IP判断多语言支持(bool)
可以通过玩家IP判断玩家的国家从而对每个玩家都显示它的本国语言(前提是翻译文件有那个语言)  
默认配置为**启用**

### singleUserRelay
#### Relay单用户模式(bool)
类似与Relay模式,但是只有一个房间  
默认配置为**禁用**

### singleUserRelayMod
#### Relay单用户模式的mod支持(bool)
为Relay单用户模式启用mod支持  
默认配置为**禁用**

### webToken
#### HttpApi令牌(str)
此令牌在[HttpApi](../api/HttpAPI.md)处使用  
默认配置为**随机生成**

### webHOST
#### HTTP主机限制(str)
限制HTTP的Host,防止从IP访问  
默认**未配置**

### webPort
#### HTTP服务器端口(int)
指定HTTP服务器所使用的端口,`0`为禁用HTTP服务器  
注意: 现已**不支持**端口复用,请勿与游戏服务器设置同一端口  
默认配置为**禁用**

### ssl
#### 启用HTTPS(bool)
**不建议**使用SSL  
启用HTTP服务器的SSL(即HTTPS)
默认配置为**禁用**

### sslPasswd
#### SSL证书的密码(str)
警告: **明文**密码  
要配置SSL,你需要在和jar同级的地方放置`ssl.jks`(大小写敏感)  
然后在此配置项输入JKS证书密码  
默认**未配置**

### runPid
#### 服务器运行PID(int)
你不需要知道这是干什么的,也不需要动它  
> 如果你想知道的话:  
> 提供当前 JVM 的 pid, 便于使用其他程序关闭 JVM


## ServerConfig.json

### enterAd
#### 进入消息(str)
玩家进入时服务器发送给玩家的消息  
默认**未配置**

### startAd
#### 开始消息(str)
游戏开始时服务器发送给玩家的消息  
默认**未配置**

### maxPlayerAd
#### 满员消息(str)
进入服务器时,服务器已满员时拒绝玩家进入的消息  
默认**未配置**

### startPlayerAd
#### 已开始消息(str)
进入服务器时,服务器已开始游戏时拒绝玩家进入的消息  
默认**未配置**

### passwd
#### 服务器密码(str)
给服务器设置密码,所有玩家进入服务器时需要输入密码才能进入  
未配置则不设置密码
默认**未配置**

### maxPlayer
#### 最大玩家数(int)
设置服务器所能容纳的最大玩家数  
默认配置为**10**

### maxGameIngTime
#### 最长游戏时间(int)
设置游戏运行的时间,若达到设定的时间,则关闭此房间(不关闭服务器)  
配置为`-1`时则无限制  
单位: 秒(sec)  
默认配置为**7200**

### maxOnlyAIGameIngTime
#### 没有其他玩家的最长游戏时间(int)
当服务器只有AI时(也就是只有一群AI),若到达设定的时间,则关闭此房间(不关闭服务器)  
配置为`-1`时则无限制  
单位: 秒(sec)  
默认配置为**3600**

### startMinPlayerSize
#### 最小开始人数(int)
服务器开始游戏所需的最小人数  
配置为`-1`则无限制  
默认配置为**无限制**

### autoStartMinPlayerSize
#### 自动开始人数(int)
当服务器人数大于或等于(≥)此配置项时,服务器将自动开始游戏  
配置为`-1`则禁用此功能  
默认配置为**4**

### maxMessageLen
#### 最大发言长度(int)
服务器所允许的最大发言长度  
默认配置为**40**

### maxUnit
#### 最大单位数量(int)
服务器所允许的最大单位数量  
默认配置为**200**

### defIncome
#### 默认倍率(float)
服务器默认资金倍率(支持小数)  
默认配置为**1.0**

### turnStoneIntoGold
#### 点石成金(bool)
字面意思  
建筑单位*不需要*时间并且*不需要*资金  
默认配置为**禁用**

### oneAdmin
#### 将第一个进入的玩家设置为管理员(bool)
服务器将第一个进入服务器的玩家设置为管理员  
默认配置为**启用**

### saveRePlayFile
#### 保存RePlay文件(bool)
服务器允许客户端保存RePlay文件用于回放游戏过程  
默认配置为**启用**


## ConfigRelay.json

### mainID
#### 前置ID(str)
Relay模式下的房间前置ID  
未配置的话默认只有数字  
默认**未配置**

### mainServer
#### 是否是 RELAY 主节点(bool)
> 主节点将可以分配分节点分ID, 例如
> RCN 分配 RA, 那么如果是 true, RA就会被跳转, 如果是 false, 就会解析
默认配置为**启用**

### upList
#### TODO(bool)
服务器是否支持 uplist
默认配置为**禁用**

### mainServerIP
#### TODO(str)

### mainServerPort
#### TODO(int)


> 什么?你问我为什么是`TODO`?  
> 其实我也不知道为什么是`TODO`呀  
> ~~也许是Dr太懒了吧~~
