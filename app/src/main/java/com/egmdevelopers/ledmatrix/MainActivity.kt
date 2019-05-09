package com.egmdevelopers.ledmatrix

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import com.egmdevelopers.ledmatrixlib.LedMatrix
import com.egmdevelopers.ledmatrixlib.RpiConstants
import java.io.IOException



/**
 * Skeleton of an Android Things activity.
 *
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 *
 * <pre>{@code
 * val service = PeripheralManagerService()
 * val mLedGpio = service.openGpio("BCM6")
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
 * mLedGpio.value = true
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
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
            Log.e("init", "Error loading Led Matrix", ex)
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
            R.id.btn1 -> {
                ledControl.apply {
                    setRow(row, 0xFF.toByte())
                }
                if (row == 8) row = 0 else row++
            }
            R.id.btn2 -> {
                ledControl.clearDisplay()
            }
            R.id.btn3 -> {
                status = !status
                Log.d(TAG, "STATUS: $status")
                ledControl.shutDown(status)
            }
            R.id.btn4 -> {
                ledControl.apply {
                    val row = randNum(8)
                    val col = randNum(8)
                    setLed(0, row, col, true)
                    Thread.sleep(2000)
                    setLed(0, row, col, false)
                }
            }
            R.id.btn5 -> {
                ledControl.apply {
                    setCol(col, 0xFF.toByte())
                }
                if (col == 8) col = 0 else col++
            }
            R.id.btn6 -> {
                ledControl.draw(BitmapFactory.decodeResource(resources, R.drawable.happy))
            }
            R.id.btn7 -> {
                ledControl.draw(BitmapFactory.decodeResource(resources, R.drawable.sad))
            }
        }
    }






    private fun randNum(max: Int): Int {
        val rand = (Math.random() * max).toInt()
        Log.d(TAG, "Random -> $rand")
        return rand
    }







    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }


}
