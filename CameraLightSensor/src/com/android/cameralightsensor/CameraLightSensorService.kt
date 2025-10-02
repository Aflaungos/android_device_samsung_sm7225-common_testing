package com.android.cameralightsensor

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.*
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.util.Log
import androidx.core.app.NotificationCompat
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class CameraLightSensorService : Service() {
    private lateinit var screenStateFilter: IntentFilter
    private lateinit var mContext: Context
    private lateinit var cameraDevice: CameraDevice
    private lateinit var mSession: CameraCaptureSession
    private val manager: CameraManager by lazy {
        getSystemService(CAMERA_SERVICE) as CameraManager
    }
    private lateinit var mCameraHandler: Handler
    private lateinit var mBackgroundHandler: Handler
    private lateinit var mBackgroundThread: HandlerThread
    private val mExecutor = Executors.newSingleThreadExecutor()
    
    // Camera state management
    private var mCameraState = CAMERA_STATE_CLOSED
    private var cameraOpenPass = true
    private var isCameraStopPending = false
    
    // Camera components
    private lateinit var mPreviewRequestBuilder: CaptureRequest.Builder
    private lateinit var surfaceTexture: android.graphics.SurfaceTexture
    private lateinit var surface: android.view.Surface
    
    // IPC messaging for system communication
    private val mCameraLightMessenger = mutableListOf<Messenger>()
    private lateinit var mServiceHandler: ServiceHandler
    private lateinit var mServiceLooper: Looper
    private lateinit var mServiceIPC: Messenger
    
    // Brightness data
    private val luxEvent = FloatArray(2)
    private var totalConnections = 0
    private var countCapture = 0
    private var ev = INT_MAX
    private var bv = 1_000_000_000f
    private var UPDATE_INTERVAL = UPDATE_5SEC

    // Broadcast receiver for screen state
    private val mScreenStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                if (mPoolExecutor == null) {
                    mPoolExecutor = ScheduledThreadPoolExecutor(4)
                    mPoolExecutor!!.scheduleWithFixedDelay(
                        mScheduler,
                        0,
                        2,
                        TimeUnit.SECONDS
                    )
                }
            } else if (intent.action == Intent.ACTION_SCREEN_OFF) {
                if (mPoolExecutor != null) {
                    mPoolExecutor!!.shutdown()
                    mPoolExecutor = null
                }
                stopCameraTask(false)
            }
        }
    }
    private val mScheduler = Runnable { readyCamera() }

    // Settings observer for brightness mode changes
    private var mSettingsObserver: ContentObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.i(TAG, "observer: Brightness Settings Changed")
                try {
                    if (Settings.System.getInt(
                            contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE
                        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    ) {
                        registerReceiver(mScreenStateReceiver, screenStateFilter)
                        mRegistered = true
                        if (mPoolExecutor == null) {
                            mPoolExecutor = ScheduledThreadPoolExecutor(4)
                            mPoolExecutor!!.scheduleWithFixedDelay(
                                mScheduler,
                                0,
                                2,
                                TimeUnit.SECONDS
                            )
                        }
                    } else {
                        if (mRegistered) unregisterReceiver(mScreenStateReceiver)
                        mRegistered = false
                        if (mPoolExecutor != null) {
                            mPoolExecutor!!.shutdown()
                            mPoolExecutor = null
                        }
                        stopCameraTask(true)
                    }
                } catch (e: SettingNotFoundException) {
                    e.printStackTrace()
                }
            }

            override fun deliverSelfNotifications(): Boolean {
                return true
            }
        }

    private fun pushNotification(): Notification {
        val nm = mContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            mContext.packageName,
            "CameraLightSensor",
            NotificationManager.IMPORTANCE_NONE
        )
        channel.isBlockable = true
        nm.createNotificationChannel(channel)
        val builder = NotificationCompat.Builder(mContext, mContext.packageName)
        val notificationIntent = Intent(mContext, CameraLightSensorService::class.java)
        val contentIntent = PendingIntent.getActivity(
            mContext,
            50,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        builder.setContentIntent(contentIntent)
        builder.setSmallIcon(R.drawable.ic_brightness)
        builder.setContentTitle("Camera Light Sensor Service")
        builder.setChannelId(mContext.packageName)
        return builder.build()
    }

    override fun onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service")
        if (mRegistered) contentResolver!!.unregisterContentObserver(mSettingsObserver)
        mRegistered = false
        
        // Proper cleanup
        stopCameraTask(true)
        handleBackgroundThread(false)
        
        mCameraHandler.removeCallbacksAndMessages(null)
        mCameraHandler.looper.quitSafely()
        mServiceLooper.quit()
        
        imageReader.close()
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return mServiceIPC.binder
    }

    private var mIAutoBrightness: IAutoBrightness? = null

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, service: IBinder?) {
            mIAutoBrightness = IAutoBrightness.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            mIAutoBrightness = null
        }
    }

    // Camera availability callback
    private val camAvailCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            if (cameraId == "1") {
                mCameraState = CAMERA_STATE_CLOSED
                cameraOpenPass = true
                if (DEBUG) Log.d(TAG, "Camera is available")
            }
        }
        
        override fun onCameraUnavailable(cameraId: String) {
            if (cameraId == "1") {
                mCameraState = CAMERA_STATE_ERROR
                cameraOpenPass = false
                if (DEBUG) Log.d(TAG, "Camera is unavailable")
                if (stopProcessCalled) {
                    stopProcess()
                }
            }
        }
    }

    private var cameraStateCallback: CameraDevice.StateCallback =
        object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Log.d(TAG, "CameraDevice.StateCallback onOpened")
                mCameraState = CAMERA_STATE_OPENED
                cameraDevice = camera
                cameraOpenPass = true
                timeStart = SystemClock.elapsedRealtime()
                startPreviewVersion2()
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.w(TAG, "CameraDevice.StateCallback onDisconnected")
                mCameraState = CAMERA_STATE_DISCONNECTED
                cameraOpenPass = false
                UPDATE_INTERVAL = UPDATE_7SEC
                if (!stopTaskCalled) {
                    stopCameraTask(true)
                }
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "CameraDevice.StateCallback onError $error")
                mCameraState = CAMERA_STATE_ERROR
                cameraOpenPass = false
                UPDATE_INTERVAL = UPDATE_7SEC
                if (!stopTaskCalled) {
                    stopCameraTask(true)
                }
            }
        }
        
    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            countCapture++
            
            if (mCameraState == CAMERA_STATE_CLOSED) {
                return
            }
            
            // Process camera metadata for brightness detection (more accurate than image processing)
            val exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            val sensorSensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY)
            val aperture = result.get(CaptureResult.LENS_APERTURE)
            
            if (exposureTime != null && sensorSensitivity != null) {
                val brightness = calculateBrightnessFromMetadata(exposureTime, sensorSensitivity, aperture)
                updateSystemBrightness(brightness)
            }
            
            if (stopTaskCalled) {
                stopCameraTask(false)
            }
        }
    }

    private var sessionStateCallback: CameraCaptureSession.StateCallback =
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                this@CameraLightSensorService.mSession = session
                try {
                    val captureRequest = createCaptureRequest()
                    if (captureRequest != null) {
                        session.setRepeatingRequest(captureRequest, mCaptureCallback, mCameraHandler)
                        if (DEBUG) Log.d(TAG, "Capture session started successfully")
                    }
                } catch (e: CameraAccessException) {
                    Log.e(TAG, "Failed to start capture session", e)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Session is NULL or camera is in use")
                }
            }
            
            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "Failed to configure capture session")
                mCameraState = CAMERA_STATE_ERROR
            }
        }
        
    private var onImageAvailableListener =
        ImageReader.OnImageAvailableListener { reader: ImageReader ->
            // Keeping this as backup, but primary method is now camera metadata
            if (DEBUG) Log.d(TAG, "onImageAvailable: Image captured (backup method)")
            val img = reader.acquireLatestImage()
            img?.close() // Just consume the image, we're using metadata instead
        }

    @SuppressLint("MissingPermission")
    fun readyCamera() {
        if (mIAutoBrightness != null) {
            if (!mIAutoBrightness!!.CameraIsFree()) return
        }
        
        if (!checkCameraAvailability()) {
            if (DEBUG) Log.d(TAG, "Camera not available, skipping")
            return
        }
        
        try {
            handleBackgroundThread(true)
            manager.openCamera("1", cameraStateCallback, mCameraHandler)
            manager.registerAvailabilityCallback(camAvailCallback, mBackgroundHandler)
            if (DEBUG) Log.d(TAG, "Camera opening requested")
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access exception", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission denied", e)
        }
    }
    
    private fun startPreviewVersion2() {
        try {
            surfaceTexture = android.graphics.SurfaceTexture(0)
            surface = android.view.Surface(surfaceTexture)
            
            mPreviewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder.addTarget(surface)
            
            // Set camera parameters for optimal light sensing
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false)
            
            val outputConfig = OutputConfiguration(surface)
            val outputs = listOf(outputConfig)
            
            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                mExecutor,
                sessionStateCallback
            )
            
            cameraDevice.createCaptureSession(sessionConfig)
            if (DEBUG) Log.d(TAG, "Capture session creation requested")
            
        } catch (e: Exception) {
            Log.e(TAG, "startPreviewVersion2 error", e)
            mCameraState = CAMERA_STATE_ERROR
            if (!stopTaskCalled) {
                stopCameraTask(true)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mCameraHandlerThread = HandlerThread("CameraLightSensor")
        mCameraHandlerThread.start()
        mCameraHandler = Handler(mCameraHandlerThread.looper)
        mContext = this
        
        @Suppress("SameParameterValue")
        startForeground(50, pushNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        mRegistered = false
        screenStateFilter = IntentFilter(Intent.ACTION_USER_PRESENT)
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF)
        try {
            if (Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
                == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            ) {
                registerReceiver(mScreenStateReceiver, screenStateFilter)
                mRegistered = true
            }
        } catch (e: SettingNotFoundException) {
            e.printStackTrace()
        }
        val setting = Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE)
        contentResolver?.registerContentObserver(setting, false, mSettingsObserver)
        if (DEBUG) Log.d(TAG, "onStartCommand flags $flags startId $startId")
        startForeground(50, pushNotification())
        return START_STICKY
    }

    override fun onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate service")
        mContext = this
        
        // Setup service handler for IPC
        val serviceThread = HandlerThread("CameraServiceThread")
        serviceThread.start()
        mServiceLooper = serviceThread.looper
        mServiceHandler = ServiceHandler(this, mServiceLooper)
        mServiceIPC = Messenger(mServiceHandler)
        
        mCameraState = CAMERA_STATE_CLOSED
        cameraOpenPass = true
        isCameraStopPending = false
        
        startForeground(50, pushNotification())
        super.onCreate()
    }

    fun actOnReadyCameraDevice() {
        // This is now handled in startPreviewVersion2
    }

    fun createCaptureRequest(): CaptureRequest? {
        return try {
            mPreviewRequestBuilder.build()
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to create capture request", e)
            null
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Camera device not ready", e)
            null
        }
    }
    
    private fun calculateBrightnessFromMetadata(exposureTime: Long, sensorSensitivity: Int, aperture: Float?): Int {
        // Calculate EV (Exposure Value) from camera metadata
        var ev = Math.log10((exposureTime * sensorSensitivity) / 1000000000.0) / Math.log10(2.0)
        
        // Adjust for aperture if available
        aperture?.let {
            ev += 2 * Math.log10(it.toDouble()) / Math.log10(2.0)
        }
        
        // Convert EV to brightness value (0-255)
        var brightness = (ev * 20 + 128).toInt()
        
        // Clamp to valid range
        brightness = brightness.coerceIn(0, 255)
        
        if (DEBUG) Log.d(TAG, "Calculated brightness: $brightness from exposure: $exposureTime, sensitivity: $sensorSensitivity")
        
        return brightness
    }

    private fun updateSystemBrightness(brightness: Int) {
        if (DEBUG) Log.i(TAG, "updateSystemBrightness: Received Brightness Value $brightness")
        
        // Store values for IPC
        luxEvent[0] = ev.toFloat()
        luxEvent[1] = bv
        
        try {
            val oldBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            
            // Smooth transition to new brightness
            var newBrightness = (oldBrightness + brightness) / 2
            
            // Clamp to valid range
            newBrightness = newBrightness.coerceIn(0, 255)
            
            if (DEBUG) {
                Log.i(TAG, "updateSystemBrightness: OldVal = $oldBrightness, NewVal = $newBrightness")
            }
            
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                newBrightness
            )
            
            // Notify all connected components
            notifyBrightnessUpdate()
            
        } catch (e: SettingNotFoundException) {
            Log.e(TAG, "Brightness setting not found", e)
        }
    }
    
    private fun notifyBrightnessUpdate() {
        try {
            val message = Message.obtain(null, MSG_MANAGER_UPDATE_LUX)
            val bundle = Bundle()
            bundle.putFloatArray("respData", luxEvent)
            message.data = bundle
            
            synchronized(mCameraLightMessenger) {
                for (messenger in mCameraLightMessenger) {
                    try {
                        messenger.send(message)
                    } catch (e: RemoteException) {
                        Log.w(TAG, "Failed to send brightness update to messenger", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in notifyBrightnessUpdate", e)
        }
    }

    @Throws(SettingNotFoundException::class)
    private fun adjustBrightness(brightness: Int) {
        // Legacy method - use updateSystemBrightness instead
        updateSystemBrightness(brightness)
    }
    
    private fun checkCameraAvailability(): Boolean {
        return try {
            manager.cameraIdList.contains("1")
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to check camera availability", e)
            false
        }
    }
    
    private fun handleBackgroundThread(start: Boolean) {
        if (start) {
            if (!::mBackgroundHandler.isInitialized || mBackgroundHandler == null) {
                mBackgroundThread = HandlerThread("CameraBackground").apply { start() }
                mBackgroundHandler = Handler(mBackgroundThread.looper)
                if (DEBUG) Log.d(TAG, "Background thread started")
            }
        } else {
            mBackgroundThread?.quitSafely()
            mBackgroundThread = null
            mBackgroundHandler = null
            if (DEBUG) Log.d(TAG, "Background thread stopped")
        }
    }
    
    private fun stopCameraTask(releaseCamera: Boolean) {
        if (DEBUG) Log.d(TAG, "stopCameraTask called, releaseCamera: $releaseCamera")
        stopTaskCalled = true
        
        try {
            manager.unregisterAvailabilityCallback(camAvailCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering availability callback", e)
        }
        
        try {
            mSession?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing session", e)
        }
        
        if (releaseCamera && ::cameraDevice.isInitialized) {
            try {
                cameraDevice.close()
                countCapture = 0
                timeRelease = SystemClock.elapsedRealtime()
                mCameraState = CAMERA_STATE_CLOSED
                if (DEBUG) Log.d(TAG, "Camera released, time taken: ${timeRelease - timeStart}ms")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing camera device", e)
            }
        }
        
        stopTaskCalled = false
    }
    
    private fun stopProcess() {
        if (DEBUG) Log.d(TAG, "Stopping process")
        stopProcessCalled = true
        stopCameraTask(true)
        stopSelf()
        stopProcessCalled = false
    }
    
    private fun isNotRunning(): Boolean {
        val result = mBackgroundHandler == null && cameraOpenPass
        if (DEBUG) Log.d(TAG, "isNotRunning : $result")
        return result
    }

    // ServiceHandler for IPC communication
    private class ServiceHandler(
        service: CameraLightSensorService,
        looper: Looper
    ) : Handler(looper) {
        private val serviceRef = WeakReference(service)

        override fun handleMessage(msg: Message) {
            val service = serviceRef.get() ?: return
            
            when (msg.what) {
                MSG_SERVICE_START_CAMERA -> {
                    // System component requesting camera start
                    synchronized(service.mCameraLightMessenger) {
                        if (!service.mCameraLightMessenger.contains(msg.replyTo)) {
                            service.mCameraLightMessenger.add(msg.replyTo)
                        }
                        service.totalConnections++
                    }
                    if (service.isNotRunning()) {
                        service.startCamera()
                    }
                    Log.i(TAG, "MSG_SERVICE_START_CAMERA totalConnections= ${service.totalConnections}")
                }
                MSG_SERVICE_STOP_CAMERA -> {
                    // System component disconnecting
                    synchronized(service.mCameraLightMessenger) {
                        service.mCameraLightMessenger.remove(msg.replyTo)
                        service.totalConnections--
                        if (service.totalConnections <= 0) {
                            service.stopCameraTask(true)
                        }
                    }
                    Log.i(TAG, "MSG_SERVICE_STOP_CAMERA totalConnections= ${service.totalConnections}")
                }
                MSG_MANAGER_UPDATE_LUX -> {
                    // Brightness update requested - handled by notifyBrightnessUpdate
                    Log.d(TAG, "MSG_MANAGER_UPDATE_LUX received")
                }
                else -> {
                    super.handleMessage(msg)
                }
            }
        }
    }

    companion object {
        private const val CAMERA_STATE_CLOSED = 0
        private const val CAMERA_STATE_OPENED = 1
        private const val CAMERA_STATE_DISCONNECTED = 2
        private const val CAMERA_STATE_ERROR = 3
        
        private const val UPDATE_5SEC = 5000
        private const val UPDATE_7SEC = 7000
        private const val INT_MAX = 999999999
        
        // Message types for IPC
        private const val MSG_SERVICE_START_CAMERA = 4
        private const val MSG_SERVICE_STOP_CAMERA = 5
        private const val MSG_MANAGER_UPDATE_LUX = 6
        private const val MSG_SERVICE_CAMERA_LOOP = 7
        
        private val imageReader = ImageReader.newInstance(50, 50, ImageFormat.JPEG, 2)
        val TAG: String = CameraLightSensorService::class.java.simpleName
        const val DEBUG = true
        
        private var mPoolExecutor: ScheduledThreadPoolExecutor? = null
        private var mRegistered = false
        private var stopTaskCalled = false
        private var stopProcessCalled = false
        private var timeStart: Long = 0
        private var timeRelease: Long = 0
    }
}

class UserPresentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tag = "CameraLightSensor_Manager"
        
        if (intent != null) {
            Log.d(tag, "UserPresentReceiver started ${intent.action}")
            
            val action = intent.action
            if (action != null && (
                action == "com.samsung.android.action.SECRET_CODE" || 
                action == "android.provider.Telephony.SECRET_CODE"
            )) {
                // Handle secret code to launch activity
                val launchIntent = Intent().apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    setClassName(
                        "com.android.cameralightsensor",
                        "com.android.cameralightsensor.AdaptiveBrightnessHwTestActivity"
                    )
                }
                context.startActivity(launchIntent)
                return
            }
        } else {
            Log.d(tag, "intent is null")
        }
        
        // If not a secret code, restart the camera service on user present
        if (intent?.action == Intent.ACTION_USER_PRESENT) {
            val serviceIntent = Intent(context, CameraLightSensorService::class.java)
            context.startService(serviceIntent)
        }
    }
}