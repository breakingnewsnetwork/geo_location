package us.bnn.geo_location

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar

class GeoLocationPlugin(context: Context, activity: Activity?) : MethodCallHandler {
    private val mContext = context
    private val mActivity = activity
    // A reference to the service used to get location updates.
    var mService: LocationUpdatesService? = null

    // Tracks the bound state of the service.
    private var mBound = false

    private val mServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.d(TAG, "onServiceConnected $name")
            val binder = service as LocationUpdatesService.LocalBinder
            mService = binder.service

            mBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "onServiceDisconnected $name")
            mService = null
            mBound = false
        }
    }

    companion object {

        private const val CHANNEL = "plugins.flutter.io/geolocation_plugin"
        private const val TAG = "geo_location_plugin"

        @JvmStatic
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

        var plugin: GeoLocationPlugin? = null

        @JvmStatic
        fun registerWith(registrar: Registrar) {
            plugin = GeoLocationPlugin(registrar.context(), registrar.activity())
            val channel = MethodChannel(registrar.messenger(), CHANNEL)
            channel.setMethodCallHandler(plugin)
        }

        @JvmStatic
        fun reRegisterAfterReboot(context: Context) {

        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val args = call.arguments() as? ArrayList<*>
        when (call.method) {
            "LocationUpdatesService.initializeService" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mActivity?.requestPermissions(REQUIRED_PERMISSIONS, 12312)
                }
                LocationUpdatesService.initializeService(mContext, args)


                result.success(true)
            }
            "LocationUpdatesService.registerGeoLocation" -> {
                val callbackHandle = args!![0] as Long
                val username = args!![1] as String
                val deviceId = args!![2] as String
                mService!!.startGeoLocationService(mContext)
                mService!!.requestLocationUpdates(callbackHandle, username, deviceId)
                result.success(true)
            }
            "LocationUpdatesService.removeGeoLocation" -> {
                mService!!.removeLocationUpdates();
                result.success(true)
            }
            else -> result.notImplemented()
        }
    }
}
