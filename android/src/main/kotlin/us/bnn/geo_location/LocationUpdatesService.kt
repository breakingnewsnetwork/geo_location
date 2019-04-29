package us.bnn.geo_location

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.core.app.JobIntentService
import androidx.core.app.NotificationCompat

import android.util.Log

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import io.flutter.view.FlutterCallbackInformation
import io.flutter.view.FlutterMain
import io.flutter.view.FlutterNativeView
import io.flutter.view.FlutterRunArguments
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


/**
 * A bound and started service that is promoted to a foreground service when location updates have
 * been requested and all clients unbind.
 *
 * For apps running in the background on "O" devices, location is computed only once every 10
 * minutes and delivered batched every 30 minutes. This restriction applies even to apps
 * targeting "N" or lower which are run on "O" devices.
 *
 * This sample show how to use a long-running service for location updates. When an activity is
 * bound to this service, frequent location updates are permitted. When the activity is removed
 * from the foreground, the service promotes itself to a foreground service, and location updates
 * continue. When the activity comes back to the foreground, the foreground service stops, and the
 * notification assocaited with that service is removed.
 */
class LocationUpdatesService : Service(), MethodChannel.MethodCallHandler {

    private val queue = ArrayDeque<List<Any>>()
    private lateinit var mBackgroundChannel: MethodChannel
    private lateinit var mContext: Context

    private val mBinder = LocalBinder()

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private var mChangingConfiguration = false

    private var mNotificationManager: NotificationManager? = null

    /**
     * Contains parameters used by [com.google.android.gms.location.FusedLocationProviderApi].
     */
    private var mLocationRequest: LocationRequest? = null

    /**
     * Provides access to the Fused Location Provider API.
     */
    private var mFusedLocationClient: FusedLocationProviderClient? = null

    /**
     * Callback for changes in location.
     */
    private var mLocationCallback: LocationCallback? = null

    private var mServiceHandler: Handler? = null

    /**
     * Returns the {@link NotificationCompat} used as part of the foreground service.
     */
    private fun getNotification(): Notification {
        val intent = Intent(this, LocationUpdatesService::class.java)

        val text = Utils.getLocationText(mLocation)

        // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)

        // The PendingIntent to launch activity.
        val c = Class.forName(MAIN_ACTIVITY_CLASS)
        val activityPendingIntent = PendingIntent.getActivity(this, 0,
                Intent(this, c), 0)
        val imageId = resources.getIdentifier("ic_stat_name", "drawable", packageName)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .addAction(imageId, getString(R.string.launch_activity), activityPendingIntent)
                .setContentText(text)
                .setContentTitle(Utils.getLocationTitle(this))
                .setOngoing(true)
                .setSmallIcon(imageId)
                .setTicker(text)
                .setWhen(System.currentTimeMillis())

        // Set the Channel ID for Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID) // Channel ID
            builder.priority = NotificationManager.IMPORTANCE_LOW
        }

