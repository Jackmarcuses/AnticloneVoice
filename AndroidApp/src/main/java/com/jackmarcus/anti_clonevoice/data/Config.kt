package com.jackmarcus.anti_clonevoice.data

object Config {
    // Dynamically switchable at runtime
    var isProduction: Boolean = true
    
    private const val PRODUCTION_HOST = "anticlonevoice.onrender.com"
    
    // Your current computer's local IP for phone-to-PC communication
    var localIp: String = "192.168.17"
    private const val LOCAL_PORT = "8081"

    // Final clean URLs for Render.com
    val BASE_URL: String get() = if (isProduction) "https://anticlonevoice.onrender.com" else "http://$localIp:$LOCAL_PORT"
    val WS_URL: String get() = if (isProduction) "wss://anticlonevoice.onrender.com" else "ws://$localIp:$LOCAL_PORT"
}
