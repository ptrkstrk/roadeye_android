package com.pkos.roadeye.model

import java.util.*
import kotlin.collections.ArrayList

class DetectionsQueue(override val size: Int=8) : Queue<Detection> {
    private val detections: ArrayList<Detection?> =  ArrayList(size)

    override fun add(element: Detection?): Boolean {
        detections.add(element)
        if(detections.size > size)
            remove()
        return true
    }

    override fun addAll(elements: Collection<Detection>): Boolean {
        TODO("Not yet implemented")
    }

    override fun clear() {
        detections.clear()
    }

    override fun iterator(): MutableIterator<Detection> {
        TODO("Not yet implemented")
    }

    override fun remove(): Detection? {
        return detections.removeAt(0)
    }

    override fun contains(element: Detection?): Boolean {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<Detection>): Boolean {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    override fun remove(element: Detection?): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeAll(elements: Collection<Detection>): Boolean {
        TODO("Not yet implemented")
    }

    override fun retainAll(elements: Collection<Detection>): Boolean {
        TODO("Not yet implemented")
    }

    override fun offer(e: Detection?): Boolean {
        TODO("Not yet implemented")
    }

    override fun poll(): Detection? {
        return detections.removeAt(0)
    }

    override fun element(): Detection {
        TODO("Not yet implemented")
    }

    override fun peek(): Detection? {
        return detections[0]
    }

}