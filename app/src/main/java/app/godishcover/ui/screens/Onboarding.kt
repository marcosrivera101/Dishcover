package app.godishcover.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.godishcover.R
import app.godishcover.data.Categories
import app.godishcover.data.Profile
import app.godishcover.ui.AppViewModel
import app.godishcover.ui.Chip
import app.godishcover.ui.ChipFlow
import app.godishcover.ui.ChipKind
import app.godishcover.ui.Dc
import app.godishcover.ui.Note
import app.godishcover.ui.PrimaryButton
import app.godishcover.ui.SecondaryButton
import app.godishcover.ui.TopBar
import app.godishcover.ui.catLabel

@Composable
fun WelcomeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val busy by vm.busy.collectAsState()
    var useEmail by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) {
        RulesBody(onBack = { showRules = false })
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Image(painterResource(R.drawable.wordmark), contentDescription = "Dishcover", modifier = Modifier.height(44.dp))
        Image(
            painterResource(R.drawable.plate), contentDescription = null,
            modifier = Modifier.size(180.dp).align(Alignment.CenterHorizontally),
        )
        ChipFlow {
            listOf("Burger", "Tacos", "Sushi", "Pizza", "Breakfast", "Dessert", "Coffee").forEach {
                Chip(it, on = it == "Burger", onClick = {})
            }
        }
        Text("Rate the dishes you love. Know what to eat tonight.", style = MaterialTheme.typography.displaySmall)
        Text(
            "Find the best burger, taco or slice near you, ranked by your ratings and everyone else's.",
            style = MaterialTheme.typography.bodyLarge, color = Dc.Muted,
        )
        PrimaryButton("Continue with Google", onClick = { vm.signInWithGoogle(ctx) }, busy = busy && !useEmail)
        if (!useEmail) {
            SecondaryButton("Use email instead", onClick = { useEmail = true }, modifier = Modifier.fillMaxWidth())
        } else {
            OutlinedTextField(
                email, { email = it }, label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password, { password = it }, label = { Text("Password (6+ characters)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
            )
            val ok = email.contains('@') && password.length >= 6
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Create account", onClick = { if (ok) vm.signInWithEmail(email, password, true) }, modifier = Modifier.weight(1f))
                SecondaryButton("Sign in", onClick = { if (ok) vm.signInWithEmail(email, password, false) }, modifier = Modifier.weight(1f))
            }
        }
        Note("Already have an account? The same button signs you in.")
        Text(
            "By continuing you agree to the Terms and Community rules.",
            style = MaterialTheme.typography.bodySmall, color = Dc.Wine,
            modifier = Modifier.clickable { showRules = true },
        )
    }
}

