package com.jackmarcus.anti_clonevoice.data

object Config {
    // Dynamically switchable at runtime
    var isProduction: Boolean = true
    
    private const val PRODUCTION_HOST = "anticlonevoice.onrender.com"
    
    // Your current computer's local IP for phone-to-PC communication
    var localIp: String = "192.168.17"
    private const val LOCAL_PORT = "8081"

    // Remove all trailing slashes and http prefixes for a clean host
    private val cleanHost = PRODUCTION_HOST.replace("https://", "").replace("http://", "").trim().trimEnd('/')

    val BASE_URL: String get() = if (isProduction) "https://$cleanHost" else "http://$localIp:$LOCAL_PORT"
    val WS_URL: String get() = if (isProduction) "wss://$cleanHost" else "ws://$localIp:$LOCAL_PORT"
}
