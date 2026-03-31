package com.naaammme.bbspace.feature.auth

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.naaammme.bbspace.core.model.GeetestResult
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GeetestDialog(
    gt: String,
    challenge: String,
    onResult: (GeetestResult) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("人机验证") },
        text = {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onSuccess(jsonStr: String) {
                                val obj = JSONObject(jsonStr)
                                val result = GeetestResult(
                                    validate = obj.optString("geetest_validate", ""),
                                    seccode = obj.optString("geetest_seccode", ""),
                                    challenge = obj.optString("geetest_challenge", "")
                                )
                                post { onResult(result) }
                            }

                            @JavascriptInterface
                            fun onError(ignored: String) {
                                post { onDismiss() }
                            }
                        }, "GeetestBridge")

                        val html = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta charset="utf-8">
                                <meta name="viewport" content="width=device-width,initial-scale=1">
                                <style>
                                    body { margin: 0; padding: 0; display: flex; justify-content: center; align-items: center; min-height: 100vh; background: transparent; }
                                    #captcha { width: 100%; }
                                </style>
                            </head>
                            <body>
                                <div id="captcha"></div>
                                <script src="https://static.geetest.com/static/js/gt.0.4.9.js"></script>
                                <script>
                                    initGeetest({
                                        gt: "$gt",
                                        challenge: "$challenge",
                                        offline: false,
                                        new_captcha: true,
                                        product: "bind",
                                        width: "100%%",
                                        https: true,
                                        protocol: "https://"
                                    }, function(captchaObj) {
                                        captchaObj.onReady(function() {
                                            captchaObj.verify();
                                        });
                                        captchaObj.onSuccess(function() {
                                            var r = captchaObj.getValidate();
                                            GeetestBridge.onSuccess(JSON.stringify(r));
                                        });
                                        captchaObj.onError(function(e) {
                                            GeetestBridge.onError(JSON.stringify(e || {}));
                                        });
                                    });
                                </script>
                            </body>
                            </html>
                        """.trimIndent()

                        loadDataWithBaseURL(
                            "https://static.geetest.com",
                            html,
                            "text/html",
                            "utf-8",
                            null
                        )
                    }
                }
            )
        }
    )
}
