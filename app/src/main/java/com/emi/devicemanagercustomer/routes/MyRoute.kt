package com.emi.devicemanagercustomer.routes

import kotlinx.serialization.Serializable

@Serializable
sealed class MyNavRoutes {

    @Serializable
    object LoginScreen : MyNavRoutes()

    @Serializable
    object SignUpScreen : MyNavRoutes()

    @Serializable
    data class HomeScreen(val userName: String) : MyNavRoutes()

}