package com.dgkrajnik.bank.server

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * Our Spring Boot application.
 */
@SpringBootApplication
private open class ServerStarter

/**
 * Starts our Spring Boot application.
 */
fun main(args: Array<String>) {
    val app = SpringApplication(ServerStarter::class.java)
    app.setBannerMode(Banner.Mode.OFF)
    app.isWebEnvironment = true
    app.run(*args)
}