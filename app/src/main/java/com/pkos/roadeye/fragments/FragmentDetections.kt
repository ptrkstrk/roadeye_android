package com.pkos.roadeye.fragments

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.*
import android.util.DisplayMetrics
import android.view.*
import android.widget.Button
import android.widget.GridView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.pkos.roadeye.activities.MainActivity
import com.pkos.roadeye.R
import com.pkos.roadeye.adapters.DetectionsAdapter
import com.pkos.roadeye.model.Constants.MESSAGE_ERROR
import com.pkos.roadeye.model.Constants.MESSAGE_WRITE
import com.pkos.roadeye.model.Constants.PAUSE_DETECTION
import com.pkos.roadeye.model.Constants.START_DETECTION
import com.pkos.roadeye.model.Constants.STATE_DATA_RECEIVED
import com.pkos.roadeye.model.Detection
import com.pkos.roadeye.services.BluetoothService
import com.pkos.roadeye.services.BluetoothService.BluetoothService.LBL_DATA_DELIMITER
import com.pkos.roadeye.utils.Utils
import kotlinx.android.synthetic.main.detection_popup.view.*
import kotlinx.android.synthetic.main.fragment_history.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


/**
 * Fragment displaying recent detections
 */
class FragmentDetections : Fragment() {

    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var gridView: GridView
    private lateinit var parent: MainActivity
    private lateinit var adapter: DetectionsAdapter
    private lateinit var labels: Set<String>
    private var displaying = true

    private var duration: Int = 0
    private var playAudio: Boolean = false
    private var dialog: Dialog? = null
    private var announcementInProgress: Boolean = false
    private var userDiscardedAnnouncement: Boolean = false
    private val detectionsQueue: LinkedList<Detection> = LinkedList()

    private val handler = Handler(Looper.getMainLooper())
    val runnable = {
        dialog!!.hide()
        announcementInProgress = false
        if (detectionsQueue.isNotEmpty())
                announceDetection()
    }

