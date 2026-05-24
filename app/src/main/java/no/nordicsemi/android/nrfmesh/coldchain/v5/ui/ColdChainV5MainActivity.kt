package no.nordicsemi.android.nrfmesh.coldchain.v5.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import no.nordicsemi.android.nrfmesh.R
import no.nordicsemi.android.nrfmesh.coldchain.v5.ui.alarm.AlarmCenterFragment
import no.nordicsemi.android.nrfmesh.coldchain.v5.ui.audit.AuditFragment
import no.nordicsemi.android.nrfmesh.coldchain.v5.ui.dashboard.DashboardFragment
import no.nordicsemi.android.nrfmesh.coldchain.v5.ui.nodes.NodeListFragment
import no.nordicsemi.android.nrfmesh.coldchain.v5.ui.provision.ProvisionFragment

/**
 * V5 增强版主界面（含配网）
 * 底部导航：配网 | 仪表盘 | 设备 | 告警 | 审计
 *
 * 启动流程：App 打开 → 配网页 → 创建网络 → 扫描配网 → 仪表盘查看数据
 */
@AndroidEntryPoint
class ColdChainV5MainActivity : AppCompatActivity() {

    /** 5个Tab Fragment，顺序与底部导航菜单一致 */
    private val fragments = listOf(
        ProvisionFragment(),      // Tab 0: 配网（默认首页）
        DashboardFragment(),      // Tab 1: 仪表盘
        NodeListFragment(),       // Tab 2: 设备管理
        AlarmCenterFragment(),    // Tab 3: 告警中心
        AuditFragment()           // Tab 4: 合规审计
    )

    private val titles = arrayOf("配网管理", "仪表盘", "设备管理", "告警中心", "合规审计")

    /** 当前显示的 Fragment 索引 */
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coldchain_v5_main)

        supportActionBar?.title = "冷链 BLE Mesh V5"

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        // 首次启动默认显示配网页
        if (savedInstanceState == null) {
            showFragment(0)
        }

        bottomNav.setOnItemSelectedListener { item ->
            val index = when (item.itemId) {
                R.id.navProvision -> 0
                R.id.navDashboard -> 1
                R.id.navDevices   -> 2
                R.id.navAlarms    -> 3
                R.id.navAudit     -> 4
                else -> -1
            }
            if (index in fragments.indices) {
                showFragment(index)
                true
            } else false
        }
    }

    private fun showFragment(index: Int) {
        if (index == currentIndex) return
        currentIndex = index
        val fragment = fragments[index]
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        supportActionBar?.title = titles.getOrElse(index) { "V5" }
    }
}
