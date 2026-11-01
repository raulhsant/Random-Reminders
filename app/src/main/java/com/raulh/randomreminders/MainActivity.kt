package com.raulh.randomreminders

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.raulh.randomreminders.ui.history.HistoryScreen
import com.raulh.randomreminders.ui.ruledit.EditRuleScreen
import com.raulh.randomreminders.ui.rulelist.RuleListScreen
import com.raulh.randomreminders.ui.theme.RandomRemindersTheme
import dagger.hilt.android.AndroidEntryPoint

/** Atividade única do app: navegação entre lista, editor e histórico. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RandomRemindersTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = ROUTE_LIST) {
                    composable(ROUTE_LIST) {
                        RuleListScreen(
                            onAddRule = { navController.navigate(ROUTE_EDIT_NEW) },
                            onEditRule = { ruleId ->
                                navController.navigate("$ROUTE_EDIT_BASE$ruleId")
                            },
                            onOpenHistory = { navController.navigate(ROUTE_HISTORY) },
                        )
                    }
                    composable(
                        route = "$ROUTE_EDIT_BASE{ruleId}",
                        arguments = listOf(navArgument("ruleId") { type = NavType.LongType }),
                    ) {
                        EditRuleScreen(onBack = { navController.popBackStack() })
                    }
                    composable(ROUTE_HISTORY) {
                        HistoryScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    private companion object {
        const val ROUTE_LIST = "list"
        const val ROUTE_HISTORY = "history"
        const val ROUTE_EDIT_BASE = "edit/"
        // ruleId = 0 significa "nova regra" (ver EditRuleViewModel)
        const val ROUTE_EDIT_NEW = "${ROUTE_EDIT_BASE}0"
    }
}
