package com.itap

/**
 * 与 [TwoTapHoldEngine] 解耦的挂钩：引擎内为 **一行** `invoke()`，其余为 twotap 源码逐行拷贝。
 * AutoTap 关闭且卸掉悬浮层后此处应为 no-op，对 Hold 无影响。
 */
internal object ItapHoldBridge {
    var onIdleToHolding: (() -> Unit)? = null
}
