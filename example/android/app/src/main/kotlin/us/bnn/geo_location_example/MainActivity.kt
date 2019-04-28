package us.bnn.geo_location_example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log

import io.flutter.app.FlutterActivity
import io.flutter.plugins.GeneratedPluginRegistrant
import us.bnn.geo_location.GeoLocationPlugin
import us.bnn.geo_location.LocationUpdatesService

class MainActivity: FlutterActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    GeneratedPluginRegistrant.registerWith(this)
  }

  private val TAG = "MainActivity"

  // Tracks the bound state of the service.
  private var mBound = false

  // Monitors the state of the connection to the service.
  private val mServiceConnection = object : ServiceConnection {

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
      Log.d(TAG, "onServiceConnected $name")
      val binder = service as LocationUpdatesService.LocalBinder
      GeoLocationPlugin.plugin?.mService = binder.service

      mBound = true
    }

    override fun onServiceDisconnected(name: ComponentName) {
      Log.d(TAG, "onServiceDisconnected $name")
      GeoLocationPlugin.plugin?.mService = null
      mBound = false
    }
  }

  override fun onStart() {
    super.onStart();
    Log.d(TAG, "onStart")
    // Bind to the service. If the service is in foreground mode, this signals to the service
    // that since this activity is in the foreground, the service can exit foreground mode.
    bindService(Intent(this, LocationUpdatesService::class.java), mServiceConnection,
            Context.BIND_AUTO_CREATE)
  }

  override fun onStop() {
    Log.d(TAG, "onStop")
    if (mBound) {
      // Unbind from the service. This signals to the service that this activity is no longer
      // in the foreground, and the service can respond by promoting itself to a foreground
      // service.
      unbindService(mServiceConnection);
      mBound = false;
    }

    super.onStop()
  }


}
