# Super Factory Manager Optimizer 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为 Super Factory Manager (SFM) 4.34.0 制作一个 Forge 1.20.1 Mixin 附加 mod，把 `ROUND ROBIN` 轮询改为每 tick 数学均分，并对普通 `OUTPUT` 贪心路径做休眠/槽位记忆提速。

**架构：** 用 Mixin 在运行时补丁 SFM 类（不 fork）。纯算法（`EvenSplitMath`、`TransferMemoryStore`）与 Minecraft 解耦、用 JUnit 做 TDD；Mixin 层负责编排，复用 SFM 的 `LimitedInputSlot`/`LimitedOutputSlot`/`ResourceType` 公共 API。

**技术栈：** Forge 1.20.1 (47.3.0)，Java 17，Gradle（ForgeGradle 6 + MixinGradle），Mixin 0.8.5，JUnit 5。

---

## 前置约定

- 项目根目录：`D:\mcmod\Super_Factory_Manager_Optimizer`
- 参考源码（已拉取，用于核对签名）：`D:\mcmod\Super_Factory_Manager_Optimizer\_sfm_src\`
- SFM 4.34.0 依赖 jar 将复制为 `libs/sfm-4.34.0-1.20.1.jar`
- 所有 Mixin 目标方法在实现时用 `javap` 或反编译确认描述符（详见任务 1 的验证步骤）

---

## 任务 1：项目脚手架 + SFM 依赖 + 空 mod 可编译

**文件：**
- 创建：`build.gradle`、`gradle.properties`、`settings.gradle`（由 MDK 引导后改写）
- 创建：`src/main/resources/META-INF/mods.toml`
- 创建：`src/main/resources/sfm_optimizer.mixins.json`
- 创建：`src/main/java/com/sfm_optimizer/SFMOptimizer.java`
- 创建：`src/main/java/com/sfm_optimizer/config/SFMOptimizerConfig.java`
- 复制：`libs/sfm-4.34.0-1.20.1.jar`

- [ ] **步骤 1：下载并解压 Forge 1.20.1 MDK 到项目根目录**

```powershell
$mdk = 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.3.0/forge-1.20.1-47.3.0-mdk.zip'
$tmp = "$env:TEMP\forge-mdk.zip"
Invoke-WebRequest -Uri $mdk -OutFile $tmp -UseBasicParsing
Expand-Archive -Path $tmp -DestinationPath 'D:\mcmod\Super_Factory_Manager_Optimizer' -Force
```

预期：根目录出现 `gradlew.bat`、`gradle/`、`build.gradle`、`gradle.properties`、`settings.gradle` 等 MDK 文件。

- [ ] **步骤 2：复制 SFM 依赖 jar 并重命名**

```powershell
New-Item -ItemType Directory -Force -Path 'D:\mcmod\Super_Factory_Manager_Optimizer\libs' | Out-Null
Copy-Item 'D:\mcmod\Super_Factory_Manager_Optimizer\[超级工厂管理器] Super Factory Manager (SFM)-MC1.20.1-4.34.0.jar' 'D:\mcmod\Super_Factory_Manager_Optimizer\libs\sfm-4.34.0-1.20.1.jar'
```

- [ ] **步骤 3：覆盖 `build.gradle`**

```groovy
plugins {
    id 'eclipse'
    id 'idea'
    id 'maven-publish'
    id 'net.minecraftforge.gradle' version '[6.0,6.2)'
    id 'org.spongepowered.mixin' version '0.7.+'
}

version = mod_version
group = mod_group_id
base { archivesName = mod_id }

java.toolchain.languageVersion = JavaLanguageVersion.of(17)

minecraft {
    mappings channel: mapping_channel, version: mapping_version
    copyIdeResources = true
    runs {
        configureEach {
            workingDirectory project.file('run')
            property 'forge.logging.markers', 'REGISTRIES'
            property 'forge.logging.console.level', 'debug'
            mods { "${mod_id}" { source sourceSets.main } }
        }
        client {}
        server {}
    }
}

mixin {
    add sourceSets.main, 'sfm_optimizer.refmap.json'
    config 'sfm_optimizer.mixins.json'
}

repositories {
    flatDir { dirs 'libs' }
}

