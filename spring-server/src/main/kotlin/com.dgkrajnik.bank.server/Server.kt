package com.dgkrajnik.bank.server

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter
import springfox.documentation.swagger2.annotations.EnableSwagger2

/**
 * Our Spring Boot application.
 */
@SpringBootApplication
@EnableSwagger2
private class ServerStarter {
    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurerAdapter() {
            override fun addCorsMappings(registry: CorsRegistry) {
                //TODO: CHANGE THIS, WOW.
                registry.addMapping("/dgkbank/*").allowedOrigins("*")
            }
        }
    }
}

/**
 * Starts our Spring Boot application.
 */
fun main(args: Array<String>) {
    val app = SpringApplication(ServerStarter::class.java)
    app.setBannerMode(Banner.Mode.OFF)
    app.isWebEnvironment = true
    app.run(*args)
}