    private val btHandler: Handler = object : Handler(Looper.getMainLooper()) {
        /*
         * handleMessage() defines the operations to perform when
         * the Handler receives a new Message to process.
         */
        var currentLabel:String = ""
        var currentImage:Bitmap? = null
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                STATE_DATA_RECEIVED -> {
                    val readBuffer: ByteArray = msg.obj as ByteArray
                    val numBytes = msg.arg1
                    var delimiterPos = 0
                    for (i in 0 until numBytes) {
                        try {
                            val b = readBuffer[i]
                            if ("${b.toChar()}${readBuffer[i + 1].toChar()}${readBuffer[i + 2].toChar()}" == LBL_DATA_DELIMITER) {
                                delimiterPos = i
                                val labelBytes = ByteArray(delimiterPos)
                                System.arraycopy(readBuffer, 0, labelBytes, 0, delimiterPos)
                                break
                            }
                        } catch (e: Exception) {
                            //println("Exception, i = $i")
                        }
                    }
                    currentLabel = String(readBuffer, 0, delimiterPos)
                    currentLabel = Utils.convertLabel(currentLabel)
                    //println("liczba bajtow ${readBuffer.size}")

                    if (currentLabel in labels) {
                        currentImage = BitmapFactory.decodeByteArray(
                            readBuffer,
                            delimiterPos + 3,
                            numBytes - delimiterPos - 3
                        )
                        val detection = Detection(
                            currentLabel,
                            LocalDateTime.now(),
                            currentImage
                        )
                        val added = adapter.recordDetection(detection)
                        if (added) {
                            adapter.notifyDataSetChanged()
                            detectionsQueue.add(detection)
                            if (!announcementInProgress and detectionsQueue.isNotEmpty())
                                announceDetection()
                        }
                    }
                }
                MESSAGE_WRITE -> {
                    val data: ByteArray = msg.obj as ByteArray
                    val message = String(data, 0, data.size)
                    if(message == PAUSE_DETECTION) {
                        start_detection_btn.visibility = View.VISIBLE
                        stop_detection_btn.visibility = View.GONE
                    }
                    else if(message == START_DETECTION){
                        stop_detection_btn.visibility = View.VISIBLE
                        start_detection_btn.visibility = View.GONE
                    }
                }

                MESSAGE_ERROR -> {
                    val data: ByteArray = msg.obj as ByteArray
                    val message = String(data, 0, data.size)
                    if(message == PAUSE_DETECTION) {
                        Toast.makeText(parent, "Couldn't send data to Jetson. Stopping detection not succeeded", Toast.LENGTH_LONG).show()
                    }
                    else if(message == START_DETECTION){
                        Toast.makeText(parent, "Couldn't send data to Jetson. Starting detection not succeeded", Toast.LENGTH_LONG).show()
                    }
                    else{
                        Toast.makeText(parent, "Could not send data to Jetson", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        parent.bindService(
            Intent(activity, BluetoothService::class.java),
            mConnection,
            Context.BIND_AUTO_CREATE
        )
        displaying = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_history, container, false)
        // Inflate the layout for this fragment
        parent = (activity as MainActivity)
        gridView = rootView.findViewById<View>(R.id.detections_gridview) as GridView
        adapter = DetectionsAdapter(activity!!)
        val displayMetrics = DisplayMetrics()
        parent.windowManager.defaultDisplay.getMetrics(displayMetrics)
        gridView.columnWidth = (displayMetrics.widthPixels * 0.7 /2).toInt()
        gridView.adapter = adapter

        return rootView
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            //btService = (iBinder as BluetoothService.LocalBinder).instance
            parent.btService!!.mHandler = btHandler
            parent.btService!!.mContext = context
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            //btService = null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setOnClickListeners(view)

        val sp = PreferenceManager.getDefaultSharedPreferences(this.context)
        duration = sp.getString("announcement_duration", "3")!!.toInt()
        playAudio = sp.getBoolean("voice_announcements", true)
        val resId = resources.getIdentifier("sign_labels", "array", parent.packageName)
        labels = sp.getStringSet("labels_header", resources.getStringArray(resId).toSet())!!

        dialog = Dialog(parent)
        dialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        lp = WindowManager.LayoutParams()
        lp.copyFrom(dialog!!.window!!.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.MATCH_PARENT
//        lp.horizontalMargin = 128f
//        lp.verticalMargin = 128f

    }

    private fun setOnClickListeners(view: View) {
        view.findViewById<Button>(R.id.start_detection_btn).setOnClickListener{
            val msgSent = parent.btService!!.write(START_DETECTION)
            if(!msgSent)
                Toast.makeText(parent, "Couldn't send data to Jetson. Starting detection not succeeded", Toast.LENGTH_LONG).show()
        }
        view.findViewById<Button>(R.id.stop_detection_btn).setOnClickListener{
            val msgSent = parent.btService!!.write(PAUSE_DETECTION)
            if(!msgSent)
                Toast.makeText(parent, "Couldn't send data to Jetson. Stopping detection not succeeded", Toast.LENGTH_LONG).show()
        }

        gridView.setOnItemClickListener { _, _, position, _ ->
            val selectedDetection = adapter.getItem(position)
            val dialogView = fillDialog(selectedDetection)
            dialogView.conf_new_sign_btn.setOnClickListener {
                dialog!!.hide()
            }
            dialog!!.setContentView(dialogView)
            dialog!!.show()
            dialog!!.window!!.attributes = lp
        }

//        view.findViewById<Button>(R.id.bt_btn).setOnClickListener {
//            findNavController().navigate(R.id.action_HistoryFragment_to_ConnectFragment)
//        }
    }

    override fun onPause() {
        super.onPause()
        duration = 2
        dialog!!.dismiss()
        displaying = false
    }

    override fun onStop() {
        super.onStop()
        parent.unbindService(mConnection)
        parent.mIsBound = false
    }

    fun announceDetection() {
        dialog!!.hide()
        val detection = detectionsQueue.first()
        detectionsQueue.removeFirst()
        userDiscardedAnnouncement = false
        announcementInProgress = true
        if(playAudio)
            parent.playVoiceAnnouncement(detection.label)

        if(displaying){
            val view = fillDialog(detection)

            view.conf_new_sign_btn.setOnClickListener {
                dialog!!.hide()
                announcementInProgress = false
                handler.removeCallbacksAndMessages(null)
                if(detectionsQueue.isNotEmpty())
                    announceDetection()
            }
            dialog!!.setContentView(view)
            dialog!!.show()
            dialog!!.window!!.attributes = lp
        }
        handler.postDelayed(runnable, (duration * 1000).toLong())

    }

    private fun fillDialog(detection: Detection): View {
        val view = layoutInflater.inflate(
            R.layout.detection_popup
            , null
        )
        view.current_detection_iv.setImageBitmap(detection.image)
        view.curr_detection_lbl.text = detection.label
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val formattedTime: String = formatter.format(detection.detection_time)
        view.curr_detection_time.text = formattedTime
        return view
    }

}
