package com.vikash.ampli.drive.system

import android.app.admin.DeviceAdminReceiver

/**
 * Settings › Power › Turn the display fully off: device admin with the force-lock policy only
 * (res/xml/device_admin.xml), so Screen off can lock the screen. It cannot wipe, read or change
 * anything else. Off unless the driver turns it on.
 */
class AmpliDeviceAdmin : DeviceAdminReceiver()
