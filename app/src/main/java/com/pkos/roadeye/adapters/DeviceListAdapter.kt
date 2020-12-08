package com.pkos.roadeye.adapters
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.pkos.roadeye.R
import java.util.*

/**
 * Adapter that holds the list of Bluetooth devices for the FragmentConnect fragment.
 */

class DeviceListAdapter(
    context: Context,
    val resourceId: Int,
    private val devices: ArrayList<BluetoothDevice>
) :
    ArrayAdapter<BluetoothDevice?>(context, resourceId, devices as List<BluetoothDevice?>) {
    private val layoutInflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {
        var convView = convertView
        convView = layoutInflater.inflate(resourceId, null)
        val device = devices[position]
        val deviceName =
            convView.findViewById<View>(R.id.deviceName) as TextView
        deviceName.text = device.name
        return convView
    }
}