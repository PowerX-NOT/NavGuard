package com.navguard.app

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import java.io.IOException
import java.security.InvalidParameterException
import java.util.*
import java.util.concurrent.Executors

class SerialSocket(
    private val context: Context,
    private val device: BluetoothDevice
) : Runnable {

    companion object {
        private val BLUETOOTH_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val disconnectBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            listener?.onSerialIoError(IOException("background disconnect"))
            disconnect() // disconnect now, else would be queued until UI re-attached
        }
    }

    private var listener: SerialListener? = null
    private var socket: BluetoothSocket? = null
    private var connected = false

    init {
        if (context is Activity) {
            throw InvalidParameterException("expected non UI context")
        }
    }

    @SuppressLint("MissingPermission")
    fun getName(): String = device.name ?: device.address

    /**
     * connect-success and most connect-errors are returned asynchronously to listener
     */
    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    fun connect(listener: SerialListener) {
        this.listener = listener
        ContextCompat.registerReceiver(
            context,
            disconnectBroadcastReceiver,
            IntentFilter(Constants.INTENT_ACTION_DISCONNECT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Executors.newSingleThreadExecutor().submit(this)
    }

    fun disconnect() {
        listener = null // ignore remaining data and errors
        // connected = false; // run loop will reset connected
        socket?.let {
            try {
                it.close()
            } catch (ignored: Exception) {
            }
            socket = null
        }
        try {
            context.unregisterReceiver(disconnectBroadcastReceiver)
        } catch (ignored: Exception) {
        }
    }

    @Throws(IOException::class)
    fun write(data: ByteArray) {
        if (!connected) throw IOException("not connected")
        socket?.outputStream?.write(data)
    }

    @SuppressLint("MissingPermission")
    override fun run() { // connect & read
        try {
            socket = device.createRfcommSocketToServiceRecord(BLUETOOTH_SPP)
            socket?.connect()
            listener?.onSerialConnect()
        } catch (e: Exception) {
            listener?.onSerialConnectError(e)
            try {
                socket?.close()
            } catch (ignored: Exception) {
            }
            socket = null
            return
        }
        
        connected = true
        try {
            val buffer = ByteArray(1024)
            var len: Int
            while (true) {
                len = socket?.inputStream?.read(buffer) ?: break
                val data = buffer.copyOf(len)
                listener?.onSerialRead(data)
            }
        } catch (e: Exception) {
            connected = false
            listener?.onSerialIoError(e)
            try {
                socket?.close()
            } catch (ignored: Exception) {
            }
            socket = null
        }
    }
}