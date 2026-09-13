package com.jackmarcus.anti_clonevoice.data

object Config {
    // 1. SET THIS TO TRUE
    private const val IS_PRODUCTION = true
    
    private const val PRODUCTION_HOST = "anticlonevoice.onrender.com"
    
    // Your current computer's local IP for phone-to-PC communication
    private const val LOCAL_IP = "192.168.1.7"
    private const val LOCAL_PORT = "8081"

    // This logic cleans up the host just in case it has http/https in it
    private val cleanHost = PRODUCTION_HOST.replace("https://", "").replace("http://", "").trimEnd('/')

    val BASE_URL = if (IS_PRODUCTION) "https://$cleanHost/" else "http://$LOCAL_IP:$LOCAL_PORT/"
    val WS_URL = if (IS_PRODUCTION) "wss://$cleanHost" else "ws://$LOCAL_IP:$LOCAL_PORT"
}
