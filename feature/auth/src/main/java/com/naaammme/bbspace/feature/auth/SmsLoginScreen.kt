package com.naaammme.bbspace.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.model.SmsLoginState
import com.naaammme.bbspace.feature.auth.model.SmsLoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsLoginScreen(
    viewModel: SmsLoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit = {},
    onBack: () -> Unit = {},
    onSwitchToQr: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val countdown by viewModel.countdown.collectAsStateWithLifecycle()
    val lastCaptchaKey by viewModel.lastCaptchaKey.collectAsStateWithLifecycle()

    var phone by rememberSaveable { mutableStateOf("") }
    var smsCode by rememberSaveable { mutableStateOf("") }

    // 极验弹窗需要的 token
    var geetestToken by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is SmsLoginState.Success) {
            onLoginSuccess()
        }
    }

    // 极验弹窗
    val geetestState = state
    if (geetestState is SmsLoginState.NeedGeetest) {
        geetestToken = geetestState.token
        GeetestDialog(
            gt = geetestState.gt,
            challenge = geetestState.challenge,
            onResult = { result ->
                viewModel.onGeetestResult(result, geetestToken)
            },
            onDismiss = {
                viewModel.resetState()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("手机号登录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() } },
                label = { Text("手机号") },
                prefix = { Text("+86 ") }, // TODO: 通过 /x/passport-login/country? 获取国家码
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )



            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = smsCode,
                    onValueChange = { smsCode = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("验证码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                val smsSent = state is SmsLoginState.SmsSent
                val sending = state is SmsLoginState.SendingSms
                val canSend = phone.length >= 11 && countdown == 0 && !sending

                Button(
                    onClick = { viewModel.sendSms(phone) },
                    enabled = canSend,
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(
                        when {
                            sending -> "发送中..."
                            countdown > 0 -> "${countdown}s"
                            smsSent -> "重新发送"
                            else -> "获取验证码"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val canLogin = smsCode.length == 6 && (state is SmsLoginState.SmsSent || (state is SmsLoginState.Error && lastCaptchaKey.isNotEmpty()))
            val logging = state is SmsLoginState.Logging

            Button(
                onClick = { viewModel.login(phone, code = smsCode) },
                enabled = canLogin && !logging,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (logging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("登录")
                }
            }

            // 错误提示
            val errorState = state
            if (errorState is SmsLoginState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorState.msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "手机号仅用于请求 B站官方接口获取鉴权信息，所有数据均保存于本地设备。请务必通过 GitHub 渠道下载本应用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onSwitchToQr) {
                Text("扫码登录")
            }
        }
    }
}
