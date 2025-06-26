package com.betfanatics.exchange.order

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication()
@ComponentScan("com.betfanatics")
@EnableScheduling
open class ExchangeOrderApiApplication

fun main(args: Array<String>) {
    runApplication<ExchangeOrderApiApplication>(*args)
} 