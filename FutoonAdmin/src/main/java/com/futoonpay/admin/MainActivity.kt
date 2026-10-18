package com.futoonpay.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.futoonpay.admin.ui.AppRoot
import com.futoonpay.admin.ui.AppLaunchTarget
import com.futoonpay.admin.ui.AppViewModel
import com.futoonpay.admin.ui.MainTab
import com.futoonpay.admin.ui.theme.FutoonTheme

class MainActivity : EmbeddedAdminActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        enableEdgeToEdge()
        setContent {
            FutoonTheme {
                val app = FutoonApplication.from(this)
                val viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory(app))
                AppRoot(
                    viewModel,
                    AppLaunchTarget(
                        tab = intent.getStringExtra(EXTRA_TAB)?.let { value ->
                            MainTab.entries.firstOrNull { it.name.equals(value, true) }
                        },
                        channelId = intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty(),
                        merchantId = intent.getStringExtra(EXTRA_MERCHANT_ID).orEmpty(),
                    ),
                )
            }
        }
    }

    companion object {
        const val EXTRA_TAB = "main_tab"
        const val EXTRA_CHANNEL_ID = "channel_id"
        const val EXTRA_MERCHANT_ID = "merchant_id"
    }
}