        return builder.build()
    }

    /**
     * The current location.
     */
    private var mLocation: Location? = null

    override fun onCreate() {
        Log.i(TAG, "onCreate")
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)
                onNewLocation(locationResult!!.lastLocation)
            }
        }

        startGeoLocationService(this)

        createLocationRequest()
        getLastLocation()

        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        mServiceHandler = Handler(handlerThread.looper)
        mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.location_updates_label)
            // Create the channel for the notification
            val mChannel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)

            // Set the Notification Channel for the Notification Manager.
            mNotificationManager!!.createNotificationChannel(mChannel)
        }


    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")
        val startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION,
                false)

        // We got here because the user decided to remove location updates from the notification.
        if (startedFromNotification) {
            removeLocationUpdates()
            stopSelf()
        }
        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i(TAG, "onConfigurationChanged")
        mChangingConfiguration = true
    }

    override fun onBind(intent: Intent): IBinder? {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.
        Log.i(TAG, "in onBind()")
        stopForeground(true)
        mChangingConfiguration = false
        return mBinder
    }

    override fun onRebind(intent: Intent) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        Log.i(TAG, "in onRebind()")
        stopForeground(true)
        mChangingConfiguration = false
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "Last client unbound from service")

        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!mChangingConfiguration && Utils.requestingLocationUpdates(this)) {
            Log.i(TAG, "Starting foreground service")

            startForeground(NOTIFICATION_ID, getNotification())
        }
        return true // Ensures onRebind() is called when a client re-binds.
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        mServiceHandler!!.removeCallbacksAndMessages(null)
    }

    /**
     * Makes a request for location updates. Note that in this sample we merely log the
     * [SecurityException].
     */
    fun requestLocationUpdates(callbackHandle: Long, username: String, deviceId: String) {
        Log.i(TAG, "Requesting location updates $callbackHandle")
        CALLBACK_HANDLE = callbackHandle
        Utils.setRequestingLocationUpdates(this, true)
        Utils.setUsername(this, username)
        Utils.setDeviceId(this, deviceId)
        startService(Intent(applicationContext, LocationUpdatesService::class.java))
        try {
            mFusedLocationClient!!.requestLocationUpdates(mLocationRequest,
                    mLocationCallback!!, Looper.myLooper())
        } catch (unlikely: SecurityException) {
            Utils.setRequestingLocationUpdates(this, false)
            Log.e(TAG, "Lost location permission. Could not request updates. $unlikely")
        }
    }

    /**
     * Removes location updates. Note that in this sample we merely log the
     * [SecurityException].
     */
    fun removeLocationUpdates() {
        Log.i(TAG, "Removing location updates")
        try {
            mFusedLocationClient!!.removeLocationUpdates(mLocationCallback!!)
            Utils.setRequestingLocationUpdates(this, false)
            stopSelf()
        } catch (unlikely: SecurityException) {
            Utils.setRequestingLocationUpdates(this, true)
            Log.e(TAG, "Lost location permission. Could not remove updates. $unlikely")
        }

    }

    private fun getLastLocation() {
        try {
            Log.i(TAG, "getLastLocation")
            mFusedLocationClient!!.lastLocation
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful && task.result != null) {
                            mLocation = task.result
                        } else {
                            Log.w(TAG, "Failed to get location.")
                        }
                    }
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission.$unlikely")
        }

    }

    private fun onNewLocation(location: Location) {
        Log.i(TAG, "New location: $location")

        mLocation = location

        // Notify anyone listening for broadcasts about the new location.
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, location)
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        //val callbackHandle = intent.getLongExtra(CALLBACK_HANDLE_KEY, 0)
        val username = Utils.getUsername(this)
        val deviceId = Utils.getDeviceId(this)
        val locationList = listOf(location.latitude, location.longitude)
        val infoList = listOf(username, deviceId)
        val geoLocationUpdateList = listOf(CALLBACK_HANDLE, locationList, infoList)

        synchronized(sServiceStarted) {
            if (!sServiceStarted.get()) {
                // Queue up geofencing events while background isolate is starting
                queue.add(geoLocationUpdateList)
            } else {
                // Callback method name is intentionally left blank.
                mBackgroundChannel.invokeMethod("", geoLocationUpdateList)
            }
        }

        // Update notification content if running as a foreground service.
        if (serviceIsRunningInForeground(this)) {
            // mNotificationManager!!.notify(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Sets the location request parameters.
     */
    private fun createLocationRequest() {
        mLocationRequest = LocationRequest()
        mLocationRequest!!.interval = UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest!!.fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        val service: LocationUpdatesService
            get() = this@LocationUpdatesService
    }

    /**
     * Returns true if this is a foreground service.
     *
     * @param context The [Context].
     */
    fun serviceIsRunningInForeground(context: Context): Boolean {
        val manager = context.getSystemService(
                Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (javaClass.name == service.service.className) {
                if (service.foreground) {
                    return true
                }
            }
        }
        return false
    }

    fun startGeoLocationService(context: Context) {
        Log.d(TAG, "GeoLocationService startGeoLocationService")
        synchronized(sServiceStarted) {
            mContext = context
            if (sBackgroundFlutterView == null) {
                val callbackHandle = context.getSharedPreferences(
                        SHARED_PREFERENCES_KEY,
                        Context.MODE_PRIVATE)
                        .getLong(CALLBACK_DISPATCHER_HANDLE_KEY, 0)

                Log.d(TAG, "GeoLocationService startGeoLocationService $callbackHandle")

                val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)
                if (callbackInfo == null) {
                    Log.e(TAG, "Fatal: failed to find callback")
                    return
                }
                Log.i(TAG, "Starting LocationUpdatesService...$callbackHandle ${callbackInfo.callbackName}")
                sBackgroundFlutterView = FlutterNativeView(context, true)

                // val registry = sBackgroundFlutterView!!.pluginRegistry
                // sPluginRegistrantCallback.registerWith(registry)
                val args = FlutterRunArguments()
                args.bundlePath = FlutterMain.findAppBundlePath(context)
                args.entrypoint = callbackInfo.callbackName
                args.libraryPath = callbackInfo.callbackLibraryPath

                sBackgroundFlutterView!!.runFromBundle(args)
                setBackgroundFlutterView(sBackgroundFlutterView)
            }
        }
        mBackgroundChannel = MethodChannel(sBackgroundFlutterView,
                "plugins.flutter.io/geolocation_plugin_background")
        mBackgroundChannel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall?, result: MethodChannel.Result?) {
        when (call?.method) {
            "LocationUpdatesService.initialized" -> {
                synchronized(sServiceStarted) {
                    while (!queue.isEmpty()) {
                        mBackgroundChannel.invokeMethod("", queue.remove())
                    }
                    sServiceStarted.set(true)
                }
            }
            "LocationUpdatesService.promoteToForeground" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mContext.startForegroundService(Intent(mContext, LocationUpdatesService::class.java))
                }
            }
            "LocationUpdatesService.demoteToBackground" -> {
                val intent = Intent(mContext, LocationUpdatesService::class.java)
                intent.action = ACTION_SHUTDOWN
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mContext.startForegroundService(intent)
                }
            }
            else -> result?.notImplemented()
        }
        result?.success(null)
    }

    companion object {

        private const val PACKAGE_NAME = "us.bnn.geo_location"

        private val TAG = LocationUpdatesService::class.java.simpleName
        const val CALLBACK_HANDLE_KEY = "callback_handle"
        /**
         * The name of the channel for notifications.
         */
        var MAIN_ACTIVITY_CLASS = ""
        private var CALLBACK_HANDLE = 0L
        private const val CHANNEL_ID = "$PACKAGE_NAME.background_channel"

        internal const val ACTION_BROADCAST = "$PACKAGE_NAME.broadcast"

        internal const val EXTRA_LOCATION = "$PACKAGE_NAME.location"
        private const val EXTRA_STARTED_FROM_NOTIFICATION = "$PACKAGE_NAME.started_from_notification"

        /**
         * The desired interval for location updates. Inexact. Updates may be more or less frequent.
         */
        private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000

        /**
         * The fastest rate for active location updates. Updates will never be more frequent
         * than this value.
         */
        private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2

        @JvmStatic
        val ACTION_SHUTDOWN = "SHUTDOWN"

        /**
         * The identifier for the notification displayed for the foreground service.
         */
        private const val NOTIFICATION_ID = 99123456

        @JvmStatic
        private val JOB_ID = UUID.randomUUID().mostSignificantBits.toInt()

        @JvmStatic
        private var sBackgroundFlutterView: FlutterNativeView? = null

        @JvmStatic
        private val sServiceStarted = AtomicBoolean(false)

        @JvmStatic
        val SHARED_PREFERENCES_KEY = "geolocation_plugin_cache"

        @JvmStatic
        val CALLBACK_DISPATCHER_HANDLE_KEY = "callback_dispatch_handler"

        @JvmStatic
        private lateinit var sPluginRegistrantCallback: PluginRegistry.PluginRegistrantCallback

        @JvmStatic
        fun setBackgroundFlutterView(view: FlutterNativeView?) {
            sBackgroundFlutterView = view
        }

        @JvmStatic
        fun enqueueWork(context: Context, work: Intent) {
            JobIntentService.enqueueWork(context, LocationUpdatesService::class.java, JOB_ID, work)
        }

        @JvmStatic
        fun setPluginRegistrant(callback: PluginRegistry.PluginRegistrantCallback) {
            sPluginRegistrantCallback = callback
        }

        @JvmStatic
        fun initializeService(context: Context, args: ArrayList<*>?) {

            val callbackHandle = args!![0] as Long

            Log.d(TAG, "Initializing GeoLocationService $callbackHandle")
            CALLBACK_HANDLE = callbackHandle

            context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(CALLBACK_DISPATCHER_HANDLE_KEY, callbackHandle)
                    .apply()

        }

    }
}