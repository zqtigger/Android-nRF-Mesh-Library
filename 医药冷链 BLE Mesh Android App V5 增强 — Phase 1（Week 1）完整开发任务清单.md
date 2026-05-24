**医药冷链 BLE Mesh Android App V5 增强 — 立即输出**

以下内容**完全针对 V5 架构**（采样/联网解耦 + 批量上传 + Hash Chain + Time Authority + 多网关冗余 + 审计优先）设计，基于现有 `https://github.com/zqtigger/Android-nRF-Mesh-Library` 库。

### 一、具体模块的 UI 原型描述（重点 P0 & P1 模块）

#### 1. 首页仪表盘（DashboardFragment / 主页面）
- **顶部导航栏**：App 名称 + 当前 Primary Gateway 状态（绿色“在线” / 红色“离线” / 角色标签）+ 刷新按钮 + 多网关切换下拉
- **卡片区（3列 Grid）**：
  - 传感器概览卡片：`238/240 在线`、`2 低电量`、`平均续航预计 5.2 年`
  - 告警卡片（红色高亮）：`3 个活跃告警`（点击直达告警中心）
  - 网关健康卡片：`Primary: 在线（RSSI -45）`、`Standby: 热备`、`Relay 覆盖率 98%`
- **实时数据区**：横向可滑动卡片或列表（最近 10 条）
  - 每行：`SENSOR_A3F1` | `4.2°C / 58%RH` | `电池 92%` | `2分钟前` | 状态标签（正常/报警）
- **底部导航栏**（5 Tab）：仪表盘 | 设备 | 告警 | 审计 | 更多（配置/诊断）

#### 2. 设备管理（NodeListFragment + DeviceDetailScreen）
- **列表页**：
  - 顶部筛选栏：全部/Sensor/Gateway/Relay | 区域（0xC100~）| 在线/离线
  - RecyclerView：
    - 左侧图标 + 名称（SENSOR_XXXX / Primary_GW_XXXX）
    - 中间：`4.1°C  62%RH  电池92%  RSSI-52  缓存: 124条`
    - 右侧：状态标签（正常/报警/离线/低电）
- **详情页（点击进入）**：
  - 顶部卡片：节点基本信息 + 最后采样时间 + 缓存条数
  - 中间：温度/湿度 24h/7天折线图（ChartView）
  - 底部操作区：`配置阈值`、`触发 OTA`、`手动校时`、`查看审计链`、`强制上传缓存`

#### 3. 告警中心（AlarmCenterFragment）
- **Tab 切换**：未处理（红色）| 已确认 | 全部历史
- **列表项**：
  - `SENSOR_B7E2  超温  9.8°C（持续 47分钟）  2026-05-24 03:15`
  - 右侧：确认按钮 + “查看多网关证据”
- **详情页**：报警轨迹曲线 + 多 Gateway received_by 表格（gw_id + rssi）+ 一键导出单条审计记录

#### 4. 审计与合规（AuditFragment）
- **顶部**：时间范围选择器 + Sensor 过滤 + “一键全网验证”大按钮
- **主要内容**：
  - Hash Chain 完整性状态条（绿色“完整” / 红色“断链”）
  - 记录列表：时间 | Sensor | 温度 | 签名状态 | Hash 链验证结果
  - 多观察点证据表格（received_by）
- **右上角 FAB**：`导出报告` → PDF/CSV（ALCOA+ 合规模板，含时间戳、签名、验证结果）

#### 5. 网络健康诊断（NetworkDiagnosticsFragment，可选 P1）
- **拓扑简化视图**（可缩放节点图）：Gateway → Relay → Sensor 连线（颜色表示 RSSI）
- **信号热图列表**：按区域显示平均 RSSI 和丢包率
- **Friendship 监控卡片**：Friend Queue 使用率、LPN 数量、Poll Timeout
- **一键操作按钮**：`覆盖测试`（发送测试包，统计接收率）

### 二、关键代码修改点清单（按优先级排序）

