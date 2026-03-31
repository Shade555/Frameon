
package com.example.frameon.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.*
import com.example.frameon.MainActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class SensitiveDataWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // 1. Get URIs that triggered this worker (Standard WorkManager feature)
        val triggeredUris = triggeredContentUris

        // 2. If no URIs were triggered, check if one was passed manually
        val urisToScan = triggeredUris.ifEmpty {
            inputData.getString("image_uri")?.let { listOf(it.toUri()) } ?: emptyList()
        }

        if (urisToScan.isNotEmpty()) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            for (uri in urisToScan) {
                try {
                    // Skip if file is gone or inaccessible
                    applicationContext.contentResolver.openInputStream(uri)?.use { it.close() } ?: continue
                    
                    val image = InputImage.fromFilePath(applicationContext, uri)
                    val visionText = recognizer.process(image).await()
                    
                    if (performProfessionalScan(visionText)) {
                        sendNotification()
                        break // One notification per batch is enough
                    }
                } catch (ignored: Exception) {
                    continue
                }
            }
        }

        // 3. CRITICAL: Re-schedule the worker to keep monitoring in the background
        scheduleNext(applicationContext)

        return Result.success()
    }

    companion object {
        fun scheduleNext(context: Context) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                .setTriggerContentMaxDelay(5, TimeUnit.SECONDS)
                .build()

            val request = OneTimeWorkRequestBuilder<SensitiveDataWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "SensitiveDataMonitor",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    private fun performProfessionalScan(visionText: Text): Boolean {
        val labels = mutableListOf<DetectedElement>()
        val values = mutableListOf<DetectedElement>()
        val labelRegex = Regex("(?i)(password|pwd|login|pin|ssn|secret|key|account|cvv|security code|pass)")
        val cardPattern = Regex("\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}")

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val text = line.text.trim()
                val box = line.boundingBox ?: continue
                if (labelRegex.containsMatchIn(text)) {
                    labels.add(DetectedElement(text, box))
                } else if (text.length >= 2) {
                    values.add(DetectedElement(text, box))
                }
                if (cardPattern.containsMatchIn(text)) {
                    val cardNumber = text.filter { it.isDigit() }
                    if (isValidLuhn(cardNumber)) return true
                }
            }
        }

        for (label in labels) {
            for (value in values) {
                if (isValueRelatedToLabel(label.box, value.box)) return true
            }
        }
        return false
    }

    private fun isValueRelatedToLabel(l: Rect, v: Rect): Boolean {
        val threshold = 200
        val isToRight = v.left >= l.right && v.left <= l.right + threshold && 
                        v.centerY() >= l.top - 20 && v.centerY() <= l.bottom + 20
        val isBelow = v.top >= l.bottom && v.top <= l.bottom + threshold && 
                      v.centerX() >= l.left - 100 && v.centerX() <= l.right + 100
        return isToRight || isBelow
    }

    private fun isValidLuhn(number: String): Boolean {
        if (number.length < 13) return false
        var sum = 0
        var alternate = false
        for (i in number.length - 1 downTo 0) {
            var n = number[i].digitToInt()
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return (sum % 10 == 0)
    }

    private data class DetectedElement(val text: String, val box: Rect)

    private fun sendNotification() {
        val channelId = "security_alerts_v3" // Force fresh settings with a new ID
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pattern = longArrayOf(0, 500, 200, 500)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Security Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts about sensitive information found in photos"
                enableVibration(true)
                vibrationPattern = pattern
                setSound(defaultSoundUri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Sensitive Data Alert")
            .setContentText("Frameon detected a potential password or credit card. Tap to review.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(pattern)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
