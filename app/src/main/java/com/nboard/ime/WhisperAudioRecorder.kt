package com.nboard.ime

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WhisperAudioRecorder {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val allShorts = mutableListOf<Short>()
    private var recordingJob: kotlinx.coroutines.Job? = null

    @SuppressLint("MissingPermission")
    fun startRecording(scope: kotlinx.coroutines.CoroutineScope) {
        if (isRecording) return
        allShorts.clear()
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )
        audioRecord?.startRecording()
        isRecording = true

        recordingJob = scope.launch(Dispatchers.IO) {
            val shortBuffer = ShortArray(bufferSize)
            while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                if (read > 0) {
                    synchronized(allShorts) {
                        allShorts.addAll(shortBuffer.take(read))
                    }
                }
            }
        }
    }

    suspend fun stopRecordingAndGetFloatArray(): FloatArray = withContext(Dispatchers.IO) {
        if (!isRecording) return@withContext FloatArray(0)
        isRecording = false
        
        recordingJob?.join()
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val floatArray = FloatArray(allShorts.size)
        synchronized(allShorts) {
            for (i in allShorts.indices) {
                floatArray[i] = allShorts[i] / 32768.0f
            }
        }
        return@withContext floatArray
    }
}
