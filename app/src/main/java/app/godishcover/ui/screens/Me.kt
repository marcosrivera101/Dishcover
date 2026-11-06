package app.godishcover.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.godishcover.BuildConfig
import app.godishcover.ui.AppViewModel
import app.godishcover.ui.Dc
import app.godishcover.ui.Note
import app.godishcover.ui.Routes
import app.godishcover.ui.SecondaryButton
import app.godishcover.ui.SectionTitle
import app.godishcover.ui.Tag
import app.godishcover.ui.TagKind
import app.godishcover.ui.ChipFlow
import app.godishcover.ui.catLabel

@Composable
fun MeScreen(vm: AppViewModel, nav: NavHostController) {
    val user by vm.user.collectAsState()
    val profile by vm.profile.collectAsState()
    val ratings by vm.myRatings.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    val p = profile

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(p?.publicName?.ifBlank { null } ?: "Me", style = MaterialTheme.typography.headlineMedium)
        user?.let { Note(it.email) }
        Note("${ratings.size} ${if (ratings.size == 1) "dish" else "dishes"} rated")

        if (p != null) {
            SectionTitle("Your taste")
            Note("Only you can see these.")
            ChipFlow {
                p.likes.forEach { Tag(catLabel(it), TagKind.You) }
                p.dislikes.forEach { Tag("Skip " + catLabel(it).lowercase()) }
                p.allergies.forEach { Tag("Allergy: $it", TagKind.Low) }
                p.wantToTry.forEach { Tag("Try $it", TagKind.Try) }
            }
            SecondaryButton("Redo taste interview", onClick = { nav.navigate(Routes.INTERVIEW) }, modifier = Modifier.fillMaxWidth())
        }

        SectionTitle("Account")
        SecondaryButton("Terms and Community rules", onClick = { nav.navigate(Routes.RULES) }, modifier = Modifier.fillMaxWidth())
        SecondaryButton("Sign out", onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { confirmDelete = true }) { Text("Delete my account", color = Dc.Wine) }

        Spacer(Modifier.height(12.dp))
        Note(
            "Dishcover ${BuildConfig.VERSION_NAME}" +
                (if (vm.repo.isDemo) " · demo mode" else "") +
                (if (!vm.placesSource.available) " · Google places off" else ""),
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = { Text("Your profile, taste answers, ratings and reviews are deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteAccount() }) { Text("Delete", color = Dc.Wine) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Dc.Muted) } },
        )
    }
}
