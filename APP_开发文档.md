# 医药冷链 BLE Mesh 配网 APP 开发文档

## 项目概述

基于 Nordic nRF-Mesh-Library 开发的医药冷链 BLE Mesh Provisioner APP（配网器）。
手机 APP 作为 Provisioner，负责将网关节点和传感器节点配入同一 BLE Mesh 网络。

### 架构变更
- **旧版（V2）**：网关承担 Provisioner 角色，主动扫描配网传感器
- **新版（V3）**：手机 APP 作为 Provisioner，网关改为 Friend+Proxy 角色

## 密钥体系

| 项目 | 值 | 说明 |
|------|-----|------|
| NetKey | `7DD7364CD842AD18C17C2B820C84C3D6` | 网络密钥 |
| AppKey | `0123456789ABCDFE/FEDCBA9876543210` | 应用密钥（与网关固件一致） |
| NetKey 索引 | 0 | |
| AppKey 索引 | 0 | |

## 地址分配

| 地址 | 设备 |
|------|------|
| 0x0001 | 网关节点 (Friend + Proxy) |
| 0x0002 | 传感器节点 1 |
| 0x0003 | 传感器节点 2 |
| 0x0004+ | ...依次递增 |

## 源码文件结构

```
app/src/main/java/no/nordicsemi/android/nrfmesh/coldchain/
├── ColdChainKeys.java           # 密钥与地址常量
├── ColdChainNetworkCreator.java  # 网络创建工具（固定密钥）
├── ColdChainNodeConfigurator.java # 节点自动配置器
└── ColdChainMainActivity.java    # 主界面

app/src/main/res/
├── layout/activity_coldchain_main.xml  # 主界面布局
└── values-zh/strings.xml              # 中文字符串
```

## 核心流程

### 1. 创建网络
```
用户点击「创建网络」
  → meshManagerApi.createMeshNetwork()
  → 生成 NetKey (固定种子)
  → 创建 Provisioner
  → 添加 AppKey (固定值)
  → 网络就绪
```

### 2. 扫描设备
```
用户点击「开始扫描」
  → BLE 扫描 (Filter: Mesh Provisioning UUID 00001827-...)
  → 持续 10 秒
  → 显示发现的未配网设备列表
```

### 3. 配网 + 自动配置
```
用户选择设备
  → 构建 UnprovisionedMeshNode
  → meshManagerApi.startProvisioning(node)
  → 配网过程 (No OOB)
  → 配网完成回调
  → ColdChainNodeConfigurator.configure():
      Step 1: ConfigAppKeyAdd (AppKey 添加到节点)
      Step 2: ConfigModelAppBind (绑定 Vendor Model 0x0000/0x02E5)
      Step 3: ConfigModelPublicationSet (发布到网关 0x0001)
```

## 与网关固件的对应关系

| APP | 网关固件 |
|-----|---------|
| 创建网络 | `ble_mesh_friend_proxy_init()` |
| 配网设备 | 原 `ble_mesh_provision_device()` |
| AppKey 0x0123... | `s_app_key_val[16]` |
| 网关地址 0x0001 | `prov_unicast_addr = 0x0001` |
| VID 0x02E5 | `CID_ESP 0x02E5` |
| Model 0x0000 | `vnd_models[0]` |

## 运行环境要求

- Android 4.3+ (API 18+)
- 蓝牙 4.0+ BLE 支持
- 定位权限 (Android 6-11)
- 蓝牙扫描权限 (Android 12+)

## 编译命令

```bash
cd Android-nRF-Mesh-Library
./gradlew :app:assembleDebug
# APK 输出: app/build/outputs/apk/debug/app-debug.apk
```

## 后续优化

1. **设备类型识别**：通过 Composition Data 解析网关/传感器
2. **一键配网**：自动连续配网多个设备
3. **网络导出/导入**：支持 Mesh 网络配置的 JSON 导入导出
4. **传感器数据展示**：Proxy 连接后读取传感器上报数据