@Composable
fun AccountScreen(vm: AppViewModel, profile: Profile) {
    val user by vm.user.collectAsState()
    val busy by vm.busy.collectAsState()
    var name by remember { mutableStateOf(profile.publicName.ifBlank { user?.displayName.orEmpty() }) }
    var agreed by remember { mutableStateOf(false) }
    var useLocation by remember { mutableStateOf(vm.hasLocationPermission()) }
    var showRules by remember { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        useLocation = res.values.any { it }
    }
    if (showRules) {
        RulesBody(onBack = { showRules = false })
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Create your account", style = MaterialTheme.typography.headlineMedium)
        user?.let {
            Note("Signed in as ${it.email.ifBlank { it.displayName }}")
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                name, { name = it.take(30) }, label = { Text("Public name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Note("Shown next to your reviews. Your email never is.")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use my location while the app is open", style = MaterialTheme.typography.titleMedium)
                Note("Finds places near you. Only used while you're looking, never in the background.")
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = useLocation,
                onCheckedChange = {
                    if (it) askLocation.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                    else useLocation = false
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Dc.Wine),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { agreed = !agreed }) {
            Checkbox(agreed, { agreed = it }, colors = CheckboxDefaults.colors(checkedColor = Dc.Wine))
            Column(Modifier.weight(1f)) {
                Text("I agree to the Terms and Community rules", style = MaterialTheme.typography.titleSmall)
                Note("Be honest, be kind. You can report or block anyone.")
            }
        }
        TextButton(onClick = { showRules = true }) { Text("Read the rules", color = Dc.Wine) }
        PrimaryButton(
            if (agreed) "Continue" else "Agree to the rules to continue",
            onClick = { vm.saveProfile(profile.copy(publicName = name.trim(), agreedToRules = true)) },
            enabled = agreed && name.isNotBlank(),
            busy = busy,
        )
        Note("Next: a 1-minute taste interview")
    }
}

private data class Step(val key: String, val kind: ChipKind, val title: String, val sub: String, val opts: List<String>, val labels: (String) -> String)

@Composable
fun InterviewScreen(vm: AppViewModel, profile: Profile, onDone: () -> Unit) {
    val busy by vm.busy.collectAsState()
    val steps = remember {
        listOf(
            Step("likes", ChipKind.Normal, "What do you love to eat?",
                "Pick every kind of food you get excited about. These shape what we suggest first.", Categories.INTERVIEW, ::catLabel),
            Step("dislikes", ChipKind.Bad, "Anything you would rather skip?",
                "These move to the bottom of your results. You can still find them if you search.", Categories.INTERVIEW, ::catLabel),
            Step("allergies", ChipKind.Bad, "Any food allergies?",
                "We use these to push dishes lower when a menu lists the ingredient.", Categories.ALLERGIES, { it }),
            Step("want", ChipKind.Try, "What have you never tried but want to?",
                "We will nudge these up when a place near you has them.", Categories.WANT_TO_TRY, { it }),
        )
    }
    var step by remember { mutableIntStateOf(0) }
    var picks by remember {
        mutableStateOf(
            mapOf(
                "likes" to profile.likes.toSet(), "dislikes" to profile.dislikes.toSet(),
                "allergies" to profile.allergies.toSet(), "want" to profile.wantToTry.toSet(),
            ),
        )
    }
    var extra by remember { mutableStateOf("") }
    fun finish() {
        vm.saveProfile(
            profile.copy(
                interviewDone = true,
                likes = picks["likes"].orEmpty().toList(),
                dislikes = picks["dislikes"].orEmpty().toList(),
                allergies = picks["allergies"].orEmpty().toList(),
                wantToTry = picks["want"].orEmpty().toList(),
            ),
            after = onDone,
        )
    }
    val s = steps[step]
    val picked = picks[s.key].orEmpty()
    // Options include anything the user typed in "Something else?" on the want-to-try step.
    val opts = (s.opts + picked.filter { it !in s.opts }).distinct()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Step ${step + 1} of 4", style = MaterialTheme.typography.labelLarge, color = Dc.Muted, modifier = Modifier.weight(1f))
            TextButton(onClick = { finish() }) { Text("Skip for now", color = Dc.Wine) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.indices.forEach { i ->
                Box(
                    Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (i <= step) Dc.Wine else Dc.ChipLine),
                )
            }
        }
        Text(s.title, style = MaterialTheme.typography.headlineMedium)
        Text(s.sub, style = MaterialTheme.typography.bodyLarge, color = Dc.Muted)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ChipFlow {
                opts.forEach { o ->
                    val on = o in picked
                    Chip(s.labels(o), on, kind = s.kind, onClick = {
                        picks = picks + (s.key to if (on) picked - o else picked + o)
                    })
                }
            }
            if (s.key == "allergies") {
                Note("Dishes whose menu lists these move lower in your results, they are not hidden. Menus are often incomplete, so always check allergies with the restaurant.")
            }
            if (s.key == "want") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(extra, { extra = it.take(40) }, label = { Text("Something else?") }, singleLine = true, modifier = Modifier.weight(1f))
                    SecondaryButton("Add", onClick = {
                        val e = extra.trim()
                        if (e.isNotEmpty()) { picks = picks + ("want" to picked + e); extra = "" }
                    })
                }
            }
            Note("${picked.size} picked · only you can see these answers")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) SecondaryButton("Back", onClick = { step-- })
            PrimaryButton(
                if (step == steps.lastIndex) "Finish" else "Next",
                onClick = { if (step == steps.lastIndex) finish() else step++ },
                modifier = Modifier.weight(1f),
                busy = busy,
            )
        }
    }
}

@Composable
fun RulesScreen(nav: NavHostController) = RulesBody(onBack = { nav.popBackStack() })

@Composable
fun RulesBody(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Terms and Community rules", onBack = onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Community rules", style = MaterialTheme.typography.titleLarge)
            listOf(
                "Rate and review food you actually ate.",
                "Be honest, be kind. No hate, harassment, threats or personal information about anyone.",
                "No spam, ads or reviews you were paid or rewarded for.",
                "Only add dishes a place really serves. Photos of menus only, never receipts.",
                "You can report any review and block anyone. Reported content is reviewed and may be removed, and accounts that break these rules can be suspended.",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
            Text("Terms", style = MaterialTheme.typography.titleLarge)
            listOf(
                "Dishcover is free. Ratings, reviews and menus come from the people who use it and may be wrong or out of date.",
                "Allergy information only appears when a menu prints it and is often incomplete. Always check allergies with the restaurant.",
                "Your public name, ratings, reviews and the dishes you add are visible to other users. Your email and your taste interview answers are private.",
                "Your location is only used while the app is open, to find places near you.",
                "You can delete your account at any time from the Me tab. Your profile, ratings and reviews are deleted with it.",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
