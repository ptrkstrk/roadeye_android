package com.pkos.roadeye.fragments

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.pkos.roadeye.R
import com.pkos.roadeye.activities.SettingsActivity
import com.pkos.roadeye.model.Constants
import com.pkos.roadeye.model.Constants.TEST_CAM
import com.pkos.roadeye.services.BluetoothService
import kotlinx.android.synthetic.main.fragment_test_cam.*

class FragmentAdjustCam : Fragment() {

    private lateinit var parent: SettingsActivity
    private var testRequired = false

    private val btHandler: Handler = object : Handler(Looper.getMainLooper()) {
        /*
         * handleMessage() defines the operations to perform when
         * the Handler receives a new Message to process.
         */
        var camImage: Bitmap? = null
        override fun handleMessage(msg: Message) {
            when(msg.what){
                Constants.STATE_DATA_RECEIVED -> {
                    if(testRequired) {
                        val readBuffer: ByteArray = msg.obj as ByteArray
                        val numBytes = msg.arg1
                        camImage = BitmapFactory.decodeByteArray(
                            readBuffer,
                            0,
                            msg.arg1
                        )
                        showCamImage(camImage)
                        testRequired = false
                        //imageView.setImageBitmap(image)
                        //discovered_lbl.text = temp
                    }
                }
            }
        }
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            parent.btService = (iBinder as BluetoothService.LocalBinder).instance
            parent.btService!!.mContext = context
            parent.mIsBound = true
            parent.btService!!.mHandler = btHandler
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            parent.btService!!.mHandler = null
            parent.mIsBound = false
        }
    }

    override fun onStop() {
        super.onStop()
        parent.unbindService(mConnection)
        parent.btService!!.mHandler = null
        parent.mIsBound = false
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        parent = (activity as SettingsActivity)
        parent.bindService(
            Intent(parent, BluetoothService::class.java),
            mConnection,
            Context.BIND_AUTO_CREATE
        )
        return inflater.inflate(R.layout.fragment_test_cam, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.confirm_cam_btn).setOnClickListener {
            findNavController().navigate(R.id.action_TestCamFragment_to_SettingsFragment)
        }
        view.findViewById<Button>(R.id.test_cam_btn).setOnClickListener {
            parent.btService!!.write(TEST_CAM)
            testRequired = true
        }
    }

    private fun showCamImage(camImage: Bitmap?) {
        cam_photo_iv.setImageBitmap(camImage)
    }
}