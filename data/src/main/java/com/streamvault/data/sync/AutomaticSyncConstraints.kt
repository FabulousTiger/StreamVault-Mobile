package com.streamvault.data.sync

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.Constraints
import androidx.work.NetworkType
import com.streamvault.domain.sync.AutomaticSyncSettings

internal fun automaticSyncConstraints(policy: AutomaticSyncSettings): Constraints {
    val networkType = if (policy.unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
    val builder = Constraints.Builder()
    if (policy.vpnOnly) {
        builder.setRequiredNetworkRequest(vpnNetworkRequest(policy.unmeteredOnly), networkType)
    } else {
        builder.setRequiredNetworkType(networkType)
    }
    return builder
        .setRequiresBatteryNotLow(true)
        .setRequiresCharging(policy.chargingOnly)
        .setRequiresDeviceIdle(policy.deviceIdleOnly)
        .build()
}

/** VPN networks lack NOT_VPN, so requiring TRANSPORT_VPN holds work until a tunnel is up. */
private fun vpnNetworkRequest(unmeteredOnly: Boolean): NetworkRequest = NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .apply { if (unmeteredOnly) addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) }
    .build()

internal fun ConnectivityManager.isVpnActive(): Boolean =
    allNetworks.any { network ->
        getNetworkCapabilities(network)?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } == true
    }
