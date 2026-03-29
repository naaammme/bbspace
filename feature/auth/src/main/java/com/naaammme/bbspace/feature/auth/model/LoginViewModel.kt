package com.naaammme.bbspace.feature.auth.model

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.naaammme.bbspace.core.model.LoginState
import com.naaammme.bbspace.core.domain.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginRepository: AuthRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private var pollJob: Job? = null

    init {
        getQrCode()
    }

    fun getQrCode() {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading

            loginRepository.getQrCode()
                .onSuccess { qrCodeData ->
                    _loginState.value = LoginState.QrCodeReady(qrCodeData)
                    _qrBitmap.value = null
                    launch(Dispatchers.Default) {
                        _qrBitmap.value = generateQrCode(qrCodeData.url, 800)
                    }
                    startPolling(qrCodeData.authCode)
                }
                .onFailure { error ->
                    _loginState.value = LoginState.Error(error.message ?: "获取二维码失败")
                }
        }
    }

    private fun startPolling(authCode: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(2000)

                loginRepository.pollQrCode(authCode)
                    .onSuccess { (status, grant) ->
                        when (status) {
                            0 -> {
                            }
                            1 -> {
                                _loginState.value = LoginState.Scanned
                            }
                            2 -> {
                                grant?.let {
                                    val saved = loginRepository.getSavedCredential()
                                    if (saved == null || saved.mid != it.mid) {
                                        _loginState.value = LoginState.Error("请先用手机号登录当前账号后再扫码绑定 HD key")
                                    } else {
                                        loginRepository.saveHdAccessKey(it.mid, it.accessKey, it.expiresIn)
                                        _loginState.value = LoginState.QrSuccess(it)
                                    }
                                }
                                pollJob?.cancel()
                            }
                        }
                    }
                    .onFailure { error ->
                        _loginState.value = LoginState.Error(error.message ?: "轮询失败")
                        pollJob?.cancel()
                    }
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
    }

    fun resetState() {
        stopPolling()
        _loginState.value = LoginState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    private fun generateQrCode(content: String, size: Int): Bitmap? {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] =
                    if (bitMatrix[x, y]) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