| 优先级 | 文件/类路径（基于 nRF Mesh Library 典型结构） | 修改内容 |
|--------|---------------------------------------------|----------|
| P0 | `app/src/main/java/.../ui/MainActivity.kt` 或 `BottomNavigationActivity.kt` | 添加 BottomNav 5个 Tab，集成新 Fragment |
| P0 | 新建 `ui/dashboard/DashboardFragment.kt` + `DashboardViewModel.kt` | 仪表盘 UI + 数据聚合（Mesh Publish + Gateway HTTP） |
| P0 | `ui/nodes/NodeListFragment.kt` / `NodeListAdapter.kt` | 扩展显示 battery_pct、缓存条数、区域分组 |
| P0 | 新建 `repository/MeshDataRepository.kt` + `GatewayHttpRepository.kt` | 处理 Group 0xC001/C002 Publish + HTTP 批量拉取 |
| P1 | 新建 `ui/alarm/AlarmCenterFragment.kt` + `AlarmViewModel.kt` | 告警列表、推送处理、本地 Room 缓存 |
| P1 | `provisioning/ProvisioningActivity.kt` 或 `ProvisioningViewModel.kt` | Provisioning 完成后自动配置 Vendor Model + Group 订阅 + TTL |
| P1 | 新建 `models/ColdChainSensorModel.kt` | Vendor Model 完整实现（注册、发送、接收） |
| P1 | 新建 `ui/audit/AuditFragment.kt` + `AuditViewModel.kt` | Hash Chain 验证 UI + 多网关证据展示 |
| P1 | `mesh/MeshManagerApiWrapper.kt` 或核心 Message Handler | 增加 Vendor OpCode 分发逻辑 |
| P2 | 新建 `ui/diagnostics/NetworkDiagnosticsFragment.kt` | 拓扑视图、RSSI 监控 |
| P2 | `database/.../RoomDatabase.kt` | 新增 `SensorRecordEntity`、`AlarmEventEntity`、`AuditLogEntity` |
| P2 | `network/GatewayApiService.kt`（Retrofit） | 新增 `/api/nodes/status`、`/api/audit/verify`、`/api/gateway/heartbeat` 等接口 |
| P2 | `utils/VendorModelOpCodes.kt` | 定义所有 OpCode 和数据类 |
| P3 | `ConfigServerModel.kt` / 配置相关 Fragment | 批量设置采样间隔、报警阈值、Time Authority 参数 |

**额外通用修改**：
- `MeshNetworkLiveData` / `NodeLiveData` 扩展支持 V2 数据字段（seq_num、battery_pct、hash 等）
- 所有列表使用 `LazyColumn` + 分页 + 搜索
- 添加本地 Room + WorkManager 实现离线缓存和后台同步

### 三、新 Vendor Model 定义（V5 核心）

**Model 基本信息**（直接在 ESP32 固件和 App 中保持一致）：
- **Company ID**（Vendor ID）：`0x02E5`（Espressif 官方推荐）
- **Model ID**：`0x0001`（ColdChain Sensor Model V5）
- **Element Index**：0（主元素）

**OpCodes 定义（8bit，低 6bit 为操作码，高 2bit 预留）**：

| OpCode | 方向 | 名称 | 用途 | Payload 长度 |
|--------|------|------|------|--------------|
| `0xC1` | Sensor → Mesh | `SENSOR_DATA_REPORT` | 轻量单条普通数据 | ≤24 字节 |
| `0xC2` | Sensor → Mesh | `SENSOR_BATCH_REPORT` | **批量上传（核心）** | 可变（8~20 条记录） |
| `0xC3` | Sensor → Mesh | `SENSOR_ALARM_REPORT` | 报警数据（带完整 ECDSA） | 固定 107+ 字节 |
| `0xC4` | Mesh → Sensor | `CONFIG_SET` | 配置下发（阈值、间隔、TTL 等） | 可变 |
| `0xC5` | Mesh → Sensor | `TIME_SYNC` | 时间权威广播/校时 | 8 字节（Unix 时间戳） |
| `0xC6` | Sensor → Mesh | `STATUS_RESPONSE` | 配置响应 / 心跳 / 缓存状态 | 短消息 |

**核心消息结构示例（Kotlin 数据类）**：

```kotlin
// SENSOR_BATCH_REPORT (OpCode 0xC2) —— 最重要
data class BatchReportMessage(
    val batchId: UInt,                    // 批次ID
    val count: UByte,                     // 本批记录数
    val records: List<SensorRecordV2>
)

data class SensorRecordV2(
    val seqNum: UInt,
    val timestamp: UInt,                  // Sensor RTC 时间
    val temp: Short,                      // ×100
    val humi: UShort,                     // ×100
    val batteryPct: UByte,
    val alarmFlag: UByte,
    val hashPrev: ByteArray? = null       // 32字节 Hash Chain（可选）
)

// SENSOR_ALARM_REPORT (0xC3)
data class AlarmReportMessage(
    val record: SensorRecordV2,
    val ecdsaSignature: ByteArray        // 64字节
)
```

**App 端实现要点**：
- 在 `ColdChainSensorModel` 类中继承 `VendorModel` 并实现 `onMessageReceived()` 和 `sendVendorMessage()`
- 通过 `MeshManagerApi.addVendorModel(model)` 注册
- 订阅 Group 0xC001/C002 后，Vendor Message 会自动通过 GATT Proxy 到达 App
- 接收到 `0xC2`/`0xC3` 后立即解析并存入 Room DB，同时通知 ViewModel 更新 UI

