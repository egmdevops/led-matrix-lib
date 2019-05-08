package com.egmdevelopers.ledmatrixlib

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
class LedMatrix(var gpio: String,
                var numDevices: Int): AutoCloseable {

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
        Log.d(TAG, "init")

        //Creamos el puerto de comunicación
        val manager = PeripheralManager.getInstance()
        spiDevice = manager.openSpiDevice(gpio)

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
            Log.d(TAG, "-----1-----")
            setScanLimit(device, 7)                   //display, 8 Leds
            clearDisplay(device)                            //Limpia el display (Apaga el display)
        }
    }

    /**
     * ShutDown the device
     *
     * @param addr [Int] The address of the display to control
     * @param status [Boolean] Status of the display
     */
    @Throws(IOException::class)
    fun shutDown(addr: Int, status: Boolean) {
        //Verify numDevices is in range
        if (addr < 0 || addr >= numDevices) return
        spiTransfer(addr, OP_SHUTDOWN, if (status) 0x00 else 0x01)
    }

    /**
     * Set the number of digits (or rows) to be displayed.
     *
     * See datasheet for sideeffects of the scanlimit on the brightness of the display
     *
     * @param addr [Int] The address of the display to control
     * @param limit [Int] Number of digits to be displayed (1..8)
     * @throws IOException If hardware doesn't responds
     */
    @Throws(IOException::class)
    fun setScanLimit(addr: Int, limit: Int) {
        if (addr < 0 || addr >= numDevices) return
        if (limit >= 0 || limit < 8) {
            spiTransfer(addr, OP_SCANLIMIT, limit.toByte())
            Log.d(TAG, "-----3-----")
        }
    }

    /**
     * Set the intensity of the light
     *
     * @param addr [Int] The address of the display to control
     * @param intensity [Byte] The luminosity of the display
     * @throws IOException If hardware doesn't responds
     */
    @Throws(IOException::class)
    fun setIntensity(addr: Int, intensity: Byte) {
        if (addr < 0 || addr >= numDevices) return
        if (intensity >= 0x00 || intensity < 0x0F) {
            spiTransfer(addr, OP_INTENSITY, intensity)
            Log.d(TAG, "-----4-----")
        }
    }

    /**
     * Switch all the display leds off
     *
     * @param addr [Int] The address of the display to control
     * @throws IOException If hardware doesn't responds
     */
    @Throws(IOException::class)
    fun clearDisplay(addr: Int) {
        //Verify numDevices is in range
        if (addr < 0 || addr >= numDevices) return

        //Jump to the correct Matrix -> Led
        val offset = addr * 8

        //Set the matrix data row by row
        for (row in 0..7) {
            status[offset + row] = 0x00                       // Enciende todos = 0XFF.toByte()
            spiTransfer(addr, (OP_DIGIT0 + row).toByte(), status[offset + row])
            Log.d(TAG, "-----2-----")
        }
    }

    /**
     * TODO
     *
     * @param addr [Int] The address of the display to control
     * @param row
     * @param col
     * @param state
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
     * TODO
     *
     * @param addr [Int] The address of the display to control
     * @param row
     * @param byte
     * @throws IOException If hardware doesn't responds
     */
    @Throws(IOException::class)
    fun setRow(addr: Int, row: Int, byte: Byte) {
        if (addr < 0 || addr >= numDevices) return
        if (row < 0 || row > 7) return

        val offset = addr * 8

        status[offset + row] = byte
        spiTransfer(addr, (OP_DIGIT0 + row).toByte(), status[offset + row])
    }

    /**
     * TODO
     *
     * @param addr [Int] The address of the display to control
     * @param row
     * @param byte
     * @throws IOException If hardware doesn't responds
     */
    @Throws(IOException::class)
    fun setCol(addr: Int, col: Int, byte: Byte) {
        if (addr < 0 || addr >= numDevices) return
        if (col < 0 || col > 7) return

        var value: Byte
        for (row in 0..7) {
            value = (byte.toInt() shr (7 - row)).toByte()
            setLed(addr, row, col, (value.toInt() and 0x01) == 0x01)
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

        for (i in 0 until maxbytes) {
            spidata[i] = 0.toByte()
        }

        // put our device data into the array
        spidata[maxbytes - offset - 2] = opcode
        spidata[maxbytes - offset - 1] = data

        spidata.forEach {
            Log.d(TAG, "$it")
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