package com.ranhroi.touchremote.network

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import androidx.annotation.RequiresPermission
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Collections

object GetIPAddress {

    // Lấy địa chỉ IP cục bộ từ mạng hiện tại
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun getLocalIpAddress(context: Context): String? {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null

        val network: Network = connectivityManager.activeNetwork ?: return null
        val linkProperties: LinkProperties =
            connectivityManager.getLinkProperties(network) ?: return null

        linkProperties.linkAddresses.forEach { linkAddress ->
            val address = linkAddress.address
            if (address is Inet4Address && !address.isLoopbackAddress) {
                return address.hostAddress
            }
        }
        return null
    }

    // Lấy địa chỉ MAC từ địa chỉ IP
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun getMacAddressFromIp(context: Context): String {
        val ipAddress = getLocalIpAddress(context) ?: return ""
        return try {
            val networkInterface = NetworkInterface.getByInetAddress(InetAddress.getByName(ipAddress))
            val macBytes = networkInterface?.hardwareAddress ?: return ""
            macBytes.joinToString(":") { String.format("%02X", it) }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    // Lấy địa chỉ MAC từ tên giao diện mạng (ví dụ: "wlan0")
    fun getMacAddressByInterfaceName(interfaceName: String = "wlan0"): String {
        return try {
            val networkInterface = NetworkInterface.getByName(interfaceName) ?: return ""
            val macBytes = networkInterface.hardwareAddress ?: return ""
            macBytes.joinToString(":") { String.format("%02X", it) }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    // Lấy địa chỉ IP cục bộ từ các giao diện mạng
    fun getLocalIp(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in Collections.list(interfaces)) {
                val addresses = networkInterface.inetAddresses
                for (inetAddress in Collections.list(addresses)) {
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
            null
        } catch (e: SocketException) {
            e.printStackTrace()
            null
        }
    }

    // Lấy lớp mạng từ địa chỉ IP
    fun getSubnet(ip: String?): String {
        return ip?.substringBeforeLast(".") + "."
    }
}