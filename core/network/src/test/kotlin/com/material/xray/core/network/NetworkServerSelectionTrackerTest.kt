package com.material.xray.core.network

import com.material.xray.core.model.ConnectionState
import com.material.xray.core.model.NetworkEnvironment
import com.material.xray.core.model.NetworkKind
import com.material.xray.core.model.NetworkServerPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkServerSelectionTrackerTest {
    private val policy = NetworkServerPolicy(true, wifiServerId = 10, mobileServerId = 20)
    private val wifi = NetworkEnvironment(NetworkKind.Wifi, 1, "Home")

    @Test
    fun `outgoing network is captured once across offline handover and policy retries`() {
        val tracker = NetworkServerSelectionTracker()
        assertNull(tracker.departingNetwork(true, wifi))
        assertNull(tracker.departingNetwork(true, wifi.copy(ssid = null)))
        assertEquals(NetworkKind.Wifi, tracker.departingNetwork(true, NetworkEnvironment()))
        val mobile = NetworkEnvironment(NetworkKind.Mobile, 2)
        assertNull(tracker.departingNetwork(true, mobile))
        assertNull(tracker.departingNetwork(true, mobile))
        assertEquals(NetworkKind.Mobile, tracker.departingNetwork(true, wifi))
        assertNull(tracker.departingNetwork(true, wifi))
        assertNull(tracker.departingNetwork(false, mobile))
        assertNull(tracker.departingNetwork(true, wifi))
    }

    @Test
    fun `settled network does not undo a manual choice on connection state changes`() {
        val tracker = NetworkServerSelectionTracker()
        assertFalse(tracker.isSettled(policy, wifi))
        tracker.settle(policy, wifi)
        assertTrue(tracker.isSettled(policy, wifi.copy()))
        assertFalse(tracker.isSettled(policy, NetworkEnvironment(NetworkKind.Mobile, 2)))
        assertFalse(tracker.isSettled(policy, wifi.copy(networkId = 3)))
        assertFalse(tracker.isSettled(policy, wifi.copy(ssid = "Work")))
        assertFalse(tracker.isSettled(policy.copy(wifiServerId = 11), wifi))
    }

    @Test
    fun `in flight connection commands defer automatic selection`() {
        listOf(ConnectionState.Connecting, ConnectionState.Disconnecting, ConnectionState.ApplyingRoutingChanges, ConnectionState.UpdatingRoutingData).forEach {
            assertTrue(it.isNetworkSwitchBusy())
        }
        assertFalse(ConnectionState.Disconnected.isNetworkSwitchBusy())
        assertFalse(ConnectionState.Error("failed").isNetworkSwitchBusy())
        assertFalse(ConnectionState.Connected("Server", 1, "tun0", "wlan0").isNetworkSwitchBusy())
    }
}
