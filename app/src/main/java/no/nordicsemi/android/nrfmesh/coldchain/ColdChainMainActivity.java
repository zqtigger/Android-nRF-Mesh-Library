package no.nordicsemi.android.nrfmesh.coldchain;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import no.nordicsemi.android.mesh.ApplicationKey;
import no.nordicsemi.android.mesh.MeshManagerApi;
import no.nordicsemi.android.mesh.MeshManagerCallbacks;
import no.nordicsemi.android.mesh.MeshNetwork;
import no.nordicsemi.android.mesh.MeshProvisioningStatusCallbacks;
import no.nordicsemi.android.mesh.MeshStatusCallbacks;
import no.nordicsemi.android.mesh.provisionerstates.ProvisioningState;
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode;
import no.nordicsemi.android.mesh.transport.ControlMessage;
import no.nordicsemi.android.mesh.transport.MeshMessage;
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode;
import no.nordicsemi.android.nrfmesh.R;
import no.nordicsemi.android.nrfmesh.ble.BleMeshManager;
import no.nordicsemi.android.nrfmesh.ble.BleMeshManagerCallbacks;
import no.nordicsemi.android.nrfmesh.databinding.ActivityColdchainMainBinding;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

/**
 * 医药冷链 BLE Mesh 配网 APP — 主界面
 * 
 * V3 架构：手机 APP 作为 Provisioner，网关为 Friend+Proxy 节点。
 * 配网地址：网关=0x0001，传感器=0x0002起。
 */
