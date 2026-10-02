Super Factory Manager Optimizer (sfm_optimizer)
===============================================

一个 Super Factory Manager (SFM) 的运行时优化附加模组：
  - ROUND ROBIN：从「每 tick 轮到一个目标」改为「每 tick 数学均分到所有目标」
  - 智能休眠：已满/无空间的输出槽在冷却期内跳过，减少无效重复扫描
  - 槽位记忆：记录最近成功的槽位，减少大容器从 0 号槽算起的扫描开销

用法：把 jar 放进 mods 文件夹即可；配置为服务端配置，服务端单边安装即可生效。

分支说明
--------
- `main`                  Forge 1.20.1（SFM 4.34.0，Java 17，ForgeGradle 6）
- `codex/neoforge-1.21.1` NeoForge 1.21.1（SFM 4.34.0，Java 21，ModDevGradle 2.0.148）

构建环境
--------
- JDK 21（gradle.properties 的 org.gradle.java.home 已指向 D:/App/zulu21.52.15-ca-jdk21.0.12-win_x64）
- Gradle 8.14（wrapper 内置，走腾讯云镜像）
- NeoForge 21.1.252（见 gradle.properties 的 neo_version）

依赖准备（重要）
----------------
仓库不提交二进制依赖。构建前需将 SFM 的 NeoForge 1.21.1 版 jar 放入 libs/：

    libs/sfm-4.34.0-1.21.1.jar

下载：Modrinth → Super Factory Manager → 版本 4.34.0（MC 1.21.1 / NeoForge），
文件名形如 "Super Factory Manager (SFM)-MC1.21.1-4.34.0.jar"，重命名后放入 libs/。

构建与测试
----------
    gradlew.bat build     # 编译 + 单元测试 + 打包（产物 build/libs/sfm_optimizer-1.1.0.jar）
    gradlew.bat test      # 仅运行纯算法单元测试（9 个用例）

开发运行（需图形环境；按项目约定，实机验证由用户本人执行）
----------------------------------------------------------
    gradlew.bat runClient
    gradlew.bat runServer

实机验证清单见：D:\Codex\Outputs\SFMOptimizer-NeoForge-1.21.1-移植说明与测试清单.md

服务端配置（游戏目录 config/sfm_optimizer-server.toml）
------------------------------------------------------
- enableEvenSplit      默认 true   ROUND ROBIN 数学均分开关
- sleepCooldownTicks   默认 20     槽位休眠冷却（tick）
- enableSlotMemory     默认 true   槽位记忆开关

许可
----
GPLv3，见 LICENSE.txt。
