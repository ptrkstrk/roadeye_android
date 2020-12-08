package com.pkos.roadeye.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.pkos.roadeye.R
import com.pkos.roadeye.model.Detection
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * Adapter that holds 8 most recent detections received from Jetson Nano
 */

class DetectionsAdapter(private val mContext: Context) : BaseAdapter() {
    private val detections: LinkedList<Detection> = LinkedList()

    override fun getCount(): Int {
        return detections.size
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getItem(position: Int): Detection {
        return detections.elementAt(detections.size - position - 1)
    }


    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        val detection = detections.elementAt(detections.size - position - 1)

        var convView = convertView
        if (convView == null) {
            val layoutInflater = LayoutInflater.from(mContext)
            convView = layoutInflater.inflate(R.layout.detection, null)

            val imageView = convView!!.findViewById(R.id.detection_img) as ImageView
            val labelTextView = convView.findViewById(R.id.detection_lbl) as TextView
            val timeTextView = convView.findViewById(R.id.detection_time) as TextView

            val viewHolder = ViewHolder(labelTextView, timeTextView, imageView, detection.detection_time)
            convView.tag = viewHolder

        }
        val viewHolder = convView.tag as ViewHolder
        if(detection.image == null) {
            viewHolder.signImageView.setImageResource(R.drawable.stop_sign_accidents)
        }
        else{
            viewHolder.signImageView.setImageBitmap(detection.image)
        }

        viewHolder.labelTV.text = detection.label.replace("regulatory", "regul.").
                                                replace("information", "info.").
                                                replace("warning", "warn.")
        val total = 30 // max count
        viewHolder.timeTextView.text = "0s ago"
        viewHolder.time = detection.detection_time
        val runnable = object : Runnable {
            override fun run() {
                var counter = Duration.between(viewHolder.time, LocalDateTime.now()).seconds
                counter -=counter%5
                viewHolder.timeTextView.text = "" + counter + "s ago"
                if(counter<total)
                    viewHolder.timeTextView.postDelayed(this, 5000)
                else
                    viewHolder.timeTextView.text = "Over 30s ago"
            }
        }
        viewHolder.timeTextView.post(runnable)


        return convView
    }

    fun recordDetection(detection: Detection) : Boolean{
        val recorded = checkIfAlreadyRecorded(detection)
        if(!recorded) {
            if (detections.size >= 8)
                detections.removeFirst()
            detections.add(detection)
        }
        return !recorded
    }

    private fun checkIfAlreadyRecorded(detection: Detection) : Boolean {
        for(det in detections){
            if(det.label == detection.label)
                if(Duration.between(det.detection_time, detection.detection_time).seconds <= 3)
                    return true
        }
        return false
    }

    private class ViewHolder(
        val labelTV: TextView,
        val timeTextView: TextView,
        val signImageView: ImageView,
        var time: LocalDateTime
    )
}