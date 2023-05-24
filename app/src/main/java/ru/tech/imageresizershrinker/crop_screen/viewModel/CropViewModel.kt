package ru.tech.imageresizershrinker.crop_screen.viewModel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarttoolfactory.cropper.model.AspectRatio
import com.smarttoolfactory.cropper.model.OutlineType
import com.smarttoolfactory.cropper.model.RectCropShape
import com.smarttoolfactory.cropper.settings.CropDefaults
import com.smarttoolfactory.cropper.settings.CropOutlineProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.tech.imageresizershrinker.utils.helper.BitmapInfo
import ru.tech.imageresizershrinker.utils.helper.compressFormat
import ru.tech.imageresizershrinker.utils.helper.extension
import ru.tech.imageresizershrinker.utils.helper.mimeTypeInt
import ru.tech.imageresizershrinker.utils.storage.FileController
import ru.tech.imageresizershrinker.utils.storage.SaveTarget
import ru.tech.imageresizershrinker.utils.helper.BitmapUtils.canShow
import ru.tech.imageresizershrinker.utils.helper.BitmapUtils.resizeBitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream


class CropViewModel : ViewModel() {

    private val _cropProperties = mutableStateOf(
        CropDefaults.properties(
            cropOutlineProperty = CropOutlineProperty(
                OutlineType.Rect,
                RectCropShape(0, "")
            ),
            fling = true
        )
    )
    val cropProperties by _cropProperties

    private val _uri = mutableStateOf(Uri.EMPTY)

    private var internalBitmap = mutableStateOf<Bitmap?>(null)

    private val _bitmap: MutableState<Bitmap?> = mutableStateOf(null)
    val bitmap: Bitmap? by _bitmap

    val isBitmapChanged get() = internalBitmap.value != _bitmap.value

    var mimeType = Bitmap.CompressFormat.PNG
        private set

    private val _isLoading: MutableState<Boolean> = mutableStateOf(false)
    val isLoading: Boolean by _isLoading

    fun updateBitmap(bitmap: Bitmap?, newBitmap: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            var bmp: Bitmap?
            withContext(Dispatchers.IO) {
                bmp = if (bitmap?.canShow() == false) {
                    bitmap.resizeBitmap(
                        height_ = (bitmap.height * 0.9f).toInt(),
                        width_ = (bitmap.width * 0.9f).toInt(),
                        resize = 1
                    )
                } else bitmap

                while (bmp?.canShow() == false) {
                    bmp = bmp?.resizeBitmap(
                        height_ = (bmp!!.height * 0.9f).toInt(),
                        width_ = (bmp!!.width * 0.9f).toInt(),
                        resize = 1
                    )
                }
            }
            if (newBitmap) {
                internalBitmap.value = bmp
            }
            _bitmap.value = bmp
            _isLoading.value = false
        }
    }

    fun updateMimeType(mime: Int) {
        mimeType = mime.extension.compressFormat
    }

    fun saveBitmap(
        bitmap: Bitmap? = _bitmap.value,
        fileController: FileController,
        onComplete: (success: Boolean) -> Unit
    ) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            bitmap?.let { bitmap ->
                if (!fileController.isExternalStorageWritable()) {
                    onComplete(false)
                } else {
                    val localBitmap = bitmap

                    val savingFolder = fileController.getSavingFolder(
                        SaveTarget(
                            bitmapInfo = BitmapInfo(
                                mimeTypeInt = mimeType.extension.mimeTypeInt,
                                width = localBitmap.width,
                                height = localBitmap.height
                            ),
                            uri = _uri.value,
                            sequenceNumber = null
                        )
                    )

                    val fos = savingFolder.outputStream

                    localBitmap.compress(mimeType, 100, fos)
                    val out = ByteArrayOutputStream()
                    localBitmap.compress(mimeType, 100, out)
                    val decoded =
                        BitmapFactory.decodeStream(ByteArrayInputStream(out.toByteArray()))

                    out.flush()
                    out.close()
                    fos!!.flush()
                    fos.close()

                    _bitmap.value = decoded
                    onComplete(true)
                }
            }
        }
    }

    fun setCropAspectRatio(aspectRatio: AspectRatio) {
        _cropProperties.value = _cropProperties.value.copy(
            aspectRatio = aspectRatio,
            fixedAspectRatio = aspectRatio != AspectRatio.Original
        )
    }

    fun resetBitmap() {
        _bitmap.value = internalBitmap.value
    }

    fun imageCropStarted() {
        _isLoading.value = true
    }

    fun imageCropFinished() {
        _isLoading.value = false
    }

    fun setUri(uri: Uri) {
        _uri.value = uri
    }

}
