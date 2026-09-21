package dev.bodyblock.prototype

import android.content.Context
import android.graphics.*
import ai.onnxruntime.*
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import java.nio.FloatBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.*

class Detector(context: Context): AutoCloseable {
    private val env=OrtEnvironment.getEnvironment()
    private val session=OrtSession.SessionOptions().use { opts ->
        opts.setIntraOpNumThreads(2)
        env.createSession(context.assets.open("320n.onnx").use { it.readBytes() },opts)
    }
    private var eyes: FaceDetector?=null
    fun detect(bitmap: Bitmap, config: Config): List<Box> {
        val size=320
        val square=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val scale=size.toFloat()/max(bitmap.width,bitmap.height)
        Canvas(square).apply { drawColor(Color.BLACK); drawBitmap(bitmap,null,RectF(0f,0f,bitmap.width*scale,bitmap.height*scale),Paint(Paint.FILTER_BITMAP_FLAG)) }
        val pixels=IntArray(size*size); square.getPixels(pixels,0,size,0,0,size,size); square.recycle()
        val input=FloatArray(size*size*3)
        for(i in pixels.indices) { val p=pixels[i]; input[i]=(p shr 16 and 255)/255f; input[i+pixels.size]=(p shr 8 and 255)/255f; input[i+pixels.size*2]=(p and 255)/255f }
        val boxes=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),longArrayOf(1,3,320,320)).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST") val values=result[0].value as Array<Array<FloatArray>>
                DetectionMath.decode(values[0],bitmap.width,bitmap.height,config.confidence/100f).filter { it.category in config.enabled }.toMutableList()
            }
        }
        if(18 in config.enabled) {
            val faceDetector=eyes ?: FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).build()).also { eyes=it }
            val faces=Tasks.await(faceDetector.process(InputImage.fromBitmap(bitmap,0)),3,TimeUnit.SECONDS)
            for(face in faces) for(type in listOf(FaceLandmark.LEFT_EYE,FaceLandmark.RIGHT_EYE)) {
                val p=face.getLandmark(type)?.position ?: continue
                val r=max(5f,face.boundingBox.width()*.16f)
                boxes.add(Box(max(0f,p.x-r),max(0f,p.y-r*.65f),min(bitmap.width.toFloat(),p.x+r),min(bitmap.height.toFloat(),p.y+r*.65f),18,1f))
            }
        }
        return boxes
    }
    override fun close() { eyes?.close(); session.close() }
}
