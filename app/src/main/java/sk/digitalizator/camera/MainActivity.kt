package sk.digitalizator.camera

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Minimálny kamerový server pre PC:
 *   GET /status   -> "ok"
 *   GET /preview  -> MJPEG stream (nízke rozlíšenie, len na náhľad)
 *   GET /capture  -> jedna fotka v plnom rozlíšení (JPEG, orientácia v EXIF)
 * Port 8765, prístup z PC cez `adb forward tcp:8765 tcp:8765`.
 */
class MainActivity : ComponentActivity() {
    private val port = 8765
    private val camExecutor = Executors.newSingleThreadExecutor()
    private val netExecutor = Executors.newCachedThreadPool()
    private lateinit var imageCapture: ImageCapture
    private lateinit var statusView: TextView
    private lateinit var cameraView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 1500)
        }
    }

    /** Kedy naposledy počítač komunikoval s appkou (náhľad, fotka, stav). */
    @Volatile private var lastClient = 0L

    @Volatile private var latestPreview: ByteArray? = null

    private val askCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startCamera() else cameraView.text = "Bez povolenia kamery appka nefunguje."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        fun label(size: Float, bold: Boolean = false) = TextView(this).apply {
            textSize = size
            gravity = Gravity.CENTER
            setPadding(32, 16, 32, 16)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val title = label(22f, true).apply { text = "Digitalizátor kamera" }
        statusView = label(26f, true)
        cameraView = label(18f).apply { text = "Štartujem kameru…" }
        val hint = label(16f).apply {
            text = "Uistite sa, že je na telefóne zapnuté USB ladenie\n(Nastavenia → Možnosti vývojára → USB ladenie)."
            setTextColor(Color.DKGRAY)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(title); addView(statusView); addView(cameraView); addView(hint)
        })
        refreshStatus()
        askCamera.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun usbPlugged(): Boolean {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged and BatteryManager.BATTERY_PLUGGED_USB != 0
    }

    private fun usbDebugging(): Boolean =
        Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

    /** Pripojené = USB kábel + zapnuté USB ladenie, alebo počítač práve komunikuje s appkou. */
    private fun refreshStatus() {
        val pcActive = SystemClock.elapsedRealtime() - lastClient < 5000
        val plugged = usbPlugged()
        val adb = usbDebugging()
        when {
            pcActive || (plugged && adb) -> {
                statusView.text = "Pripojené"
                statusView.setTextColor(Color.rgb(46, 125, 50))
            }
            plugged -> {
                statusView.text = "Kábel je pripojený,\nUSB ladenie je vypnuté"
                statusView.setTextColor(Color.rgb(230, 120, 0))
            }
            else -> {
                statusView.text = "Nepripojené\nPripojte USB kábel k počítaču"
                statusView.setTextColor(Color.rgb(198, 40, 40))
            }
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()

            val highest = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setJpegQuality(100)
                .setResolutionSelector(highest)
                .build()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                android.util.Size(1280, 960),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                            )
                        ).build()
                ).build()
            analysis.setAnalyzer(camExecutor) { img ->
                try {
                    var bmp = img.toBitmap()
                    if (img.imageInfo.rotationDegrees != 0) {
                        val m = Matrix().apply { postRotate(img.imageInfo.rotationDegrees.toFloat()) }
                        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                    }
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
                    latestPreview = out.toByteArray()
                } finally {
                    img.close()
                }
            }

            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageCapture, analysis)
            startServer()
            cameraView.text = "Kamera beží (port $port)"
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startServer() {
        netExecutor.execute {
            val server = ServerSocket(port)
            while (!server.isClosed) {
                val client = server.accept()
                netExecutor.execute { handle(client) }
            }
        }
    }

    private fun handle(s: Socket) {
        s.use {
            val reader = it.getInputStream().bufferedReader()
            val request = reader.readLine() ?: return
            while (true) { if (reader.readLine().isNullOrEmpty()) break }
            val path = request.split(" ").getOrElse(1) { "/" }
            lastClient = SystemClock.elapsedRealtime()
            val out = it.getOutputStream()
            when {
                path.startsWith("/status") -> respond(out, "text/plain", "ok".toByteArray())
                path.startsWith("/preview") -> streamPreview(out)
                path.startsWith("/capture") -> {
                    val jpeg = capture()
                    if (jpeg == null) respond(out, "text/plain", "capture failed".toByteArray(), 500)
                    else respond(out, "image/jpeg", jpeg)
                }
                else -> respond(out, "text/plain", "not found".toByteArray(), 404)
            }
        }
    }

    private fun respond(out: java.io.OutputStream, type: String, body: ByteArray, code: Int = 200) {
        val head = "HTTP/1.1 $code OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun streamPreview(out: java.io.OutputStream) {
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\n" +
                "Cache-Control: no-cache\r\nConnection: close\r\n\r\n").toByteArray()
        )
        var last: ByteArray? = null
        try {
            while (true) {
                lastClient = SystemClock.elapsedRealtime()
                val frame = latestPreview
                if (frame != null && frame !== last) {
                    out.write("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n".toByteArray())
                    out.write(frame)
                    out.write("\r\n".toByteArray())
                    out.flush()
                    last = frame
                }
                Thread.sleep(40)
            }
        } catch (_: Exception) {
            // klient sa odpojil
        }
    }

    private fun capture(): ByteArray? {
        val file = File.createTempFile("cap", ".jpg", cacheDir)
        val latch = CountDownLatch(1)
        var ok = false
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) { ok = true; latch.countDown() }
                override fun onError(e: ImageCaptureException) { latch.countDown() }
            }
        )
        latch.await(20, TimeUnit.SECONDS)
        val bytes = if (ok) file.readBytes() else null
        file.delete()
        return bytes
    }
}
