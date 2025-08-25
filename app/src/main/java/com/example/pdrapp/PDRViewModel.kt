package com.example.pdrapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Point(val x: Float, val y: Float)

class PDRViewModel : ViewModel(), SensorEventListener {
    
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gyroscope: Sensor? = null
    
    // 学習モード関連
    private var gpsManager: GPSManager? = null
    private var mlEngine: MLCorrectionEngine? = null
    
    // State variables
    var isTracking by mutableStateOf(false)
    var isLearningMode by mutableStateOf(false)
    var currentPosition by mutableStateOf(Point(0f, 0f))
    var pathPoints by mutableStateOf(listOf<Point>())
    var gpsPathPoints by mutableStateOf(listOf<Point>()) // GPS軌跡
    var stepCount by mutableStateOf(0)
    var totalDistance by mutableStateOf(0.0f)
    var trainingDataCount by mutableStateOf(0)
    var hasTrainedModel by mutableStateOf(false)
    
    // Sensor data
    private var accelerometerValues = FloatArray(3)
    private var magnetometerValues = FloatArray(3)
    private var gyroscopeValues = FloatArray(3)
    
    // PDR parameters
    private var currentHeading = 0f // 現在の方位角（ラジアン）
    
    // 改良されたPDRアルゴリズム
    private var stepDetector = StepDetector()
    private var headingEstimator = HeadingEstimator()
    private var stepLengthEstimator = StepLengthEstimator()
    
    fun initializeSensors(context: Context) {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        // GPS機能と機械学習エンジンを初期化
        gpsManager = GPSManager(context)
        mlEngine = MLCorrectionEngine(context)
        mlEngine?.loadModel()
        hasTrainedModel = mlEngine?.hasTrainedModel() ?: false
        
        // GPS位置の監視を開始
        viewModelScope.launch {
            gpsManager?.currentLocation?.collect { gpsLocation ->
                if (isLearningMode && gpsLocation != null) {
                    val (gpsX, gpsY) = gpsManager?.getRelativePosition(gpsLocation) ?: Pair(0f, 0f)
                    
                    // GPS軌跡を更新
                    gpsPathPoints = gpsPathPoints + Point(gpsX * 150f, gpsY * 150f)
                    
                    // 学習データを追加
                    mlEngine?.addTrainingData(
                        timestamp = gpsLocation.timestamp,
                        pdrX = currentPosition.x,
                        pdrY = currentPosition.y,
                        gpsX = gpsX * 150f,
                        gpsY = gpsY * 150f,
                        stepCount = stepCount,
                        heading = currentHeading,
                        stepLength = stepLengthEstimator.getAverageStepLength()
                    )
                    
                    trainingDataCount = mlEngine?.getTrainingDataSize() ?: 0
                }
            }
        }
    }
    
    fun startTracking(learningMode: Boolean = false) {
        isTracking = true
        isLearningMode = learningMode
        
        if (learningMode) {
            // 学習モードの場合はGPSも開始
            gpsManager?.startTracking()
            gpsManager?.resetInitialLocation()
            mlEngine?.clearTrainingData()
            gpsPathPoints = emptyList()
            trainingDataCount = 0
        }
        
        // センサーレートを適度に調整して精度向上
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager?.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager?.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI)
    }
    
    fun stopTracking() {
        isTracking = false
        sensorManager?.unregisterListener(this)
        
        if (isLearningMode) {
            gpsManager?.stopTracking()
            // 学習データがある場合はモデルを訓練
            if ((mlEngine?.getTrainingDataSize() ?: 0) > 0) {
                mlEngine?.trainModel()
                hasTrainedModel = true
            }
        }
        
        isLearningMode = false
    }
    
    fun resetPath() {
        pathPoints = emptyList()
        gpsPathPoints = emptyList()
        currentPosition = Point(0f, 0f)
        stepCount = 0
        totalDistance = 0f
        trainingDataCount = 0
        // センサー履歴もクリア
        stepDetector = StepDetector()
        headingEstimator = HeadingEstimator()
        stepLengthEstimator = StepLengthEstimator()
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (!isTracking) return
        
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelerometerValues = event.values.clone()
                
                // 改良されたステップ検出を使用
                if (stepDetector.detectStep(event.values)) {
                    stepCount++
                    onStepDetected(event.values)
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magnetometerValues = event.values.clone()
                updateHeading()
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroscopeValues = event.values.clone()
            }
        }
    }
    
    private fun onStepDetected(accelerometerValues: FloatArray) {
        // 動的歩幅推定
        val magnitude = sqrt(
            accelerometerValues[0] * accelerometerValues[0] +
            accelerometerValues[1] * accelerometerValues[1] +
            accelerometerValues[2] * accelerometerValues[2]
        )
        
        var stepLength = stepLengthEstimator.estimateStepLength(magnitude)
        
        // 通常モードで学習済みモデルがある場合は歩幅補正を適用
        if (hasTrainedModel && !isLearningMode) {
            stepLength *= mlEngine?.getStepLengthCorrection() ?: 1.0f
        }
        
        // 新しい位置を計算（適切なスケールで）
        val scaleFactor = 150f
        val deltaX = stepLength * cos(currentHeading) * scaleFactor
        val deltaY = -stepLength * sin(currentHeading) * scaleFactor // Y軸反転
        
        var newX = currentPosition.x + deltaX
        var newY = currentPosition.y + deltaY
        
        // 通常モードで学習済みモデルがある場合は位置補正を適用
        if (hasTrainedModel && !isLearningMode) {
            val (correctedX, correctedY) = mlEngine?.applyCorrection(newX, newY) ?: Pair(newX, newY)
            newX = correctedX
            newY = correctedY
        }
        
        val newPosition = Point(newX, newY)
        
        // パスに新しい点を追加
        pathPoints = pathPoints + newPosition
        currentPosition = newPosition
        
        // 総距離を更新
        totalDistance += stepLength
    }
    
    private fun updateHeading() {
        if (accelerometerValues.isNotEmpty() && magnetometerValues.isNotEmpty()) {
            currentHeading = headingEstimator.updateHeading(accelerometerValues, magnetometerValues)
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 必要に応じて精度変更を処理
    }
    
    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }
}
