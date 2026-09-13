package com.jackmarcus.anti_clonevoice.data

object Config {
    // Switch to true when you deploy your server to Render/Railway
    private const val IS_PRODUCTION = false
    
    // Replace with your actual hosted URL after deployment
    private const val PRODUCTION_HOST = "anti-clone-voice.onrender.com"
    
    // Your current computer's local IP for phone-to-PC communication
    private const val LOCAL_IP = "192.168.1.7"
    private const val LOCAL_PORT = "8081"

    val BASE_URL = if (IS_PRODUCTION) "https://$PRODUCTION_HOST/" else "http://$LOCAL_IP:$LOCAL_PORT/"
    val WS_URL = if (IS_PRODUCTION) "wss://$PRODUCTION_HOST" else "ws://$LOCAL_IP:$LOCAL_PORT"
}
