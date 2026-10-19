# AGENTS.md — GT6 NeoForge 移植工作规范

本文件是任何 AI/人类在本仓库工作的 **强制规范**。核心目标：把 GregTech 6
（Minecraft 1.7.10）**完整、无损、无改变地**复刻到 NeoForge 1.21.1。

## 第一条（最高原则）：完整复刻

> 移植的一切内容以 1.7.10 原版源码为唯一事实来源。**不允许**对原版内容做
> 任何调整、删减、重排或"改进"：数值、ID、概率、时序、tick 顺序、配方、
> NBT 键名、能量换算、结构尺寸、掉落表、死亡消息文本——全部逐字照搬。

唯一允许的改动类别是 **平台 API 适配**：1.7.10/Forge 已不存在的机制
（如 `GameRegistry`、metadata 方块状态、ITexture/IIcon、FML 网络通道、
ASM Coremod）用 NeoForge 1.21.1 的等价机制重写，且重写后的 **可观察行为
必须与原版一致**。

- 拿不准原版意图 → 停下来读源码，禁止凭"常识"或对其他 GT 版本的了解编造。
- 原版明确停用的死代码（如 `gregapi/old` 大部分、`gregtech/old`）可以不移植，
  但必须在移植台账中标注"原版未使用，跳过"。

## 第二条：疑似 bug 与可优化点 —— 标注、上报、审核制

移植中发现原版代码存在疑似 bug，或发现明显可优化之处时，**默认动作仍然是
原样移植**，但必须同时完成三件事：

1. **代码内标注**：在移植侧对应位置写醒目标注，格式：
   `// GT6-REVIEW[BUG]: <原版位置> <问题一句话>` 或
   `// GT6-REVIEW[OPT]: <原版位置> <可优化点一句话>`。
2. **登记审核清单**：在 `REVIEW.md` 追加条目（原版位置、问题描述、
   建议处理、影响评估），状态记为"待审核"。
3. **当场告知用户**：在工作汇报中明确列出本次新增的所有 REVIEW 条目。

**任何对原版行为的修改（包括修 bug、优化数值、调整逻辑）都必须先经用户
明确批准。** 未获批准前一律按原版行为移植。用户批准后，更新 `REVIEW.md`
条目状态为"已批准"再实施；用户拒绝则保留原版行为并标记"已拒绝"。
条目最终状态只有四种：`待审核 / 已批准 / 已拒绝 / 已按原版保留`。

## 事实来源（Source of Truth）

按优先级：

1. **原版源码**：`../gregtech6-legacy/src/main/java/`（两个独立 git 仓库，
   绝不允许修改 legacy 仓库的源码文件）。
2. **原版功能索引**：`../gregtech6-legacy/FEATURE_INDEX/`。
   先读 `FEATURE_INDEX/INDEX.md` 总目录，再按包进入 `modules/` 下对应模块
   文档定位源码文件与行号。索引只负责指路，**最终以源码原文为准**。
3. 本仓库已生成的机器可读清单：`migration/*.json`（由
   `tools/inventory_gt6.py` 等脚本从原版源码提取，见 `SOURCE_OF_TRUTH.md`）。

## 强制工作流（每移植一个功能单元）

1. **定位**：在 `FEATURE_INDEX/INDEX.md` 找到功能所属模块，打开模块文档，
   再打开它引用的原版源码文件，通读相关类/方法全文。
2. **摘约**：在动手前明确该功能的不可变契约：注册名/ID、数值、公式、
   NBT 键、tick 顺序、同步行为。模块文档的"关键机制/移植要点"两节就是
   为此准备的。
3. **实现**：写 NeoForge 侧代码。每个实现类/注册点的 Javadoc 或邻近注释中
   必须标注原版出处，格式：`// GT6 1.7.10: gregtech/tileentity/energy/MultiTileEntityMotor.java:123`。
4. **登记**：在 `PORTING_STATUS.md`（或对应台账文件）更新该单元的状态与
   原版引用。
5. **验证**：数值与行为逐条对照原版源码自检；构建通过；
   可交互的功能必须在游戏内实测（工具链见 `tools/run_client.py`）。
   - 可交互功能**优先且默认**使用已配置的 `mcpfabric` MCP 工具做黑盒交互测试。
   - 编译、单元测试或静态检查不能代替交互测试；没有实测时必须明确报告未验证。

