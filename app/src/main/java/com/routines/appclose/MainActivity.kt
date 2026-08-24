package com.routines.appclose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.routines.appclose.ui.HistoryScreen
import com.routines.appclose.ui.PermissionsScreen
import com.routines.appclose.ui.RuleEditorScreen
import com.routines.appclose.ui.RulesListScreen
import com.routines.appclose.ui.RulesViewModel
import com.routines.appclose.ui.theme.CloseRoutinesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CloseRoutinesTheme {
                AppNav()
            }
        }
    }
}

@Composable
private fun AppNav() {
    val navController = rememberNavController()
    val viewModel: RulesViewModel = viewModel()

    NavHost(navController = navController, startDestination = "rules") {
        composable("rules") {
            RulesListScreen(
                viewModel = viewModel,
                onAddRule = { navController.navigate("edit/0") },
                onEditRule = { id -> navController.navigate("edit/$id") },
                onOpenPermissions = { navController.navigate("permissions") },
                onOpenHistory = { navController.navigate("history") },
            )
        }
        composable(
            route = "edit/{ruleId}",
            arguments = listOf(navArgument("ruleId") { type = NavType.LongType }),
        ) { backStackEntry ->
            RuleEditorScreen(
                viewModel = viewModel,
                ruleId = backStackEntry.arguments?.getLong("ruleId") ?: 0L,
                onDone = { navController.popBackStack() },
            )
        }
        composable("permissions") {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }
        composable("history") {
            HistoryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
