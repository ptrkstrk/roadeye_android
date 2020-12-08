package com.pkos.roadeye.activities

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.pkos.roadeye.R
import com.pkos.roadeye.services.BluetoothService
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class MainActivity : AppCompatActivity() {

    companion object MainActivity{
        const val TAG : String = "MainActivity"
        //takie uuid wpisałem w serwerze. Jak coś można też uuid dynamicznie pobrac po polaczeniu z urzadzeniem (zakomentowane w changedDBreceiver),
        //ja tego uzywam do sprawdzenia czy wybrane przez usera urzadzenie jest jetsonem
        var MY_UUID: UUID = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee")
        val REQUEST_ENABLE_BT: Int = 1
    }

    // Create a BroadcastReceiver for ACTION_UUID and ACTION_STATE_CHANGED.
    private val changedBcReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> startBT()
                        BluetoothAdapter.STATE_TURNING_OFF -> Log.d(TAG, "STATE TURNING OFF")
                        BluetoothAdapter.STATE_ON -> Log.d(TAG, "STATE ON")
                        BluetoothAdapter.STATE_TURNING_ON -> Log.d(TAG, "STATE TURNING ON")
                    }
                }
            }
        }
    }

    lateinit var tts: TextToSpeech
    var btService: BluetoothService? = null
    var mIsBound = false


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(changedBcReceiver)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val btServiceIntent = Intent(this, BluetoothService::class.java)
        startService(btServiceIntent)
        val btIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(changedBcReceiver, btIntent)
        initTTS()
    }

    private fun initTTS() {
        tts = TextToSpeech(applicationContext)
        {
            if (it == TextToSpeech.SUCCESS) {
                val lang = tts.setLanguage(Locale.ENGLISH)
            }
        }
    }
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
////        val navFragment = supportFragmentManager.findFragmentById(R.id.main_fragment)
////        val connFragment = navFragment!!.childFragmentManager.primaryNavigationFragment
////        connFragment!!.onActivityResult(requestCode, resultCode, data)
//    }
//
////    private fun doUnbindService() {
////        if (mIsBound) {
////            // Detach our existing connection.
////            unbindService(mConnection)
////            mIsBound = false
////        }
////    }

    fun startBT() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent,
            REQUEST_ENABLE_BT
        )
    }


    fun checkBTPermissions() {
        var permissionCheck =
            checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION")
        permissionCheck += checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION")
        if (permissionCheck != 0) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), 1001
            ) //Any number
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                val settingsIntent = Intent(this, SettingsActivity::class.java)
                startActivity(settingsIntent)
                true
            }
//            R.id.action_bluetooth -> {
//                supportFragmentManager.beginTransaction()
//                    .replace(R.id.main_fragment, FragmentConnect())
//                    .addToBackStack(null)
//                    .commit()
//                true
//            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun playVoiceAnnouncement(text: String) {
        val tts = tts
//        try {
//            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
//            val r = RingtoneManager.getRingtone(
//                applicationContext,
//                notification
//            )
//
//            r.play()
//            Thread.sleep(800)
//            r.stop()
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
        val speech = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null)
    }
}
