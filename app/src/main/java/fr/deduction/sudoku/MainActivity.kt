package fr.deduction.sudoku

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import fr.deduction.sudoku.ui.C
import fr.deduction.sudoku.ui.CareerScreen
import fr.deduction.sudoku.ui.DeductionTheme
import fr.deduction.sudoku.ui.GameScreen
import fr.deduction.sudoku.ui.HomeScreen
import fr.deduction.sudoku.ui.LessonScreen
import fr.deduction.sudoku.ui.TrainingSetupScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val light = Color.rgb(0xF4, 0xF6, 0xFA)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(light, light),
            navigationBarStyle = SystemBarStyle.light(light, light),
        )
        setContent {
            DeductionTheme {
                val g = vm.game
                BackHandler(enabled = vm.screen != Screen.Home) {
                    if (vm.screen == Screen.Play && g != null && g.hintOpen) g.hintOpen = false
                    else vm.back()
                }
                Box(Modifier.fillMaxSize().background(C.bg).safeDrawingPadding()) {
                    when (val s = vm.screen) {
                        Screen.Home -> HomeScreen(vm)
                        Screen.Career -> CareerScreen(vm)
                        is Screen.Lesson -> LessonScreen(vm, s.tech)
                        Screen.TrainingSetup -> TrainingSetupScreen(vm)
                        Screen.Play -> if (g != null) GameScreen(vm, g) else HomeScreen(vm)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        vm.saveNow()
    }
}
