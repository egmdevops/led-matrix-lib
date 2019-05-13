package com.egmdevelopers.ledmatrix

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import com.egmdevelopers.ledmatrixlib.LedMatrix
import com.egmdevelopers.ledmatrixlib.RpiConstants
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException



/**
 *
 *
 */
class MainActivity : Activity() {

    private lateinit var ledControl: LedMatrix
    private var status = false
    private var row = 0
    private var col = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        try {
            initPeripherals()
        } catch(ex: IOException) {
            Log.e(TAG, "Error loading Led Matrix", ex)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        ledControl.apply {
            clearDisplay()
            shutDown(true)
            close()
        }
    }

    private fun initPeripherals() {
        ledControl = LedMatrix(RpiConstants.CS_2, 1)
        ledControl.apply {
            shutDown(false)     //Enable display
            clearDisplay()             //Set all leds to 0
            setIntensity(0x01)
        }
    }

    fun onClick(view: View) {
        when (view.id) {
            R.id.btnSetDigit    -> ledControl.showDigit(randNum(9))
            R.id.btnClear       -> ledControl.clearDisplay()
            R.id.btnClose       -> finish()
        }
    }






    private fun randNum(max: Int): Int = (Math.random() * max).toInt()

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }


}
