//package com.example.devicemanagercustomer.jacompose
//
//import androidx.compose.runtime.Composable
//import androidx.navigation.compose.NavHost
//import androidx.navigation.compose.composable
//import androidx.navigation.compose.rememberNavController
//import androidx.navigation.toRoute
//
//@Composable
//fun NavGraph() {
//
//    val navController = rememberNavController()
//
//    NavHost(
//        navController = navController,
//        startDestination = MyNavRoutes.LoginScreen
//    ){
//
//        composable<MyNavRoutes.LoginScreen> {
//            LoginScreenUi(navController)
//        }
//
//        composable<MyNavRoutes.SignUpScreen> {
//            SignUpScreenUi(navController)
//        }
//
//        // backStackEntry is a Lambda Function having
//        // data and arguments which are passed during Navigation
//        composable<MyNavRoutes.HomeScreen> { backStackEntry ->
//
//            val data = backStackEntry.toRoute<MyNavRoutes.HomeScreen>()
//
//            HomeScreenUi( data.userName, navController )
//        }
//
//    }
//
//}
