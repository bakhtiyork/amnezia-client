/* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.amnezia.vpn

import android.content.Context
import android.content.Intent
import android.os.*
import android.os.StrictMode.VmPolicy
import android.text.TextUtils
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File


class VPNServiceBinder(service: VPNService) : Binder() {

    private val mService = service
    private val tag = "VPNServiceBinder"
    private var mListener: IBinder? = null
    private var mResumeConfig: JSONObject? = null

    /**
     * The codes this Binder does accept in [onTransact]
     */
    object ACTIONS {
        const val activate = 1
        const val deactivate = 2
        const val registerEventListener = 3
        const val requestStatistic = 4
        const val requestGetLog = 5
        const val requestCleanupLog = 6
        const val resumeActivate = 7
        const val setNotificationText = 8
        const val setFallBackNotification = 9
        const val SHARE_CONFIG = 10
    }

    /**
     * Gets called when the VPNServiceBinder gets a request from a Client.
     * The [code] determines what action is requested. - see [ACTIONS]
     * [data] may contain a utf-8 encoded json string with optional args or is null.
     * [reply] is a pointer to a buffer in the clients memory, to reply results.
     * we use this to send result data.
     *
     * returns true if the [code] was accepted
     */
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        Log.i(tag, "GOT TRANSACTION " + code)

        when (code) {
            ACTIONS.activate -> {
                try {
                    Log.i(tag, "Activiation Requested, parsing Config")
                    // [data] is here a json containing the wireguard/openvpn conf
                    val buffer = data.createByteArray()
                    val json = buffer?.let { String(it) }
                    val config = JSONObject(json)
                    Log.v(tag, "Stored new Tunnel config in Service")

                    if (!mService.checkPermissions()) {
                        mResumeConfig = config
                        // The Permission prompt was already
                        // send, in case it's accepted we will
                        // receive ACTIONS.resumeActivate
                        return true
                    }
                    this.mService.turnOn(config)
                } catch (e: Exception) {
                    Log.e(tag, "An Error occurred while enabling the VPN: ${e.localizedMessage}")
                    dispatchEvent(EVENTS.activationError, e.localizedMessage)
                }
                return true
            }

            ACTIONS.resumeActivate -> {
                // [data] is empty
                // Activate the current tunnel
                try {
                    mResumeConfig?.let { this.mService.turnOn(it) }
                } catch (e: Exception) {
                    Log.e(tag, "An Error occurred while enabling the VPN: ${e.localizedMessage}")
                }
                return true
            }

            ACTIONS.deactivate -> {
                // [data] here is empty
                this.mService.turnOff()
                return true
            }

            ACTIONS.registerEventListener -> {
                // [data] contains the Binder that we need to dispatch the Events
                val binder = data.readStrongBinder()
                mListener = binder
                val obj = JSONObject()
                obj.put("connected", mService.isUp)
                obj.put("time", mService.connectionTime)
                dispatchEvent(EVENTS.init, obj.toString())
                return true
            }

            ACTIONS.requestStatistic -> {
                dispatchEvent(EVENTS.statisticUpdate, mService.status.toString())
                return true
            }

            ACTIONS.requestGetLog -> {
                // Grabs all the Logs and dispatch new Log Event
                dispatchEvent(EVENTS.backendLogs, Log.getContent())
                return true
            }
            ACTIONS.requestCleanupLog -> {
                Log.clearFile()
                return true
            }
            ACTIONS.setNotificationText -> {
                NotificationUtil.update(data)
                return true
            }
            ACTIONS.setFallBackNotification -> {
                NotificationUtil.saveFallBackMessage(data, mService)
                return true
            }
            ACTIONS.SHARE_CONFIG -> {
                val byteArray = data.createByteArray()
                val json = byteArray?.let { String(it) }
                val config = JSONObject(json)
                val configContent = config.getString("data")
                val suggestedName = config.getString("suggestedName")
                val filePath = saveAsFile(mService, configContent, suggestedName)
                Log.i(tag, "save file: $filePath")
                shareFile(mService, filePath, tag)
                return true
            }
            IBinder.LAST_CALL_TRANSACTION -> {
                Log.e(tag, "The OS Requested to shut down the VPN")
                this.mService.turnOff()
                return true
            }

            else -> {
                Log.e(tag, "Received invalid bind request \t Code -> $code")
                // If we're hitting this there is probably something wrong in the client.
                return false
            }
        }
        return false
    }

    private fun saveAsFile(
        context: Context,
        configContent: String?,
        suggestedFileName: String
    ): String {
        val rootDirPath = context.filesDir.absolutePath
        //val rootDirPath = context.cacheDir.absolutePath
        val rootDir = File(rootDirPath)
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        val fileName =
            if (TextUtils.isEmpty(suggestedFileName)) "amnezia.cfg" else suggestedFileName
        val file = File(rootDir, fileName)
        try {
            file.bufferedWriter().use { out -> out.write(configContent) }
            return file.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    private fun shareFile(context: Context, attachmentFile: String?, message: String?) {
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/*"
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // authority is defined in the Manifest
            val file = File(attachmentFile)
            val uri = FileProvider.getUriForFile(
                context,
                BuildConfig.APPLICATION_ID + ".fileprovider",
                file
            )
            val builder = VmPolicy.Builder()
            StrictMode.setVmPolicy(builder.build())
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            //intent.putExtra(Intent.EXTRA_TEXT, message)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val createChooser = Intent.createChooser(intent, "Config sharing")
            createChooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(createChooser)
        } catch (e: Exception) {
            Log.i(tag, e.localizedMessage)
        }
    }

    /**
     * Dispatches an Event to all registered Binders
     * [code] the Event that happened - see [EVENTS]
     * To register an Eventhandler use [onTransact] with
     * [ACTIONS.registerEventListener]
     */
    fun dispatchEvent(code: Int, payload: String?) {
        try {
            mListener?.let {
                if (it.isBinderAlive) {
                    val data = Parcel.obtain()
                    data.writeByteArray(payload?.toByteArray(charset("UTF-8")))
                    it.transact(code, data, Parcel.obtain(), 0)
                }
            }
        } catch (e: DeadObjectException) {
            // If the QT Process is killed (not just inactive)
            // we cant access isBinderAlive, so nothing to do here.
        }
    }

    /**
     *  The codes we Are Using in case of [dispatchEvent]
     */
    object EVENTS {
        const val init = 0
        const val connected = 1
        const val disconnected = 2
        const val statisticUpdate = 3
        const val backendLogs = 4
        const val activationError = 5
    }
}
