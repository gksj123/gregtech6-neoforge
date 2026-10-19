# DEVIATIONS.md — 无法避免的可观察差异

本文件只记录 **平台限制或尚未生成的数据** 导致的、与原版存在可观察差异的行为。
原版疑似 bug / 可优化点仍按 `REVIEW.md` 审核流程处理。

| # | 位置 | 原版行为 | 现行行为 | 原因 | 可恢复 |
|---|------|----------|----------|------|--------|
| D1 | GT 温度伤害 | `UT.Entities.applyTemperatureDamage` 使用 GT 的 heat/frost damage source、防护判定和数据 | 伤害曲线和 320K/260K 阈值按原版实现，伤害源映射为 `onFire`/`freeze` | 1.21.1 的伤害类型与 GT 独立伤害源不能一一对应 | 可恢复：注册 GT 伤害类型并移植防护判定 |
| D2 | 模具模型分层 | 25 个 cell 与外围框架均由动态渲染生成并按相邻关系裁剪 | 外围框架使用静态模型，cell 几何与侧面裁剪使用动态渲染 | NeoForge block model 与 1.7.10 ISBRH 的分层方式不同，几何和可观察外形一致 | 可恢复：改为完整动态模型 |
| D3 | 蒸汽管道 | 原版使用通用 GT6 流体管道 | 使用实际 Steam 流体和指向接入面的 capability，由专用 `Steam Pipe` 方块承载 | 通用 GT6 流体管网尚未整体移植 | 可恢复：接入 `MultiTileEntityPipeFluid` 后替换专用管块 |
| D4 | 蒸汽设备外观 | 锅炉气压计、引擎活塞和涡轮正反转使用原版分层纹理与动画 overlay | 当前使用功能模型占位，运行状态与机械方向已生效，但视觉 overlay 未逐面复现 | 原版设备纹理需要单独的动态材质/overlay 渲染层 | 可恢复：导入对应 colored/overlay 纹理并补 BlockEntityRenderer |
| D5 | 流体 NBT | 1.7.10 使用 GT `FluidTankGT` 的旧流体 NBT 格式 | 保留 `gt.tank.N` 键，但内部使用 NeoForge `FluidStack` 数据组件格式 | 1.21.1 流体注册表和 NBT 表示不同 | 可恢复：增加 1.7.10 存档转换器 |

新增差异必须在此登记；未登记的差异视为违规。
