package com.ckzhang.remotecap

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearMessageListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "WearMsgListener"
        private const val SHUTTER_PATH = "/shutter_command"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == SHUTTER_PATH) {
            Log.d(TAG, "收到手錶拍照指令 (Shutter command received)!")
            // TODO: 在第二階段將這裡串接無障礙服務，模擬按下音量鍵
        } else {
            super.onMessageReceived(messageEvent)
        }
    }
}