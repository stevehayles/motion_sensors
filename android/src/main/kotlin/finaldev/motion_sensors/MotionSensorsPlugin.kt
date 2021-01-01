package finaldev.motion_sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.NonNull;
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry.Registrar

// translate from https://github.com/flutter/plugins/tree/master/packages/sensors
/** MotionSensorsPlugin */
public class MotionSensorsPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
  private val METHOD_CHANNEL_NAME = "motion_sensors/method"
  private val ACCELEROMETER_CHANNEL_NAME = "motion_sensors/accelerometer"
  private val GYROSCOPE_CHANNEL_NAME = "motion_sensors/gyroscope"
  private val MAGNETOMETER_CHANNEL_NAME = "motion_sensors/magnetometer"
  private val USER_ACCELEROMETER_CHANNEL_NAME = "motion_sensors/user_accelerometer"
  private val ORIENTATION_CHANNEL_NAME = "motion_sensors/orientation"
  private val ABSOLUTE_ORIENTATION_CHANNEL_NAME = "motion_sensors/absolute_orientation"
  private val SCREEN_ORIENTATION_CHANNEL_NAME = "motion_sensors/screen_orientation"

  private var sensorManager: SensorManager? = null
  private var methodChannel: MethodChannel? = null
  private var accelerometerChannel: EventChannel? = null
  private var gyroscopeChannel: EventChannel? = null
  private var magnetometerChannel: EventChannel? = null
  private var userAccelerometerChannel: EventChannel? = null
  private var orientationChannel: EventChannel? = null
  private var absoluteOrientationChannel: EventChannel? = null
  private var screenOrientationChannel: EventChannel? = null


  private var accelerationStreamHandler: StreamHandlerImpl? = null
  private var gyroScopeStreamHandler: StreamHandlerImpl? = null
  private var magnetometerStreamHandler: StreamHandlerImpl? = null
  private var userAccelerationStreamHandler: StreamHandlerImpl? = null
  private var orientationStreamHandler: RotationVectorStreamHandler? = null
  private var absoluteOrientationStreamHandler: RotationVectorStreamHandler? = null
  private var screenOrientationStreamHandler: ScreenOrientationStreamHandler? = null

  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val plugin = MotionSensorsPlugin()
      plugin.setupEventChannels(registrar.context(), registrar.messenger())
    }
  }

  override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    val context = binding.applicationContext
    setupEventChannels(context, binding.binaryMessenger)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    teardownEventChannels()
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "isSensorAvailable" -> result.success(sensorManager!!.getSensorList(call.arguments as Int).isNotEmpty())
      "setSensorUpdateInterval" -> setSensorUpdateInterval(call.argument<Int>("sensorType")!!, call.argument<Int>("interval")!!)
      else -> result.notImplemented()
    }
  }
  

  private fun setupEventChannels(context: Context, messenger: BinaryMessenger) {
    sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    methodChannel = MethodChannel(messenger, METHOD_CHANNEL_NAME)
    methodChannel!!.setMethodCallHandler(this)

    accelerometerChannel = EventChannel(messenger, ACCELEROMETER_CHANNEL_NAME)
    accelerationStreamHandler = StreamHandlerImpl(sensorManager!!, Sensor.TYPE_ACCELEROMETER)
    accelerometerChannel!!.setStreamHandler(accelerationStreamHandler!!)

    userAccelerometerChannel = EventChannel(messenger, USER_ACCELEROMETER_CHANNEL_NAME)
    userAccelerationStreamHandler = StreamHandlerImpl(sensorManager!!, Sensor.TYPE_LINEAR_ACCELERATION)
    userAccelerometerChannel!!.setStreamHandler(userAccelerationStreamHandler!!)

    gyroscopeChannel = EventChannel(messenger, GYROSCOPE_CHANNEL_NAME)
    gyroScopeStreamHandler = StreamHandlerImpl(sensorManager!!, Sensor.TYPE_GYROSCOPE)
    gyroscopeChannel!!.setStreamHandler(gyroScopeStreamHandler!!)

    magnetometerChannel = EventChannel(messenger, MAGNETOMETER_CHANNEL_NAME)
    magnetometerStreamHandler = StreamHandlerImpl(sensorManager!!, Sensor.TYPE_MAGNETIC_FIELD)
    magnetometerChannel!!.setStreamHandler(magnetometerStreamHandler!!)

    orientationChannel = EventChannel(messenger, ORIENTATION_CHANNEL_NAME)
    orientationStreamHandler = RotationVectorStreamHandler(context, sensorManager!!, Sensor.TYPE_GAME_ROTATION_VECTOR)
    orientationChannel!!.setStreamHandler(orientationStreamHandler!!)

    absoluteOrientationChannel = EventChannel(messenger, ABSOLUTE_ORIENTATION_CHANNEL_NAME)
    absoluteOrientationStreamHandler = RotationVectorStreamHandler(context, sensorManager!!, Sensor.TYPE_ROTATION_VECTOR)
    absoluteOrientationChannel!!.setStreamHandler(absoluteOrientationStreamHandler!!)

    screenOrientationChannel = EventChannel(messenger, SCREEN_ORIENTATION_CHANNEL_NAME)
    screenOrientationStreamHandler = ScreenOrientationStreamHandler(context, sensorManager!!, Sensor.TYPE_ACCELEROMETER)
    screenOrientationChannel!!.setStreamHandler(screenOrientationStreamHandler)
  }

  private fun teardownEventChannels() {
    methodChannel!!.setMethodCallHandler(null)
    accelerometerChannel!!.setStreamHandler(null)
    userAccelerometerChannel!!.setStreamHandler(null)
    gyroscopeChannel!!.setStreamHandler(null)
    magnetometerChannel!!.setStreamHandler(null)
    orientationChannel!!.setStreamHandler(null)
    absoluteOrientationChannel!!.setStreamHandler(null)
    screenOrientationChannel!!.setStreamHandler(null)
  }

  private fun setSensorUpdateInterval(sensorType: Int, interval: Int) {
    when (sensorType) {
      Sensor.TYPE_ACCELEROMETER -> accelerationStreamHandler!!.setUpdateInterval(interval)
      Sensor.TYPE_MAGNETIC_FIELD -> magnetometerStreamHandler!!.setUpdateInterval(interval)
      Sensor.TYPE_GYROSCOPE -> gyroScopeStreamHandler!!.setUpdateInterval(interval)
      Sensor.TYPE_LINEAR_ACCELERATION -> userAccelerationStreamHandler!!.setUpdateInterval(interval)
      Sensor.TYPE_GAME_ROTATION_VECTOR -> orientationStreamHandler!!.setUpdateInterval(interval)
      Sensor.TYPE_ROTATION_VECTOR -> absoluteOrientationStreamHandler!!.setUpdateInterval(interval)
    }
  }
}

