package com.example.kaoshishuati

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.kaoshishuati.data.BankDb
import com.example.kaoshishuati.data.DoneStore
import com.example.kaoshishuati.data.DrillSettingsStore
import com.example.kaoshishuati.data.DrillRoundStore
import com.example.kaoshishuati.data.FeedbackStore
import com.example.kaoshishuati.data.ProgressStore
import com.example.kaoshishuati.data.WrongStore
import com.example.kaoshishuati.ui.App
import com.example.kaoshishuati.ui.theme.KaoshishuatiTheme

class MainActivity : ComponentActivity() {

    private val bankDb by lazy { BankDb(applicationContext) }
    private val wrongStore by lazy { WrongStore(applicationContext) }
    private val progressStore by lazy { ProgressStore(applicationContext) }
    private val doneStore by lazy { DoneStore(applicationContext) }
    private val feedbackStore by lazy { FeedbackStore(applicationContext) }
    private val drillSettingsStore by lazy { DrillSettingsStore(applicationContext) }
    private val drillRoundStore by lazy { DrillRoundStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KaoshishuatiTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(bank = bankDb, wrongStore = wrongStore, progressStore = progressStore,
                        doneStore = doneStore, feedbackStore = feedbackStore,
                        drillSettingsStore = drillSettingsStore, drillRoundStore = drillRoundStore)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bankDb.close()
    }
}
