package com.navguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.io.IOException
import java.util.ArrayDeque

/**
 * create notification and queue serial data while activity is not in the foreground
 * use listener chain: SerialSocket -> SerialService -> UI fragment
 */
class SerialService : Service(), SerialListener {

    inner class SerialBinder : Binder() {
        fun getService(): SerialService = this@SerialService
    }

    private enum class QueueType { Connect, ConnectError, Read, IoError }

    private data class QueueItem(
        val type: QueueType,
        var datas: ArrayDeque<ByteArray>? = null,
        val e: Exception? = null
    ) {
        constructor(type: QueueType) : this(type, if (type == QueueType.Read) ArrayDeque() else null, null)
        constructor(type: QueueType, e: Exception) : this(type, null, e)
        constructor(type: QueueType, datas: ArrayDeque<ByteArray>) : this(type, datas, null)

        fun add(data: ByteArray) {
            datas?.add(data)
        }
    }

    private val mainLooper = Handler(Looper.getMainLooper())
    private val binder = SerialBinder()
    private val queue1 = ArrayDeque<QueueItem>()
    private val queue2 = ArrayDeque<QueueItem>()
    private val lastRead = QueueItem(QueueType.Read)

    private var socket: SerialSocket? = null
    private var listener: SerialListener? = null
    private var connected = false

    // Expose the currently connected device address (or null)
    val connectedDeviceAddress: String?
        get() = socket?.let {
            try {
                val field = it.javaClass.getDeclaredField("device")
                field.isAccessible = true
                val device = field.get(it) as? android.bluetooth.BluetoothDevice
                device?.address
            } catch (e: Exception) {
                null
            }
        }

    val isConnected: Boolean
        get() = connected

    /**
     * Lifecycle
     */
    override fun onDestroy() {
        cancelNotification()
        disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder = binder

    /**
     * Ensure foreground service compliance
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always create notification and start foreground as soon as service starts
        createNotification()
        // If you want the service to be restarted if killed, return START_STICKY
        return START_STICKY
    }

    /**
     * Api
     */
    @Throws(IOException::class)
    fun connect(socket: SerialSocket) {
        socket.connect(this)
        this.socket = socket
        connected = true
    }

    fun disconnect() {
        connected = false // ignore data,errors while disconnecting
        cancelNotification()
        socket?.let {
            it.disconnect()
            socket = null
        }
    }

    @Throws(IOException::class)
    fun write(data: ByteArray) {
        if (!connected) throw IOException("not connected")
        socket?.write(data)
    }

    fun attach(listener: SerialListener) {
        if (Looper.getMainLooper().thread != Thread.currentThread()) {
            throw IllegalArgumentException("not in main thread")
        }
        initNotification()
        cancelNotification()
        
        // use synchronized() to prevent new items in queue2
        // new items will not be added to queue1 because mainLooper.post and attach() run in main thread
        synchronized(this) {
            this.listener = listener
        }
        
        for (item in queue1) {
            when (item.type) {
                QueueType.Connect -> listener.onSerialConnect()
                QueueType.ConnectError -> listener.onSerialConnectError(item.e!!)
                QueueType.Read -> listener.onSerialRead(item.datas!!)
                QueueType.IoError -> listener.onSerialIoError(item.e!!)
            }
        }
        
        for (item in queue2) {
            when (item.type) {
                QueueType.Connect -> listener.onSerialConnect()
                QueueType.ConnectError -> listener.onSerialConnectError(item.e!!)
                QueueType.Read -> listener.onSerialRead(item.datas!!)
                QueueType.IoError -> listener.onSerialIoError(item.e!!)
            }
        }
        
        queue1.clear()
        queue2.clear()
    }

    fun detach() {
        if (connected) createNotification()
        // items already in event queue (posted before detach() to mainLooper) will end up in queue1
        // items occurring later, will be moved directly to queue2
        // detach() and mainLooper.post run in the main thread, so all items are caught
        listener = null
    }

    private fun initNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nc = NotificationChannel(Constants.NOTIFICATION_CHANNEL, "Background service", NotificationManager.IMPORTANCE_LOW)
            nc.setShowBadge(false)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(nc)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun areNotificationsEnabled(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val nc = nm.getNotificationChannel(Constants.NOTIFICATION_CHANNEL)
        return nm.areNotificationsEnabled() && nc != null && nc.importance > NotificationManager.IMPORTANCE_NONE
    }

    private fun createNotification() {
        val disconnectIntent = Intent()
            .setPackage(packageName)
            .setAction(Constants.INTENT_ACTION_DISCONNECT)
        val restartIntent = Intent()
            .setClassName(this, Constants.INTENT_CLASS_MAIN_ACTIVITY)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val disconnectPendingIntent = PendingIntent.getBroadcast(this, 1, disconnectIntent, flags)
        val restartPendingIntent = PendingIntent.getActivity(this, 1, restartIntent, flags)
        
        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(resources.getColor(R.color.colorPrimary, null))
            .setContentTitle(resources.getString(R.string.app_name))
            .setContentText(socket?.getName() ?: "Background Service")
            .setContentIntent(restartPendingIntent)
            .setOngoing(true)
            .addAction(NotificationCompat.Action(R.drawable.ic_clear_white_24dp, "Disconnect", disconnectPendingIntent))
        
        val notification = builder.build()
        startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification)
    }

