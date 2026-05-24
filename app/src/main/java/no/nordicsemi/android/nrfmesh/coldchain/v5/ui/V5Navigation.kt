package no.nordicsemi.android.nrfmesh.coldchain.v5.ui

/**
 * V5 模块导航路由
 * 管理 Fragment 之间的导航
 */
object V5Navigation {

    /** 节点详情路由 */
    const val ROUTE_NODE_DETAIL = "node_detail/{nodeAddr}"

    /** 节点详情路径构建 */
    fun nodeDetailPath(nodeAddr: Int): String = "node_detail/$nodeAddr"

    /** 告警详情路由 */
    const val ROUTE_ALARM_DETAIL = "alarm_detail/{alarmId}"

    /** 网关管理路由 */
    const val ROUTE_GATEWAY_MANAGE = "gateway_manage"

    /** 更多设置路由 */
    const val ROUTE_SETTINGS = "settings"
}