## MCP 交互测试（强制流程）

本仓库的开发环境已配置 Codex MCP 服务 `mcpfabric`，它通过 NeoForge 侧
结构化 API 读取游戏状态并执行操作，不依赖视觉识别。MCP 服务、
MCPFabric 兼容 jar 和维护说明位于：

- `~/.codex/mcpfabric/mcp-server/dist/index.js`
- `run-client/mods/mcpfabric-neoforge-0.8.0+1.21.1-gt6dev.jar`
- `~/.codex/mcpfabric/GT6DEV.md`

### 启动与连接

1. 先启动隔离开发客户端：

   ```sh
   cd gregtech6-neoforge
   python3 tools/run_client.py
   ```

   需要直接进入开发世界时可使用：

   ```sh
   GT6_QUICK_PLAY='新的世界' python3 tools/run_client.py
   ```

2. 游戏启动后，在**新的 Codex 会话**中使用 `mcpfabric`。MCP Server 由
   Codex 自动启动；不要重复手动启动第二个 MCP Server。

3. 第一次调用必须是 `get_status`。确认：

   - `loader` 为 `neoforge`；
   - `side` 为 `client` 或 `dedicated_server`；
   - 进入世界后 `integratedServer` 为 `true`（客户端模式）；
   - 客户端模式至少有 1 名在线玩家。

4. 然后调用 `agent_brief` 和 `get_self`，记录世界、维度、坐标、生命值、
   游戏模式和当前界面。必要时用 `get_screen` 检查是否卡在暂停菜单或加载界面。

### 操作与观测原则

- **优先使用结构化状态判断结果**：`get_block`、`get_blocks_region`、
  `get_inventory`、`query_entities`、`poll_events`、`open_container` 及其回读等。
- Codex 侧已禁用 `screenshot`。不要通过截图识别或让模型猜测画面；MCP 交互
  测试应以坐标、方块状态、背包、实体、聊天、事件和方块回读为依据。
- `enableVision` 仍用于结构化 `perception`/地图观察，不代表必须使用图像模型。
- 若本功能依赖自定义 GUI，本项目默认 `enableUiControl=false`。需要测试
  GT6 自定义界面时，必须在隔离存档中取得用户同意，再临时开启并记录风险；
  GT6 的机器交互应优先通过无 GUI 的世界交互和原版源码定义的输入路径验证。

### 标准测试步骤

1. **准备可复现场景**：使用测试世界和固定坐标。记录维度、坐标、初始背包、
   方块状态和机器配置。
2. **搭建场景**：用原版命令或 `set_block`/`fill_blocks` 只修改明确声明的
   测试体积；用 `give_item`、`select_hotbar_slot` 准备工具和材料。
3. **执行真实交互**：用 `look_at`、`navigate_to`、`break_block`、`place_block`、
   `use_item`、`attack_entity`、`open_container` 等工具走实际交互路径。
   不得用命令或直接修改存档绕过被测功能来伪造通过结果。
4. **等待并回读**：按原版源码确认 tick/秒数，再读取方块、背包、容器、能量或
   实体状态。不能只看工具调用返回值。
5. **验证边界和失败路径**：至少覆盖工具错误、能量不足、错误端口模式、
   错误等级、无效结构、掉落/拾取和重启后的持久化中适用的项目。
6. **清理现场**：恢复游戏模式、天气和时间（如测试修改过），停止后台任务并
   关闭客户端。除非用户要求保留运行，测试结束后释放 `127.0.0.1:25599`。

推荐的最小调用顺序：

```text
get_status
agent_brief
get_self
get_block / get_blocks_region
look_at
use_item / place_block / break_block
poll_events
get_inventory / container.state
get_block（回读）
```

### GT6 交互测试重点

按功能适用的项目逐项验证：

- 方块注册、放置、破坏和工具等级限制；
- 机器朝向、端口模式、螺丝刀/扳手/软锤等工具行为；
- 多方块结构成型、失效、旋转和重建；
- RU/KU/EU/HU/蒸汽等能量或流体的输入、转换、损失和超额处理；
- 输入/输出总线的插入、抽取、满仓和错误物品；
- tick 顺序、加工时长、掉落和 NBT/Data Component 持久化；
- 客户端与服务端状态同步；条件允许时用专用服务器验证专用端路径。

测试结果汇报时至少写明：

