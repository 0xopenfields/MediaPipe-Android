package com.example.facedetection_live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.mediapipe.solutioncore.CameraInput
import com.google.mediapipe.solutioncore.ResultGlRenderer
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView
import com.google.mediapipe.solutions.facedetection.FaceDetection
import com.google.mediapipe.solutions.facedetection.FaceDetectionOptions
import com.google.mediapipe.solutions.facedetection.FaceDetectionResult
import com.google.mediapipe.solutions.facedetection.FaceKeypoint
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer


class MainActivity : AppCompatActivity() {

    private val TAG: String = "MainActivity"

    private var faceDetection: FaceDetection? = null

    private var glSurfaceView: SolutionGlSurfaceView<FaceDetectionResult>? = null

    private var cameraInput: CameraInput? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        cameraInput = CameraInput(this@MainActivity)
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            faceDetection = FaceDetection(this@MainActivity,
                                          FaceDetectionOptions.builder()
                                              .setStaticImageMode(true)
                                              .setModelSelection(0)
                                              .build())
                .apply {
                    this.setErrorListener{ message, _ -> Log.e(TAG, "MediaPipe Face Detection error:$message") }
                    glSurfaceView = SolutionGlSurfaceView<FaceDetectionResult>(this@MainActivity, this.glContext, this.glMajorVersion)
                    glSurfaceView!!.setSolutionResultRenderer(renderer)
                    glSurfaceView!!.setRenderInputImage(true)
                    this.setResultListener { faceDetectionResult ->
                        glSurfaceView!!.setRenderData(faceDetectionResult)
                        glSurfaceView!!.requestRender()
                    }

                    cameraInput!!.setNewFrameListener{ textureFrame -> this.send(textureFrame) }
                }

            glSurfaceView!!.post { cameraInput!!.start(this@MainActivity,
                                                       faceDetection!!.glContext,
                                                       CameraInput.CameraFacing.FRONT,
                                                       glSurfaceView!!.width,
                                                       glSurfaceView!!.height) }
            glSurfaceView!!.visibility = View.VISIBLE

            val layout = findViewById<ConstraintLayout>(R.id.preview_display_layout)
            layout.addView(glSurfaceView)
            layout.requestLayout()
        }
    }

    override fun onPause() {
        super.onPause()

        cameraInput?.let {
            it.setNewFrameListener(null)
            it.close()
        }
        faceDetection?.let {
            it.close()
        }
        glSurfaceView?.let {
            it.setSolutionResultRenderer(null)
            it.visibility = View.GONE
        }
    }

    private val renderer = object : ResultGlRenderer<FaceDetectionResult> {

        private var program = 0

        private var u_PointSize = 0

        private var a_Position = 0

        private var u_ProjectionMatrix = 0

        private var u_Color = 0

        override fun setupRendering() {
            program = GLES20.glCreateProgram().also {
                val vs = loadGLShader(this@MainActivity, GLES20.GL_VERTEX_SHADER, "shaders/points.vert")
                val fs = loadGLShader(this@MainActivity, GLES20.GL_FRAGMENT_SHADER, "shaders/points.frag")

                GLES20.glAttachShader(it, vs)
                GLES20.glAttachShader(it, fs)
                GLES20.glLinkProgram(it)
                GLES20.glUseProgram(it)

                u_PointSize = GLES20.glGetUniformLocation(it, "u_PointSize")
                a_Position = GLES20.glGetAttribLocation(it, "a_Position")
                u_ProjectionMatrix = GLES20.glGetUniformLocation(it, "u_ProjectionMatrix")
                u_Color = GLES20.glGetUniformLocation(it, "u_Color")

                GLES20.glUseProgram(0)
            }
        }

        override fun renderResult(result: FaceDetectionResult?, projectionMatrix: FloatArray?) {
            result ?: return

            GLES20.glUseProgram(program)
            GLES20.glUniformMatrix4fv(u_ProjectionMatrix, 1, false, projectionMatrix, 0)
            GLES20.glUniform1f(u_PointSize, 16.0f)
            GLES20.glUniform4fv(u_Color, 1, floatArrayOf(1.0f, 0.0f, 0.0f, 1.0f), 0)

            for (face in result.multiFaceDetections()!!) {
                if (!face.hasLocationData()) continue

                var points = FloatArray(FaceKeypoint.NUM_KEY_POINTS * 2)
                for (i in 0 until FaceKeypoint.NUM_KEY_POINTS) {
                    points[2 * i] = face.locationData.getRelativeKeypoints(i).x
                    points[2 * i + 1] = face.locationData.getRelativeKeypoints(i).y
                }

                val vertexBuffer = makeFloatBuffer(points)
                GLES20.glEnableVertexAttribArray(a_Position)
                GLES20.glVertexAttribPointer(a_Position, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
                GLES20.glDrawArrays(GLES20.GL_POINTS, 0, FaceKeypoint.NUM_KEY_POINTS)
                GLES20.glDisableVertexAttribArray(a_Position)
            }

            GLES20.glUseProgram(0)
        }
    }

    companion object {
        @Throws(IOException::class)
        private fun loadGLShader(context: Context, type: Int, filename: String): Int {
            var code: String
            context.assets.open(filename).use { inputStream ->
                code = inputStream.bufferedReader().use { it.readText() }
            }

            var shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, code)
            GLES20.glCompileShader(shader)

            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

            if (compileStatus[0] == 0) {
                Log.e("loadGLShader", "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                shader = 0
            }
            if (shader == 0) {
                throw RuntimeException("Error creating shader.")
            }
            return shader
        }

        private fun makeFloatBuffer(arr: FloatArray): FloatBuffer {
            return ByteBuffer.allocateDirect(arr.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(arr)
                .apply { this.position(0) }
        }
    }
}