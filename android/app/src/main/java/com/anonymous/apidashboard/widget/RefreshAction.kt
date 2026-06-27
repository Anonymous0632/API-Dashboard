package com.anonymous.apidashboard.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionCallback
import androidx.glance.action.ActionParameters
import com.anonymous.apidashboard.worker.RefreshWorker

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        RefreshWorker.refreshOnce(context)
    }
}
