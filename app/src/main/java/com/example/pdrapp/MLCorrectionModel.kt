package com.example.pdrapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlin.math.*

data class TrainingData(
    val timestamp: Long,
    val pdrX: Float,
    val pdrY: Float,
    val gpsX: Float,
    val gpsY: Float,
    val stepCount: Int,
    val heading: Float,
    val stepLength: Float
)

data class CorrectionModel(
    var positionWeightX: Float = 1.0f,
    var positionWeightY: Float = 1.0f,
    var stepLengthCorrection: Float = 1.0f,
    var headingCorrection: Float = 0.0f,
    var driftCorrectionX: Float = 0.0f,
    var driftCorrectionY: Float = 0.0f
)

class MLCorrectionEngine(private val context: Context) {
    private val trainingData = mutableListOf<TrainingData>()
    private var correctionModel = CorrectionModel()
    private val gson = Gson()
    
    fun addTrainingData(
        timestamp: Long,
        pdrX: Float,
        pdrY: Float,
        gpsX: Float,
        gpsY: Float,
        stepCount: Int,
        heading: Float,
        stepLength: Float
    ) {
        trainingData.add(
            TrainingData(
                timestamp,
                pdrX,
                pdrY,
                gpsX,
                gpsY,
                stepCount,
                heading,
                stepLength
            )
        )
    }
    
    fun trainModel() {
        if (trainingData.size < 10) return // 最低10データポイント必要
        
        // 簡単な線形回帰アプローチで補正係数を計算
        val n = trainingData.size.toFloat()
        
        // 位置補正の計算
        var sumPdrX = 0f
        var sumPdrY = 0f
        var sumGpsX = 0f
        var sumGpsY = 0f
        var sumPdrX2 = 0f
        var sumPdrY2 = 0f
        var sumPdrXGpsX = 0f
        var sumPdrYGpsY = 0f
        
        trainingData.forEach { data ->
            sumPdrX += data.pdrX
            sumPdrY += data.pdrY
            sumGpsX += data.gpsX
            sumGpsY += data.gpsY
            sumPdrX2 += data.pdrX * data.pdrX
            sumPdrY2 += data.pdrY * data.pdrY
            sumPdrXGpsX += data.pdrX * data.gpsX
            sumPdrYGpsY += data.pdrY * data.gpsY
        }
        
        // 線形回帰による重み計算
        val denomX = n * sumPdrX2 - sumPdrX * sumPdrX
        val denomY = n * sumPdrY2 - sumPdrY * sumPdrY
        
        if (denomX != 0f) {
            correctionModel.positionWeightX = (n * sumPdrXGpsX - sumPdrX * sumGpsX) / denomX
            correctionModel.driftCorrectionX = (sumGpsX - correctionModel.positionWeightX * sumPdrX) / n
        }
        
        if (denomY != 0f) {
            correctionModel.positionWeightY = (n * sumPdrYGpsY - sumPdrY * sumGpsY) / denomY
            correctionModel.driftCorrectionY = (sumGpsY - correctionModel.positionWeightY * sumPdrY) / n
        }
        
        // 歩行距離補正の計算
        var totalPdrDistance = 0f
        var totalGpsDistance = 0f
        
        for (i in 1 until trainingData.size) {
            val prevPdr = trainingData[i-1]
            val currPdr = trainingData[i]
            val prevGps = trainingData[i-1]
            val currGps = trainingData[i]
            
            val pdrDist = sqrt((currPdr.pdrX - prevPdr.pdrX).pow(2) + (currPdr.pdrY - prevPdr.pdrY).pow(2))
            val gpsDist = sqrt((currGps.gpsX - prevGps.gpsX).pow(2) + (currGps.gpsY - prevGps.gpsY).pow(2))
            
            totalPdrDistance += pdrDist
            totalGpsDistance += gpsDist
        }
        
        if (totalPdrDistance > 0f) {
            correctionModel.stepLengthCorrection = totalGpsDistance / totalPdrDistance
        }
        
        saveModel()
    }
    
    fun applyCorrection(pdrX: Float, pdrY: Float): Pair<Float, Float> {
        val correctedX = pdrX * correctionModel.positionWeightX + correctionModel.driftCorrectionX
        val correctedY = pdrY * correctionModel.positionWeightY + correctionModel.driftCorrectionY
        return Pair(correctedX, correctedY)
    }
    
    fun getStepLengthCorrection(): Float = correctionModel.stepLengthCorrection
    
    fun saveModel() {
        try {
            val file = File(context.filesDir, "pdr_correction_model.json")
            file.writeText(gson.toJson(correctionModel))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun loadModel() {
        try {
            val file = File(context.filesDir, "pdr_correction_model.json")
            if (file.exists()) {
                val json = file.readText()
                correctionModel = gson.fromJson(json, CorrectionModel::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun clearTrainingData() {
        trainingData.clear()
    }
    
    fun getTrainingDataSize(): Int = trainingData.size
    
    fun hasTrainedModel(): Boolean {
        return File(context.filesDir, "pdr_correction_model.json").exists()
    }
}
