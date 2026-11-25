package com.godskingdomnow.familybank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.godskingdomnow.familybank.ui.AppNav
import com.godskingdomnow.familybank.ui.FamilyBankTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FamilyBankApp).container
        setContent {
            FamilyBankTheme {
                AppNav(container)
            }
        }
    }
}
