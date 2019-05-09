package com.egmdevelopers.ledmatrixlib

import android.graphics.Bitmap
import android.graphics.Color
import java.io.IOException
import com.google.android.things.pio.SpiDevice
import com.google.android.things.pio.PeripheralManager
import android.util.Log
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

/**
 * TODO
 *
 * @property gpio [String] The name of the GPIO pin to be CE in SPI interface
 * @property numDevices [Int] Number of displays to control
 */
class LedMatrix(private var gpio: RpiConstants,
                private var numDevices: Int): AutoCloseable {

    private var spiDevice   : SpiDevice? = null
    private val spidata     = ByteArray(16)    //Arreglo de comunicaciones
    private val status      = ByteArray(64)    //Status de los leds 8x8 (64)


    init { init() }

    /********************************************************************************************************************/
    /**     INTERFACES                                                                                                  */
    /********************************************************************************************************************/

    /**
     * Implementación de la interface [AutoCloseable]
     *  Termina la conexión del puerto serie
     */
    override fun close() {
        try {
            spiDevice!!.close()
        } finally {
            spiDevice = null
        }
    }

    /********************************************************************************************************************/
    /**     ACTIONS                                                                                                     */
    /********************************************************************************************************************/

    /**
     * Creates SPI communication, with [gpio] as CS
     * (Require PERIPHERAL permises in Manifest)
     *
     * @throws IOException If hardware doesn't responds
     */
    @Throws(IOException::class)
    private fun init() {
        val pin = if (gpio == RpiConstants.CS_1) RpiConstants.CS_1.gpio else RpiConstants.CS_2.gpio

        //Creamos el puerto de comunicación
        val manager = PeripheralManager.getInstance()
        spiDevice = manager.openSpiDevice(pin)

        //Configuración del puerto
        spiDevice!!.apply {
            setMode(SpiDevice.MODE0)
            setFrequency(1_000_000)     //1 MHz
            setBitsPerWord(8)
            setBitJustification(SpiDevice.BIT_JUSTIFICATION_MSB_FIRST)
        }

        //Validamos el tamaño máximo de dispositivos (8)
        if (numDevices < 1 || numDevices > 8) numDevices = 8

        //Inicializamos el display
        for (device in 0 until numDevices) {
            spiTransfer(device, OP_DISPLAYTEST, 0)    //Comunicación de prueba con el display
            setScanLimit(7)                                 //8 Row Leds
            clearDisplay()                                  //Limpia el display (Apaga los leds del display)
        }
    }

    /**
     * ShutDown the device
     *
     * @param status [Boolean] Status of the display
     */
    @Throws(IOException::class)
    fun shutDown(status: Boolean) {
        for (device in 0 until numDevices) {
            spiTransfer(device, OP_SHUTDOWN, if (status) 0x00 else 0x01)
        }
    }

    /**
     * Set the number of digits (or rows) to be displayed.
     *
     * See datasheet for side-effects of the scanlimit on the brightness of the display
     *
     * @param limit [Int] Number of digits to be displayed (1..8)
     * @throws IOException If hardware doesn't responds
     */
    @Throws(IOException::class)
    fun setScanLimit(limit: Int) {
        if (limit >= 0 || limit < 8) {
            for (device in 0 until numDevices) {
                spiTransfer(device, OP_SCANLIMIT, limit.toByte())
            }
        }
    }

    /**
     * Set the intensity of the light
     *
     * @param intensity [Byte] The luminosity of the display
     * @throws IOException If hardware doesn't responds
     */
    @Throws(IOException::class)
    fun setIntensity(intensity: Byte) {
        for (display in 0 until numDevices) {
            if (intensity >= 0x00 || intensity < 0x0F)
                spiTransfer(display, OP_INTENSITY, intensity)
            else
                spiTransfer(display, OP_INTENSITY, 0x08)
        }
    }

    /**
     * Switch all the display leds off
     *
     * @throws IOException If hardware doesn't responds
     */
    @Throws(IOException::class)
    fun clearDisplay() {
        for (display in 0 until numDevices) {
            val offset = display * 8
            for (row in 0..7) {
                status[offset + row] = 0x00
                spiTransfer(display, (OP_DIGIT0 + row).toByte(), status[offset + row])
            }
        }
    }

    /**
     * Turns on and off an unique led
     *
     * @param addr [Int] Device to show led
     * @param row [Int] Row where the selected led is
     * @param col [Int] Col where the selected led is
     * @param state Turn on and off the led
     * @throws IOException If hardware doesn't responds
     */
    @Throws(IOException::class)
    fun setLed(addr: Int, row: Int, col: Int, state: Boolean) {
        if (addr < 0 || addr >= numDevices) return
        if (row < 0 || row > 7 || col < 0 || col > 7) return

        val offset = addr * 8

        var byte = (0x80 shr col).toByte()
        if (state) {
            status[offset + row] = (status[offset + row] or byte)
        } else {
            byte = byte.inv()
            status[offset + row] = (status[offset + row] and byte)
        }
        spiTransfer(addr, (OP_DIGIT0 + row).toByte(), status[offset + row])
    }

    /**
     * Turn on a row with specified data
     *
     * @param row [Int] Row to turn on
     * @param byte [Byte] Data to show in the row
     * @throws IOException If hardware doesn't responds
     */
    @Throws(IOException::class)
    fun setRow(row: Int, byte: Byte) {
        if (row < 0 || row > 7) return

        for (display in 0 until numDevices) {
            val offset = display * 8

            status[offset + row] = byte
            spiTransfer(display, (OP_DIGIT0 + row).toByte(), status[offset + row])
        }
    }

    /**
     * Turn on a column with specified data
     *
     * @param col [Int] Column to turn on
     * @param byte [Byte] Data to show in the column
     * @throws IOException If hardware doesn't responds
     */
    @Throws(IOException::class)
    fun setCol(col: Int, byte: Byte) {
        if (col < 0 || col > 7) return

        for (display in 0 until numDevices) {
            var value: Byte
            for (row in 0..7) {
                value = (byte.toInt() shr (7 - row)).toByte()
                setLed(display, row, col, (value.toInt() and 0x01) == 0x01)
            }
        }
    }

    /**
     * Decode a bitmap no-dpi into a 8x8 matrix showable with leds
     *
     * Compare [Color.WHITE] to set the leds "on"
     *
     * @param bitmap [Bitmap] The resource to show in the matrix
     * @throws IOException If hardware doesn't responds
     */
    @Throws(IOException::class)
    fun draw(bitmap: Bitmap) {
        val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        for (row in 0..7) {
            for (device in 0 until numDevices) {
                var value = 0
                for (col in 0..7) {
                    value = value or if (scaled.getPixel((device * 8) + col, row) == Color.WHITE) 0x80 shr col else 0x00
                }
                Log.d(TAG, "DIBUJA -> ${value.toByte()}")
                setRow(row, value.toByte())
            }
        }
    }

    /********************************************************************************************************************/
    /**     SPI COMMUNICATION                                                                                           */
    /********************************************************************************************************************/

    /**
     * Send out a single command to the device
     *
     * @throws IOException If hardware doesn't responds
     */
    @Throws(IOException::class)
    private fun spiTransfer(addr: Int, opcode: Byte, data: Byte) {
        val offset = addr * 2
        val maxbytes = numDevices * 2

        Log.d(TAG, "maxBytes: $maxbytes")

        for (i in 0 until maxbytes) {
            spidata[i] = 0.toByte()
        }

        // put our device data into the array
        spidata[maxbytes - offset - 2] = opcode
        spidata[maxbytes - offset - 1] = data

        spidata.forEach {
            Log.d(TAG, "Final: $it")
        }

        spiDevice!!.write(spidata, maxbytes)
    }


    /********************************************************************************************************************/
    /**     COMPANION OBJECT                                                                                            */
    /********************************************************************************************************************/
    companion object {
        private val TAG = LedMatrix::class.java.simpleName

        //the opcodes for the MAX7221 and MAX7219
        private const val OP_NOOP           : Byte = 0x00
        private const val OP_DIGIT0         : Byte = 0x01
        private const val OP_DIGIT1         : Byte = 0x02
        private const val OP_DIGIT2         : Byte = 0x03
        private const val OP_DIGIT3         : Byte = 0x04
        private const val OP_DIGIT4         : Byte = 0x05
        private const val OP_DIGIT5         : Byte = 0x06
        private const val OP_DIGIT6         : Byte = 0x07
        private const val OP_DIGIT7         : Byte = 0x08
        private const val OP_DECODEMODE     : Byte = 0x09
        private const val OP_INTENSITY      : Byte = 0x0A
        private const val OP_SCANLIMIT      : Byte = 0x0B
        private const val OP_SHUTDOWN       : Byte = 0x0C
        private const val OP_DISPLAYTEST    : Byte = 0x0F
    }

}