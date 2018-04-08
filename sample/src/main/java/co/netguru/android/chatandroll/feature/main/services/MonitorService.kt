package co.netguru.android.chatandroll.feature.main.services

import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import java.math.BigDecimal

class MonitorService : Service() {

    private val TAG = "monitorService"
    private val frequency = 22050
    private val channelConfiguration = AudioFormat.CHANNEL_IN_MONO
    private val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
    private val blockSize = 512
    private val toTransform = DoubleArray(blockSize)
    private var transformer: RealDoubleFFT? = null
    private var sensitivityLimit = 3.5
    private val currentVolumeLimit = 40
    private var recording: Boolean = false

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Monitor Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            running = true

            sensitivityLimit = intent.getDoubleExtra("sensitivity", 25.0)


            sensitivityTextView = sensitivityLimit

            sensitivityLimit = (sensitivityLimit + 10) / 10

            Log.d(TAG, "current sensitivity limit $sensitivityLimit")

            transformer = RealDoubleFFT(blockSize)
            recording = true
            isServiceRunning = true
            val recordThread = RecordThread()
            recordThread.start()
            Toast.makeText(this, "Congrats! My Service Started", Toast.LENGTH_LONG).show()
            return Service.START_STICKY
        } else {
            return Service.START_NOT_STICKY
        }
    }

    fun Double.roundTo2DecimalPlaces() =
            BigDecimal(this).setScale(4, BigDecimal.ROUND_HALF_UP).toDouble()

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        Log.d(TAG, "Monitor Service stopped")
    }



    fun checkDouble(dobl: Double):Boolean {
        if (dobl.isFinite() && !dobl.isNaN() && !dobl.isInfinite()) return true
          return false


    }




    internal inner class RecordThread : Thread() {

        override fun run() {
            super.run()
            val bufferSize = AudioRecord.getMinBufferSize(frequency,
                    channelConfiguration, audioEncoding)
            val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC, frequency,
                    channelConfiguration, audioEncoding, bufferSize)

            val buffer = ShortArray(blockSize)

            audioRecord.startRecording()

            while (recording) {
                 Log.d(TAG, "recording")
                val bufferReadResult = audioRecord.read(buffer, 0, blockSize)

                // Test current loudness
                val mean: Double
                var sum: Long = 0
                val currentVolume: Double
                for (i in buffer.indices) {
                    sum = sum + buffer[i] * buffer[i]
                }
                mean = sum / bufferReadResult.toDouble()
                currentVolume = 10 * Math.log10(mean)

                // FFT transform
                var i = 0
                while (i < blockSize && i < bufferReadResult) {
                    toTransform[i] = buffer[i].toDouble() / 32768.0 // signed 16 bit
                    i++
                }
                transformer!!.ft(toTransform)

                 Log.d(TAG, "outside current volume is " + currentVolume + " 89 " + toTransform[89] + " 90 " + toTransform[90] + " 91 " + toTransform[91])
                if (checkDouble(currentVolume) && checkDouble(toTransform[89]) &&checkDouble(toTransform[90]) )
                {val intent = Intent("volume_changed")
                intent.putExtra("currentVolume", currentVolume.roundTo2DecimalPlaces())
                intent.putExtra("toTransform[89]", toTransform[89].roundTo2DecimalPlaces())
                intent.putExtra("toTransform[90]", toTransform[90].roundTo2DecimalPlaces())
                sendBroadcast(intent)}
                if (currentVolume > currentVolumeLimit && toTransform[89] + toTransform[90] + toTransform[91] > sensitivityLimit) {
                    //call number or pass to activity

//                    running = false
//                    recording = false
//                    if (null != BabyAlarmActivity.babyAlarmHandler) {
//                        val msgToActivity = Message()
//                        msgToActivity.what = 0
//                        if (isServiceRunning!!)
//                            msgToActivity.obj = "Monitor Service is Running"
//                        else
//                            msgToActivity.obj = "Monitor Service is not Running"
//
//                        BabyAlarmActivity.babyAlarmHandler.sendMessage(msgToActivity)
//                    }
                }
            }

            audioRecord.stop()
        }
    }

    companion object {

        protected var monitorServiceHanlder: Handler? = null
        protected var running = false
        protected var isServiceRunning: Boolean? = false
        protected var sensitivityTextView = 35.0
        protected var number: String? = null
    }

}
