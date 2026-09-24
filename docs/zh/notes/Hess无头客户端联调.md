# Hess 无头客户端联调

独立 JVM 用 Hess（game-lib `-nodisplay`）当**游戏客户端**连本服。不要塞进正在跑房间的服务端进程：那个进程已经有 Hess，默认会 `startHeadlessServer`。

入口：`--hess-client [host:port]`（`HessClientMode.applyFromArgs`，在插件 `onEnable` 之前解析）。加载完 game-lib 后走 `LinkGameNet.newConnect`，跳过监听。聊天脚本按 `--chat` / `--chat-delay` / `--chat2` / `--chat2-delay` 发明文，可选 `--start`。

## 为什么要覆盖 game-lib

服端 ASM 把 `com.corrodinggames.rts.gameFramework.j.d` / `j.e`（收发线程）stub 掉了。客户端如果用这份缓存，TCP 能连上但发不出握手 160。客户端模式会从 `HESS_CLIENT_LIBS/game-lib.jar` 把这两类覆盖回去。`ad.b(ip, forceTcp=true)`，避免 RUDP。

## 前提

- Java 21、Docker
- `./gradlew :Server-All:jar :plugin:AllyRequest:jar`
- `data/gameData`（仓库已带）
- `Server-Core/libs/game-lib.jar`（原始游戏库，不进 git；与 RWHR 同源）
- `./docker/docker-build.sh` 得到镜像 `rwmhps-server:local`

无 TTY 时加 `-Drwhps.eula.accepted=true`，跳过 EULA 交互。脚本已带上。

## 单个客户端

```bash
./docker/scripts/hess-client.sh
# 环境变量：HESS_CLIENT_TARGET HESS_CLIENT_NAME HESS_CLIENT_CHAT HESS_CLIENT_CHAT_DELAY
#           HESS_CLIENT_CHAT2 HESS_CLIENT_CHAT2_DELAY HESS_CLIENT_START=1 HESS_CLIENT_TIMEOUT
```

## 结盟场景

隔离临时服（默认端口 25240）+ 两个客户端。开局约 10 秒倒计时后，bot1 发 `.jm 2`，bot2 发 `.y`。

```bash
./docker/scripts/hess-ally-test.sh
```

断言看**服务端** `docker logs`，不是客户端 141：

```text
[AllyRequest] 玩家 bot2 同意了 bot1 的结盟请求, 已加入其队伍
```

JUnit 不启动 Hess。`HessClientMode` / `HessClientConnect` 只测参数与 overlay 选择。真实进房、SYNC、雾战仍靠这条脚本或实机。
