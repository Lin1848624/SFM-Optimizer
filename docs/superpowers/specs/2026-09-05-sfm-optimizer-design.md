# Super Factory Manager Optimizer — 设计规格说明

- 日期：2026-09-05
- 目标：为 Super Factory Manager (SFM) 4.34.0 制作一个运行时优化附加 mod
- 加载器 / 版本：Forge 1.20.1，Java 17
- modId：`sfm_optimizer`
- displayName：Super Factory Manager Optimizer
- 依赖：`forge`、`minecraft [1.20.1,1.21)`、`sfm [4.34.0,)`

## 1. 目标与非目标

### 目标

1. 将 SFM 的 `ROUND ROBIN`（轮询）分发从「每 tick 轮到一个目标」改为「每 tick 数学均分到所有目标」，单次完成物品 / 流体 / 能量 / 气体的分配。
2. 保持非轮询 `OUTPUT` 的贪心语义不变，仅优化其运行效率。
3. 增加「智能休眠」与「槽位记忆」，减少无效重复轮询与槽位扫描。

### 非目标（本期不做）

- 不引入 Pipez Optimizer 的「自定义抽取频率」与「高级升级物品」（SFM 由 Manager 按 tick 驱动，无此对应概念）。
- 不改动 SFML 语法、解析器、GUI、网络协议、物品 / 方块注册。
- 不 fork SFM 源码，不改 SFM 的公开行为（除已确认的轮询均分语义）。

## 2. 背景：SFM 传输架构与性能热点

SFM 不是「管道逐格轮询」，而是 Manager 每 tick 执行编译后的程序：

1. `InputStatement.tick()` 收集输入语句并缓存输入槽（`limitedInputSlotsCache`）。
2. `OutputStatement.tick()` 每 tick **重新**扫描所有标签方块的所有槽位（`gatherSlots`，无缓存），随后执行双层循环：

```text
for (inputSlot : inputSlots)
    for (outputSlot : outputSlots)
        moveTo(context, inputSlot, outputSlot)
```

3. `moveTo` 对每对槽位执行：`insert(simulate=true)` 估算 → 计算限额 → `extract` → `insert(simulate=false)`。

性能热点：

- **O(输入 × 输出)** 的双层轮询 + 每对一次模拟插入。
- `gatherSlots` 每 tick 无缓存地遍历所有槽位。
- `RoundRobin.next()` 为 `nextIndex++ % length`，`BY_BLOCK` 每次调用都重建候选列表。

## 3. 集成方式

采用 Mixin 在运行时补丁 SFM 类（与 Pipez Optimizer 补丁 Pipez 完全同构），不 fork、不改 SFM 源码。

- Mixin 配置：`sfm_optimizer.mixins.json`，`compatibilityLevel = JAVA_17`。
- 使用 `refmap` 将本 mod 中引用的 Minecraft / Forge 名称在构建时重映射，与 SFM 发布 jar 的 SRG 名称对齐。
- Mixin 插件：`SFMOptimizerMixinPlugin`，用于按 SFM 版本 / 特性做条件应用（防御性）。

## 4. 包结构

```text
com.sfm_optimizer/
  SFMOptimizer.java               # 主 mod 类、注册配置与 Mixin
  config/
    SFMOptimizerConfig.java       # Forge 配置
  transfer/
    TransferMemoryStore.java      # 休眠状态 + 槽位记忆（按 Manager 键控）
    EvenSplitPlanner.java         # 数学均分算法
    GreedyFastPlanner.java        # 优化后的贪心算法
  mixin/
    OutputStatementMixin.java
    RoundRobinMixin.java
    InputStatementMixin.java
    LimitedInputSlotMixin.java
    LimitedOutputSlotMixin.java
    SFMOptimizerMixinPlugin.java
```

## 5. 组件设计

### 5.1 核心传输：单次数学均分（`EvenSplitPlanner` + `OutputStatementMixin`）

将 `OutputStatement.tick()` 的双层贪心轮询替换为按「资源类型 × 资源 key」分组的一次性均分：

1. 收集匹配的输入槽与输出槽。
2. 对每个 `(resourceType, resourceKey)` 组：
   - `totalAvailable = Σ min(槽内数量 - 保留义务, 输入 tracker 的 maxTransferable)`
   - `totalCapacity = Σ (输出槽当前可容纳量)`，同时受输出 tracker 的 `maxTransferable` 与 `matchesStack` 约束
   - `toMove = clamp(min(totalAvailable, totalCapacity), 0, 语句数量上限)`
   - 均分：`base = toMove / n`，`rem = toMove % n`；按输出槽收集的确定顺序，前 `rem` 个目标多分 1（余数摊给顺序靠前的目标，保证结果可复现）
   - 对物品类需按每个目标槽的槽位上限做「水平填充」（water-filling），避免单槽超限；流体 / 能量 / 气体等连续 / 标量资源直接均分
3. 批量执行：按计划从源槽 `extract`、向目标槽 `insert`，把模拟插入次数从 O(输入×输出) 降到 O(输入+输出)。

