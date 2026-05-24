package no.nordicsemi.android.nrfmesh.coldchain.v5.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import no.nordicsemi.android.nrfmesh.R
import no.nordicsemi.android.nrfmesh.coldchain.v5.ui.alarm.AlarmCenterFragment
import no.nordicsemi.android.nrfmesh.coldchain.v5.ui.audit.AuditFragment
import no.nordicsemi.android.nrfmesh.coldchain.v5.ui.dashboard.DashboardFragment
import no.nordicsemi.android.nrfmesh.coldchain.v5.ui.nodes.NodeListFragment

/**
 * V5 增强版主界面
 * 底部导航栏：仪表盘 | 设备 | 告警 | 审计 | 更多
 */
@AndroidEntryPoint
class ColdChainV5MainActivity : AppCompatActivity() {

    private val fragments = listOf(
        DashboardFragment(),
        NodeListFragment(),
        AlarmCenterFragment(),
        AuditFragment()
    )

    private val titles = arrayOf("仪表盘", "设备管理", "告警中心", "合规审计")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coldchain_v5_main)

        if (supportActionBar != null) {
            supportActionBar?.title = "冷链 BLE Mesh 运维 V5"
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        // 默认显示仪表盘
        if (savedInstanceState == null) {
            showFragment(0)
        }

        bottomNav.setOnItemSelectedListener { item ->
            val index = when (item.itemId) {
                R.id.navDashboard -> 0
                R.id.navDevices -> 1
                R.id.navAlarms -> 2
                R.id.navAudit -> 3
                else -> -1
            }
            if (index in fragments.indices) {
                showFragment(index)
                true
            } else false
        }
    }

    private fun showFragment(index: Int) {
        val fragment = fragments[index]
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        supportActionBar?.title = titles.getOrElse(index) { "V5" }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_v5_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_provisioning -> {
                // 跳转到原有配网界面
                val intent = android.content.Intent(this,
                    no.nordicsemi.android.nrfmesh.coldchain.ColdChainMainActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
