package com.projectbharat.counterapp

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Build
import android.os.SystemClock

/**
 * Holds the communication route for one recognition run. The worker closes
 * it after releasing AudioRecord, before a new worker can acquire it.
 */
internal class ChantAudioRoute(private val audio: AudioManager) : AutoCloseable {
    private val previousMode = audio.mode
    private var modeChanged = false
    private var selectedDevice: AudioDeviceInfo? = null
    private var startedSco = false
    var headsetRequested = false
        private set

    fun prepare(): Int {
        val headset = if (Build.VERSION.SDK_INT >= 31) {
            audio.availableCommunicationDevices.firstOrNull { it.isBluetoothVoice() }
        } else {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        }
        headsetRequested = headset != null
        if (headset == null) return android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION

        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        modeChanged = true
        if (Build.VERSION.SDK_INT >= 31) {
            if (audio.setCommunicationDevice(headset)) selectedDevice = headset
        } else if (audio.isBluetoothScoAvailableOffCall) {
            audio.startBluetoothSco()
            startedSco = true
            val deadline = SystemClock.elapsedRealtime() + 2500L
            while (!audio.isBluetoothScoOn && SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(75)
                // Some older Android builds expose the input only after SCO connects.
                if (audio.getDevices(AudioManager.GET_DEVICES_INPUTS).any { it.isBluetoothVoice() }) break
            }
            audio.isBluetoothScoOn = true
        }
        return android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION
    }

    fun preferHeadset(record: AudioRecord) {
        if (!headsetRequested) return
        val inputs = audio.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.isBluetoothVoice() }
        val selected = selectedDevice
        val input = inputs.firstOrNull { selected != null && it.type == selected.type &&
            it.address.isNotEmpty() && it.address == selected.address }
            ?: inputs.firstOrNull { it.type == selected?.type } ?: inputs.firstOrNull()
        if (input != null) record.setPreferredDevice(input)
    }

    override fun close() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val selected = selectedDevice
                if (selected != null && audio.communicationDevice?.id == selected.id) {
                    audio.clearCommunicationDevice()
                }
            } else if (startedSco) {
                audio.isBluetoothScoOn = false
                audio.stopBluetoothSco()
            }
        } finally {
            if (modeChanged && audio.mode == AudioManager.MODE_IN_COMMUNICATION) audio.mode = previousMode
        }
    }
}

internal fun AudioDeviceInfo.isBluetoothVoice(): Boolean =
    type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        (Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_HEADSET)