class StreamHandlerImpl(private val sensorManager: SensorManager, sensorType: Int, private var interval: Int = SensorManager.SENSOR_DELAY_NORMAL) :
        EventChannel.StreamHandler, SensorEventListener {
  private val sensor = sensorManager.getDefaultSensor(sensorType)
  private var eventSink: EventChannel.EventSink? = null

  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    if (sensor != null) {
      eventSink = events
      sensorManager.registerListener(this, sensor, interval)
    }
  }

  override fun onCancel(arguments: Any?) {
    sensorManager.unregisterListener(this)
    eventSink = null
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

  }

  override fun onSensorChanged(event: SensorEvent?) {
    val sensorValues = listOf(event!!.values[0], event.values[1], event.values[2])
    eventSink?.success(sensorValues)
  }

  fun setUpdateInterval(interval: Int) {
    this.interval = interval
    if (eventSink != null) {
      sensorManager.unregisterListener(this)
      sensorManager.registerListener(this, sensor, interval)
    }
  }
}

class RotationVectorStreamHandler(private val context: Context, private val sensorManager: SensorManager, sensorType: Int, private var interval: Int = SensorManager.SENSOR_DELAY_NORMAL) :
        EventChannel.StreamHandler, SensorEventListener {
  private val sensor = sensorManager.getDefaultSensor(sensorType)
  private var eventSink: EventChannel.EventSink? = null

  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    if (sensor != null) {
      eventSink = events
      sensorManager.registerListener(this, sensor, interval)
    }
  }

  override fun onCancel(arguments: Any?) {
    sensorManager.unregisterListener(this)
    eventSink = null
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

  }

  override fun onSensorChanged(event: SensorEvent?) {
    var matrix = FloatArray(9)
    SensorManager.getRotationMatrixFromVector(matrix, event!!.values)

    // see https://stackoverflow.com/questions/53408642/azimuth-reading-changes-to-opposite-when-user-holds-the-phone-upright
    /*
     *   /  R[ 0]   R[ 1]   R[ 2]  \
     *   |  R[ 3]   R[ 4]   R[ 5]  |
     *   \  R[ 6]   R[ 7]   R[ 8]  /
     *
     *  The rotation matrix consists of the components of the vectors that point in the device's three axes:
     *  column 0: vector pointing to phone's right axis
     *  column 1: vector pointing to phone's up axis
     *  column 2: vector pointing to phone's front axis
     *  The vectors are effectively described in an 'Earth' coordinate system of East, North and Up / Sky
     *  For azimuth we use the Atan2 function (ignoring the Up vector)
     *  For pitch we use Asin on the Sky vector of the phones Up axis and for roll we use the same on the right vector
     *  Using a screen rotation check we can adjust which axis is used from the rotation matrix and flip the sign as needed
     *
     *  This returns a result similar to 'SensorManager.getOrientation' but without the issue when the phone is pointing 
     *  upwards and is akin to the functionality expected in a mapping application like Google Maps
     */

    val (matrixColumn, sense) = when (val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
      Surface.ROTATION_0 -> Pair(0, 1)
      Surface.ROTATION_90 -> Pair(1, -1)
      Surface.ROTATION_180 -> Pair(0, -1)
      Surface.ROTATION_270 -> Pair(1, 1)
      else -> error("Invalid screen rotation value: $rotation")
    }
    
    val x = sense * matrix[matrixColumn]
    val y = sense * matrix[matrixColumn + 3]
    val azimuth = kotlin.math.atan2(y, x)

    val pitch = kotlin.math.asin(matrix[7 - matrixColumn])
    val roll = sense * kotlin.math.asin(matrix[6 + matrixColumn])

    val sensorValues = listOf(azimuth, pitch, -roll)
    
    eventSink?.success(sensorValues)
  }

  fun setUpdateInterval(interval: Int) {
    this.interval = interval
    if (eventSink != null) {
      sensorManager.unregisterListener(this)
      sensorManager.registerListener(this, sensor, interval)
    }
  }
}

