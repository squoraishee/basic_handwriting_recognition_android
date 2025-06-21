package com.mobilonix.handwritingrecognitiontestbad

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class DrawPoint(
    val x: Float,
    val y: Float,
    val time: Long
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HandwritingCanvas(
    modifier: Modifier = Modifier,
    onLetterRecognized: (String) -> Unit = {}
) {
    var currentPath by remember { mutableStateOf(Path()) }
    var paths by remember { mutableStateOf(listOf<Path>()) }
    var drawPoints by remember { mutableStateOf(mutableListOf<DrawPoint>()) }
    var recognizedLetter by remember { mutableStateOf("") }
    var isDrawing by remember { mutableStateOf(false) }
    var drawTrigger by remember { mutableStateOf(0) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Initialize ML Kit recognizer
    val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
    val model = modelIdentifier?.let { DigitalInkRecognitionModel.builder(it).build() }
    val recognizer = model?.let { DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(it).build()) }
    
    LaunchedEffect(modelIdentifier) {
        modelIdentifier?.let {
            val remoteModelManager = RemoteModelManager.getInstance()
            remoteModelManager.download(model!!, DownloadConditions.Builder().build())
        }
    }

    Box(
        modifier = modifier
            .size(60.dp)
            .background(Color.White)
            .border(2.dp, Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            paths = emptyList()
                            currentPath = Path()
                            currentPath.moveTo(event.x, event.y)
                            drawPoints.clear()
                            drawPoints.add(DrawPoint(event.x, event.y, System.currentTimeMillis()))
                            isDrawing = true
                            recognizedLetter = ""
                            drawTrigger++
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            currentPath.lineTo(event.x, event.y)
                            drawPoints.add(DrawPoint(event.x, event.y, System.currentTimeMillis()))
                            drawTrigger++
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            paths = paths + currentPath
                            isDrawing = false
                            drawTrigger++
                            scope.launch {
                                kotlinx.coroutines.delay(800)
                                if (!isDrawing && drawPoints.isNotEmpty()) {
                                    val result = performRecognition(drawPoints, recognizer)
                                    if (result.isNotEmpty()) {
                                        recognizedLetter = result.first().uppercase()
                                        onLetterRecognized(recognizedLetter)
                                        // Clear the drawing after recognition
                                        paths = emptyList()
                                        currentPath = Path()
                                        drawTrigger++
                                    }
                                }
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            drawTrigger
            paths.forEach { path ->
                drawPath(
                    path = path,
                    color = Color.Blue.copy(alpha = 0.7f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            
            // Draw current path
            if (isDrawing) {
                drawPath(
                    path = currentPath,
                    color = Color.Blue.copy(alpha = 0.7f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        if (recognizedLetter.isNotEmpty() && !isDrawing) {
            Text(
                text = recognizedLetter,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}

suspend fun performRecognition(
    drawPoints: List<DrawPoint>,
    recognizer: DigitalInkRecognizer?
): List<String> = suspendCoroutine { continuation ->
    if (recognizer == null || drawPoints.isEmpty()) {
        continuation.resume(emptyList())
        return@suspendCoroutine
    }
    
    val inkBuilder = Ink.builder()
    val strokeBuilder = Ink.Stroke.builder()
    
    drawPoints.forEach { point ->
        strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, point.time))
    }
    
    inkBuilder.addStroke(strokeBuilder.build())
    val ink = inkBuilder.build()
    
    recognizer.recognize(ink)
        .addOnSuccessListener { result ->
            val candidates = result.candidates.map { it.text }
            continuation.resume(candidates)
        }
        .addOnFailureListener {
            continuation.resume(emptyList())
        }
}

@Composable
fun HandwritingGrid(
    rows: Int = 5,
    columns: Int = 5,
    modifier: Modifier = Modifier
) {
    var recognizedLetters by remember { mutableStateOf(Array(rows) { Array(columns) { "" } }) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Write Letters in the Grid",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Card(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in 0 until rows) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        for (col in 0 until columns) {
                            HandwritingCanvas(
                                onLetterRecognized = { letter ->
                                    val newLetters = recognizedLetters.clone()
                                    newLetters[row][col] = letter
                                    recognizedLetters = newLetters
                                }
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = {
                    recognizedLetters = Array(rows) { Array(columns) { "" } }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Clear All")
            }
            
            Button(
                onClick = {
                    val result = recognizedLetters.joinToString("\n") { row ->
                        row.joinToString(" ") { it.ifEmpty { "_" } }
                    }
                    println("Grid Result:\n$result")
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Show Result")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Tap and draw a letter in each square",
            fontSize = 14.sp,
            color = Color.Gray
        )
    }
} 