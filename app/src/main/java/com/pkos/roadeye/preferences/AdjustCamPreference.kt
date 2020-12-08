package com.pkos.roadeye.preferences

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.pkos.roadeye.R


/**
 * Preference for setting the camera position
 */

class AdjustCamPreference(context: Context?, attrs: AttributeSet?) :
    Preference(context, attrs) {
    private var mImageView: ImageView? = null
    private var mPhoto: Bitmap? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        mImageView = holder!!.itemView.findViewById(R.id.cam_photo_iv) as ImageView
        mImageView!!.setImageBitmap(mPhoto)
    }

    init {
        this.layoutResource = R.layout.fragment_test_cam
        if (mPhoto == null) {
            mPhoto = BitmapFactory.decodeResource(
                getContext().resources, R.drawable.give_way_outdoor
            )
        }
    }
}