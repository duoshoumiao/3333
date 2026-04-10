package com.pcrjjc.app.ui.navigation  
  
import androidx.compose.runtime.Composable  
import androidx.navigation.NavType  
import androidx.navigation.compose.NavHost  
import androidx.navigation.compose.composable  
import androidx.navigation.compose.rememberNavController  
import androidx.navigation.navArgument  
import com.pcrjjc.app.ui.account.AccountScreen  
import com.pcrjjc.app.ui.bind.BindScreen  
import com.pcrjjc.app.ui.detail.DetailScreen  
import com.pcrjjc.app.ui.history.HistoryScreen  
import com.pcrjjc.app.ui.home.HomeScreen  
import com.pcrjjc.app.ui.master.MasterScreen  
import com.pcrjjc.app.ui.query.QueryScreen  
import com.pcrjjc.app.ui.settings.SettingsScreen  
  
sealed class Screen(val route: String) {  
    data object Home : Screen("home")  
    data object Bind : Screen("bind")  
    data object Query : Screen("query/{bindId}") {  
        fun createRoute(bindId: Int) = "query/$bindId"  
    }  
    data object Detail : Screen("detail/{bindId}") {  
        fun createRoute(bindId: Int) = "detail/$bindId"  
    }  
    data object History : Screen("history?pcrid={pcrid}&platform={platform}") {  
        fun createRoute(pcrid: Long = 0, platform: Int = -1) =  
            "history?pcrid=$pcrid&platform=$platform"  
    }  
    data object Settings : Screen("settings")  
    data object Account : Screen("account")  
    data object Master : Screen("master")  
}  
  
@Composable  
fun PcrJjcNavHost() {  
    val navController = rememberNavController()  
  
    NavHost(  
        navController = navController,  
        startDestination = Screen.Home.route  
    ) {  
        composable(Screen.Home.route) {  
            HomeScreen(  
                onNavigateToBind = { navController.navigate(Screen.Bind.route) },  
                onNavigateToQuery = { bindId -> navController.navigate(Screen.Query.createRoute(bindId)) },  
                onNavigateToDetail = { bindId -> navController.navigate(Screen.Detail.createRoute(bindId)) },  
                onNavigateToHistory = { pcrid, platform ->  
                    navController.navigate(Screen.History.createRoute(pcrid, platform))  
                },  
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },  
                onNavigateToAccount = { navController.navigate(Screen.Account.route) },  
                onNavigateToMaster = { navController.navigate(Screen.Master.route) }  
            )  
        }  
  
        composable(Screen.Bind.route) {  
            BindScreen(onNavigateBack = { navController.popBackStack() })  
        }  
  
        composable(  
            route = Screen.Query.route,  
            arguments = listOf(navArgument("bindId") { type = NavType.IntType })  
        ) { backStackEntry ->  
            val bindId = backStackEntry.arguments?.getInt("bindId") ?: 0  
            QueryScreen(  
                bindId = bindId,  
                onNavigateBack = { navController.popBackStack() },  
                onNavigateToDetail = { navController.navigate(Screen.Detail.createRoute(bindId)) }  
            )  
        }  
  
        composable(  
            route = Screen.Detail.route,  
            arguments = listOf(navArgument("bindId") { type = NavType.IntType })  
        ) { backStackEntry ->  
            val bindId = backStackEntry.arguments?.getInt("bindId") ?: 0  
            DetailScreen(  
                bindId = bindId,  
                onNavigateBack = { navController.popBackStack() }  
            )  
        }  
  
        composable(  
            route = Screen.History.route,  
            arguments = listOf(  
                navArgument("pcrid") { type = NavType.LongType; defaultValue = 0L },  
                navArgument("platform") { type = NavType.IntType; defaultValue = -1 }  
            )  
        ) { backStackEntry ->  
            val pcrid = backStackEntry.arguments?.getLong("pcrid") ?: 0L  
            val platform = backStackEntry.arguments?.getInt("platform") ?: -1  
            HistoryScreen(  
                pcrid = pcrid,  
                platform = platform,  
                onNavigateBack = { navController.popBackStack() }  
            )  
        }  
  
        composable(Screen.Settings.route) {  
            SettingsScreen(onNavigateBack = { navController.popBackStack() })  
        }  
  
        composable(Screen.Account.route) {  
            AccountScreen(onNavigateBack = { navController.popBackStack() })  
        }  
  
        composable(Screen.Master.route) {  
            MasterScreen(onNavigateBack = { navController.popBackStack() })  
        }  
    }  
}