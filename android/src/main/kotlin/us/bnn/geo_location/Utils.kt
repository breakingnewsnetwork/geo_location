package us.bnn.geo_location

import android.content.Context
import android.location.Location
import android.preference.PreferenceManager

import java.text.DateFormat
import java.util.Date

internal object Utils {

    private const val KEY_REQUESTING_LOCATION_UPDATES = "requesting_location_updates"
    private const val KEY_LOCATION_UPDATES_USERNAME = "requesting_location_updates_username"
    private const val KEY_LOCATION_UPDATES_DEVICE_ID = "requesting_location_updates_device_id"
    private const val KEY_LOCATION_UPDATES_INTERVAL = "requesting_location_updates_interval"
    private const val CALLBACK_HANDLE_KEY = "callback_handle"
    /**
     * Returns true if requesting location updates, otherwise returns false.
     *
     * @param context The [Context].
     */
    fun requestingLocationUpdates(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_REQUESTING_LOCATION_UPDATES, false)
    }

    /**
     * Stores the location updates state in SharedPreferences.
     * @param requestingLocationUpdates The location updates state.
     */
    fun setRequestingLocationUpdates(context: Context, requestingLocationUpdates: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(KEY_REQUESTING_LOCATION_UPDATES, requestingLocationUpdates)
                .apply()
    }

    fun getUsername(context: Context): String? {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KEY_LOCATION_UPDATES_USERNAME, "")
    }

    /**
     * Stores the location updates state in SharedPreferences.
     * @param requestingLocationUpdates The location updates state.
     */
    fun setUsername(context: Context, username: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(KEY_LOCATION_UPDATES_USERNAME, username)
                .apply()
    }

    fun getDeviceId(context: Context): String? {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KEY_LOCATION_UPDATES_DEVICE_ID, "")
    }

    /**
     * Stores the location updates state in SharedPreferences.
     * @param requestingLocationUpdates The location updates state.
     */
    fun setDeviceId(context: Context, deviceId: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(KEY_LOCATION_UPDATES_DEVICE_ID, deviceId)
                .apply()
    }

    fun getCallbackHandler(context: Context): Long {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getLong(CALLBACK_HANDLE_KEY, 0L)
    }

    /**
     * Stores the location updates state in SharedPreferences.
     * @param requestingLocationUpdates The location updates state.
     */
    fun setCallbackHandler(context: Context, callbackHandler: Long) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putLong(CALLBACK_HANDLE_KEY, callbackHandler)
                .apply()
    }

    fun getLocationInterval(context: Context): Long {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getLong(KEY_LOCATION_UPDATES_INTERVAL, 60000)
    }

    /**
     * Stores the location updates state in SharedPreferences.
     * @param requestingLocationUpdates The location updates state.
     */
    fun setLocationInterval(context: Context, interval: Long) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putLong(KEY_LOCATION_UPDATES_INTERVAL, interval)
                .apply()
    }

    /**
     * Returns the `location` object as a human readable string.
     * @param location  The [Location].
     */
    fun getLocationText(location: Location?): String {
        return if (location == null)
            "Unknown location"
        else
            "(" + location.latitude + ", " + location.longitude + ")"
    }

    fun getLocationTitle(context: Context): String {
        return context.getString(R.string.location_updated,
                DateFormat.getDateTimeInstance().format(Date()))
    }
}