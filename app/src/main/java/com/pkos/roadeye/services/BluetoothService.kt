package com.pkos.roadeye.services

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import android.widget.Toast
import com.pkos.roadeye.activities.MainActivity
import com.pkos.roadeye.model.Constants.DEBUG_TAG
import com.pkos.roadeye.model.Constants.MESSAGE_CONNECTION_ERROR
import com.pkos.roadeye.model.Constants.MESSAGE_CONNECTION_SUCCESS
import com.pkos.roadeye.model.Constants.MESSAGE_ERROR
import com.pkos.roadeye.model.Constants.MESSAGE_WRITE
import com.pkos.roadeye.model.Constants.STATE_DATA_RECEIVED
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception
import java.nio.charset.Charset


/**
 * This service starts when the app is started and runs in the background.
 * It is responsible for connecting to other device, handling input/output data and maintaining connection.
 */
class BluetoothService : Service() {
    var selectedDevice: BluetoothDevice? = null
    var btAdapter : BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    val pairedDevices = ArrayList<BluetoothDevice>()
    val discoveredDevices = ArrayList<BluetoothDevice>()
    var mHandler :Handler? = null
    var mContext: Context? = null
    var transferThread: TransferDataThread? = null
    var clientThread: ConnectThread? = null
    private var mBinder: IBinder = LocalBinder()

    companion object BluetoothService{
        const val LBL_DATA_DELIMITER = "&&&"
        const val DATA_START_SYMBOL = "###"
        const val SIZE_START_SYMBOL = "$$$"
    }


    override fun onCreate() {
        super.onCreate()
        //mBinder = LocalBinder()
        if (btAdapter == null)
            Toast.makeText(mContext, "Device doesn't support Bluetooth", Toast.LENGTH_SHORT).show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mHandler != null) {
            mHandler = null
        }
    }

    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        val instance: com.pkos.roadeye.services.BluetoothService
            // Return this instance of LocalService so clients can call public methods
            get() = this@BluetoothService
    }

    override fun onBind(intent: Intent?): IBinder? {
        return mBinder
    }

    fun updatePairedDevices(){
        pairedDevices.clear()
        val pairedSet: Set<BluetoothDevice>? = btAdapter!!.bondedDevices
        pairedSet?.forEach { device ->
            pairedDevices.add(device)
            device.bondState
        }
    }

