package com.ckzhang.remotecap.wear

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WatchPreviewListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/preview") {
            val intent = Intent("ACTION_PREVIEW_FRAME")
            intent.putExtra("image_data", messageEvent.data)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }
}
