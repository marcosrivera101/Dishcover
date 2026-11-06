package app.godishcover

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.godishcover.ui.DishcoverRoot
import app.godishcover.ui.DishcoverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DishcoverTheme { DishcoverRoot() } }
    }
}
