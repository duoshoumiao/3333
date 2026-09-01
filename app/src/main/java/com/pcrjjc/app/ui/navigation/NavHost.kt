package com.pcrjjc.app.ui.navigation      
  
import androidx.compose.runtime.Composable      
import androidx.navigation.NavType      
import androidx.navigation.compose.NavHost      
import androidx.navigation.compose.composable      
import androidx.navigation.compose.rememberNavController      
import androidx.navigation.navArgument      
import com.pcrjjc.app.ui.account.AccountScreen  
import com.pcrjjc.app.ui.bind.BindScreen  
import com.pcrjjc.app.ui.daily.DailyScreen  
import com.pcrjjc.app.ui.detail.DetailScreen  
import com.pcrjjc.app.ui.fortnightly.FortnightlyScreen  
import com.pcrjjc.app.ui.history.HistoryScreen  
import com.pcrjjc.app.ui.home.HomeScreen  
import com.pcrjjc.app.ui.query.QueryScreen  
import com.pcrjjc.app.ui.room.ChatScreen  
import com.pcrjjc.app.ui.room.RoomScreen  
import com.pcrjjc.app.ui.clanranking.ClanRankingScreen
import com.pcrjjc.app.ui.settings.TopBarBgCropScreen
import com.pcrjjc.app.ui.settings.SettingsScreen  
import com.pcrjjc.app.ui.eqa.EqaScreen
import com.pcrjjc.app.ui.labyrinth.LabyrinthScreen
import com.pcrjjc.app.ui.exequip.ExEquipScreen
  
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
    data object Fortnightly : Screen("fortnightly")  
    data object Daily : Screen("daily")  
    data object Room : Screen("room")  
    data object ClanRanking : Screen("clan_ranking")
	data object Eqa : Screen("eqa")
	data object Labyrinth : Screen("labyrinth")
	data object ExEquip : Screen("ex_equip")
	data object TopBarBgCrop : Screen("topbar_crop/{uri}") {  
        fun createRoute(uri: String) =  
            "topbar_crop/${java.net.URLEncoder.encode(uri, "UTF-8")}"  
    }
	data object Chat : Screen("chat/{roomId}/{playerQq}/{playerName}/{roomName}/{hostQq}") {
        fun createRoute(roomId: String, playerQq: String, playerName: String, roomName: String, hostQq: String) =
            "chat/$roomId/$playerQq/${java.net.URLEncoder.encode(playerName, "UTF-8")}/${java.net.URLEncoder.encode(roomName, "UTF-8")}/$hostQq"
    }  
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
                onNavigateToFortnightly = { navController.navigate(Screen.Fortnightly.route) },  
                onNavigateToDaily = { navController.navigate(Screen.Daily.route) },  
                onNavigateToRoom = { navController.navigate(Screen.Room.route) },  
                onNavigateToClanRanking = { navController.navigate(Screen.ClanRanking.route) },  
                onNavigateToEqa = { navController.navigate(Screen.Eqa.route) },  
                onNavigateToLabyrinth = { navController.navigate(Screen.Labyrinth.route) },  
                onNavigateToExEquip = { navController.navigate(Screen.ExEquip.route) }
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
            SettingsScreen(  
                onNavigateBack = { navController.popBackStack() },  
                onNavigateToCrop = { uri ->  
                    navController.navigate(Screen.TopBarBgCrop.createRoute(uri))  
                }  
            )  
        }    
  
        composable(Screen.Account.route) {      
            AccountScreen(onNavigateBack = { navController.popBackStack() })      
        }      
  
        composable(Screen.Fortnightly.route) {      
            FortnightlyScreen(onNavigateBack = { navController.popBackStack() })      
        }      
  
        composable(Screen.Daily.route) {      
            DailyScreen(onNavigateBack = { navController.popBackStack() })      
        }  
  
        // 房间路由  
        composable(Screen.Room.route) {  
            RoomScreen(  
                onNavigateBack = { navController.popBackStack() },  
                onNavigateToChat = { roomId, playerQq, playerName, roomName, hostQq ->
                    navController.navigate(
                        Screen.Chat.createRoute(roomId, playerQq, playerName, roomName, hostQq)
                    )
                }  
            )  
        }  
  
        // 聊天路由  
        composable(  
            route = Screen.Chat.route,  
            arguments = listOf(  
                navArgument("roomId") { type = NavType.StringType },  
                navArgument("playerQq") { type = NavType.StringType },  
                navArgument("playerName") { type = NavType.StringType },
                navArgument("roomName") { type = NavType.StringType },
                navArgument("hostQq") { type = NavType.StringType }
            )  
        ) {  
            ChatScreen(  
                onNavigateBack = { navController.popBackStack() }  
            )  
        }
		// 公会排名路由  
        composable(Screen.ClanRanking.route) {  
            ClanRankingScreen(onNavigateBack = { navController.popBackStack() })  
        }  
        // 问答路由  
        composable(Screen.Eqa.route) {  
            EqaScreen(onNavigateBack = { navController.popBackStack() })  
        }
		// 黎明界刷开局路由  
        composable(Screen.Labyrinth.route) {  
            LabyrinthScreen(onNavigateBack = { navController.popBackStack() })  
        }
		// EX状态管理路由  
        composable(Screen.ExEquip.route) {  
            ExEquipScreen(onNavigateBack = { navController.popBackStack() })  
        }
		// 顶栏背景裁剪路由  
        composable(  
            route = Screen.TopBarBgCrop.route,  
            arguments = listOf(navArgument("uri") { type = NavType.StringType })  
        ) { backStackEntry ->  
            val encoded = backStackEntry.arguments?.getString("uri") ?: ""  
            val uri = android.net.Uri.parse(java.net.URLDecoder.decode(encoded, "UTF-8"))  
            TopBarBgCropScreen(  
                imageUri = uri,  
                onDone = { navController.popBackStack() },  
                onCancel = { navController.popBackStack() }  
            )  
        }
	}      
}