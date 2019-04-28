// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package us.bnn.geo_location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.flutter.view.FlutterMain


class GeoLocationBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "GeoLocationBroadcastRec"
    }
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive geolocation!")
        FlutterMain.ensureInitializationComplete(context, null)
        LocationUpdatesService.enqueueWork(context, intent)
    }
}