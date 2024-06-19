package com.example.springmvc

@Target(AnnotationTarget.CLASS)
@Retention
annotation class Controller

@Target(AnnotationTarget.CLASS)
@Retention
annotation class Service

@Target(AnnotationTarget.FIELD)
@Retention
annotation class AutoWired(val name: String = "")

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention
annotation class RequestMapping(val path: String = "")

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention
annotation class RequestParam(val name: String)