    private fun cancelNotification() {
        stopForeground(true)
    }

    /**
     * SerialListener
     */
    override fun onSerialConnect() {
        if (connected) {
            synchronized(this) {
                listener?.let { listener ->
                    mainLooper.post {
                        this.listener?.onSerialConnect() ?: queue1.add(QueueItem(QueueType.Connect))
                    }
                } ?: queue2.add(QueueItem(QueueType.Connect))
            }
        }
    }

    override fun onSerialConnectError(e: Exception) {
        if (connected) {
            synchronized(this) {
                listener?.let { listener ->
                    mainLooper.post {
                        this.listener?.onSerialConnectError(e) ?: run {
                            queue1.add(QueueItem(QueueType.ConnectError, e))
                            disconnect()
                        }
                    }
                } ?: run {
                    queue2.add(QueueItem(QueueType.ConnectError, e))
                    disconnect()
                }
            }
        }
        // Always clear socket and connected state on error
        socket = null
        connected = false
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray>) {
        throw UnsupportedOperationException()
    }

    /**
     * reduce number of UI updates by merging data chunks.
     * Data can arrive at hundred chunks per second, but the UI can only
     * perform a dozen updates if receiveText already contains much text.
     *
     * On new data inform UI thread once (1).
     * While not consumed (2), add more data (3).
     */
    override fun onSerialRead(data: ByteArray) {
        if (connected) {
            synchronized(this) {
                listener?.let { listener ->
                    val first: Boolean
                    synchronized(lastRead) {
                        first = lastRead.datas?.isEmpty() == true // (1)
                        lastRead.add(data) // (3)
                    }
                    if (first) {
                        mainLooper.post {
                            val datas: ArrayDeque<ByteArray>
                            synchronized(lastRead) {
                                datas = lastRead.datas!!
                                lastRead.datas = ArrayDeque() // (2)
                            }
                            this.listener?.onSerialRead(datas) ?: queue1.add(QueueItem(QueueType.Read, datas))
                        }
                    }
                } ?: run {
                    if (queue2.isEmpty() || queue2.last.type != QueueType.Read) {
                        queue2.add(QueueItem(QueueType.Read))
                    }
                    queue2.last.add(data)
                }
            }
        }
    }

    override fun onSerialIoError(e: Exception) {
        if (connected) {
            synchronized(this) {
                listener?.let { listener ->
                    mainLooper.post {
                        this.listener?.onSerialIoError(e) ?: run {
                            queue1.add(QueueItem(QueueType.IoError, e))
                            disconnect()
                        }
                    }
                } ?: run {
                    queue2.add(QueueItem(QueueType.IoError, e))
                    disconnect()
                }
            }
        }
        // Always clear socket and connected state on error
        socket = null
        connected = false
    }
}