---

**完成以上内容后**，App 即可全面支撑 V5 的“超低功耗医药审计系统”特性。

**医药冷链 BLE Mesh Android App V5 增强 — Phase 1（Week 1）完整开发任务清单**

**目标**：Week 1 结束时，完成**项目结构现代化重构** + **P0 核心模块（仪表盘 + 设备列表 + 实时监控）**，实现 **Group Publish 实时接收** + **Gateway HTTP 初步集成** + **本地 Room 缓存**，为后续告警、审计模块打好基础。

**预计工时**：5 个工作日（1 人全职开发，可并行 1-2 人）  
**分支**：`feature/v5-phase1`（从 `main` 或 `develop` 拉取）  
**技术栈要求**：Kotlin + Jetpack Compose（推荐）或 XML + MVVM + Hilt + Room + Retrofit + nRF Mesh Library  
**验收标准**：App 能成功配网后，实时显示仪表盘数据、节点列表，支持离线缓存，UI 流畅，无内存泄漏。

---

### **Day 1：项目结构重构 & 基础依赖升级（必须优先完成）**

- [ ] **1.1 项目 Gradle & 依赖升级**
  - 更新 `build.gradle`（app + project）：Hilt、Compose（或 ViewBinding）、Room、Retrofit、Coroutines、Lifecycle、Navigation Component
  - 添加依赖：
    ```kotlin
    implementation "com.google.dagger:hilt-android:2.51"
    implementation "androidx.room:room-ktx:2.6.1"
    implementation "com.squareup.retrofit2:retrofit:2.11.0"
    implementation "com.squareup.retrofit2:converter-gson:2.11.0"
    ```
  - 启用 Hilt（`@HiltAndroidApp`、`HiltAndroidRule`）

- [ ] **1.2 包结构重构（推荐最终结构）**
  ```
  com.zqtigger.coldchainmesh/
  ├── di/                  # Hilt Module
  ├── ui/
  │   ├── dashboard/       # 新建
  │   ├── nodes/           # 扩展
  │   ├── common/          # BaseFragment/BaseViewModel
  ├── data/
  │   ├── repository/      # MeshDataRepository、GatewayHttpRepository
  │   ├── local/           # Room DAO、Entity
  │   ├── remote/          # Retrofit ApiService
  ├── domain/
  │   ├── model/           # ColdChainSensorModel.kt、GatewayStatus.kt
  ├── mesh/                # MeshManagerApiWrapper.kt（封装 Vendor Model）
  ├── utils/               # VendorModelOpCodes.kt、DateUtils.kt
  └── database/            # AppDatabase.kt
  ```

- [ ] **1.3 新建核心文件**
  - `di/AppModule.kt`、`di/NetworkModule.kt`、`di/MeshModule.kt`
  - `data/local/AppDatabase.kt` + `SensorRecordEntity.kt`（含 seqNum、hashPrev 等 V5 字段）
  - `data/remote/GatewayApiService.kt`（先实现 `/api/gateway/status`、`/api/nodes/list`）

- [ ] **1.4 Mesh Library 适配**
  - 在 `MeshManagerApiWrapper.kt` 中增加 Vendor Model 注册入口（`ColdChainSensorModel` 占位类）

**Day 1 交付物**：项目可编译运行，Hilt 注入正常，旧配网功能不回归。

---

### **Day 2：新 Vendor Model 基础实现 & 数据模型定义**

- [ ] **2.1 新建 Vendor Model（核心）**
  - `domain/model/ColdChainSensorModel.kt`（继承 `VendorModel`）
  - 实现 `getModelId()`、`getCompanyIdentifier()`、`onMessageReceived()`
  - 定义所有 OpCode（参考上一回复）：
    ```kotlin
    object VendorOpCodes {
        const val SENSOR_DATA_REPORT = 0xC1
        const val SENSOR_BATCH_REPORT = 0xC2  // 重点
        const val SENSOR_ALARM_REPORT = 0xC3
        const val TIME_SYNC = 0xC5
        // ...
    }
    ```

- [ ] **2.2 数据模型定义**
  - `domain/model/SensorRecordV2.kt`、`BatchReportMessage.kt`、`AlarmReportMessage.kt`
  - `domain/model/GatewayStatus.kt`（包含 Primary/Standby、心跳时间、角色）

- [ ] **2.3 Mesh 消息处理**
  - 在 `MeshManagerApiWrapper.kt` 或 `MeshNetworkManager` 中注册 `ColdChainSensorModel`
  - 实现 `onVendorMessageReceived` 分发到 `MeshDataRepository`

