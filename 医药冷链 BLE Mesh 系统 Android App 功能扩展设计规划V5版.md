**医药冷链 BLE Mesh 系统 Android App 功能扩展设计规划（V5 版）**

### 一、当前 App 现状评估
- **基础**：基于 [`https://github.com/zqtigger/Android-nRF-Mesh-Library`](https://github.com/zqtigger/Android-nRF-Mesh-Library)（Nordic nRF Mesh Library 的 fork），已完成**配网管理核心功能**：
  - PB-ADV / PB-GATT 批量 Provisioning（支持 eFuse UUID 自动识别）
  - 自动分配角色（Primary / Standby / Relay / Sensor）
  - 自动配置 Group Address 订阅与 Publish（0xC001~0xC005 + 区域分组）
  - 基础节点配置（TTL、Relay 开关、AppKey/NetKey）
- **优势**：已适配 ESP-BLE-MESH，批量操作友好。
- **不足**：当前主要停留在 **Provisioning + 静态配置** 阶段，缺少**运行期监控、审计、运维**功能，与“超低功耗医药审计系统”核心需求严重脱节。

**目标**：将 App 升级为**完整冷链运维 + 审计工具**，成为现场工程师、质控/审计人员的主要操作入口，同时与网关 HTTP Server（web/index.html）形成互补。

### 二、V5 架构适配的核心设计原则
1. **Sensor 极限省电优先**：App **绝不主动轮询 Sensor**，仅接收 Publish（Group 消息）或通过 Gateway 获取缓存数据。
2. **最终一致性 + 审计优先**：重点支持 Hash Chain 验证、批量签名验证、多 Gateway received_by 证据查看。
3. **多网关友好**：支持同时连接多个 Gateway（Primary/Standby 状态同步）。
4. **离线优先**：App 支持本地缓存 + 网关 HTTP 直连，即使 Mesh 不可达也能配置/查看历史。
5. **医药合规导向**：所有关键操作留痕，支持一键导出 ALCOA+ 审计报告。

### 三、推荐新增功能模块（优先级排序）

| 优先级 | 模块名称 | 核心功能 | 与 V5 架构契合点 | 预计开发难度 |
|--------|----------|----------|------------------|--------------|
| **P0** | **仪表盘 & 实时监控** | - 全局概览（在线 Sensor 数、告警数、网关状态）<br>- 温湿度实时/近实时列表 + 折线图<br>- 报警事件推送与列表 | 接收 Group 0xC001/C002 Publish；Gateway HTTP API 补充批量数据 | 中 |
| **P0** | **设备管理** | - 节点列表（按角色/区域过滤）<br>- Sensor 详情（电池%、RSSI、最后采样时间、缓存条数）<br>- Gateway 角色 & 心跳状态查看 | 支持 Primary/Standby 切换、手动选举 | 低 |
| **P1** | **告警中心** | - 本地/云端推送（≤120s 要求）<br>- 告警历史 + 确认流程<br>- 阈值动态配置（2°C/8°C/65%RH） | 报警模式高频上传支持；云端去重后同步 | 低 |
| **P1** | **网络健康诊断** | - Mesh 拓扑简化视图<br>- RSSI 热图 & 覆盖测试工具<br>- Friendship 状态 & Friend Queue 监控 | Relay 策略、Friendship 功耗验证 | 中 |
| **P1** | **审计与合规** | - Hash Chain 查看与验证<br>- 批量 ECDSA 签名验证<br>- 多 Gateway received_by 证据<br>- 一键导出 PDF/CSV 审计报告 | V5 审计重型化核心（ALCOA+） | 中高 |
| **P2** | **配置中心** | - 采样/上传间隔、报警恢复条件<br>- Time Authority 参数<br>- TTL/Retransmit 批量设置<br>- Provisioning 后自动配置增强 | 与 Phase 1-3 固件配置完全对齐 | 低 |
| **P2** | **OTA 管理** | - Sensor OTA（夜间窗口提醒 + 分片）<br>- Gateway OTA（HTTPS/Mesh Distributor）<br>- 进度监控 & 回滚 | Sensor 极低 OTA 频率要求 | 中 |
| **P3** | **报表与导出** | - 温度曲线/异常统计<br>- 历史数据批量导出<br>- 合规报告模板 | 医药审计核心输出 | 低 |
| **P3** | **用户与权限** | - 多用户登录（本地/云端）<br>- 角色权限（运维/审计/只读） | 企业级部署需求 | 中 |

**新增功能原则**：
- **不增加 Sensor 功耗**：全部通过 Group Publish 或 Gateway 缓存获取数据。
- **与网关 Web 互补**：App 专注移动端便捷操作，复杂配置仍可通过浏览器访问 Gateway HTTP。
- **支持离线**：App 本地 Room DB 缓存最近 7 天数据。

### 四、技术实现方案（基于现有 Library）

1. **核心依赖保持不变**：
   - `MeshManagerApi` / `MeshNetwork` / `Provisioner` / `ConfigurationServer` 等核心类继续使用。
   - 新增 **Vendor Model**（或扩展 Generic Model）接收传感器自定义数据包（V2 格式：packet_id、temp、humi、battery_pct、seq_num、hash 等）。

2. **数据接收方式**（推荐优先级）：
   - **首选**：GATT Proxy 模式订阅 Group 0xC001/C002/C005 → 实时接收 Publish。
   - **补充**：通过 Gateway HTTP Server（已有的 `/api` 接口）拉取批量缓存数据 + 网关状态。
   - **未来**：若网关增加 WebSocket，可实现真正的“云端推送”体验。

3. **架构分层建议**：
   - **UI Layer**：Jetpack Compose（推荐）或继续 XML + MVVM。
   - **Repository Layer**：`MeshRepository` + `GatewayHttpRepository` + `LocalCacheRepository`。
   - **ViewModel**：DashboardViewModel、NodeListViewModel、AuditViewModel 等。
   - **本地存储**：Room + DataStore（缓存 Mesh Network JSON + 历史数据）。
   - **推送**：Firebase FCM（或自建 MQTT） + 本地通知。

4. **关键新 API 调用**：
   - `meshManagerApi.sendConfigMessage()` → 批量设置 TTL/Relay/Time Sync。
   - `meshManagerApi.getProxy()` → 监听 Publish。
   - 新增 Vendor Model OpCode 处理传感器 V2 批量数据。

### 五、实施阶段规划（建议 4 周完成 MVP）

**Phase 1（Week 1）：基础框架 & P0 模块**
- 重构项目结构（MVVM + Hilt）
- 仪表盘 + 设备列表 + 实时监控（Group Publish 接收）
- 网关角色 & 心跳显示
- 本地缓存机制

**Phase 2（Week 2）：告警 & 配置**
- 告警中心 + 推送
- 配置中心（阈值、间隔、Time Authority）
- OTA 入口（UI + 进度）

**Phase 3（Week 3）：审计 & 诊断**
- 审计模块（Hash Chain 验证工具）
- 网络健康诊断
- 报表导出

**Phase 4（Week 4）：优化 & 测试**
- 多语言（中英）、深色模式、权限控制
- 压力测试（200+ 节点场景）
- 与 V5 固件联合验证（实测冷库环境）

### 六、风险与注意事项
- **功耗风险**：App 长时间打开 Proxy 模式会增加手机耗电 → 提供“后台监听”开关，默认仅前台接收。
- **兼容性**：确保与 V4/V5 配置迁移无缝（自动检测网关版本）。
- **安全**：App 操作需 AppKey 验证；敏感审计操作增加二次确认。
- **性能**：200+ 节点时列表使用分页 + LazyColumn。
- **测试重点**：冷库弱信号场景、断网恢复、多 Gateway 切换、Hash Chain 完整性验证。

### 七、下一步行动建议
1. **立即**：fork 当前 App 代码，建立 `feature/v5-enhancement` 分支。
2. **本周**：完成 Phase 1 原型（仪表盘 + 设备列表），与固件团队联调 Group Publish。
3. **同步**：在网关 HTTP Server 补充必要 API（`/api/nodes/status`、`/api/audit/verify` 等），App 与 Web 双向对齐。

这个规划完全围绕 **V5 “超低功耗医药审计系统”** 核心哲学设计，既充分利用了现有 nRF Mesh Library 的强大 Provisioning 能力，又填补了**现场运维 + 合规审计**的空白，能显著提升整个系统的可运维性和医药合规性。

如果需要，我可以立刻输出**具体模块的 UI 原型描述**、**关键代码修改点清单** 或 **新 Vendor Model 定义**，随时告诉我优先从哪个模块开始。