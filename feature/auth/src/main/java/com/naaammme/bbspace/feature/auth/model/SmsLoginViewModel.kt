package com.naaammme.bbspace.feature.auth.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.auth.AuthRepository
import com.naaammme.bbspace.core.model.GeetestResult
import com.naaammme.bbspace.core.model.SmsLoginState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmsLoginViewModel @Inject constructor(
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<SmsLoginState>(SmsLoginState.Idle)
    val state: StateFlow<SmsLoginState> = _state.asStateFlow()

    private val _countdown = MutableStateFlow(0)
    val countdown: StateFlow<Int> = _countdown.asStateFlow()

    private var countdownJob: Job? = null
    private val _lastCaptchaKey = MutableStateFlow("")
    val lastCaptchaKey: StateFlow<String> = _lastCaptchaKey.asStateFlow()

    // 暂存极验重试时需要的参数
    private var pendingTel = ""
    private var pendingCid = 86

    fun sendSms(tel: String, cid: Int = 86) {
        pendingTel = tel
        pendingCid = cid
        doSendSms(tel, cid)
    }

    private fun doSendSms(
        tel: String, cid: Int,
        geeValidate: String = "", geeSeccode: String = "",
        geeChallenge: String = "", recaptchaToken: String = ""
    ) {
        viewModelScope.launch {
            _state.value = SmsLoginState.SendingSms

            authRepo.sendSmsCode(tel, cid, geeValidate, geeSeccode, geeChallenge, recaptchaToken)
                .onSuccess { result ->
                    if (result.needGeetest) {
                        _state.value = SmsLoginState.NeedGeetest(
                            gt = result.geeGt,
                            challenge = result.geeChallenge,
                            token = result.recaptchaToken
                        )
                    } else {
                        _lastCaptchaKey.value = result.captchaKey
                        _state.value = SmsLoginState.SmsSent(result.captchaKey)
                        startCountdown()
                    }
                }
                .onFailure { e ->
                    _state.value = SmsLoginState.Error(e.message ?: "发送验证码失败")
                }
        }
    }

    fun onGeetestResult(result: GeetestResult, recaptchaToken: String) {
        doSendSms(
            tel = pendingTel,
            cid = pendingCid,
            geeValidate = result.validate,
            geeSeccode = result.seccode,
            geeChallenge = result.challenge,
            recaptchaToken = recaptchaToken
        )
    }

    fun login(tel: String, cid: Int = 86, code: String) {
        val captchaKey = (_state.value as? SmsLoginState.SmsSent)?.captchaKey ?: _lastCaptchaKey.value
        if (captchaKey.isEmpty()) return
        viewModelScope.launch {
            _state.value = SmsLoginState.Logging

            authRepo.loginBySms(tel, cid, code, captchaKey)
                .onSuccess { credential ->
                    authRepo.guestMode = false
                    authRepo.saveCredential(credential)
                    authRepo.fetchMyInfo(credential)
                    _state.value = SmsLoginState.Success(credential)
                }
                .onFailure { e ->
                    _state.value = SmsLoginState.Error(e.message ?: "登录失败")
                }
        }
    }

    fun resetState() {
        _state.value = SmsLoginState.Idle
        countdownJob?.cancel()
        _countdown.value = 0
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in 60 downTo 1) {
                _countdown.value = i
                delay(1000)
            }
            _countdown.value = 0
        }
    }
}