```text
原版出处：
测试场景：
坐标/维度：
MCP 调用：
实际回读：
期望行为：
结论：通过 / 未通过 / 未验证
日志或复现命令：
```

### MCP 失败处理

- 工具不存在或连接失败：先确认游戏进程仍在运行、MCP 配置已加载，并检查
  `run-client/logs/latest.log`；不要直接把 MCP 失败当作功能失败。
- 工具返回超时：先用 `get_status`、`get_screen` 和 `poll_events` 判断游戏是否
  卡住；不要盲目重复破坏性操作。
- MCP 无法暴露目标交互时，补充窄范围、可验证的 MCP 工具或在测试报告中明确
  说明覆盖缺口；不得用“人工看过”替代结构化断言。
- `run-server` 已放置同一 MCP jar 和配置，但专用服务器路径目前尚未完成无头
  冒烟测试。使用前先按 `tools/run_server.py` 启动并单独验证；客户端和专用
  服务器不要同时占用 `25599` 端口。

### 安全边界

- 只在 `run-client`、`run-server` 等隔离开发实例中授权 MCP 的世界写入和命令。
- 禁止把 MCP HTTP 桥接暴露到局域网或公网；保持 `127.0.0.1` 和 bearer token。
- 禁止在测试中执行大范围 `fill`/`clear`、删除存档或修改用户主实例。
- 禁止打印、提交或复制 MCP token。配置文件权限应保持为仅用户可读。

## 平台映射约定（1.7.10 → 1.21.1 等价重写，不改变行为）

| 原版机制 | NeoForge 1.21.1 等价物 | 不可变的部分 |
|---|---|---|
| ItemStack metadata / 32767 子 ID | Data Components / 独立注册项 | ID 语义、NBT 键名与格式 |
| `ITexture`/`IIcon`/ISBRH | 模型 JSON + BakedModel/客户端扩展 | 纹理选择规则、材质着色 |
| FML 网络通道/IPacket | NeoForge Payload/Registrar | 包 ID 分配表、字段编码顺序 |
| ASM Coremod 字节码修改 | Mixin / NeoForge 事件 | 修改后的运行时行为（如矿车限速 4.0F） |
| `GameRegistry` 注册 | `DeferredRegister`/RegistryEvent | 注册名（registry name）字符串 |
| World/Chunk 1.7.10 API | 1.21.1 WorldGen/Feature 体系 | 生成算法、噪声参数、概率、坐标规则 |
| NBT (`NBTTagCompound`) | NBT + Data Components | 键名字符串与数值语义（存档兼容目标） |

## 偏差日志（DEVIATIONS.md）

任何 **无法避免** 与原版产生可观察差异的地方（平台限制、新版 MC 机制
冲突），必须追加一条记录：位置、原版行为、现行行为、原因、是否可恢复。
未记录的偏差视为违规。

注意区分：`DEVIATIONS.md` 记录 **被迫产生** 的差异；原版的疑似 bug 与
可优化点属于 **可选择是否修改** 的事项，走 `REVIEW.md` 审核流程
（见"第二条"），不经用户批准不得实施。

## 禁止事项速查

- 禁止修改 `../gregtech6-legacy/` 下的任何源码。
- 禁止凭记忆重写 GT6 逻辑；每个实现必须能指出对应原版文件。
- 禁止"顺手优化"原版数值与公式，包括明显可疑的——必须原样移植、
  标注 `GT6-REVIEW`、登记 `REVIEW.md` 并上报用户，获批前不得改动。
- 禁止引入原版没有的机制、物品、数值平衡改动。
- 禁止跳过 `PORTING_STATUS.md` 台账登记直接提交实现。

## 相关文档

- `README.md` — 项目现状与构建
- `AGENTS.md` — 本规范
- `REVIEW.md` — 疑似 bug / 可优化点审核清单（用户审批制）
- `SOURCE_OF_TRUTH.md` — 机器可读迁移清单的生成与口径
- `PORTING_STATUS.md` — 移植台账
- `GAMEPLAY_INTERACTIONS.md` — 交互行为审计
- `ASSET_SOURCES.md` — 资源文件来源
- `~/.codex/mcpfabric/GT6DEV.md` — MCP 开发环境维护说明
- `../gregtech6-legacy/FEATURE_INDEX/INDEX.md` — 原版功能索引总目录