@AndroidEntryPoint
public class ColdChainMainActivity extends AppCompatActivity 
        implements BleMeshManagerCallbacks, MeshStatusCallbacks {
    private ActivityColdchainMainBinding binding;


    @Inject
    MeshManagerApi meshManagerApi;


    @Inject
    BleMeshManager bleMeshManager;

    private MeshNetwork meshNetwork;
    private final ColdChainNodeConfigurator configurator = new ColdChainNodeConfigurator();
    private int nextSensorAddr = ColdChainKeys.SENSOR_START_ADDR;
    private boolean networkReady = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;
    private ScanCallback currentScanCallback;
    private boolean blePermissionsGranted = false;
    private MeshManagerCallbacks meshManagerCallbacks;

    // 配网状态
    private ScanResultWrapper pendingDevice;
    private int pendingUnicastAddr;
    private boolean isConnecting = false;
    private boolean capabilitiesReceived = false;
    private boolean provisioningStarted = false;
    private boolean isReconnectingForConfig = false;  /* 标志：重连以发送 config */
    private ProvisionedMeshNode pendingConfigNode = null;  /* 待配置节点 */

    // 权限请求Launcher
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                blePermissionsGranted = true;
                for (Boolean granted : permissions.values()) {
                    if (!granted) {
                        blePermissionsGranted = false;
                        break;
                    }
                }
                if (blePermissionsGranted) {
                    log("BLE权限已授予");
                } else {
                    log("BLE权限被拒绝");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityColdchainMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("冷链 BLE Mesh 配网");
        }

        // 注册 BLE 回调
        bleMeshManager.setGattCallbacks(this);

        // 检查并请求BLE权限
        checkAndRequestBlePermissions();

        binding.btnCreateNetwork.setOnClickListener(v -> createNetwork());
        binding.btnResetNetwork.setOnClickListener(v -> askResetNetwork());
        binding.btnStartScan.setOnClickListener(v -> startBleScan());

        /* 设备列表点击事件：选中设备开始配网 */
        binding.tvDeviceList.setOnClickListener(v -> {
            /* 弹出选择对话框 */
            if (foundDevices.isEmpty()) return;
            String[] items = new String[foundDevices.size()];
            for (int i = 0; i < foundDevices.size(); i++) {
                ScanResultWrapper w = foundDevices.get(i);
                items[i] = (w.device.getName() != null ? w.device.getName() : "未配网设备")
                        + " [" + w.device.getAddress() + "] RSSI=" + w.rssi;
            }
            new AlertDialog.Builder(this)
                    .setTitle("选择要配网的设备")
                    .setItems(items, (dialog, which) -> onDeviceClicked(which))
                    .show();
        });

        updateUi();
        log("系统就绪。使用固定密钥 AppKey=0123…3210");
    }

    /**
     * 检查并请求BLE权限 (Android 12+)
     */
    private void checkAndRequestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要 BLUETOOTH_SCAN 和 BLUETOOTH_CONNECT 权限
            List<String> permissionsNeeded = new ArrayList<>();

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }

            if (!permissionsNeeded.isEmpty()) {
                log("请求BLE权限...");
                permissionLauncher.launch(permissionsNeeded.toArray(new String[0]));
            } else {
                blePermissionsGranted = true;
                log("BLE权限已存在");
            }
        } else {
            // Android 11 及以下
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION});
            } else {
                blePermissionsGranted = true;
            }
        }
    }

    /* ═══ 网络管理 ═══ */
    
    /** Provisioner 在 Mesh 网络中的地址（手机 App 作为 Provisioner） */
    private static final int PROVISIONER_UNICAST_ADDR = 0x0000;
    
    private void createNetwork() {
        try {
            // ⭐ 先设置回调，确保 createMeshNetwork 的回调不会崩溃
            if (meshManagerCallbacks == null) {
                setupMeshManagerCallbacks();
            }

            // 使用库的 createMeshNetwork 创建网络
            meshManagerApi.createMeshNetwork();
            meshNetwork = meshManagerApi.getMeshNetwork();

            if (meshNetwork != null) {
                // ⭐ 关键：为 Provisioner 分配单播地址（否则无法发送任何 Mesh 消息！）
                assignProvisionerAddress();

                // 延迟添加 AppKey，等待 mesh_network 父行先写入数据库
                handler.postDelayed(() -> {
                    try {
                        if (meshNetwork.getAppKey(ColdChainKeys.APP_KEY_INDEX) == null) {
                            ApplicationKey appKey = new ApplicationKey(
                                    ColdChainKeys.APP_KEY_INDEX, ColdChainKeys.APP_KEY_VAL);
                            appKey.setName(ColdChainKeys.APP_KEY_NAME);
                            appKey.setBoundNetKeyIndex(ColdChainKeys.NET_KEY_INDEX);
                            meshNetwork.addAppKey(appKey);
                            log("AppKey 已添加: " + ColdChainKeys.APP_KEY_NAME);
                        }
                    } catch (Exception e) {
                        log("添加 AppKey 失败: " + e.getMessage());
                    }
                    updateUi();
                }, 500);

                networkReady = true;
                nextSensorAddr = ColdChainKeys.SENSOR_START_ADDR;
                log("网络创建成功 ✓ (Provisioner=0x" 
                        + Integer.toHexString(PROVISIONER_UNICAST_ADDR).toUpperCase() + ")");
                log("NetKey[0] 已配置，AppKey[0]=0123456789ABCDEF/FEDCBA9876543210");
                log("网关地址: 0x0001, 传感器起始地址: 0x0002");
                toast("Mesh 网络已创建");
            }
        } catch (Exception e) {
            log("创建网络失败: " + e.getMessage());
            toast("创建网络失败");
        }
        updateUi();
    }

    private void askResetNetwork() {
        new AlertDialog.Builder(this)
                .setTitle("确认重置")
                .setMessage("确定要重置整个 Mesh 网络吗？\n所有已配网设备将被清除。")
                .setPositiveButton("确定", (d, w) -> resetNetwork())
                .setNegativeButton("取消", null)
                .show();
    }

    private void resetNetwork() {
        try {
            // resetMeshNetwork() 内部会删除旧网络并生成新网络
            meshManagerApi.resetMeshNetwork();
            meshNetwork = meshManagerApi.getMeshNetwork();
            if (meshNetwork != null) {
                // 为新网络分配 Provisioner 地址
                assignProvisionerAddress();
                networkReady = true;
                nextSensorAddr = ColdChainKeys.SENSOR_START_ADDR;
                binding.tvDeviceList.setText("暂无设备");
                binding.tvProvisionedNodes.setText("暂无已配网节点");
                log("网络已重置");
                toast("网络已重置");
            } else {
                networkReady = false;
                log("网络重置失败: getMeshNetwork() 返回 null");
                toast("重置失败");
            }
        } catch (Exception e) {
            log("重置失败: " + e.getMessage());
        }
        updateUi();
    }

    /**
     * 为 Provisioner（手机 App）分配单播地址
     * ⭐ 必须在 createMeshNetwork 后、发送任何 Mesh 消息前调用
     * 否则 createMeshPdu() 会抛出 "Provisioner address not set"
     */
    private void assignProvisionerAddress() {
        if (meshNetwork == null) {
            toast("❌ meshNetwork 为空!");
            return;
        }
        
        try {
            if (meshNetwork.getProvisioners().isEmpty()) {
                no.nordicsemi.android.mesh.AllocatedUnicastRange unicastRange =
                        new no.nordicsemi.android.mesh.AllocatedUnicastRange(
                                PROVISIONER_UNICAST_ADDR, 0x000F);
                no.nordicsemi.android.mesh.AllocatedGroupRange groupRange =
                        new no.nordicsemi.android.mesh.AllocatedGroupRange(0xC000, 0xCCFF);
                no.nordicsemi.android.mesh.AllocatedSceneRange sceneRange =
                        new no.nordicsemi.android.mesh.AllocatedSceneRange(0x0000, 0xFFFF);

                no.nordicsemi.android.mesh.Provisioner provisioner =
                        meshNetwork.createProvisioner("nRF Mesh Provisioner",
                                unicastRange, groupRange, sceneRange);
                provisioner.setProvisionerAddress(PROVISIONER_UNICAST_ADDR);
                meshNetwork.selectProvisioner(provisioner);
                meshNetwork.addProvisioner(provisioner);

                log("✓ Provisioner 已创建，地址=0x"
                        + Integer.toHexString(PROVISIONER_UNICAST_ADDR).toUpperCase());
                toast("✓ Provisioner=0x" + Integer.toHexString(PROVISIONER_UNICAST_ADDR).toUpperCase());
            } else {
                no.nordicsemi.android.mesh.Provisioner prov = 
                        meshNetwork.getSelectedProvisioner();
                if (prov != null && prov.getProvisionerAddress() == null) {
                    prov.setProvisionerAddress(PROVISIONER_UNICAST_ADDR);
                    log("✓ Provisioner 地址已设置=0x" 
                            + Integer.toHexString(PROVISIONER_UNICAST_ADDR).toUpperCase());
                    toast("✓ Prov地址已设");
                } else if (prov != null) {
                    log("✓ Provisioner 地址=0x" 
                            + Integer.toHexString(prov.getProvisionerAddress()).toUpperCase());
                    toast("✓ Prov OK");
                } else {
                    toast("⚠ 无选中Prov");
                }
            }
        } catch (Exception e) {
            String err = "Prov失败: " + e.getMessage();
            log(err);
            e.printStackTrace();
            toast(err);
        }
    }

    /**
     * 初始化 MeshManagerCallbacks（只需设置一次）
     */
    private void setupMeshManagerCallbacks() {
        meshManagerApi.setMeshStatusCallbacks(this);  /* ⭐ 接收 Config 响应，防止 NPE */
        meshManagerCallbacks = new MeshManagerCallbacks() {
            @Override
            public void onNetworkLoaded(MeshNetwork network) {
                log("网络已加载: " + (network != null ? network.getMeshName() : "null"));
            }

            @Override
            public void onNetworkUpdated(MeshNetwork network) {
                log("网络已更新");
            }

            @Override
            public void onNetworkLoadFailed(String error) {
                log("网络加载失败: " + error);
            }

            @Override
            public void onNetworkImported(MeshNetwork network) {
                log("网络已导入");
            }

            @Override
            public void onNetworkImportFailed(String error) {
                log("网络导入失败: " + error);
            }

            @Override
            public void sendProvisioningPdu(UnprovisionedMeshNode node, byte[] pdu) {
                if (bleMeshManager != null) {
                    bleMeshManager.sendPdu(pdu);
                }
            }

            @Override
            public void onMeshPduCreated(byte[] pdu) {
                if (bleMeshManager != null) {
                    bleMeshManager.sendPdu(pdu);
                }
            }

            @Override
            public int getMtu() {
                if (bleMeshManager != null) {
                    return bleMeshManager.getMaximumPacketSize();
                }
                return 23;
            }
        };
        meshManagerApi.setMeshManagerCallbacks(meshManagerCallbacks);
        log("MeshManagerCallbacks 已初始化");
    }

    /**
     * 设置配网状态回调
     */
    private void setupProvisioningCallbacks() {
        meshManagerApi.setProvisioningStatusCallbacks(new MeshProvisioningStatusCallbacks() {
            @Override
            public void onProvisioningStateChanged(UnprovisionedMeshNode node,
                    ProvisioningState.States state, byte[] data) {
                handler.post(() -> {
                    String stateName = (state != null) ? state.name() : "NULL";
                    binding.tvStatus.setText("配网: " + stateName);
                    log("配网状态: " + stateName);

                    // 当进入 capabilities 阶段时，标记并触发配网
                    if (state == ProvisioningState.States.PROVISIONING_CAPABILITIES) {
                        capabilitiesReceived = true;
                        log("已收到配网能力 → 启动 startProvisioning");
                        // 延短延迟：capabilities 已就绪，立即开始
                        handler.postDelayed(() -> startProvisioningNow(), 500);
                    }
                });
            }

            @Override
            public void onProvisioningFailed(UnprovisionedMeshNode node,
                    ProvisioningState.States state, byte[] data) {
                log("配网失败: " + state.name());
                isConnecting = false;
                handler.post(() -> {
                    binding.tvStatus.setText("配网失败: " + state.name());
                    toast("配网失败");
                });
            }

            @Override
            public void onProvisioningCompleted(ProvisionedMeshNode node,
                    ProvisioningState.States state, byte[] data) {
                int addr = node.getUnicastAddress();
                log("配网完成 ✓ addr=0x" + Integer.toHexString(addr).toUpperCase());
                isConnecting = false;
                
                /*
                 * ⭐ V3 关键：参照官方 NrfMeshRepository 流程
                 * 
                 * 1. 断开 GATT（配网阶段结束）
                 * 2. 延迟 2s 后重连（网关此时已启动 Proxy Service 0x1828）
                 * 3. BleMeshManager.isRequiredServiceSupported() 自动发现 Proxy
                 *    → 后续 Mesh PDU 写入 MESH_PROXY_DATA_IN (0x2ADD) ✅
                 * 4. onDeviceReady 触发（isReconnectingForConfig=true）
                 *    → 跳过 Provisioning，直接启动 Configurator
                 */
                log("配网完成，断开准备重连 Proxy...");
                try {
                    bleMeshManager.disconnect().enqueue();
                    log("GATT 已断开");
                } catch (Exception e) {
                    log("断开异常: " + e.getMessage());
                }

                handler.post(() -> {
                    binding.tvStatus.setText("配网完成: 0x"
                            + Integer.toHexString(addr).toUpperCase());
                    toast("配网成功!");
                    if (!ColdChainKeys.isGatewayAddr(addr)) {
                        nextSensorAddr = addr + 1;
                    }
                    updateUi();
                });

                /* 延迟后重连 → Proxy 服务被发现 → onDeviceReady → Configurator */
                if (meshNetwork != null && pendingDevice != null) {
                    log("★ 延迟 2s 后重连以发现 Proxy 服务");
                    ProvisionedMeshNode cfgNode = node;
                    configurator.setCallback(new ColdChainNodeConfigurator.ConfigCallback() {
                        @Override public void onConfigProgress(String step, String msg) {
                            handler.post(() -> binding.tvStatus.setText("配置: " + step));
                        }
                        @Override public void onConfigComplete(int nodeAddr, boolean success) {
                            log("配置完成: addr=0x" + Integer.toHexString(nodeAddr) + " success=" + success);
                            try {
                                bleMeshManager.disconnect().enqueue();
                                log("GATT 已断开（配网+配置完成）");
                            } catch (Exception e) {
                                log("断开异常: " + e.getMessage());
                            }
                            handler.post(() -> {
                                binding.tvStatus.setText(success ? "✓ 节点就绪" : "✗ 配置异常");
                                updateUi();
                                toast(success ? "节点配置完成!" : "配置部分失败");
                            });
                        }
                    });
                    /* 延迟 2s → 重连 → BleMeshManager 发现 Proxy Service (0x1828) */
                    handler.postDelayed(() -> {
                        isReconnectingForConfig = true;
                        pendingConfigNode = cfgNode;
                        log("重连: " + pendingDevice.device.getAddress() + " (Proxy 扫描)");
                        binding.tvStatus.setText("重连 Proxy...");
                        bleMeshManager.connect(pendingDevice.device).retry(3, 300).enqueue();
                    }, 2000);
                } else {
                    log("❌ meshNetwork 为空，无法配置!");
                    toast("⚠ meshNetwork 为空，跳过配置");
                }
            }
        });
    }

    /* ═══ BLE 扫描 ═══ */
    private final List<ScanResultWrapper> foundDevices = new ArrayList<>();

    /** 包装扫描结果，保存设备 + UUID + RSSI */
    static class ScanResultWrapper {
        final android.bluetooth.BluetoothDevice device;
        final UUID uuid;
        final int rssi;

        ScanResultWrapper(android.bluetooth.BluetoothDevice device, UUID uuid, int rssi) {
            this.device = device;
            this.uuid = uuid;
            this.rssi = rssi;
        }
    }

    private void startBleScan() {
        if (!networkReady) {
            toast("请先创建 Mesh 网络");
            return;
        }
        if (!blePermissionsGranted) {
            toast("请先授予BLE权限");
            checkAndRequestBlePermissions();
            return;
        }
        if (isScanning) return;

        isScanning = true;
        binding.btnStartScan.setEnabled(false);
        binding.btnStartScan.setText("扫描中...");
        binding.tvStatus.setText("正在扫描未配网设备... (10秒)");
        foundDevices.clear();
        refreshDeviceList();

        log("开始 BLE 扫描 (Service UUID: 00001827-0000-1000-8000-00805F9B34FB)");

        BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setUseHardwareBatchingIfSupported(false)
                .build();

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BleMeshManager.MESH_PROVISIONING_UUID))
                .build());

        currentScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int cbType, ScanResult result) {
                android.bluetooth.BluetoothDevice dev = result.getDevice();
                UUID meshUuid = parseMeshUuid(result);

                /* 去重：同一设备只记录一次（以 MAC 地址为准） */
                boolean exists = false;
                for (ScanResultWrapper w : foundDevices) {
                    if (w.device.getAddress().equals(dev.getAddress())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists && meshUuid != null) {
                    foundDevices.add(new ScanResultWrapper(dev, meshUuid, result.getRssi()));
                    String name = dev.getName() != null ? dev.getName() : "未配网设备";
                    String type = name.startsWith("Gateway_") ? "【网关】" : 
                                  name.startsWith("SENSOR_") ? "【传感器】" : "";
                    log("发现: " + type + name + " [" + dev.getAddress() + "] RSSI=" + result.getRssi()
                            + " UUID=" + meshUuid.toString().substring(0, 8) + "...");
                    refreshDeviceList();
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                log("扫描失败 error=" + errorCode);
                handler.post(() -> stopUiScan("扫描失败"));
            }
        };

        scanner.startScan(filters, settings, currentScanCallback);

        // 15秒后停止（给更多时间发现设备）
        handler.postDelayed(() -> {
            if (currentScanCallback != null) {
                scanner.stopScan(currentScanCallback);
                currentScanCallback = null;
            }
            if (isScanning) stopUiScan("扫描完成，共发现 " + foundDevices.size() + " 个未配网设备");
        }, 15000);
    }

    private void stopUiScan(String msg) {
        isScanning = false;
        binding.btnStartScan.setEnabled(true);
        binding.btnStartScan.setText("🔍 开始扫描 BLE 设备");
        binding.tvStatus.setText(msg);
    }

    private void refreshDeviceList() {
        handler.post(() -> {
            if (foundDevices.isEmpty()) {
                binding.tvDeviceList.setText("(暂无)");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < foundDevices.size(); i++) {
                ScanResultWrapper w = foundDevices.get(i);
                sb.append(String.format(Locale.getDefault(),
                        " ┌─ [%d] %s\n" +
                        " │  MAC: %s\n" +
                        " │  RSSI: %d dBm\n" +
                        " └─ → 点击配网\n\n",
                        i,
                        w.device.getName() != null ? w.device.getName() : "未配网设备",
                        w.device.getAddress(),
                        w.rssi
                ));
            }
            binding.tvDeviceList.setText(sb.toString().trim());
        });
    }

    /* ═══ 配网 ═══ */
    /** 点击设备列表项触发配网 */
    private void onDeviceClicked(int index) {
        if (index < 0 || index >= foundDevices.size()) return;
        ScanResultWrapper wrapper = foundDevices.get(index);

        String devName = wrapper.device.getName();
        if (devName == null) devName = "";

        /*
         * ⭐ 根据 BLE 设备名称判断角色：
         *   网关: "Gateway_XXXX" → 分配 0x0001
         *   传感器: "SENSOR_XXXX" 或其他 → 分配递增地址
         */
        int unicastAddr;
        if (devName.startsWith("Gateway_")) {
            unicastAddr = ColdChainKeys.GATEWAY_START_ADDR;
            log("识别为网关: " + devName + " → 0x" + Integer.toHexString(unicastAddr).toUpperCase());
        } else if (!hasGatewayProvisioned()) {
            log("未识别网关名称，作为首个设备分配网关地址");
            unicastAddr = ColdChainKeys.GATEWAY_START_ADDR;
        } else {
            unicastAddr = nextSensorAddr;
        }

        provisionDevice(wrapper, unicastAddr);
    }

    private boolean hasGatewayProvisioned() {
        if (meshNetwork == null) return false;
        for (ProvisionedMeshNode n : meshNetwork.getNodes()) {
            if (ColdChainKeys.isGatewayAddr(n.getUnicastAddress())) return true;
        }
        return false;
    }

    private void provisionDevice(ScanResultWrapper wrapper, int unicastAddr) {
        if (isConnecting) {
            toast("正在连接中，请稍候");
            return;
        }
        String name = wrapper.device.getName() != null ? wrapper.device.getName() : "未配网设备";
        log("开始配网: " + name + " [" + wrapper.device.getAddress() + "] → 0x"
                + Integer.toHexString(unicastAddr).toUpperCase());
        binding.tvStatus.setText("正在连接 " + name + "...");

        // 保存待配网设备信息
        pendingDevice = wrapper;
        pendingUnicastAddr = unicastAddr;
        isConnecting = true;
        capabilitiesReceived = false;
        provisioningStarted = false;

        // 先初始化回调
        if (meshManagerCallbacks == null) {
            setupMeshManagerCallbacks();
        }
        setupProvisioningCallbacks();

        /* 通过 GATT 连接到目标设备
         * onDeviceReady 回调会自动触发 identifyNode → startProvisioning */
        bleMeshManager.connect(wrapper.device).retry(3, 200).enqueue();

        // 设置总超时：30秒（Provisioning 完整流程可能需要较长时间）
        handler.postDelayed(() -> {
            if (isConnecting) {
                log("配网总超时(30s)，断开连接");
                bleMeshManager.disconnect().enqueue();
                isConnecting = false;
                handler.post(() -> binding.tvStatus.setText("配网超时，请重试"));
            }
        }, 30000);
    }

    /* ═══ BLE 回调接口实现 (BleMeshManagerCallbacks) ═══ */
    @Override
    public void onDataReceived(@NonNull android.bluetooth.BluetoothDevice device, int mtu, @NonNull byte[] pdu) {
        log("收到数据: " + pdu.length + " bytes");
        // 将数据交给 MeshManagerApi 处理
        meshManagerApi.handleNotifications(mtu, pdu);
    }

    @Override
    public void onDataSent(@NonNull android.bluetooth.BluetoothDevice device, int mtu, @NonNull byte[] pdu) {
        meshManagerApi.handleWriteCallbacks(mtu, pdu);
    }

    @Override
    public void onDeviceConnecting(@NonNull android.bluetooth.BluetoothDevice device) {
        log("正在连接: " + device.getAddress());
    }

    @Override
    public void onDeviceConnected(@NonNull android.bluetooth.BluetoothDevice device) {
        log("设备已连接: " + device.getAddress());
        handler.post(() -> binding.tvStatus.setText("已连接，发现服务中..."));
    }

    @Override
    public void onDeviceDisconnecting(@NonNull android.bluetooth.BluetoothDevice device) {
        log("正在断开: " + device.getAddress());
    }

    @Override
    public void onDeviceDisconnected(@NonNull android.bluetooth.BluetoothDevice device) {
        log("设备已断开: " + device.getAddress());
        isConnecting = false;
        provisioningStarted = false;
        handler.post(() -> {
            if (!capabilitiesReceived) {
                binding.tvStatus.setText("连接断开");
            }
        });
    }

    @Override
    public void onBondingRequired(@NonNull android.bluetooth.BluetoothDevice device) {
        log("需要绑定: " + device.getAddress());
    }

    @Override
    public void onBonded(@NonNull android.bluetooth.BluetoothDevice device) {
        log("已绑定: " + device.getAddress());
    }

    @Override
    public void onBondingFailed(@NonNull android.bluetooth.BluetoothDevice device) {
        log("绑定失败: " + device.getAddress());
        isConnecting = false;
        handler.post(() -> {
            binding.tvStatus.setText("绑定失败");
            toast("设备绑定失败");
        });
    }

    @Override
    public void onLinkLossOccurred(@NonNull android.bluetooth.BluetoothDevice device) {
        log("链路丢失: " + device.getAddress());
        isConnecting = false;
        handler.post(() -> binding.tvStatus.setText("连接丢失"));
    }

    @Override
    public void onError(@NonNull android.bluetooth.BluetoothDevice device, @NonNull String message, int code) {
        log("BLE设备错误: " + device.getAddress() + " - " + message + " (code:" + code + ")");
        isConnecting = false;
        handler.post(() -> {
            binding.tvStatus.setText("连接错误: " + message);
            toast("连接失败: " + message);
        });
    }

    @Override
    public void onServicesDiscovered(@NonNull android.bluetooth.BluetoothDevice device, boolean optionalServicesFound) {
        log("服务发现完成: " + device.getAddress());
        handler.post(() -> binding.tvStatus.setText("服务已发现，等待配网数据..."));
    }

    @Override
    public void onDeviceReady(@NonNull android.bluetooth.BluetoothDevice device) {
        log("设备已就绪: " + device.getAddress());

        /* ⭐⭐ 分支A：重连以发送 Config（Proxy 服务已发现） */
        if (isReconnectingForConfig && pendingConfigNode != null) {
            log("→ Proxy 服务已就绪，启动 Configurator (GATT Proxy)");
            isReconnectingForConfig = false;
            ProvisionedMeshNode node = pendingConfigNode;
            pendingConfigNode = null;
            handler.post(() -> binding.tvStatus.setText("Proxy OK，配置中..."));
            new Thread(() -> {
                log("[ConfigThread] Proxy 重连后执行 configure()");
                try {
                    configurator.configure(meshManagerApi, meshNetwork, node);
                    log("[ConfigThread] configure() 返回");
                } catch (Exception e) {
                    log("[ConfigThread] 异常: " + e.getMessage());
                    e.printStackTrace();
                    handler.post(() -> toast("Config异常: " + e.getMessage()));
                }
            }).start();
            return;
        }

        /* ⭐⭐ 分支B：初次配网（Provisioning Service） */
        /* ⭐ 关键：GATT 连接 + 服务发现完成，立即启动 Mesh 配网 */
        if (!isConnecting || pendingDevice == null) {
            log("警告: onDeviceReady 但无待配网设备");
            return;
        }

        handler.post(() -> binding.tvStatus.setText("已连接，启动 Identify..."));

        try {
            /* Step 1: 分配单播地址 */
            meshNetwork.assignUnicastAddress(pendingUnicastAddr);
            log("地址分配: 0x" + Integer.toHexString(pendingUnicastAddr).toUpperCase());

            /* Step 2: identifyNode — 使设备闪烁（可选但推荐）
             * 这会通过 GATT 向设备发送 Identify 命令
             * 同时库内部会开始获取设备的 Provisioning Capabilities */
            log("identifyNode UUID=" + pendingDevice.uuid.toString().substring(0, 8) + "...");
            meshManagerApi.identifyNode(pendingDevice.uuid, 10);

            /*
             * Step 3: 延迟后创建 UnprovisionedMeshNode 并启动配网
             * identifyNode 后，库需要一点时间处理 capabilities 数据。
             * PROVISIONING_CAPABILITIES 回调会在 onProvisioningStateChanged 中触发，
             * 之后我们调用 startProvisioning。
             *
             * 作为备用：延迟 2 秒后直接尝试 startProvisioning（某些情况下
             * capabilities 回调可能在 identifyNode 完成前就到达了）。
             */
            handler.postDelayed(() -> {
                if (!isConnecting) return;
                startProvisioningNow();
            }, 2000);

        } catch (Exception e) {
            log("配网准备异常: " + e.getMessage());
            e.printStackTrace();
            binding.tvStatus.setText("配网准备失败: " + e.getMessage());
            isConnecting = false;
        }
    }

    /**
     * 创建节点并调用 startProvisioning（从 onDeviceReady 或 PROVISIONING_CAPABILITIES 回调中触发）
     * 
     * ⭐ 关键：必须使用 identifyNode() 创建的内部 UnprovisionedMeshNode，
     * 不能新建！因为库的确认阶段始终使用内部 mUnprovisionedMeshNode。
     */
    private void startProvisioningNow() {
        if (!isConnecting || pendingDevice == null || meshNetwork == null) return;
        if (provisioningStarted) {
            log("startProvisioning 已调用过，跳过重复调用");
            return;
        }
        provisioningStarted = true;

        try {
            // 确保地址已分配
            meshNetwork.assignUnicastAddress(pendingUnicastAddr);

            /*
             * ⭐⭐ 关键修复：获取 identifyNode() 内部创建的节点对象
             * 
             * identifyNode() 调用 MeshProvisioningHandler.identify() → 
             *   initializeMeshNode() → 创建完整的 UnprovisionedMeshNode 
             *   （含 networkKey/flags/ivIndex/ttl）→ 存入 mUnprovisionedMeshNode
             * 
             * 如果我们 new 一个新节点传给 startProvisioning，库在确认阶段
             * 仍使用内部的 mUnprovisionedMeshNode，导致 confirmationInputs 为 null 崩溃：
             *   "Exception in PROVISIONING_CONFIRMATION : Attempt to get length of null array"
             */
            UnprovisionedMeshNode provisionNode = getInternalMeshNode();

            String addrHex = Integer.toHexString(pendingUnicastAddr).toUpperCase();
            if (provisionNode != null) {
                // 使用内部节点 — 它已有 networkKey/flags/ivIndex 等完整配置
                provisionNode.setNodeName("设备-" + addrHex);
                log("★ startProvisioning: addr=0x" + addrHex
                        + " (复用 identifyNode 内部节点)");
                binding.tvStatus.setText("正在配网 0x" + addrHex + "...");

                meshManagerApi.startProvisioning(provisionNode);
            } else {
                // 回退：如果内部节点不可用（理论上不应该发生），手动创建并初始化
                log("警告: 内部节点为空，尝试手动创建");
                provisionNode = createAndInitNode();
                log("★ startProvisioning: addr=0x" + addrHex + " (手动创建)");
                binding.tvStatus.setText("正在配网 0x" + addrHex + "...");
                meshManagerApi.startProvisioning(provisionNode);
            }

        } catch (Exception e) {
            log("startProvisioning 异常: " + e.getMessage());
            e.printStackTrace();
            binding.tvStatus.setText("配网异常: " + e.getMessage());
            isConnecting = false;
        }
    }

    /**
     * 通过反射获取 MeshProvisioningHandler 内部持有的 UnprovisionedMeshNode
     * 这是 identifyNode() 创建的完整节点（含 networkKey/flags/ivIndex/ttl）
     */
    private UnprovisionedMeshNode getInternalMeshNode() {
        try {
            // 1. 获取 MeshManagerApi 的 mMeshProvisioningHandler 字段
            java.lang.reflect.Field handlerField =
                    no.nordicsemi.android.mesh.MeshManagerApi.class.getDeclaredField("mMeshProvisioningHandler");
            handlerField.setAccessible(true);
            Object handler = handlerField.get(meshManagerApi);

            // 2. 调用 getMeshNode() 方法（包级私有）
            java.lang.reflect.Method getMeshNodeMethod =
                    handler.getClass().getDeclaredMethod("getMeshNode");
            getMeshNodeMethod.setAccessible(true);
            return (UnprovisionedMeshNode) getMeshNodeMethod.invoke(handler);

        } catch (Exception e) {
            log("getInternalMeshNode 反射失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 回退方案：手动创建并完整初始化 UnprovisionedMeshNode
     * 模拟 MeshProvisioningHandler.initializeMeshNode() 的逻辑
     */
    private UnprovisionedMeshNode createAndInitNode() throws Exception {
        UnprovisionedMeshNode node = new UnprovisionedMeshNode(pendingDevice.uuid);
        node.setNodeName("设备-" + Integer.toHexString(pendingUnicastAddr).toUpperCase());

        // 复制 initializeMeshNode() 的初始化逻辑
        no.nordicsemi.android.mesh.NetworkKey netKey = meshNetwork.getPrimaryNetworkKey();
        if (netKey != null) {
            node.setNetworkKey(netKey.getTxNetworkKey());
            node.setKeyIndex(netKey.getKeyIndex());
        }

        byte[] flagBytes = java.nio.ByteBuffer.allocate(1)
                .put((byte) meshNetwork.getProvisioningFlags()).array();
        node.setFlags(flagBytes);

        byte[] ivBytes = java.nio.ByteBuffer.allocate(4)
                .putInt(meshNetwork.getIvIndex().getIvIndex()).array();
        node.setIvIndex(ivBytes);

        node.setTtl(meshNetwork.getGlobalTtl());

        return node;
    }

    @Override
    public void onDeviceNotSupported(@NonNull android.bluetooth.BluetoothDevice device) {
        log("设备不支持: " + device.getAddress());
        handler.post(() -> {
            binding.tvStatus.setText("设备不支持 Mesh");
            Toast.makeText(this, "设备不支持 Mesh 功能", Toast.LENGTH_SHORT).show();
        });
    }

    /* ═══ UI ═══ */
    private void updateUi() {
        if (networkReady && meshNetwork != null) {
            binding.tvNetworkInfo.setText(String.format(Locale.getDefault(),
                    "网络就绪 ✓\n" +
                    "网关地址: 0x%04X\n" +
                    "下个传感器: 0x%04X\n" +
                    "AppKey 索引: %d (0123456789ABCDEF…)",
                    ColdChainKeys.GATEWAY_START_ADDR,
                    nextSensorAddr,
                    ColdChainKeys.APP_KEY_INDEX));
            binding.btnCreateNetwork.setEnabled(false);
        } else {
            binding.tvNetworkInfo.setText("未创建网络\n点击「创建网络」开始");
            binding.btnCreateNetwork.setEnabled(true);
        }

        // 更新已配网节点
        if (meshNetwork != null) {
            List<ProvisionedMeshNode> nodes = meshNetwork.getNodes();
            StringBuilder sb = new StringBuilder();
            if (nodes.isEmpty()) {
                sb.append("暂无已配网节点");
            } else {
                for (ProvisionedMeshNode n : nodes) {
                    String type = ColdChainKeys.isGatewayAddr(n.getUnicastAddress())
                            ? "【网关】" : "【传感器】";
                    sb.append(String.format(Locale.getDefault(),
                            "%s 0x%04X | %s\n",
                            type, n.getUnicastAddress(),
                            n.getUuid() != null ? n.getUuid() : "未知"));
                }
            }
            binding.tvProvisionedNodes.setText(sb.toString().trim());
        }
    }

    private void log(String msg) {
        Log.i("ColdChain", msg);
        handler.post(() -> {
            CharSequence current = binding.tvLog.getText();
            String newText = msg + "\n" + (current != null ? current.toString() : "");
            // 保留最近50行
            String[] lines = newText.split("\n");
            if (lines.length > 50) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 50; i++) sb.append(lines[i]).append("\n");
                binding.tvLog.setText(sb.toString().trim());
            } else {
                binding.tvLog.setText(newText.trim());
            }
        });
    }

    private void toast(String msg) {
        handler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    /** 从扫描结果的广播数据中解析 Unprovisioned Device Beacon UUID */
    private static UUID parseMeshUuid(ScanResult result) {
        // Unprovisioned Device Beacon: Service Data of MESH_PROVISIONING_UUID
        // 格式: [UUID(16 bytes)] [OOB data...]
        try {
            ParcelUuid svcUuid = new ParcelUuid(BleMeshManager.MESH_PROVISIONING_UUID);
            byte[] svcData = result.getScanRecord().getServiceData(svcUuid);
            if (svcData != null && svcData.length >= 16) {
                byte[] uuidBytes = new byte[16];
                System.arraycopy(svcData, 0, uuidBytes, 0, 16);
                return uuidFromBytes(uuidBytes);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 将 16 字节数组转换为标准 UUID */
    private static UUID uuidFromBytes(byte[] bytes) {
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) msb = (msb << 8) | (bytes[i] & 0xFF);
        for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (bytes[i] & 0xFF);
        return new UUID(msb, lsb);
    }

    /* ═══ MeshStatusCallbacks 实现 ═══ */
    @Override
    public void onTransactionFailed(int dst, boolean hasIncompleteTimerExpired) {
        log("Transaction failed: dst=0x" + Integer.toHexString(dst) + " timer=" + hasIncompleteTimerExpired);
    }

    @Override
    public void onUnknownPduReceived(int src, byte[] accessPayload) {
        log("Unknown PDU from 0x" + Integer.toHexString(src) + ", len=" + accessPayload.length);
    }

    @Override
    public void onBlockAcknowledgementProcessed(int dst, @NonNull ControlMessage message) {
        // No-op: 库内部处理
    }

    @Override
    public void onBlockAcknowledgementReceived(int src, @NonNull ControlMessage message) {
        // No-op: 库内部处理
    }

    @Override
    public void onHeartbeatMessageReceived(int src, @NonNull ControlMessage message) {
        // No-op
    }

    @Override
    public void onMeshMessageProcessed(int dst, @NonNull MeshMessage meshMessage) {
        log("Mesh message processed: dst=0x" + Integer.toHexString(dst));
    }

    @Override
    public void onMeshMessageReceived(int src, @NonNull MeshMessage meshMessage) {
        log("Mesh message received: src=0x" + Integer.toHexString(src)
                + " msg=" + meshMessage.getClass().getSimpleName());
        /* 库自动处理 Config Status 消息，无需手动干预 */
    }

    @Override
    public void onMessageDecryptionFailed(String meshLayer, String errorMessage) {
        log("Decryption failed: " + meshLayer + " - " + errorMessage);
    }
}
