package com.example.apple.glidetest.media

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageView
import com.example.apple.glidetest.R
import com.orhanobut.logger.Logger
import com.txy.androidutils.TxyFileUtils
import com.txy.androidutils.TxyToastUtils
import com.txy.androidutils.dialog.TxyDialogUtils
import kotlinx.android.synthetic.main.activity_record_media.view.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Created by Apple on 17/9/16.
 */
class MediaSurfaceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : SurfaceView(context, attrs, defStyleAttr), Camera.PreviewCallback {

    var camera: Camera? = null
    var camerasCount = 1
    private var mediaRecorder: MediaRecorder? = null
    var mediaFile: File? = null
    private var currentCameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK
    private var cameraFlashType = Camera.Parameters.FLASH_MODE_AUTO
    private var videoFlashType = Camera.Parameters.FLASH_MODE_OFF
    var isCamera: Boolean = false
    private var toastUtils: TxyToastUtils? = null
    private var dialogUtisl: TxyDialogUtils? = null
    var previewSize: Camera.Size? = null

    private var surfaceCallBack = object : SurfaceHolder.Callback {
        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            previewSize = setCameraParameters(camera!!)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
            Logger.d("surfaceDestroyed---->")
        }

        override fun surfaceCreated(holder: SurfaceHolder?) {
            Logger.d("surfaceCreated---->")
            startPreview(holder!!)
        }
    }

    init {
        dialogUtisl = TxyDialogUtils(context)
        getCamera()
        toastUtils = TxyToastUtils(context)
        camerasCount = Camera.getNumberOfCameras()
        surfaceView.holder.setKeepScreenOn(true)
        surfaceView.holder.addCallback(surfaceCallBack)
    }

    fun takePicture() {
        camera!!.takePicture(null, null, object : Camera.PictureCallback {
            override fun onPictureTaken(data: ByteArray?, camera: Camera?) {
                Logger.e("onPictureTaken-->" + data + camera)
                mediaFile = TxyFileUtils.createIMGFile(context)
                val fos = FileOutputStream(mediaFile)
                val matrix = Matrix()
                if (currentCameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    matrix.setRotate(90f)
                } else {
                    matrix.setRotate(-90f)
                }
                var bitmap = BitmapFactory.decodeByteArray(data, 0, data!!.size)
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                fos.flush()
                fos.close()
                listener?.afterTakePicture(mediaFile!!)
                camera?.stopPreview()
            }
        })
    }

    fun startRecord() {
        Logger.d("initMediaRecorder")
        camera!!.unlock()
        mediaFile = TxyFileUtils.createVIDFile(context)
        mediaRecorder = MediaRecorder()
        mediaRecorder?.reset()
        mediaRecorder?.setCamera(camera)
        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        mediaRecorder?.setVideoSource(MediaRecorder.VideoSource.CAMERA)
//        设置视频输出格式和编码
        mediaRecorder?.setProfile(CamcorderProfile.get(currentCameraFacing, CamcorderProfile.QUALITY_480P))
        mediaRecorder?.setOrientationHint(90)
//        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
//        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
//        mediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP)
//        mediaRecorder?.setVideoFrameRate(4)
//        mediaRecorder?.setVideoSize(camera!!.parameters.previewSize.width,camera!!.parameters.previewSize.height)
//        mediaRecorder?.setMaxDuration(12000)
        mediaRecorder?.setOutputFile(mediaFile!!.getAbsolutePath())
        mediaRecorder?.setPreviewDisplay(holder.surface)
        try {
            mediaRecorder!!.prepare()
        } catch (e: IllegalStateException) {
            Logger.e("IllegalStateException preparing MediaRecorder: " + e.message)
            mediaRecorder?.release()
        } catch (e: IOException) {
            Logger.e("IOException preparing MediaRecorder: " + e.message)
            mediaRecorder?.release()
        }
        mediaRecorder?.start()

    }

    //  meizu  stop called in an invalid state: 0  stop failed: -1007
    fun stopRecord() {
        Logger.d("------>stopRecord")
        mediaRecorder?.setOnErrorListener(null)
        mediaRecorder?.setOnInfoListener(null)
        mediaRecorder?.setPreviewDisplay(null)
        try {
            mediaRecorder?.stop()
        } catch(e: RuntimeException) {
            Logger.e(e.message)
            mediaRecorder = null
            mediaRecorder = MediaRecorder()
            toastUtils!!.toast(context.getString(R.string.record_time_is_too_short))
            if (mediaFile != null && mediaFile!!.exists()) {
                mediaFile!!.delete()
                mediaFile = null
            }
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }
        if (mediaFile != null) {
            stopPreview()
            listener?.afterStopRecord(mediaFile!!)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event!!.action) {
            MotionEvent.ACTION_DOWN -> {
//                显示对焦指示器
                if (event.pointerCount == 1) {
                    listener?.touchFocus(event.x, event.y)
                    handleFocus(event.x, event.y)
                }
                if (event.pointerCount == 2)
                    Logger.e("pointerCount==" + 2)
            }
        }
        return super.onTouchEvent(event)
    }

    var handlerTime: Int = 0

    private fun handleFocus(x: Float, y: Float) {
        if (camera != null) {
            val parameters = camera!!.parameters
            val focusRect = calculateTapArea(x, y, 1f, context)
            camera!!.cancelAutoFocus()
            if (parameters.maxNumFocusAreas > 0) {
                val focusAreas = ArrayList<Camera.Area>()
                focusAreas.add(Camera.Area(focusRect, 800))
                parameters.focusAreas = focusAreas
            } else {
                Logger.i("focus area not supported")
                listener?.onFocusSuccess()
                return
            }
            val currentFocusMode = parameters.focusMode
            try {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                camera!!.parameters = parameters
                camera!!.autoFocus { success, camera ->
                    if (success && handlerTime > 1) {
                        val params = camera!!.parameters
                        params.focusMode = currentFocusMode
                        camera.parameters = params
                        handlerTime = 0
                        listener!!.onFocusSuccess()
                        Logger.e("onFocusSuccess")
                    } else {
                        handlerTime++
                        handleFocus(x, y)
                    }
                }
            } catch (e: Exception) {
                Logger.e(e.message + "--->autoFocus fail")
            }

        }
    }


    fun changeCameraFacing(ivFlash: ImageView): Int {
        if (camerasCount > 1) {
            currentCameraFacing
            if (currentCameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                currentCameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT
                ivFlash.visibility = View.INVISIBLE
            } else {
                ivFlash.visibility = View.VISIBLE
                currentCameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK
            }
            releaseCamera()
            camera = Camera.open(currentCameraFacing)
            startPreview(holder)
        } else toastUtils!!.toast("手机不支持前置摄像头！")
        return currentCameraFacing
    }

    fun switchFlash(ivFlash: ImageView) {
        if (isCamera) {
            when (cameraFlashType) {
                Camera.Parameters.FLASH_MODE_AUTO -> cameraFlashType = Camera.Parameters.FLASH_MODE_OFF
                Camera.Parameters.FLASH_MODE_OFF -> cameraFlashType = Camera.Parameters.FLASH_MODE_ON
                Camera.Parameters.FLASH_MODE_ON -> cameraFlashType = Camera.Parameters.FLASH_MODE_AUTO
            }
        } else {
            if (videoFlashType == Camera.Parameters.FLASH_MODE_OFF)
                videoFlashType = Camera.Parameters.FLASH_MODE_TORCH
            else if (videoFlashType == Camera.Parameters.FLASH_MODE_TORCH)
                videoFlashType = Camera.Parameters.FLASH_MODE_OFF
        }
        setFlashMode(ivFlash)
    }

    fun setFlashMode(ivFlash: ImageView) {
        val flashType = if (isCamera) cameraFlashType else videoFlashType
        when (flashType) {
            Camera.Parameters.FLASH_MODE_AUTO -> ivFlash.setImageResource(R.drawable.flash_auto)
            Camera.Parameters.FLASH_MODE_OFF -> ivFlash.setImageResource(R.drawable.flash_off)
            Camera.Parameters.FLASH_MODE_ON -> ivFlash.setImageResource(R.drawable.flash_on)
            Camera.Parameters.FLASH_MODE_TORCH -> ivFlash.setImageResource(R.drawable.flash_on)
        }
        val parameters = camera!!.parameters
        parameters.flashMode = flashType
        camera!!.parameters = parameters
    }

    interface OnMediaListener {
        fun afterTakePicture(mediaFile: File)
        fun afterStopRecord(mediaFile: File)
        fun onFocusSuccess()
        fun touchFocus(x: Float, y: Float)
    }

    private var listener: OnMediaListener? = null

    fun setOnMediaFinishListener(listener: OnMediaListener) {
        this.listener = listener
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
//  每一帧的回调

    }

    fun startPreview(holder: SurfaceHolder) {
        camera!!.setPreviewCallback(this)
        camera!!.setPreviewDisplay(holder)
        setCameraDisplayOrientation(context as Activity, currentCameraFacing, camera!!)
        camera!!.startPreview()
        Logger.d("------>startPreview")
    }

    private fun stopPreview() {
        try {
            camera?.setPreviewCallback(null)
            camera?.stopPreview()
            camera?.setPreviewDisplay(null)
            Logger.i("=======stop preview======")
        } catch (e: IOException) {
            Logger.e(e.message)
        }
    }

    fun releaseCamera() {
        try {
            camera?.setPreviewCallback(null)
            camera?.stopPreview()
//            这句要在stopPreview后执行，不然会卡顿或者花屏
            camera?.setPreviewDisplay(null)
            camera?.release()
            camera = null
        } catch(e: Exception) {
            Logger.e(e.message)
        }
    }


    fun getCamera() {
        if (camera == null) {
            try {
                camera = Camera.open(currentCameraFacing)
            } catch(e: Exception) {
                Logger.e(e.message)
            }
        }
    }

    fun destroy() {
        toastUtils?.destroy()
        dialogUtisl?.destroy()
        mediaRecorder?.release()
        mediaRecorder = null
        releaseCamera()
    }
}