package com.pkos.roadeye.fragments

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.pkos.roadeye.activities.MainActivity
import com.pkos.roadeye.R
import com.pkos.roadeye.adapters.DeviceListAdapter
import com.pkos.roadeye.model.Constants
import com.pkos.roadeye.model.Constants.MESSAGE_CONNECTION_SUCCESS
import com.pkos.roadeye.services.BluetoothService
import kotlinx.android.synthetic.main.device_adapter_view.view.*
import kotlinx.android.synthetic.main.fragment_connect.*
import java.lang.Exception

/**
 * Fragment for displaying paired and available devices and connecting with Jetson
 */
class FragmentConnect : Fragment() {
    lateinit var deviceListAdapter: DeviceListAdapter
    lateinit var pairedAdapter: DeviceListAdapter
    private lateinit var parent: MainActivity
    private var selectedView: View? = null
    private var displaying = true

    private val btConnectionHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_CONNECTION_SUCCESS -> {
                    if(displaying)
                        startDetection()
                }
                Constants.MESSAGE_CONNECTION_ERROR -> {
                    informAboutWrongDevice()
                }
            }
        }
    }

    private val discDevicesBcReceiver = object : BroadcastReceiver(){
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothDevice.ACTION_FOUND ->{
                    val device : BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    parent.btService!!.discoveredDevices.add(device)
                    Log.d(MainActivity.TAG, "onReceive:" + device.name + ":"+ device.address)
                    deviceListAdapter.notifyDataSetChanged()
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED ->{
                    progressBar.visibility = View.VISIBLE
                    findUnpairedDevices_btn.text = "CANCEL"
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED ->{
                    progressBar.visibility = View.GONE
                    findUnpairedDevices_btn.text = "DISCOVER"
                }
            }
        }
    }

    // BroadcastReceiver for ACTION_UUID
    private val deviceConnectedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothDevice.ACTION_UUID -> {
                    val deviceExtra: BluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                    var correct_device = false
                    Log.d(MainActivity.TAG, "DeviceExtra address - " + deviceExtra.address)
                    Log.d(MainActivity.TAG, "DeviceExtra name - " + deviceExtra.name)
                    showPairedDevices()
                    if (uuidExtra != null) {
                        for (p in uuidExtra) {
                            println("uuidExtra - $p")
                            if (p.toString() == MainActivity.MY_UUID.toString()) {
                                println("correct")
                                correct_device = true
                            }
                        }
                        val connected =
                            if(correct_device)
                                parent.btService!!.connectWithDevice()
                            else
                                false
                        if(!connected)
                            informAboutWrongDevice()
                    }
                    if(selectedView != null)
                        selectedView!!.deviceState.visibility = View.VISIBLE
                }
            }
        }
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            parent.btService = (iBinder as BluetoothService.LocalBinder).instance
            parent.btService!!.mHandler = btConnectionHandler
            parent.btService!!.mContext = context
            parent.startBT()
            println("fill view")
            fillView()
            parent.mIsBound = true
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            parent.mIsBound = false
            //btService = null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_connect, container, false)
        parent = (activity as MainActivity)
        activity!!.bindService(
            Intent(activity, BluetoothService::class.java),
            mConnection,
            Context.BIND_AUTO_CREATE
        )


        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setOnClickListeners()
        super.onViewCreated(view, savedInstanceState)

        val btIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        btIntent.addAction(BluetoothDevice.ACTION_UUID)
        parent.registerReceiver(deviceConnectedReceiver, btIntent)
        displaying = true
    }

    override fun onDestroy() {
        super.onDestroy()
        parent.unregisterReceiver(deviceConnectedReceiver)
        parent.unbindService(mConnection)
        parent.mIsBound = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        displaying = false
    }


    private fun startDetection() {
        if(parent.btService!!.selectedDevice!= null) {
            try {
                findNavController().navigate(R.id.action_ConnectFragment_to_HistoryFragment)
            }
            catch (e: Exception){
                findNavController().navigateUp()
            }
        }
        else
            informAboutWrongDevice()
    }

    private fun informAboutWrongDevice() {
        if(displaying)
            Toast.makeText(parent, "Select Jetson from the list", Toast.LENGTH_LONG).show()
        if(selectedView != null)
            selectedView!!.deviceState.visibility = View.GONE
    }

    private fun fillView() {
        deviceListAdapter = DeviceListAdapter(context!!,
            R.layout.device_adapter_view, parent.btService!!.discoveredDevices)
        lvNewDevices.adapter = deviceListAdapter
        pairedAdapter = DeviceListAdapter(context!!,
            R.layout.device_adapter_view, parent.btService!!.pairedDevices)
        lvPairedDevices.adapter = pairedAdapter
        showPairedDevices()
    }

    private fun showPairedDevices() {
        parent.btService!!.updatePairedDevices()
        pairedAdapter.notifyDataSetChanged()
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if(requestCode == MainActivity.REQUEST_ENABLE_BT
//            && resultCode == AppCompatActivity.RESULT_OK
//        )
//    }

    private fun discoverDevices() {
        Log.d(MainActivity.TAG, "looking for devices")
        parent.btService!!.discoveredDevices.clear()
        val discDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
        discDevicesIntent.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        discDevicesIntent.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        parent.registerReceiver(discDevicesBcReceiver, discDevicesIntent)
        parent.btService!!.btAdapter!!.startDiscovery()
        parent.checkBTPermissions()
    }

    private fun setOnClickListeners() {
        findUnpairedDevices_btn.setOnClickListener{
            if(parent.btService!!.btAdapter!!.isDiscovering)
                parent.btService!!.btAdapter!!.cancelDiscovery()
            else
                discoverDevices()
        }
        lvNewDevices.setOnItemClickListener { adapter, view, position, _ ->
            if(selectedView != null)
                selectedView!!.isSelected = false
            //lvPairedDevices.isSelected = false
            selectedView = view
            view.isSelected = true
            confirm_jetson_sel_btn.visibility = View.VISIBLE
            parent.btService!!.selectedDevice = adapter.getItemAtPosition(position) as BluetoothDevice?
        }
        lvPairedDevices.setOnItemClickListener { adapter, view, position, _ ->
            if(selectedView != null)
                selectedView!!.isSelected = false
            selectedView = view
            view.isSelected = true
            confirm_jetson_sel_btn.visibility = View.VISIBLE
            parent.btService!!.selectedDevice = adapter.getItemAtPosition(position) as BluetoothDevice?
        }
        confirm_jetson_sel_btn.setOnClickListener{
            if(parent.btService!!.btAdapter!!.isDiscovering)
                parent.btService!!.btAdapter!!.cancelDiscovery()
            val result = parent.btService!!.selectedDevice!!.fetchUuidsWithSdp()
            selectedView!!.deviceState.visibility = View.VISIBLE
            selectedView!!.isSelected = true
            //            val uuids = selectedDevice!!.uuids
//            var foundCorrectUUID = false
//            for (uuid in uuids) {
//                Log.d(TAG, uuid.uuid.toString())
//                if (uuid.uuid == MY_UUID)
//                    foundCorrectUUID = true
//            }
        }
    }

    private fun doUnbindService() {
        if (parent.mIsBound) {
            // Detach our existing connection.
            parent.unbindService(mConnection)
            parent.mIsBound = false
        }
    }
}