**触发条件**：仅当该 `OUTPUT` 语句处于 `ROUND ROBIN` 模式时走均分路径。

### 5.2 Round-robin → 均分（`RoundRobinMixin`）

- 去掉 `nextIndex++ % length` 的逐 tick 轮转。
- 使 `getPositionsForLabels` 在 `BY_BLOCK` / `BY_LABEL` 模式下返回**全部**候选位置（供 `OutputStatement` 均分），并为语句标记「均分模式」。
- 通过 `LabelAccess` / `RoundRobin` 的 accessor 让 `OutputStatement` 读取到该模式标志。

### 5.3 非轮询贪心：仅提速（`GreedyFastPlanner`）

保持「贪心填满第一个目标 / 槽位」的结果分布不变，只做：

- 输出槽位发现结果跨 tick 缓存，网络结构变化时失效。
- 槽位记忆：从上次成功的槽位继续，避免从槽 0 全扫。
- 已知源 / 目标容量关系时，跳过可省略的模拟插入。

### 5.4 智能休眠（`TransferMemoryStore`）

- 状态按 Manager 键控，键 = `(managerPos, label, pos, direction, slot, kind)`。
- 当某目标满或某源为空（本次传输量为 0）时，标记该槽位「休眠」，在可配置的冷却 tick 内跳过。
- 冷却到期或网络结构变化时唤醒。

### 5.5 槽位记忆（`TransferMemoryStore`）

- 记录每个 `(capability, label, pos, direction)` 最近成功的输入 / 输出槽位索引。
- `gatherSlots` 从记忆位置开始迭代（环形回绕），减少大容器从头扫描。

**状态存放原则**：休眠与记忆状态放在独立的 `TransferMemoryStore`，不往 SFM 的池化对象（`LimitedInputSlot` / `LimitedOutputSlot`）里加字段，避免对象池复用导致状态错乱。若确需槽位级字段，必须在 `init` 中重置。

## 6. Mixin 目标清单

| Mixin                    | 目标方法                                   | 动作                                                         |
| ------------------------ | ------------------------------------------ | ------------------------------------------------------------ |
| `OutputStatementMixin`   | `tick(ProgramContext)`                     | `@Inject` HEAD、`cancellable`，替换轮询循环为均分 / 优化贪心 |
| `OutputStatementMixin`   | `gatherSlots(...)`                         | 输出槽缓存与槽位记忆                                         |
| `RoundRobinMixin`        | `getPositionsForLabels(...)` / `next(int)` | 返回全部候选、标记均分模式                                   |
| `InputStatementMixin`    | `gatherSlots(...)`                         | 输入槽缓存失效策略 + 休眠读取                                |
| `LimitedInputSlotMixin`  | `isDone()` / `extract()`                   | 槽位记忆辅助（可选）                                         |
| `LimitedOutputSlotMixin` | `isDone()` / `insert()`                    | 槽位记忆辅助（可选）                                         |

> 精确的方法描述符与字段 accessor 在实现计划阶段以 `sfm 4.34.0` 反编译 / 源码为准确定。

## 7. 配置项（`SFMOptimizerConfig`）

| 键                       | 默认   | 说明          |
| ------------------------ | ------ | ------------- |
| `enableEvenSplit`        | `true` | 启用轮询均分  |
| `sleepCooldownTicks`     | `20`   | 休眠冷却 tick |
| `enableSlotMemory`       | `true` | 启用槽位记忆  |
| `outputSlotCacheEnabled` | `true` | 输出槽位缓存  |

## 8. 错误处理与安全

- 复用 SFM `moveTo` 中已有的资源丢失检测逻辑；均分计划实际插入量与模拟不一致（目标方块「撒谎」）时，回退到安全的逐对 `moveTo` 或按 SFM 现有方式记录 `RESOURCE LOSS`。
- 关注整数溢出：均分与求和使用 `long`，与 SFM 对大量物品溢出的既有担忧保持一致。
- 所有 Mixin 用 `defaultRequire = 1`，若目标签名不匹配则构建 / 加载失败并显式报错，避免静默降级导致行为异常。

## 9. 测试与验证

1. `gradlew build` 通过编译，确认 refmap 与 Mixin 目标解析成功。
2. `runClient` / `runServer` + SFM 4.34.0 实机加载，确认无 Mixin 应用失败日志。
3. 用测试 SFML 程序验证：
   - `ROUND ROBIN BY BLOCK` 到 N 个箱子：单 tick 均分，结果均匀。
   - 普通 `OUTPUT` 到 N 个箱子：分布与优化前一致（贪心）。
   - 物品 / 流体 / 能量 / 气体（含 Mekanism 气体）各覆盖一次。
4. 用大容器 + 多目标构造压力场景，对比优化前后每 tick 耗时（`/tick` 或 profiler）。

## 10. 语义变更确认（已与用户确认）

- `ROUND ROBIN`：改为**每 tick 均分到所有目标**（消除逐 tick 轮转卡顿）。
- 非轮询 `OUTPUT`：**保持贪心语义**，仅提速。
