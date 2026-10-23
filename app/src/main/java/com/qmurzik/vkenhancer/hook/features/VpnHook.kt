package com.qmurzik.vkenhancer.hook.features

import android.net.NetworkCapabilities
import com.qmurzik.vkenhancer.common.Keys
import com.qmurzik.vkenhancer.hook.core.Env
import com.qmurzik.vkenhancer.hook.core.Hooks
import com.qmurzik.vkenhancer.hook.core.L

/**
 * Hides an active VPN from VK so the app keeps working behind one.
 *
 * Apps detect a VPN through [NetworkCapabilities]: either `hasTransport(TRANSPORT_VPN)` returning
 * true, or `hasCapability(NET_CAPABILITY_NOT_VPN)` returning false. Both are framework methods, so
 * neutralising them here is independent of the VK version and of how the check is written.
 */
object VpnHook {
    fun install() {
        if (!Env.settings.bool(Keys.NET_HIDE_VPN)) return

        val hasTransport = Hooks.method(
            NetworkCapabilities::class.java, "hasTransport", Int::class.javaPrimitiveType!!
        )
        if (hasTransport != null) {
            Hooks.intercept(hasTransport) { chain ->
                if (chain.getArg(0) as Int == NetworkCapabilities.TRANSPORT_VPN) false else chain.proceed()
            }
        }

        val hasCapability = Hooks.method(
            NetworkCapabilities::class.java, "hasCapability", Int::class.javaPrimitiveType!!
        )
        if (hasCapability != null) {
            Hooks.intercept(hasCapability) { chain ->
                // NET_CAPABILITY_NOT_VPN = 15; report the network as "not a VPN".
                if (chain.getArg(0) as Int == NetworkCapabilities.NET_CAPABILITY_NOT_VPN) true else chain.proceed()
            }
        }
        L.i("VPN detection masked")
    }
}
