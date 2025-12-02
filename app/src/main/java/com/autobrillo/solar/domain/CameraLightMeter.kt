package com.autobrillo.solar.domain

import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class CameraLightMeter(private val context: Context) {
    suspend fun measureLux(): Float = withContext(Dispatchers.Main) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await(context)
        val selector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        val lifecycleOwner = OneShotLifecycleOwner()
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(320, 240))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val startTime = SystemClock.elapsedRealtime()
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(context)
            analysis.setAnalyzer(executor) { image ->
                val elapsed = SystemClock.elapsedRealtime() - startTime
                if (elapsed < WARMUP_MS) {
                    image.close()
                    return@setAnalyzer
                }
                val plane = image.planes.firstOrNull()
                if (plane != null) {
                    val avg = plane.averageCenterLuma(image.width, image.height)
                    val normalized = avg / 255f
                    val pseudoLux = luminanceToLux(normalized)
                    image.close()
                    analysis.clearAnalyzer()
                    lifecycleOwner.destroy()
                    cameraProvider.unbindAll()
                    if (cont.isActive) {
                        cont.resume(pseudoLux, onCancellation = null)
                    }
                } else {
                    image.close()
                }
            }

            try {
                lifecycleOwner.start()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, analysis)
            } catch (t: Throwable) {
                lifecycleOwner.destroy()
                cameraProvider.unbindAll()
                if (cont.isActive) {
                    cont.resumeWith(Result.failure(t))
                }
            }

            cont.invokeOnCancellation {
                lifecycleOwner.destroy()
                cameraProvider.unbindAll()
                analysis.clearAnalyzer()
            }
        }
    }

    private fun ImageProxy.PlaneProxy.averageCenterLuma(width: Int, height: Int): Float {
        val planeRowStride = rowStride
        val planePixelStride = pixelStride
        val planeBuffer = buffer
        val total = planeBuffer.remaining()
        val data = ByteArray(total)
        planeBuffer.get(data)
        planeBuffer.rewind()
        val startX = width / 4
        val endX = width - startX
        val startY = height / 4
        val endY = height - startY
        var sum = 0L
        var count = 0
        for (y in startY until endY) {
            val rowOffset = y * planeRowStride
            for (x in startX until endX) {
                val index = rowOffset + x * planePixelStride
                if (index >= total) continue
                sum += (data[index].toInt() and 0xFF)
                count++
            }
        }
        return if (count > 0) sum.toFloat() / count else 0f
    }

    private fun luminanceToLux(normalized: Float): Float {
        val safe = normalized.coerceIn(0f, 1f)
        val scaled = safe * MAX_LUX
        return scaled
    }

    private class OneShotLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        init {
            registry.currentState = Lifecycle.State.CREATED
        }

        fun start() {
            registry.currentState = Lifecycle.State.STARTED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }

        override val lifecycle: Lifecycle
            get() = registry
    }

    companion object {
        private const val MAX_LUX = 1200f
        private const val WARMUP_MS = 1000L
    }
}

private suspend fun <T> ListenableFuture<T>.await(context: Context): T = suspendCancellableCoroutine { cont ->
    addListener({
        try {
            cont.resume(get(), onCancellation = null)
        } catch (t: Throwable) {
            cont.resumeWith(Result.failure(t))
        }
    }, ContextCompat.getMainExecutor(context))
    cont.invokeOnCancellation { cancel(true) }
}
