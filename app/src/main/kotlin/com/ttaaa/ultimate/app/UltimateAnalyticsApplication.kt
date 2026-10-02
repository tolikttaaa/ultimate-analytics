package com.ttaaa.ultimate.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class UltimateAnalyticsApplication

fun main(args: Array<String>) {
    runApplication<UltimateAnalyticsApplication>(*args)
}
