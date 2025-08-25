package com.example.pdrapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PDRScreen(viewModel: PDRViewModel = viewModel()) {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        viewModel.initializeSensors(context)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // タイトル
        Text(
            text = "Pedestrian Dead Reckoning",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        // 学習モード状態表示
        if (viewModel.isLearningMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = "学習モード",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "学習モード",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "GPSとPDRの軌跡を同時記録中...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "学習データ: ${viewModel.trainingDataCount}件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // 統計情報
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "歩数",
                value = "${viewModel.stepCount}",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "距離",
                value = "${String.format("%.1f", viewModel.totalDistance)}m",
                modifier = Modifier.weight(1f)
            )
            if (viewModel.hasTrainedModel && !viewModel.isLearningMode) {
                StatCard(
                    title = "補正",
                    value = "ON",
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            }
        }
        
        // 軌跡表示
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = if (viewModel.isLearningMode) "PDR軌跡（青）+ GPS軌跡（緑）" else "歩行軌跡",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8F9FA))
                ) {
                    if (viewModel.pathPoints.isNotEmpty() || viewModel.gpsPathPoints.isNotEmpty()) {
                        // 全ての点を考慮した境界を計算
                        val allPoints = viewModel.pathPoints + viewModel.gpsPathPoints
                        if (allPoints.isNotEmpty()) {
                            val bounds = calculateBounds(allPoints)
                            val scale = calculateScale(bounds, size.width, size.height)
                            val offset = calculateOffset(bounds, size.width, size.height, scale)
                            
                            // PDR軌跡を描画（青色）
                            if (viewModel.pathPoints.isNotEmpty()) {
                                drawPath(
                                    points = viewModel.pathPoints,
                                    color = if (viewModel.isLearningMode) Color.Blue else Color.Red,
                                    scale = scale,
                                    offset = offset
                                )
                            }
                            
                            // GPS軌跡を描画（緑色、学習モードのみ）
                            if (viewModel.isLearningMode && viewModel.gpsPathPoints.isNotEmpty()) {
                                drawPath(
                                    points = viewModel.gpsPathPoints,
                                    color = Color.Green,
                                    scale = scale,
                                    offset = offset
                                )
                            }
                            
                            // 現在位置をマーク
                            if (viewModel.pathPoints.isNotEmpty()) {
                                val currentPoint = viewModel.pathPoints.last()
                                val screenX = currentPoint.x * scale + offset.x
                                val screenY = currentPoint.y * scale + offset.y
                                
                                drawCircle(
                                    color = Color.Red,
                                    radius = 8f,
                                    center = Offset(screenX, screenY)
                                )
                            }
                        }
                    } else {
                        // 軌跡がない場合の表示
                        drawText("歩行を開始すると軌跡が表示されます")
                    }
                }
            }
        }
        
        // コントロールボタン
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 通常モード開始/停止ボタン
            if (!viewModel.isLearningMode) {
                Button(
                    onClick = {
                        if (viewModel.isTracking) {
                            viewModel.stopTracking()
                        } else {
                            viewModel.startTracking(false)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (viewModel.isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (viewModel.isTracking) "停止" else "開始")
                }
            }
            
            // 学習モード開始/停止ボタン
            Button(
                onClick = {
                    if (viewModel.isLearningMode) {
                        viewModel.stopTracking()
                    } else {
                        viewModel.startTracking(true)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.isLearningMode) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (viewModel.isLearningMode) Icons.Default.Stop else Icons.Default.School,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (viewModel.isLearningMode) "学習停止" else "学習開始")
            }
            
            // リセットボタン
            OutlinedButton(
                onClick = { viewModel.resetPath() },
                modifier = Modifier.weight(1f),
                enabled = !viewModel.isTracking
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("リセット")
            }
        }
        
        // 学習済みモデル情報
        if (viewModel.hasTrainedModel) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "✓ 学習済みモデルが利用可能",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "通常モードでは自動的に軌跡補正が適用されます",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun DrawScope.drawPath(
    points: List<Point>,
    color: Color,
    scale: Float,
    offset: Offset
) {
    if (points.size >= 2) {
        val path = Path()
        val firstPoint = points.first()
        path.moveTo(
            firstPoint.x * scale + offset.x,
            firstPoint.y * scale + offset.y
        )
        
        for (i in 1 until points.size) {
            val point = points[i]
            path.lineTo(
                point.x * scale + offset.x,
                point.y * scale + offset.y
            )
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

fun DrawScope.drawText(text: String) {
    // シンプルなテキスト表示のプレースホルダー
    // 実際の実装では、nativeCanvasを使用してテキストを描画
}

data class Bounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
)

fun calculateBounds(points: List<Point>): Bounds {
    return Bounds(
        minX = points.minOf { it.x },
        maxX = points.maxOf { it.x },
        minY = points.minOf { it.y },
        maxY = points.maxOf { it.y }
    )
}

fun calculateScale(bounds: Bounds, canvasWidth: Float, canvasHeight: Float): Float {
    val dataWidth = bounds.maxX - bounds.minX
    val dataHeight = bounds.maxY - bounds.minY
    
    if (dataWidth == 0f && dataHeight == 0f) return 1f
    
    val padding = 40f
    val scaleX = if (dataWidth > 0) (canvasWidth - 2 * padding) / dataWidth else Float.MAX_VALUE
    val scaleY = if (dataHeight > 0) (canvasHeight - 2 * padding) / dataHeight else Float.MAX_VALUE
    
    return min(scaleX, scaleY).coerceAtLeast(0.1f)
}

fun calculateOffset(bounds: Bounds, canvasWidth: Float, canvasHeight: Float, scale: Float): Offset {
    val centerX = (bounds.minX + bounds.maxX) / 2
    val centerY = (bounds.minY + bounds.maxY) / 2
    
    return Offset(
        canvasWidth / 2 - centerX * scale,
        canvasHeight / 2 - centerY * scale
    )
}
