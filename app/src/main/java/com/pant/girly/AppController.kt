package com.pant.girly

import android.app.Application
import com.google.firebase.FirebaseApp

class AppController : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        // Other application-wide initializations
    }
}