//    fun startBT() {
//        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//        mContext!!.startActivity(enableBtIntent)//#, DetectionsActivity.REQUEST_ENABLE_BT)
//    }

    fun connectWithDevice() : Boolean {
        if(selectedDevice!= null){
            if(transferThread != null) {
                transferThread!!.interrupt()
                transferThread!!.cancel()
            }
            if(clientThread != null) {
                clientThread!!.cancel()
                clientThread!!.interrupt()
            }
            //Thread.sleep(500)
            clientThread = ConnectThread(selectedDevice!!)
            clientThread!!.start()
            return true
        }
        else
            return false
    }

    fun write(message: String) : Boolean {
        if (transferThread != null) {
            transferThread!!.write(message.toByteArray())
            return true
        } else {
            Toast.makeText(mContext, "Not connected to Jetson", Toast.LENGTH_LONG).show()
            return false
        }
    }


    /**
     * Thread responsible for establishing connection with Jetson and starting TransferThread.
     */
    inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(MainActivity.MY_UUID)
        }

        public override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            if(btAdapter!!.isDiscovering)
                btAdapter!!.cancelDiscovery()

            //mmSocket?.use { socket ->
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
            try {
                mmSocket!!.connect()
                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                transferThread = TransferDataThread(mmSocket!!)
                transferThread!!.start()
                val connectMsg = mHandler!!.obtainMessage(MESSAGE_CONNECTION_SUCCESS, -1, -1)
                mHandler!!.sendMessage(connectMsg)
            }
            catch(e: Exception){
                Log.e(MainActivity.TAG, "Could not connect to socket", e)
                val connectionErrorMsg = mHandler!!.obtainMessage(MESSAGE_CONNECTION_ERROR, -1, -1)
                mHandler!!.sendMessage(connectionErrorMsg)
            }

            //}
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(MainActivity.TAG, "Could not close the client socket", e)
                return
            }
        }
    }

    /**
     * Thread responsible for obtaining and sending data from/to other devices
     */
    inner class TransferDataThread(val mmSocket: BluetoothSocket) : Thread(){
        private var mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private var buffer = ByteArray(1024)
        private var lengthTurn = true
        private var index = 0
        private var totalNumOfBytes = 0
        private var data = ByteArray(1024)
        private var numBytes:Int = 0
        private var startedReadingData = false

        override fun run(){
            while (!currentThread().isInterrupted) {
                // Read from the InputStream.
                data = ByteArray(mmInStream.available())
                numBytes = mmInStream.read(data)
                try {
                    //mmInStream = mmSocket.inputStream
                    //println("available ${mmInStream.available()}")
                    if(lengthTurn) {
                        handleDataSize()
                    }
                    else {
                        handleData()
                    }
                } catch (e: Exception) {
                    Log.d(MainActivity.TAG, "Input stream was disconnected", e)
                    print(e.message)
                    break
                }
            }
            println("interrupted")
        }

        private fun handleDataSize() {
            index = 0
            if(numBytes> 0){
                var i =0
                //start size
                var b = data[i].toChar()
                while("$b${data[i+1].toChar()}${data[i+2].toChar()}" != SIZE_START_SYMBOL) {
                    i++
                    b = data[i].toChar()
                }
                i+=3
                val sizeStartPos = i

                //read size
                b = data[i].toChar()
                while(b.isDigit() && i+1 < data.size) {
                    i++
                    b = data[i].toChar()
                }
                if(!b.isDigit())
                    i--

                val lengthBytes = ByteArray(i - sizeStartPos + 1)
                System.arraycopy(data, sizeStartPos, lengthBytes, 0, i - sizeStartPos + 1)
                val dataLength = String(lengthBytes, Charset.forName("UTF-8"))
                totalNumOfBytes = Integer.parseInt(dataLength)
                println("dlugosc danych $totalNumOfBytes")
                println("i $i")
                println("sizeStartPos $sizeStartPos")
                println("data.size ${data.size}")
                buffer = ByteArray(totalNumOfBytes)

                //write(CONFIRM_RECEIVE_LENGTH.toByteArray())
                lengthTurn = false
                if(i+1< data.size) {
                    println("handleData from size")
                    handleData()
                }
            }
        }

        private fun handleData() {
            if(numBytes > 0) {
                var i = 0
                var dataStartPos = 0
                if(!startedReadingData) {
                    //start data
                    var b = data[i].toChar()
                    while ("$b${data[i+1].toChar()}${data[i+2].toChar()}" != DATA_START_SYMBOL) {
                        i++
                        b = data[i].toChar()
                    }
                    dataStartPos = i+3
                    startedReadingData = true
                }
                println("data start $dataStartPos")

                //check data length
                var b = data[i].toChar()
                var sizeStarted = false
                while (i + 2 < data.size && !sizeStarted)
                {
                    if("$b${data[i+1].toChar()}${data[i+2].toChar()}" == SIZE_START_SYMBOL)
                        sizeStarted = true
                    else {
                        i++
                        b = data[i].toChar()
                    }
                }
                if(!sizeStarted && i > 0)
                    i++
                else
                    i--
                val dataLength = i+1 - dataStartPos
                println("data length $dataLength")
                println("size started $sizeStarted")
                if(dataLength < 3) {
                    for (j in 0 until dataLength)
                        print(data[dataStartPos + j])
                }
                //read data
                System.arraycopy(data, dataStartPos, buffer, index, dataLength)
                index += dataLength//numBytes
                if (index >= totalNumOfBytes) {
                    println("index $index")
                    if (mHandler != null) {
                        val msg: Message? = mHandler!!.obtainMessage(
                            STATE_DATA_RECEIVED,
                            buffer.size,
                            -1,
                            buffer
                        )
                        msg!!.sendToTarget()
                    }
                    //write(CONFIRM_RECEIVE_DATA.toByteArray())
                    //lengthTurn = true
                    startedReadingData = false
                    if(i+1< data.size) {
                        println("handleSize from data")
                        handleDataSize()
                    }
                    else
                        lengthTurn = true

                }
            }
        }

        fun write(bytes: ByteArray) {
            try {
                println("sending ${String(bytes,0,bytes.size)}")
                mmOutStream.write(bytes)
                mmOutStream.flush()
            } catch (e: IOException) {
                Log.e(DEBUG_TAG, "Error occurred when sending data", e)

                // Send a failure message back to the activity.
                val writeErrorMsg = mHandler!!.obtainMessage(MESSAGE_ERROR, -1, -1, bytes)
                mHandler!!.sendMessage(writeErrorMsg)
                return
            }

            // Share the sent message with the UI activity.
            if(mHandler != null) {
                val writtenMsg = mHandler!!.obtainMessage(
                    MESSAGE_WRITE, -1, -1, bytes
                )
                writtenMsg.sendToTarget()
            }
        }

        // Call this method from the main activity to shut down the connection.
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(DEBUG_TAG, "Could not close the connect socket", e)
            }
        }
    }
}