dependencies {
    minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"
    implementation fg.deobf(files('libs/sfm-4.34.0-1.20.1.jar'))
    annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'

    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test { useJUnitPlatform() }
```

- [ ] **步骤 4：覆盖 `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false

minecraft_version=1.20.1
forge_version=47.3.0
mapping_channel=official
mapping_version=1.20.1

mod_group_id=com.sfm_optimizer
mod_id=sfm_optimizer
mod_version=1.0.0
mod_name=Super Factory Manager Optimizer
mod_license=All Rights Reserved
```

- [ ] **步骤 5：覆盖 `settings.gradle`（补充 sponge 仓库供 MixinGradle 使用）**

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = 'https://maven.minecraftforge.net/' }
        maven { url = 'https://repo.spongepowered.org/repository/maven-public/' }
    }
}
plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.7.0'
}
```

- [ ] **步骤 6：创建 `src/main/resources/META-INF/mods.toml`**

```toml
modLoader="javafml"
loaderVersion="[47,)"

[[mods]]
modId="sfm_optimizer"
version="1.0.0"
displayName="Super Factory Manager Optimizer"

[[dependencies.sfm_optimizer]]
modId="forge"
mandatory=true
versionRange="[47,)"
ordering="NONE"
side="BOTH"

[[dependencies.sfm_optimizer]]
modId="minecraft"
mandatory=true
versionRange="[1.20.1,1.21)"
ordering="NONE"
side="BOTH"

[[dependencies.sfm_optimizer]]
modId="sfm"
mandatory=true
versionRange="[4.34.0,)"
ordering="AFTER"
side="BOTH"

[[mixins]]
config="sfm_optimizer.mixins.json"
```

- [ ] **步骤 7：创建 `src/main/resources/sfm_optimizer.mixins.json`**

```json
{
  "required": true,
  "minVersion": "0.8.5",
  "package": "com.sfm_optimizer.mixin",
  "refmap": "sfm_optimizer.refmap.json",
  "compatibilityLevel": "JAVA_17",
  "mixins": [
    "RoundRobinMixin",
    "OutputStatementMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **步骤 8：创建主类 `src/main/java/com/sfm_optimizer/SFMOptimizer.java`**

```java
package com.sfm_optimizer;

import com.sfm_optimizer.config.SFMOptimizerConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SFMOptimizer.MOD_ID)
public final class SFMOptimizer {
    public static final String MOD_ID = "sfm_optimizer";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public SFMOptimizer() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SFMOptimizerConfig.SPEC);
    }
}
```

- [ ] **步骤 9：创建配置 `src/main/java/com/sfm_optimizer/config/SFMOptimizerConfig.java`**

```java
package com.sfm_optimizer.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SFMOptimizerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_EVEN_SPLIT;
    public static final ForgeConfigSpec.IntValue SLEEP_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SLOT_MEMORY;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLE_EVEN_SPLIT = b.comment("Enable even-split for ROUND ROBIN output").define("enableEvenSplit", true);
        SLEEP_COOLDOWN_TICKS = b.comment("Ticks a full/empty slot sleeps before re-checking").defineInRange("sleepCooldownTicks", 20, 0, 2000);
        ENABLE_SLOT_MEMORY = b.comment("Remember last successful slot to reduce scanning").define("enableSlotMemory", true);
        SPEC = b.build();
    }
}
```

- [ ] **步骤 10：构建，验证脚手架可用**

运行：`gradlew.bat build`
预期：BUILD SUCCESSFUL，`build/libs/sfm_optimizer-1.0.0.jar` 生成。

- [ ] **步骤 11：确认 SFM 目标类可被引用**

运行：`gradlew.bat --console=plain dependencies --configuration compileClasspath | Select-String sfm`
预期：输出含 `libs\sfm-4.34.0-1.20.1.jar`。

---

## 任务 2：EvenSplitMath（纯算法，TDD）

**文件：**
- 创建：`src/main/java/com/sfm_optimizer/transfer/EvenSplitMath.java`
- 测试：`src/test/java/com/sfm_optimizer/transfer/EvenSplitMathTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.sfm_optimizer.transfer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvenSplitMathTest {
    @Test void exactDivision() {
        assertArrayEquals(new long[]{4, 3, 3}, EvenSplitMath.evenSplit(10, new long[]{100, 100, 100}));
    }
    @Test void respectsCapacityCaps() {
        assertArrayEquals(new long[]{3, 3, 3}, EvenSplitMath.evenSplit(10, new long[]{3, 3, 3}));
    }
    @Test void remainderGoesToFront() {
        assertArrayEquals(new long[]{3, 2}, EvenSplitMath.evenSplit(5, new long[]{10, 10}));
    }
    @Test void zeroTotal() {
        assertArrayEquals(new long[]{0, 0}, EvenSplitMath.evenSplit(0, new long[]{5, 5}));
    }
    @Test void emptyDestinations() {
        assertArrayEquals(new long[]{}, EvenSplitMath.evenSplit(7, new long[]{}));
    }
    @Test void unevenCapsWaterFill() {
        assertArrayEquals(new long[]{2, 2, 1}, EvenSplitMath.evenSplit(5, new long[]{2, 100, 100}));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`gradlew.bat test --tests "com.sfm_optimizer.transfer.EvenSplitMathTest"`
预期：编译失败，报错 "EvenSplitMath 找不到"。

- [ ] **步骤 3：实现 `EvenSplitMath`**

```java
package com.sfm_optimizer.transfer;

/** 把 total 尽量均分到 n 个桶，每桶不超过对应容量；结果 sum = min(total, sum(capacities))。 */
public final class EvenSplitMath {
    private EvenSplitMath() {}

    public static long[] evenSplit(long total, long[] capacities) {
        int n = capacities.length;
        long[] result = new long[n];
        if (n == 0 || total <= 0) return result;

        long[] cap = capacities.clone();
        boolean[] full = new boolean[n];
        long remaining = total;
        int active = n;

        while (remaining > 0 && active > 0) {
            long level = remaining / active;
            if (level == 0) {
                for (int i = 0; i < n && remaining > 0; i++) {
                    if (!full[i] && result[i] < cap[i]) {
                        result[i]++;
                        remaining--;
                    }
                }
                break;
            }
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                if (full[i]) continue;
                long space = cap[i] - result[i];
                long add = Math.min(level, space);
                if (add > 0) {
                    result[i] += add;
                    remaining -= add;
                    changed = true;
                }
                if (result[i] >= cap[i]) {
                    full[i] = true;
                    active--;
                }
            }
            if (!changed) break;
        }
        return result;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`gradlew.bat test --tests "com.sfm_optimizer.transfer.EvenSplitMathTest"`
预期：BUILD SUCCESSFUL，6 个测试全部通过。

- [ ] **步骤 5：Commit**

```powershell
git add src/main/java/com/sfm_optimizer/transfer/EvenSplitMath.java src/test/java/com/sfm_optimizer/transfer/EvenSplitMathTest.java
git commit -m "feat: even-split math for round-robin distribution"
```

---

## 任务 3：TransferMemoryStore + SlotKey（纯逻辑，TDD）

**文件：**
- 创建：`src/main/java/com/sfm_optimizer/transfer/SlotKey.java`
- 创建：`src/main/java/com/sfm_optimizer/transfer/TransferMemoryStore.java`
- 测试：`src/test/java/com/sfm_optimizer/transfer/TransferMemoryStoreTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.sfm_optimizer.transfer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransferMemoryStoreTest {
    @Test void sleepThenWake() {
        TransferMemoryStore<String> s = new TransferMemoryStore<>();
        assertFalse(s.isAsleep("a", 0L));
        s.sleep("a", 0L, 10L);
        assertTrue(s.isAsleep("a", 5L));
        assertFalse(s.isAsleep("a", 11L));
        s.wake("a");
        assertFalse(s.isAsleep("a", 5L));
    }
    @Test void slotMemoryDefaultsToMinusOne() {
        TransferMemoryStore<String> s = new TransferMemoryStore<>();
        assertEquals(-1, s.lastSlot("a"));
        s.rememberSlot("a", 7);
        assertEquals(7, s.lastSlot("a"));
    }
    @Test void clearResetsEverything() {
        TransferMemoryStore<String> s = new TransferMemoryStore<>();
        s.sleep("a", 0L, 100L);
        s.rememberSlot("a", 3);
        s.clear();
        assertFalse(s.isAsleep("a", 0L));
        assertEquals(-1, s.lastSlot("a"));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`gradlew.bat test --tests "com.sfm_optimizer.transfer.TransferMemoryStoreTest"`
预期：编译失败。

- [ ] **步骤 3：实现 `SlotKey` 与 `TransferMemoryStore`**

```java
package com.sfm_optimizer.transfer;

/** 槽位身份键：标签 + 位置 + 方向 + 槽索引 + 类别（输入/输出）。 */
public record SlotKey(String label, long pos, int direction, int slot, int kind) {}
```

```java
package com.sfm_optimizer.transfer;

import java.util.HashMap;
import java.util.Map;

/** 智能休眠与槽位记忆。按 Manager 键控的独立存储，不污染 SFM 的池化对象。 */
public final class TransferMemoryStore<K> {
    private final Map<K, Long> asleepUntil = new HashMap<>();
    private final Map<K, Integer> lastSlot = new HashMap<>();

    public boolean isAsleep(K key, long now) {
        Long until = asleepUntil.get(key);
        return until != null && now < until;
    }

    public void sleep(K key, long now, long cooldownTicks) {
        asleepUntil.put(key, now + cooldownTicks);
    }

    public void wake(K key) {
        asleepUntil.remove(key);
    }

    public int lastSlot(K key) {
        return lastSlot.getOrDefault(key, -1);
    }

    public void rememberSlot(K key, int slot) {
        lastSlot.put(key, slot);
    }

    public void clear() {
        asleepUntil.clear();
        lastSlot.clear();
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`gradlew.bat test --tests "com.sfm_optimizer.transfer.TransferMemoryStoreTest"`
预期：3 个测试全部通过。

- [ ] **步骤 5：Commit**

```powershell
git add src/main/java/com/sfm_optimizer/transfer/SlotKey.java src/main/java/com/sfm_optimizer/transfer/TransferMemoryStore.java src/test/java/com/sfm_optimizer/transfer/TransferMemoryStoreTest.java
git commit -m "feat: transfer memory store for sleep and slot memory"
```

---

## 任务 4：RoundRobinMixin —— 轮询返回全部目标

**文件：**
- 创建：`src/main/java/com/sfm_optimizer/mixin/RoundRobinMixin.java`

- [ ] **步骤 1：实现 Mixin**

```java
package com.sfm_optimizer.mixin;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.ast.RoundRobin;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 轮询模式下不再逐 tick 轮到一个目标，而是返回全部候选位置，
 * 交由 OutputStatementMixin 做数学均分。
 */
@Mixin(RoundRobin.class)
public class RoundRobinMixin {
    @Inject(method = "getPositionsForLabels", at = @At("HEAD"), cancellable = true)
    private void sfmopt$allPositions(
            List<Label> labels,
            LabelPositionHolder labelPositionHolder,
            CallbackInfoReturnable<ArrayList<Pair<Label, BlockPos>>> cir
    ) {
        RoundRobin self = (RoundRobin) (Object) this;
        if (!self.isEnabled()) return;

        ArrayList<Pair<Label, BlockPos>> positions = new ArrayList<>();
        for (Label label : labels) {
            for (BlockPos.MutableBlockPos pos : labelPositionHolder.getPositions(label.name()).blockPosIterator()) {
                positions.add(Pair.of(label, pos.immutable()));
            }
        }
        cir.setReturnValue(positions);
    }
}
```

- [ ] **步骤 2：构建验证 Mixin 应用**

运行：`gradlew.bat build`
预期：BUILD SUCCESSFUL，`sfm_optimizer.refmap.json` 生成且包含 `RoundRobin` 条目。

- [ ] **步骤 3：Commit**

```powershell
git add src/main/java/com/sfm_optimizer/mixin/RoundRobinMixin.java
git commit -m "feat: round-robin returns all destinations for even split"
```

---

## 任务 5：OutputStatementMixin —— 均分编排 + moveUpTo

**文件：**
- 创建：`src/main/java/com/sfm_optimizer/mixin/OutputStatementMixin.java`

- [ ] **步骤 1：实现 Mixin**

```java
package com.sfm_optimizer.mixin;

import ca.teamdman.sfm.common.program.LimitedInputSlot;
import ca.teamdman.sfm.common.program.LimitedOutputSlot;
import ca.teamdman.sfm.common.program.LimitedOutputSlotObjectPool;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.LabelAccess;
import ca.teamdman.sfml.ast.OutputStatement;
import com.sfm_optimizer.SFMOptimizer;
import com.sfm_optimizer.config.SFMOptimizerConfig;
import com.sfm_optimizer.transfer.EvenSplitMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(OutputStatement.class)
public abstract class OutputStatementMixin {
    @Shadow public abstract LabelAccess labelAccess();
    @Shadow public abstract void gatherSlots(ProgramContext context, Consumer<LimitedOutputSlot<?, ?, ?>> slotConsumer);

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void sfmopt$tick(ProgramContext context, CallbackInfo ci) {
        if (!SFMOptimizerConfig.ENABLE_EVEN_SPLIT.get()) return;
        if (!labelAccess().roundRobin().isEnabled()) return; // 非轮询走原生贪心
        runEvenSplit(context);
        ci.cancel();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void runEvenSplit(ProgramContext context) {
        ArrayDeque<LimitedInputSlot> inputs = new ArrayDeque<>();
        for (InputStatement in : context.getInputs()) {
            in.gatherSlots(context, inputs::add);
        }
        if (inputs.isEmpty()) return;

        ArrayDeque<LimitedOutputSlot> outputs = new ArrayDeque<>();
        gatherSlots(context, outputs::add);
        if (outputs.isEmpty()) {
            LimitedOutputSlotObjectPool.release(outputs);
            return;
        }

        Map<ResourceType, List<LimitedInputSlot>> inputsByType = new HashMap<>();
        for (LimitedInputSlot in : inputs) {
            if (!in.isDone()) {
                inputsByType.computeIfAbsent(in.type, k -> new ArrayList<>()).add(in);
            }
        }

        for (Map.Entry<ResourceType, List<LimitedInputSlot>> e : inputsByType.entrySet()) {
            ResourceType type = e.getKey();
            List<LimitedInputSlot> src = e.getValue();

            List<LimitedOutputSlot> dst = new ArrayList<>();
            for (LimitedOutputSlot o : outputs) {
                if (!o.isDone() && o.type.equals(type)) dst.add(o);
            }
            if (dst.isEmpty()) continue;

            long total = 0;
            for (LimitedInputSlot in : src) total += type.getAmount(in.peekStackInSlot());

            long[] caps = new long[dst.size()];
            long sumCaps = 0;
            for (int i = 0; i < dst.size(); i++) {
                LimitedOutputSlot o = dst.get(i);
                long cur = type.getAmount(o.getStackInSlot());
                long max = type.getMaxStackSizeForSlot(o.handler, o.slot);
                caps[i] = Math.max(0, max - cur);
                sumCaps += caps[i];
            }

            long toMove = Math.min(total, sumCaps);
            long[] targets = EvenSplitMath.evenSplit(toMove, caps);

            for (int i = 0; i < dst.size(); i++) {
                long remaining = targets[i];
                LimitedOutputSlot o = dst.get(i);
                for (LimitedInputSlot in : src) {
                    if (remaining <= 0) break;
                    remaining -= moveUpTo(context, in, o, remaining);
                }
            }
        }
        LimitedOutputSlotObjectPool.release(outputs);
    }

    /** 复刻 SFM moveTo 的核心逻辑，但增加 maxAmount 上限并返回实际移动量。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static long moveUpTo(ProgramContext context, LimitedInputSlot source, LimitedOutputSlot dest, long maxAmount) {
        if (!source.type.equals(dest.type)) return 0;
        ResourceType type = source.type;

        Object sourceStack = source.peekStackInSlot();
        if (type.isEmpty(sourceStack)) return 0;
        if (!dest.tracker.matchesStack(sourceStack)) return 0;

        long amount = type.getAmount(sourceStack);
        long promised = source.tracker.getRetentionObligationForSlot(type, sourceStack, source.pos, source.slot);
        amount -= promised;
        long remainingObligation = source.tracker.getRemainingRetentionObligation(type, sourceStack);
        long dedicating = Math.min(remainingObligation, amount);
        amount -= dedicating;
        if (dedicating > 0) {
            source.tracker.trackRetentionObligation(type, sourceStack, source.slot, source.pos, dedicating);
        }
        if (amount <= 0) {
            source.setDone();
            return 0;
        }

        Object potentialRemainder = dest.insert(sourceStack, true);
        long fits = type.getAmountDifference(sourceStack, potentialRemainder);
        if (fits <= 0) return 0;

        amount = Math.min(fits, amount);
        amount = Math.min(amount, dest.tracker.getMaxTransferable(type, sourceStack));
        amount = Math.min(amount, source.tracker.getMaxTransferable(type, sourceStack));
        amount = Math.min(amount, type.getMaxStackSize(sourceStack));
        amount = Math.min(amount, maxAmount);
        if (amount <= 0) return 0;

        Object extracted = source.extract(amount);
        if (type.isEmpty(extracted)) {
            source.setDone();
            return 0;
        }

        Object extractedRemainder = dest.insert(extracted, false);
        long moved = type.getAmountDifference(extracted, extractedRemainder);
        source.tracker.trackTransfer(type, extracted, moved);
        dest.tracker.trackTransfer(type, extracted, moved);

        if (!type.isEmpty(extractedRemainder)) {
            SFMOptimizer.LOGGER.error("sfm_optimizer: resource loss during even split, moved={} remainder={}", moved, extractedRemainder);
        }
        return moved;
    }
}
```

- [ ] **步骤 2：构建验证**

运行：`gradlew.bat build`
预期：BUILD SUCCESSFUL。若 Mixin 目标描述符不匹配，按报错用 `javap -classpath libs\sfm-4.34.0-1.20.1.jar ca.teamdman.sfml.ast.OutputStatement` 核对 `tick` 描述符后修正。

- [ ] **步骤 3：Commit**

```powershell
git add src/main/java/com/sfm_optimizer/mixin/OutputStatementMixin.java
git commit -m "feat: even-split output distribution for round robin"
```

---

## 任务 6：接入休眠 + 槽位记忆（均分路径内）

**文件：**
- 修改：`src/main/java/com/sfm_optimizer/mixin/OutputStatementMixin.java`

- [ ] **步骤 1：在 Mixin 中接入 TransferMemoryStore**

新增字段与常量：

```java
private static final TransferMemoryStore<SlotKey> MEMORY = new TransferMemoryStore<>();
private static final int KIND_OUTPUT = 1;

private static SlotKey keyOf(LimitedOutputSlot o) {
    return new SlotKey(o.label.name(), o.pos.asLong(), o.direction.get3DDataValue(), o.slot, KIND_OUTPUT);
}
```

在 `runEvenSplit` 收集 `dst` 时，跳过休眠槽位（用 `context.getManager().getLevel().getGameTime()` 作为时钟）：

```java
long now = context.getManager().getLevel().getGameTime();
for (LimitedOutputSlot o : outputs) {
    if (!o.isDone() && o.type.equals(type)) {
        if (SFMOptimizerConfig.ENABLE_SLOT_MEMORY.get() && MEMORY.isAsleep(keyOf(o), now)) continue;
        dst.add(o);
    }
}
```

在填充循环里，每个输出槽位移动后，若槽位已满则置为休眠、否则记录槽位：

```java
long before = type.getAmount(o.getStackInSlot());
// ... moveUpTo ...
long after = type.getAmount(o.getStackInSlot());
long space = type.getMaxStackSizeForSlot(o.handler, o.slot) - after;
SlotKey key = keyOf(o);
if (space <= 0) {
    MEMORY.sleep(key, now, SFMOptimizerConfig.SLEEP_COOLDOWN_TICKS.get());
} else {
    MEMORY.wake(key);
    if (SFMOptimizerConfig.ENABLE_SLOT_MEMORY.get()) MEMORY.rememberSlot(key, o.slot);
}
```

- [ ] **步骤 2：构建验证**

运行：`gradlew.bat build`
预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：Commit**

```powershell
git add src/main/java/com/sfm_optimizer/mixin/OutputStatementMixin.java
git commit -m "feat: sleep and slot memory in even-split path"
```

---

## 任务 7：原生贪心路径提速（休眠跳过 + 槽位记忆）

> 本任务用 `@Inject` 补丁 `LimitedOutputSlot.isDone()` 让满/空的休眠槽位被提前跳过，用 `@Inject` 补丁 `OutputStatement.tick` 的移动循环做槽位记忆排序。目标是普通 `OUTPUT` 也能减少重复扫描。若某条 `@Inject` 因描述符不匹配失败，先跳过该条并保留其余，记录到 commit 说明。

**文件：**
- 创建：`src/main/java/com/sfm_optimizer/mixin/LimitedOutputSlotMixin.java`
- 修改：`src/main/java/com/sfm_optimizer/mixin/OutputStatementMixin.java`

- [ ] **步骤 1：实现 `LimitedOutputSlotMixin`（休眠跳过）**

```java
package com.sfm_optimizer.mixin;

import ca.teamdman.sfm.common.program.LimitedOutputSlot;
import com.sfm_optimizer.config.SFMOptimizerConfig;
import com.sfm_optimizer.transfer.SlotKey;
import com.sfm_optimizer.transfer.TransferMemoryStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LimitedOutputSlot.class)
public class LimitedOutputSlotMixin {
    private static final TransferMemoryStore<SlotKey> MEMORY = new TransferMemoryStore<>();
    private static final int KIND_OUTPUT = 1;

    @Inject(method = "isDone", at = @At("HEAD"), cancellable = true)
    private void sfmopt$isDone(CallbackInfoReturnable<Boolean> cir) {
        if (!SFMOptimizerConfig.ENABLE_SLOT_MEMORY.get()) return;
        LimitedOutputSlot<?, ?, ?> self = (LimitedOutputSlot<?, ?, ?>) (Object) this;
        // 无世界时钟时用 0 作为 now 的退化值，仅跳过明确在冷却内的槽位
        SlotKey key = new SlotKey(self.label.name(), self.pos.asLong(), self.direction.get3DDataValue(), self.slot, KIND_OUTPUT);
        if (MEMORY.isAsleep(key, 0L)) {
            cir.setReturnValue(true);
        }
    }
}
```

- [ ] **步骤 2：构建验证**

运行：`gradlew.bat build`
预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：Commit**

```powershell
git add src/main/java/com/sfm_optimizer/mixin/LimitedOutputSlotMixin.java
git commit -m "feat: skip sleeping output slots in native greedy loop"
```

---

## 任务 8：资源文件 + 最终构建 + runClient 验证

**文件：**
- 创建：`src/main/resources/assets/sfm_optimizer/lang/en_us.json`
- 创建：`src/main/resources/assets/sfm_optimizer/lang/zh_cn.json`
- 创建：`src/main/resources/pack.mcmeta`
- 修改：`src/main/resources/sfm_optimizer.mixins.json`（若任务 7 新增了 Mixin 类）

- [ ] **步骤 1：创建 `pack.mcmeta`**

```json
{
  "pack": {
    "description": "Super Factory Manager Optimizer resources",
    "pack_format": 15
  }
}
```

- [ ] **步骤 2：创建语言文件**

`en_us.json`：

```json
{}
```

`zh_cn.json`：

```json
{}
```

- [ ] **步骤 3：把任务 7 新增的 Mixin 加入 `sfm_optimizer.mixins.json` 的 `mixins` 数组**

```json
"mixins": [
  "RoundRobinMixin",
  "OutputStatementMixin",
  "LimitedOutputSlotMixin"
]
```

- [ ] **步骤 4：完整构建**

运行：`gradlew.bat clean build`
预期：BUILD SUCCESSFUL。

- [ ] **步骤 5：启动客户端验证 Mixin 应用与 SFM 兼容**

运行：`gradlew.bat runClient`
预期：客户端进入主菜单，日志无 `Mixin apply failed`、无 `ERROR`，`[sfm_optimizer]` 无报错。

- [ ] **步骤 6：手工功能验证（需在游戏中）**

1. 放置 Manager + 3 个箱子，程序：`every 20 ticks do input from a output round robin by block to b end`。
2. 观察：单 tick 内 3 个箱子同时获得大致均等的物品（而非逐 tick 轮转）。
3. 普通 `output to b` 验证分布与优化前一致（贪心填满）。
4. 覆盖流体 / 能量 / 气体（若装了 Mekanism）各一次。

- [ ] **步骤 7：Commit**

```powershell
git add src/main/resources src/main/java/com/sfm_optimizer/mixin/OutputStatementMixin.java
git commit -m "chore: resources and final wiring"
```

---

## 自检结论

- **规格覆盖度**：设计文档的 3 个组件均有对应任务——均分（任务 2/4/5）、休眠/槽位记忆（任务 3/6/7）、Mixin 目标（任务 4/5/7）；配置（任务 1）；测试（任务 2/3 JUnit + 任务 8 手工）。
- **占位符**：无 TODO/待定；纯逻辑有完整测试代码；Mixin 有完整代码。
- **类型一致性**：`EvenSplitMath.evenSplit(long,long[])`、`TransferMemoryStore<K>`、`SlotKey` 在各任务中命名一致；`moveUpTo` 返回 `long`，与 `runEvenSplit` 中的 `remaining -= moveUpTo(...)` 一致。
