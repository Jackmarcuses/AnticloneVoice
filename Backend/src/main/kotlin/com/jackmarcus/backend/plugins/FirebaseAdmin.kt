package com.jackmarcus.backend.plugins

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.ktor.server.application.*
import java.io.File
import java.io.FileInputStream

fun Application.configureFirebaseAdmin() {
    val possiblePaths = listOf("service-account.json", "Backend/service-account.json", "service-account.json.json", "Backend/service-account.json.json")
    val file = possiblePaths.map { File(it) }.firstOrNull { it.exists() }
    
    if (file == null) {
        println("WARNING: service-account.json not found. Looked in: $possiblePaths. Firebase Auth will not work.")
        return
    }

    try {
        val serviceAccount = FileInputStream(file)
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build()
        
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options)
            println("Firebase Admin SDK initialized successfully using ${file.absolutePath}")
        }
    } catch (e: Exception) {
        println("ERROR: Failed to initialize Firebase Admin SDK: ${e.message}")
        e.printStackTrace()
    }
}