class ScreenOrientationStreamHandler(private val context: Context, private val sensorManager: SensorManager, sensorType: Int, private var interval: Int = SensorManager.SENSOR_DELAY_NORMAL) :
        EventChannel.StreamHandler, SensorEventListener {
  private val sensor = sensorManager.getDefaultSensor(sensorType)
  private var eventSink: EventChannel.EventSink? = null
  private var lastRotation: Double = -1.0

  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    eventSink = events
    sensorManager.registerListener(this, sensor, interval)
  }

  override fun onCancel(arguments: Any?) {
    sensorManager.unregisterListener(this)
    eventSink = null
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

  }

  override fun onSensorChanged(event: SensorEvent?) {
    val rotation = getScreenOrientation()
    if (rotation != lastRotation) {
      eventSink?.success(rotation)
      lastRotation = rotation
    }
  }

  fun setUpdateInterval(interval: Int) {
    this.interval = interval
    if (eventSink != null) {
      sensorManager.unregisterListener(this)
      sensorManager.registerListener(this, sensor, interval)
    }
  }

  private fun getScreenOrientation(): Double {
    return when ((context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
      Surface.ROTATION_0 -> 0.0
      Surface.ROTATION_90 -> 90.0
      Surface.ROTATION_180 -> 180.0
      Surface.ROTATION_270 -> -90.0
      else -> 0.0
    }
  }
}