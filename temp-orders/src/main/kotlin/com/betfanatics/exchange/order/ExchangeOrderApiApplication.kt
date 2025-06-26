package com.betfanatics.exchange.order

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication()
@ComponentScan("com.betfanatics")
open class ExchangeOrderApiApplication

fun main(args: Array<String>) {
    runApplication<ExchangeOrderApiApplication>(*args)
} 