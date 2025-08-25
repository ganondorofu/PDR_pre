package com.example.pdrapp

import android.hardware.SensorManager
import kotlin.math.*

/**
 * 改良されたステップ検出アルゴリズム
 */
class StepDetector {
    private val accelerationHistory = mutableListOf<Float>()
    private val maxHistorySize = 20
    private var lastStepTime = 0L
    private val minTimeBetweenSteps = 300L // ステップ間隔を長くして重複を防止
    
    // より適切な閾値パラメータ
    private var dynamicThreshold = 1.5f // 閾値を適度に設定
    private var baselineAcceleration = 9.8f
    private var lastPeakValue = 0f
    private var isInStep = false // ステップ状態の管理
    
    fun detectStep(accelerationValues: FloatArray): Boolean {
        val magnitude = sqrt(
            accelerationValues[0] * accelerationValues[0] +
            accelerationValues[1] * accelerationValues[1] +
            accelerationValues[2] * accelerationValues[2]
        )
        
        // 重力成分を除去
        val normalizedMagnitude = abs(magnitude - baselineAcceleration)
        
        // 履歴に追加
        accelerationHistory.add(normalizedMagnitude)
        if (accelerationHistory.size > maxHistorySize) {
            accelerationHistory.removeAt(0)
        }
        
        // 動的閾値を更新
        updateDynamicThreshold()
        
        val currentTime = System.currentTimeMillis()
        
        // 時間制約チェック
        if (currentTime - lastStepTime < minTimeBetweenSteps) {
            return false
        }
        
        // 改良されたピーク検出
        if (accelerationHistory.size >= 5) {
            val current = normalizedMagnitude
            val recent = accelerationHistory.takeLast(5)
            val average = recent.average().toFloat()
            
            // ピーク条件：現在値が閾値を超え、最近の平均より十分高い
            if (current > dynamicThreshold && 
                current > average * 1.3f && 
                !isInStep) {
                
                isInStep = true
                lastPeakValue = current
                return false // ピークの開始点では検出しない
            }
            
            // ピークの終了検出（谷の検出）
            if (isInStep && current < lastPeakValue * 0.7f) {
                isInStep = false
                lastStepTime = currentTime
                return true // 実際のステップとして検出
            }
        }
        
        return false
    }
    
    private fun updateDynamicThreshold() {
        if (accelerationHistory.size < 10) return
        
        val recentHistory = accelerationHistory.takeLast(10)
        val mean = recentHistory.average().toFloat()
        val variance = recentHistory.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev = sqrt(variance)
        
        // 動的閾値を標準偏差に基づいて調整
        dynamicThreshold = mean + 1.5f * stdDev
        dynamicThreshold = max(1.0f, min(dynamicThreshold, 5.0f)) // 範囲制限
    }
}

/**
 * 方位推定の改良版
 */
class HeadingEstimator {
    private val rotationMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)
    private val headingHistory = mutableListOf<Float>()
    private val maxHistorySize = 5 // 履歴サイズを小さくして応答性向上
    
    fun updateHeading(accelerometer: FloatArray, magnetometer: FloatArray): Float {
        if (accelerometer.size >= 3 && magnetometer.size >= 3) {
            if (SensorManager.getRotationMatrix(
                    rotationMatrix, null,
                    accelerometer, magnetometer
                )) {
                SensorManager.getOrientation(rotationMatrix, orientationValues)
                val rawHeading = orientationValues[0]
                
                // 角度の正規化
                val normalizedHeading = normalizeAngle(rawHeading)
                
                // 移動平均フィルタを適用
                headingHistory.add(normalizedHeading)
                if (headingHistory.size > maxHistorySize) {
                    headingHistory.removeAt(0)
                }
                
                return calculateFilteredHeading()
            }
        }
        return 0f
    }
    
    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle
        while (normalized > PI) normalized -= 2 * PI.toFloat()
        while (normalized < -PI) normalized += 2 * PI.toFloat()
        return normalized
    }
    
    private fun calculateFilteredHeading(): Float {
        if (headingHistory.isEmpty()) return 0f
        
        // 円形の平均を計算（角度の特性を考慮）
        var sinSum = 0.0
        var cosSum = 0.0
        
        headingHistory.forEach { angle ->
            sinSum += sin(angle.toDouble())
            cosSum += cos(angle.toDouble())
        }
        
        return atan2(sinSum / headingHistory.size, cosSum / headingHistory.size).toFloat()
    }
}

/**
 * 歩幅推定器
 */
class StepLengthEstimator {
    private val baseStepLength = 0.75f // より現実的な基本歩幅（メートル）
    private val accelerationHistory = mutableListOf<Float>()
    private val maxHistorySize = 10
    
    fun estimateStepLength(accelerationMagnitude: Float): Float {
        accelerationHistory.add(accelerationMagnitude)
        if (accelerationHistory.size > maxHistorySize) {
            accelerationHistory.removeAt(0)
        }
        
        if (accelerationHistory.size < 5) return baseStepLength
        
        // 加速度の分散に基づいて歩幅を調整（より控えめに）
        val recentHistory = accelerationHistory.takeLast(5)
        val variance = calculateVariance(recentHistory)
        
        // より控えめな歩幅調整
        val varianceFactor = max(0.85f, min(1.2f, 1.0f + (variance - 1.0f) * 0.1f))
        
        return baseStepLength * varianceFactor
    }
    
    fun getAverageStepLength(): Float {
        return if (accelerationHistory.isNotEmpty()) {
            accelerationHistory.average().toFloat()
        } else {
            baseStepLength
        }
    }
    
    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }
}