- [ ] **2.4 基础 Repository 搭建**
  - `data/repository/MeshDataRepository.kt`（接收 Publish → 存 Room）
  - `data/repository/GatewayHttpRepository.kt`（Retrofit 接口实现）

**Day 2 交付物**：Vendor Model 能成功接收模拟 `0xC2` Batch Report 并解析，数据存入 Room。

---

### **Day 3：仪表盘（Dashboard）实现**

- [ ] **3.1 UI 实现**
  - 新建 `ui/dashboard/DashboardFragment.kt`（Compose 或 XML）
  - 实现顶部导航栏（Primary 状态 + 多网关切换）
  - 3 个概览卡片 + 实时数据横向列表（使用 `LazyRow`）

- [ ] **3.2 ViewModel**
  - `DashboardViewModel.kt`（Hilt 注入 Repository）
  - 订阅 `MeshDataRepository` 的 LiveData / Flow（实时更新仪表盘）

- [ ] **3.3 数据聚合逻辑**
  - 在线 Sensor 数、告警数、平均电池、缓存统计
  - 最近 10 条记录显示（从 Room + Mesh Publish）

- [ ] **3.4 刷新机制**
  - 下拉刷新 + 自动 30 秒轮询 Gateway HTTP 补充数据

**Day 3 交付物**：仪表盘页面可正常显示实时数据，点击卡片可跳转到设备列表。

---

### **Day 4：设备管理列表（NodeList）增强**

- [ ] **4.1 扩展现有 NodeListFragment**
  - 添加 V5 字段显示：`batteryPct`、`cacheCount`、`lastSampleTime`、`regionGroup`
  - 筛选栏（角色 + 区域 + 状态）
  - 使用 `LazyColumn` + `Paging 3`（支持 200+ 节点）

- [ ] **4.2 NodeListAdapter / ViewHolder**
  - 更新数据绑定，增加状态标签颜色（正常/报警/低电）

- [ ] **4.3 NodeDetail 占位页**
  - 简单详情页（基本信息 + 24h 温度曲线占位）
  - 后续 Phase 2 再完善图表

- [ ] **4.4 与 Mesh 集成**
  - NodeListViewModel 订阅 Mesh Network 节点变化 + V5 自定义数据

**Day 4 交付物**：设备列表完整显示 V5 扩展字段，支持过滤，点击可进入详情。

---

### **Day 5：本地缓存 & 集成测试 + 收尾**

- [ ] **5.1 Room 完整实现**
  - `SensorRecordDao.kt`（insert、queryByTime、deleteOld 等）
  - 实现离线缓存策略（最近 7 天数据）

- [ ] **5.2 WorkManager 后台同步**
  - 新建 `SyncWorker.kt`：定期从 Gateway HTTP 拉取批量数据补全

- [ ] **5.3 集成测试**
  - 真实设备测试：配网 → 传感器正常采样 → App 接收 Batch Report → 仪表盘 & 列表更新
  - 模拟断网场景（缓存正常工作）
  - 内存 / ANR / 泄漏检测（LeakCanary）

- [ ] **5.4 文档 & 代码规范**
  - 更新 `README.md`（V5 增强说明）
  - 添加 KDoc 注释 + 单元测试（ViewModel 层）
  - PR 提交 Checklist

- [ ] **5.5 风险验证**
  - 测试 Proxy 模式下手机功耗（提供开关）
  - 确认与现有 ProvisioningActivity 兼容

**Day 5 交付物**：完整可运行的 Phase 1 版本，提交 PR，Demo 视频或截图。

---

### **Week 1 总体交付物 & 验收**
- 可编译运行的完整 App
- 仪表盘 + 设备列表实时刷新（Mesh Publish + HTTP）
- Vendor Model 0xC1/C2/C3 消息解析正常
- Room 本地缓存工作正常
- 代码结构清晰、可扩展
- 所有 P0 任务 100% 完成

**风险提醒**：
- Vendor Model OpCode 与固件必须严格一致（需与固件团队同步）
- nRF Mesh Library 版本兼容性（建议锁定当前 fork 最新 commit）
- Compose vs XML：如果团队不熟悉 Compose，可先用 XML + ViewBinding，Week 2 再迁移

**下一阶段准备**：
Week 2 将直接进入 **告警中心 + 配置中心**（P1 模块）。

**立即行动**：请开发者 **今天** fork 分支，开始 Day 1 重构。如需我输出 **具体文件的完整代码模板**（例如 `ColdChainSensorModel.kt`、`DashboardViewModel.kt` 或 `GatewayApiService.kt`），**直接回复对应文件名** 即可立即提供。

Phase 1 完成后，我们将拥有坚实的 V5 基础架构！🚀

