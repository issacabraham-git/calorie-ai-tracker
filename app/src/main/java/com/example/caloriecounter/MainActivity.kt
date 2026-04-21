package com.example.caloriecounter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.security.MessageDigest

// --- DATA CLASSES ---
data class UserProfile(
    val weightKg: Float,
    val heightCm: Float,
    val age: Int,
    val isMale: Boolean,
    val activityLevel: Float,
    val goal: String = "Maintain",
    val dailyCalorieTarget: Int
)

data class FoodItem(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val calories: String,
    val protein: String,
    val carbs: String,
    val fat: String,
    val mealType: String,
    val dateString: String
)

// --- THEME COLORS ---
object AppColors {
    // Dark theme
    val darkBackground = Color(0xFF0F0F0F)
    val darkSurface = Color(0xFF1A1A2E)
    val darkCard = Color(0xFF16213E)
    val darkAccentPrimary = Color(0xFF00D4AA)
    val darkAccentSecondary = Color(0xFF7C5CBF)
    val darkTextPrimary = Color(0xFFF0F0F0)
    val darkTextSecondary = Color(0xFF9E9E9E)

    // Light theme
    val lightBackground = Color(0xFFF5F7FA)
    val lightSurface = Color(0xFFFFFFFF)
    val lightCard = Color(0xFFFFFFFF)
    val lightAccentPrimary = Color(0xFF00A389)
    val lightAccentSecondary = Color(0xFF6B46C1)
    val lightTextPrimary = Color(0xFF1A1A2E)
    val lightTextSecondary = Color(0xFF6B7280)
}

data class ThemeColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val isDark: Boolean
)

fun getDarkTheme() = ThemeColors(
    background = AppColors.darkBackground,
    surface = AppColors.darkSurface,
    card = AppColors.darkCard,
    accentPrimary = AppColors.darkAccentPrimary,
    accentSecondary = AppColors.darkAccentSecondary,
    textPrimary = AppColors.darkTextPrimary,
    textSecondary = AppColors.darkTextSecondary,
    isDark = true
)

fun getLightTheme() = ThemeColors(
    background = AppColors.lightBackground,
    surface = AppColors.lightSurface,
    card = AppColors.lightCard,
    accentPrimary = AppColors.lightAccentPrimary,
    accentSecondary = AppColors.lightAccentSecondary,
    textPrimary = AppColors.lightTextPrimary,
    textSecondary = AppColors.lightTextSecondary,
    isDark = false
)

// --- STORAGE HELPERS ---
fun saveUserProfile(context: Context, profile: UserProfile?) {
    val editor = context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE).edit()
    if (profile == null) {
        editor.remove("user_profile_v2").apply()
    } else {
        editor.putString("user_profile_v2", Gson().toJson(profile)).apply()
    }
}

fun loadUserProfile(context: Context): UserProfile? {
    val json = context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE).getString("user_profile_v2", null)
    return if (json != null) Gson().fromJson(json, UserProfile::class.java) else null
}

fun saveLog(context: Context, list: List<FoodItem>) {
    context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE)
        .edit().putString("daily_log_v3", Gson().toJson(list)).apply()
}

fun loadLog(context: Context): List<FoodItem> {
    val json = context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE).getString("daily_log_v3", null)
    return if (json != null) Gson().fromJson(json, object : TypeToken<List<FoodItem>>() {}.type) else emptyList()
}

fun saveThemePreference(context: Context, isDark: Boolean) {
    context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE)
        .edit().putBoolean("is_dark_theme", isDark).apply()
}

fun loadThemePreference(context: Context): Boolean {
    return context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE)
        .getBoolean("is_dark_theme", true)
}

// --- COGNITO CONFIG ---
const val COGNITO_REGION = "eu-north-1"
const val COGNITO_CLIENT_ID = "5a0ejg7g8dhg06umpk93616lpa"
const val COGNITO_POOL_ID = "eu-north-1_Cw2gKvIkQ"

fun saveCognitoSession(context: Context, userId: String, email: String, accessToken: String) {
    context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE).edit()
        .putString("cognito_user_id", userId)
        .putString("cognito_email", email)
        .putString("cognito_access_token", accessToken)
        .apply()
}

fun loadCognitoSession(context: Context): Triple<String, String, String>? {
    val prefs = context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE)
    val userId = prefs.getString("cognito_user_id", null)
    val email = prefs.getString("cognito_email", null)
    val token = prefs.getString("cognito_access_token", null)
    return if (userId != null && email != null && token != null) Triple(userId, email, token) else null
}

fun clearCognitoSession(context: Context) {
    context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE).edit()
        .remove("cognito_user_id")
        .remove("cognito_email")
        .remove("cognito_access_token")
        .apply()
}

// --- COGNITO API CALLS (pure HTTP, no SDK needed) ---
suspend fun cognitoSignUp(email: String, password: String): Result<String> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://cognito-idp.$COGNITO_REGION.amazonaws.com/")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-amz-json-1.1")
            conn.setRequestProperty("X-Amz-Target", "AWSCognitoIdentityProviderService.SignUp")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val body = JSONObject().apply {
                put("ClientId", COGNITO_CLIENT_ID)
                put("Username", email)
                put("Password", password)
                put("UserAttributes", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("Name", "email")
                        put("Value", email)
                    })
                })
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            if (conn.responseCode == 200) {
                Result.success("Verification email sent to $email. Please verify before logging in.")
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "Sign up failed"
                val msg = JSONObject(error).optString("message", "Sign up failed")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

suspend fun cognitoConfirmSignUp(email: String, code: String): Result<String> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://cognito-idp.$COGNITO_REGION.amazonaws.com/")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-amz-json-1.1")
            conn.setRequestProperty("X-Amz-Target", "AWSCognitoIdentityProviderService.ConfirmSignUp")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val body = JSONObject().apply {
                put("ClientId", COGNITO_CLIENT_ID)
                put("Username", email)
                put("ConfirmationCode", code)
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            if (conn.responseCode == 200) {
                Result.success("Account verified! You can now log in.")
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "Verification failed"
                val msg = JSONObject(error).optString("message", "Verification failed")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class CognitoSignInResult(val userId: String, val email: String, val accessToken: String)

suspend fun cognitoSignIn(email: String, password: String): Result<CognitoSignInResult> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://cognito-idp.$COGNITO_REGION.amazonaws.com/")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-amz-json-1.1")
            conn.setRequestProperty("X-Amz-Target", "AWSCognitoIdentityProviderService.InitiateAuth")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val body = JSONObject().apply {
                put("AuthFlow", "USER_PASSWORD_AUTH")
                put("ClientId", COGNITO_CLIENT_ID)
                put("AuthParameters", JSONObject().apply {
                    put("USERNAME", email)
                    put("PASSWORD", password)
                })
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            if (conn.responseCode == 200) {
                val response = JSONObject(conn.inputStream.bufferedReader().readText())
                val authResult = response.getJSONObject("AuthenticationResult")
                val accessToken = authResult.getString("AccessToken")
                val idToken = authResult.getString("IdToken")

                // Decode userId from IdToken (it's a JWT — middle part is base64 payload)
                val payload = idToken.split(".")[1]
                val decoded = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING))
                val claims = JSONObject(decoded)
                val userId = claims.getString("sub") // sub = unique Cognito user ID

                Result.success(CognitoSignInResult(userId, email, accessToken))
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "Sign in failed"
                val msg = JSONObject(error).optString("message", "Sign in failed")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// --- DEMO DATA GENERATOR ---
fun generateDemoData(context: Context) {
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val fakeLogs = mutableListOf<FoodItem>()

    for (i in 0..14) {
        val dateStr = dateFormat.format(calendar.time)
        val isWeekend = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

        fakeLogs.add(FoodItem(System.nanoTime(), "Oatmeal & 2 Eggs", "450", "25", "50", "15", "Breakfast", dateStr))
        fakeLogs.add(FoodItem(System.nanoTime() + 1, "Chicken Breast & Brown Rice", "600", "50", "65", "10", "Lunch", dateStr))

        if (isWeekend) {
            fakeLogs.add(FoodItem(System.nanoTime() + 2, "Half Large Pepperoni Pizza", "1250", "40", "140", "50", "Dinner", dateStr))
            fakeLogs.add(FoodItem(System.nanoTime() + 3, "Chocolate Milkshake", "500", "10", "70", "20", "Snack", dateStr))
        } else {
            fakeLogs.add(FoodItem(System.nanoTime() + 2, "Grilled Salmon & Asparagus", "400", "35", "10", "20", "Dinner", dateStr))
        }

        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }

    saveLog(context, fakeLogs)
    Toast.makeText(context, "15 Days of Demo Data Generated!", Toast.LENGTH_LONG).show()
}

// --- API HELPERS ---
const val API_URL = "https://ac3zofmupl.execute-api.eu-north-1.amazonaws.com/calories"

suspend fun apiPost(body: JSONObject): JSONObject {
    return withContext(Dispatchers.IO) {
        val url = URL(API_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        val text = if (conn.responseCode == 200) {
            conn.inputStream.bufferedReader().readText()
        } else {
            conn.errorStream?.bufferedReader()?.readText() ?: "{}"
        }
        JSONObject(text)
    }
}

suspend fun saveProfileToCloud(userId: String, profile: UserProfile) {
    try {
        val profileJson = JSONObject().apply {
            put("weightKg", profile.weightKg)
            put("heightCm", profile.heightCm)
            put("age", profile.age)
            put("isMale", profile.isMale)
            put("activityLevel", profile.activityLevel)
            put("goal", profile.goal)
            put("dailyCalorieTarget", profile.dailyCalorieTarget)
        }
        apiPost(JSONObject().apply {
            put("action", "save_profile")
            put("userId", userId)
            put("profile", profileJson)
        })
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

suspend fun fetchProfileFromCloud(userId: String): UserProfile? {
    return try {
        val result = apiPost(JSONObject().apply {
            put("action", "get_profile")
            put("userId", userId)
        })
        val item = result.optJSONObject("profile") ?: return null
        UserProfile(
            weightKg = item.optString("weightKg", "0").toFloatOrNull() ?: 0f,
            heightCm = item.optString("heightCm", "0").toFloatOrNull() ?: 0f,
            age = item.optString("age", "0").toIntOrNull() ?: 0,
            isMale = item.optString("isMale", "true").toBoolean(),
            activityLevel = item.optString("activityLevel", "1.2").toFloatOrNull() ?: 1.2f,
            goal = item.optString("goal", "Maintain"),
            dailyCalorieTarget = item.optString("dailyCalorieTarget", "2000").toIntOrNull() ?: 2000
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun fetchLogsFromCloud(userId: String): List<FoodItem> {
    return try {
        val result = apiPost(JSONObject().apply {
            put("action", "get_logs")
            put("userId", userId)
        })
        val logsArray = result.optJSONArray("logs") ?: return emptyList()
        (0 until logsArray.length()).mapNotNull { i ->
            val obj = logsArray.getJSONObject(i)
            FoodItem(
                id = obj.optString("id", "0").replace("_", "").take(13).toLongOrNull()
                    ?: System.nanoTime(),
                name = obj.optString("name", "Unknown"),
                calories = obj.optString("calories", "0"),
                protein = obj.optString("protein", "0"),
                carbs = obj.optString("carbs", "0"),
                fat = obj.optString("fat", "0"),
                mealType = obj.optString("mealType", "Snack"),
                dateString = obj.optString("dateString", "")
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

// --- MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppOrchestrator()
        }
    }
}

// --- ORCHESTRATOR ---
@Composable
fun MainAppOrchestrator() {
    val context = LocalContext.current
    var userProfile by remember { mutableStateOf(loadUserProfile(context)) }
    var isDarkTheme by remember { mutableStateOf(loadThemePreference(context)) }
    var cognitoSession by remember { mutableStateOf(loadCognitoSession(context)) }
    var isRestoring by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val theme = if (isDarkTheme) getDarkTheme() else getLightTheme()

    val colorScheme = if (isDarkTheme) {
        darkColorScheme(
            background = theme.background,
            surface = theme.surface,
            primary = theme.accentPrimary,
            onBackground = theme.textPrimary,
            onSurface = theme.textPrimary
        )
    } else {
        lightColorScheme(
            background = theme.background,
            surface = theme.surface,
            primary = theme.accentPrimary,
            onBackground = theme.textPrimary,
            onSurface = theme.textPrimary
        )
    }

    // Restore data from cloud when session exists but local data is empty
    LaunchedEffect(cognitoSession) {
        val session = cognitoSession ?: return@LaunchedEffect
        if (userProfile == null) {
            isRestoring = true
            // Fetch profile from cloud
            val cloudProfile = fetchProfileFromCloud(session.first)
            if (cloudProfile != null) {
                saveUserProfile(context, cloudProfile)
                userProfile = cloudProfile
            }
            // Fetch food logs from cloud
            val cloudLogs = fetchLogsFromCloud(session.first)
            if (cloudLogs.isNotEmpty()) {
                saveLog(context, cloudLogs)
            }
            isRestoring = false
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = theme.background) {
            when {
                // Not logged in → show auth screen
                cognitoSession == null -> {
                    AuthScreen(theme = theme) { userId, email, token ->
                        saveCognitoSession(context, userId, email, token)
                        cognitoSession = Triple(userId, email, token)
                    }
                }
                // Restoring from cloud → show loading
                isRestoring -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = theme.accentPrimary)
                            Spacer(Modifier.height(16.dp))
                            Text("Restoring your data...", color = theme.textSecondary)
                        }
                    }
                }
                // Logged in but no profile → onboarding
                userProfile == null -> {
                    OnboardingScreen(theme = theme) { newProfile ->
                        saveUserProfile(context, newProfile)
                        userProfile = newProfile
                        // Save profile to cloud after onboarding
                        scope.launch {
                            saveProfileToCloud(cognitoSession!!.first, newProfile)
                        }
                    }
                }
                // Logged in + profile exists → main app
                else -> {
                    CalorieTrackerScreen(
                        userProfile = userProfile!!,
                        theme = theme,
                        isDarkTheme = isDarkTheme,
                        loggedInEmail = cognitoSession!!.second,
                        loggedInUserId = cognitoSession!!.first,
                        onToggleTheme = {
                            isDarkTheme = !isDarkTheme
                            saveThemePreference(context, isDarkTheme)
                        },
                        onEditProfile = {
                            saveUserProfile(context, null)
                            userProfile = null
                        },
                        onSignOut = {
                            clearCognitoSession(context)
                            saveUserProfile(context, null)
                            saveLog(context, emptyList())
                            cognitoSession = null
                            userProfile = null
                        }
                    )
                }
            }
        }
    }
}

// --- AUTH SCREEN (Login / Signup) ---
@Composable
fun AuthScreen(theme: ThemeColors, onAuthSuccess: (userId: String, email: String, token: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var successMsg by remember { mutableStateOf("") }
    var needsVerification by remember { mutableStateOf(false) }
    var pendingEmail by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .systemBarsPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Text("🥗", fontSize = 56.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "CalorieAI",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = theme.textPrimary
            )
            Text(
                "Track smarter with AI",
                color = theme.textSecondary,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(40.dp))

            if (needsVerification) {
                // --- VERIFICATION CODE SCREEN ---
                Text("Check your email", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = theme.textPrimary)
                Spacer(Modifier.height(8.dp))
                Text("We sent a code to $pendingEmail", color = theme.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))

                StyledTextField("Verification Code", verificationCode, theme) { verificationCode = it }
                Spacer(Modifier.height(16.dp))

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = Color(0xFFEF5350), fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                }
                if (successMsg.isNotBlank()) {
                    Text(successMsg, color = theme.accentPrimary, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                }

                PrimaryButton(if (isLoading) "Verifying..." else "Verify Account", theme) {
                    if (verificationCode.isBlank()) return@PrimaryButton
                    isLoading = true
                    errorMsg = ""
                    scope.launch {
                        val result = cognitoConfirmSignUp(pendingEmail, verificationCode)
                        isLoading = false
                        result.fold(
                            onSuccess = {
                                successMsg = "✅ Verified! Now log in."
                                needsVerification = false
                                isLoginMode = true
                                email = pendingEmail
                            },
                            onFailure = { errorMsg = it.message ?: "Verification failed" }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { needsVerification = false }) {
                    Text("← Back", color = theme.textSecondary)
                }

            } else {
                // --- LOGIN / SIGNUP SCREEN ---
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.card)
                        .padding(4.dp)
                ) {
                    listOf("Login" to true, "Sign Up" to false).forEach { (label, isLogin) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isLoginMode == isLogin) theme.accentPrimary else Color.Transparent)
                                .clickable { isLoginMode = isLogin; errorMsg = ""; successMsg = "" }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (isLoginMode == isLogin) Color.White else theme.textSecondary,
                                fontWeight = if (isLoginMode == isLogin) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                StyledTextField("Email", email, theme) { email = it }
                Spacer(Modifier.height(12.dp))
                StyledTextField("Password", password, theme) { password = it }

                if (!isLoginMode) {
                    Spacer(Modifier.height(12.dp))
                    StyledTextField("Confirm Password", confirmPassword, theme) { confirmPassword = it }
                }

                Spacer(Modifier.height(8.dp))

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = Color(0xFFEF5350), fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                }
                if (successMsg.isNotBlank()) {
                    Text(successMsg, color = theme.accentPrimary, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))

                PrimaryButton(
                    text = when {
                        isLoading -> if (isLoginMode) "Logging in..." else "Creating account..."
                        isLoginMode -> "Login"
                        else -> "Create Account"
                    },
                    theme = theme
                ) {
                    if (email.isBlank() || password.isBlank()) {
                        errorMsg = "Please fill in all fields"
                        return@PrimaryButton
                    }
                    if (!isLoginMode && password != confirmPassword) {
                        errorMsg = "Passwords don't match"
                        return@PrimaryButton
                    }
                    if (!isLoginMode && password.length < 8) {
                        errorMsg = "Password must be at least 8 characters"
                        return@PrimaryButton
                    }

                    isLoading = true
                    errorMsg = ""
                    successMsg = ""

                    scope.launch {
                        if (isLoginMode) {
                            val result = cognitoSignIn(email.trim(), password)
                            isLoading = false
                            result.fold(
                                onSuccess = { session ->
                                    onAuthSuccess(session.userId, session.email, session.accessToken)
                                },
                                onFailure = { errorMsg = it.message ?: "Login failed" }
                            )
                        } else {
                            val result = cognitoSignUp(email.trim(), password)
                            isLoading = false
                            result.fold(
                                onSuccess = {
                                    pendingEmail = email.trim()
                                    needsVerification = true
                                    errorMsg = ""
                                },
                                onFailure = { errorMsg = it.message ?: "Sign up failed" }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- ONBOARDING (Paged, no scroll needed) ---
@Composable
fun OnboardingScreen(theme: ThemeColors, onComplete: (UserProfile) -> Unit) {
    var page by remember { mutableStateOf(0) } // 0 = mode select, 1 = stats, 2 = activity/goal, 3 = manual

    var isManualEntry by remember { mutableStateOf(false) }
    var manualCalories by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var isMale by remember { mutableStateOf(true) }
    var activityMultiplier by remember { mutableStateOf(1.2f) }
    var goal by remember { mutableStateOf("Maintain") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .systemBarsPadding()
    ) {
        when (page) {
            // PAGE 0: Choose mode
            0 -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Welcome 👋",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = theme.textPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "How would you like to set your calorie goal?",
                        color = theme.textSecondary,
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(48.dp))

                    OnboardingOptionCard(
                        icon = "🧮",
                        title = "Calculate for me",
                        subtitle = "Uses your stats to estimate TDEE",
                        accentColor = theme.accentPrimary,
                        theme = theme
                    ) {
                        isManualEntry = false
                        page = 1
                    }

                    Spacer(Modifier.height(16.dp))

                    OnboardingOptionCard(
                        icon = "✏️",
                        title = "Enter manually",
                        subtitle = "I know my daily calorie target",
                        accentColor = theme.accentSecondary,
                        theme = theme
                    ) {
                        isManualEntry = true
                        page = 3
                    }
                }
            }

            // PAGE 1: Basic stats
            1 -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    OnboardingHeader("Your Stats", "Step 1 of 2", theme)
                    Spacer(Modifier.height(32.dp))

                    StyledTextField("Weight (kg)", weight, theme) { weight = it }
                    Spacer(Modifier.height(16.dp))
                    StyledTextField("Height (cm)", height, theme) { height = it }
                    Spacer(Modifier.height(16.dp))
                    StyledTextField("Age", age, theme) { age = it }

                    Spacer(Modifier.height(24.dp))
                    Text("Biological Sex", color = theme.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToggleChip("Male", isMale, theme, Modifier.weight(1f)) { isMale = true }
                        ToggleChip("Female", !isMale, theme, Modifier.weight(1f)) { isMale = false }
                    }

                    Spacer(Modifier.height(40.dp))
                    PrimaryButton("Next →", theme) {
                        if (weight.toFloatOrNull() != null && height.toFloatOrNull() != null && age.toIntOrNull() != null) {
                            page = 2
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { page = 0 }) {
                        Text("← Back", color = theme.textSecondary)
                    }
                }
            }

            // PAGE 2: Activity + Goal
            2 -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    OnboardingHeader("Your Lifestyle", "Step 2 of 2", theme)
                    Spacer(Modifier.height(24.dp))

                    Text("Activity Level", color = theme.textSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        Triple("🛋️", "Sedentary", 1.2f),
                        Triple("🚶", "Lightly Active", 1.375f),
                        Triple("🏃", "Moderately Active", 1.55f),
                        Triple("💪", "Very Active", 1.725f)
                    ).forEach { (icon, label, value) ->
                        ActivityChip(icon, label, value, activityMultiplier, theme) { activityMultiplier = value }
                        Spacer(Modifier.height(6.dp))
                    }

                    Spacer(Modifier.height(20.dp))
                    Text("Your Goal", color = theme.textSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Lose", "Maintain", "Gain").forEach { g ->
                            ToggleChip(g, goal == g, theme, Modifier.weight(1f)) { goal = g }
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                    PrimaryButton("Save & Start 🚀", theme) {
                        val w = weight.toFloatOrNull()
                        val h = height.toFloatOrNull()
                        val a = age.toIntOrNull()
                        if (w != null && h != null && a != null) {
                            val s = if (isMale) 5 else -161
                            val bmr = (10 * w) + (6.25 * h) - (5 * a) + s
                            val tdee = (bmr * activityMultiplier).toInt()
                            val finalTarget = when (goal) {
                                "Lose" -> tdee - 500
                                "Gain" -> tdee + 500
                                else -> tdee
                            }
                            onComplete(UserProfile(w, h, a, isMale, activityMultiplier, goal, finalTarget))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { page = 1 }) {
                        Text("← Back", color = theme.textSecondary)
                    }
                }
            }

            // PAGE 3: Manual calories
            3 -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    OnboardingHeader("Custom Target", "Manual Entry", theme)
                    Spacer(Modifier.height(32.dp))

                    StyledTextField("Daily Calorie Target (e.g. 2200)", manualCalories, theme) { manualCalories = it }

                    Spacer(Modifier.height(40.dp))
                    PrimaryButton("Save & Start 🚀", theme) {
                        val target = manualCalories.toIntOrNull()
                        if (target != null) {
                            onComplete(UserProfile(0f, 0f, 0, true, 0f, "Custom", target))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { page = 0 }) {
                        Text("← Back", color = theme.textSecondary)
                    }
                }
            }
        }
    }
}

// --- ONBOARDING HELPER COMPOSABLES ---
@Composable
fun OnboardingHeader(title: String, subtitle: String, theme: ThemeColors) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Text(subtitle, color = theme.accentPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = theme.textPrimary)
    }
}

@Composable
fun OnboardingOptionCard(icon: String, title: String, subtitle: String, accentColor: Color, theme: ThemeColors, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.card),
        elevation = CardDefaults.cardElevation(defaultElevation = if (theme.isDark) 0.dp else 4.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 32.sp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = theme.textPrimary, fontSize = 16.sp)
                Text(subtitle, color = theme.textSecondary, fontSize = 13.sp)
            }
            Icon(Icons.Default.KeyboardArrowRight, null, tint = accentColor)
        }
    }
}

@Composable
fun StyledTextField(label: String, value: String, theme: ThemeColors, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = theme.textSecondary) },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = theme.textPrimary,
            unfocusedTextColor = theme.textPrimary,
            focusedBorderColor = theme.accentPrimary,
            unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.4f),
            cursorColor = theme.accentPrimary
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun ToggleChip(label: String, selected: Boolean, theme: ThemeColors, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bgColor = if (selected) theme.accentPrimary else theme.surface
    val textColor = if (selected) Color.White else theme.textSecondary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
    }
}

@Composable
fun ActivityChip(icon: String, label: String, value: Float, selectedValue: Float, theme: ThemeColors, onClick: () -> Unit) {
    val selected = value == selectedValue
    val bgColor = if (selected) theme.accentPrimary.copy(alpha = 0.15f) else theme.card
    val borderColor = if (selected) theme.accentPrimary else Color.Transparent
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, borderColor) else null
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Text(label, color = if (selected) theme.accentPrimary else theme.textPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
fun PrimaryButton(text: String, theme: ThemeColors, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = theme.accentPrimary)
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

// --- SCREEN 2: MAIN TRACKER ---
@Composable
fun CalorieTrackerScreen(
    userProfile: UserProfile,
    theme: ThemeColors,
    isDarkTheme: Boolean,
    loggedInEmail: String,
    loggedInUserId: String,
    onToggleTheme: () -> Unit,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current

    var userInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var allTimeFoodLog by remember { mutableStateOf(loadLog(context)) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showHistoryScreen by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showClearTodayDialog by remember { mutableStateOf(false) }
    var showCustomEntryDialog by remember { mutableStateOf(false) }
    var selectedMealForCustom by remember { mutableStateOf("") }

    // Delete confirmation state
    var itemPendingDelete by remember { mutableStateOf<FoodItem?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val original = BitmapFactory.decodeStream(stream)
                val maxDim = 800f
                val scale = minOf(maxDim / original.width, maxDim / original.height)
                selectedBitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
                } else original
            }
        }
    }

    val scope = rememberCoroutineScope()
    val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val todaysFoodLog = allTimeFoodLog.filter { it.dateString == todayString }

    fun String.extractFloat(): Float = Regex("[0-9]*\\.?[0-9]+").find(this)?.value?.toFloatOrNull() ?: 0f

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    val sb = StringBuilder("Date,Meal,Food Item,Calories,Protein,Carbs,Fat\n")
                    allTimeFoodLog.sortedByDescending { log -> log.dateString }.forEach { item ->
                        sb.append("${item.dateString},${item.mealType},${item.name},${item.calories},${item.protein},${item.carbs},${item.fat}\n")
                    }
                    stream.write(sb.toString().toByteArray())
                }
                Toast.makeText(context, "Full History Saved!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun addFood(mealCategory: String) {
        if (userInput.isBlank() && selectedBitmap == null) {
            Toast.makeText(context, "Please enter a food name or pick an image first", Toast.LENGTH_SHORT).show()
            return
        }
        isLoading = true
        scope.launch {
            try {
                val description = userInput.ifBlank { "food from image" }

                val responseText = withContext(Dispatchers.IO) {
                    val url = URL(API_URL)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.connectTimeout = 30000
                    conn.readTimeout = 30000

                    val body = JSONObject().apply {
                        put("action", "analyze_food")
                        put("foodDescription", description)
                        put("mealType", mealCategory)
                        put("dateString", todayString)
                        put("userId", loggedInUserId)
                    }.toString()

                    OutputStreamWriter(conn.outputStream).use { it.write(body) }

                    if (conn.responseCode == 200) {
                        conn.inputStream.bufferedReader().readText()
                    } else {
                        conn.errorStream?.bufferedReader()?.readText() ?: "Error ${conn.responseCode}"
                    }
                }

                // Parse response from AWS
                val json = JSONObject(responseText)
                val success = json.optBoolean("success", false)

                if (!success) {
                    val errMsg = json.optString("error", "Unknown error from server")
                    Toast.makeText(context, "Server error: $errMsg", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val itemsArray = json.getJSONArray("items")
                val newItems = (0 until itemsArray.length()).mapNotNull { i ->
                    val obj = itemsArray.getJSONObject(i)
                    FoodItem(
                        id = System.nanoTime() + i,
                        name = obj.optString("name", "Unknown"),
                        calories = obj.optString("calories", "0"),
                        protein = obj.optString("protein", "0"),
                        carbs = obj.optString("carbs", "0"),
                        fat = obj.optString("fat", "0"),
                        mealType = mealCategory,
                        dateString = todayString
                    )
                }

                val updatedList = allTimeFoodLog + newItems
                allTimeFoodLog = updatedList
                saveLog(context, updatedList)
                userInput = ""
                selectedBitmap = null

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    fun addCustomFood(name: String, calories: String, protein: String, carbs: String, fat: String, meal: String) {
        val item = FoodItem(
            id = System.nanoTime(),
            name = name,
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            mealType = meal,
            dateString = todayString
        )
        val updatedList = allTimeFoodLog + item
        allTimeFoodLog = updatedList
        saveLog(context, updatedList)
    }

    fun deleteFromDynamoDB(entryId: Long) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val url = URL(API_URL)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000

                    val body = JSONObject().apply {
                        put("action", "delete_entry")
                        put("userId", loggedInUserId)
                        put("id", entryId.toString())
                    }.toString()

                    OutputStreamWriter(conn.outputStream).use { it.write(body) }
                    conn.inputStream.bufferedReader().readText()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Silent fail — local delete already happened, not critical
            }
        }
    }

    // --- DELETE CONFIRMATION DIALOG ---
    itemPendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemPendingDelete = null },
            containerColor = theme.card,
            title = { Text("Delete Entry?", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Remove \"${item.name}\" from your log?", color = theme.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = allTimeFoodLog.filter { it.id != item.id }
                        allTimeFoodLog = updated
                        saveLog(context, updated)
                        deleteFromDynamoDB(item.id)
                        itemPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { itemPendingDelete = null }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }

    // --- CLEAR TODAY CONFIRMATION ---
    if (showClearTodayDialog) {
        AlertDialog(
            onDismissRequest = { showClearTodayDialog = false },
            containerColor = theme.card,
            title = { Text("Clear Today?", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all food entries logged today.", color = theme.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = allTimeFoodLog.filter { it.dateString != todayString }
                        allTimeFoodLog = updated
                        saveLog(context, updated)
                        showClearTodayDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) { Text("Clear", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showClearTodayDialog = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }

    // --- CUSTOM ENTRY DIALOG ---
    if (showCustomEntryDialog) {
        CustomEntryDialog(
            theme = theme,
            initialMeal = selectedMealForCustom,
            onDismiss = { showCustomEntryDialog = false },
            onSave = { name, calories, protein, carbs, fat, meal ->
                addCustomFood(name, calories, protein, carbs, fat, meal)
                showCustomEntryDialog = false
            }
        )
    }

    // --- SETTINGS DIALOG ---
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = theme.card,
            title = { Text("Settings", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Logged in as
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null, tint = theme.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(loggedInEmail, color = theme.textSecondary, fontSize = 13.sp)
                    }
                    HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.2f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark Theme", color = theme.textPrimary)
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { onToggleTheme() },
                            colors = SwitchDefaults.colors(checkedThumbColor = theme.accentPrimary, checkedTrackColor = theme.accentPrimary.copy(alpha = 0.4f))
                        )
                    }
                    HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.2f))
                    TextButton(
                        onClick = {
                            generateDemoData(context)
                            allTimeFoodLog = loadLog(context)
                            showSettingsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Inject 15-Day Demo Data", color = theme.accentSecondary) }
                    HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.2f))
                    TextButton(
                        onClick = {
                            showSettingsDialog = false
                            onEditProfile()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Edit Profile / Reset Onboarding", color = theme.textSecondary) }
                    HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.2f))
                    TextButton(
                        onClick = {
                            showSettingsDialog = false
                            onSignOut()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sign Out", color = Color(0xFFEF5350)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Done", color = theme.accentPrimary)
                }
            }
        )
    }

    // --- HISTORY SCREEN ---
    if (showHistoryScreen) {
        BackHandler { showHistoryScreen = false }
        var showClearAllDialog by remember { mutableStateOf(false) }

        if (showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllDialog = false },
                containerColor = theme.card,
                title = { Text("Clear All History?", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("This permanently deletes your entire food log. This cannot be undone.", color = theme.textSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            allTimeFoodLog = emptyList()
                            saveLog(context, emptyList())
                            showClearAllDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                    ) { Text("Delete All", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllDialog = false }) {
                        Text("Cancel", color = theme.textSecondary)
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
                .systemBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showHistoryScreen = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = theme.textPrimary)
                    }
                    Text("All-Time History", style = MaterialTheme.typography.titleLarge, color = theme.textPrimary, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { showClearAllDialog = true }) {
                    Icon(Icons.Default.Delete, "Clear All", tint = Color(0xFFEF5350))
                }
            }

            val groupedLogs = allTimeFoodLog.groupBy { it.dateString }.toSortedMap(reverseOrder())

            LazyColumn(modifier = Modifier.weight(1f)) {
                groupedLogs.forEach { (date, logs) ->
                    val dayTotal = logs.sumOf { it.calories.let { c -> Regex("[0-9]*\\.?[0-9]+").find(c)?.value?.toFloatOrNull() ?: 0f }.toInt() }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(date, color = theme.accentPrimary, fontWeight = FontWeight.Bold)
                            Text("$dayTotal kcal total", color = theme.textSecondary, fontSize = 12.sp)
                        }
                    }
                    items(logs, key = { it.id }) { item ->
                        FoodRow(item, theme, onDelete = { itemPendingDelete = item })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { saveLauncher.launch("calorie_full_history.csv") },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accentPrimary)
            ) {
                Text("EXPORT ALL TO CSV", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    // --- MAIN SCREEN ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // TOP BAR
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Today's Log", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = theme.textPrimary)
                Text("Goal: ${userProfile.goal} · ${userProfile.dailyCalorieTarget} kcal", color = theme.textSecondary, fontSize = 12.sp)
            }
            Row {
                TextButton(
                    onClick = { onToggleTheme() },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(if (isDarkTheme) "☀️" else "🌙", fontSize = 20.sp)
                }
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(Icons.Default.Settings, "Settings", tint = theme.textSecondary)
                }
                IconButton(onClick = { showHistoryScreen = true }) {
                    Icon(Icons.AutoMirrored.Filled.List, "History", tint = theme.accentSecondary)
                }
            }
        }

        // CALORIE SUMMARY CARD
        val totalCals = todaysFoodLog.sumOf { it.calories.extractFloat().toInt() }
        val dailyGoal = userProfile.dailyCalorieTarget.toFloat()
        val progress = (totalCals / dailyGoal).coerceIn(0f, 1f)
        val remaining = (dailyGoal - totalCals).toInt()
        val barColor by animateColorAsState(
            targetValue = if (progress >= 1f) Color(0xFFEF5350) else if (progress >= 0.85f) Color(0xFFFFB300) else theme.accentPrimary,
            animationSpec = tween(500), label = "barColor"
        )
        val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(700), label = "progress")

        Card(
            colors = CardDefaults.cardColors(containerColor = theme.card),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = if (theme.isDark) 0.dp else 6.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("CONSUMED", fontSize = 10.sp, color = theme.textSecondary, letterSpacing = 1.5.sp)
                        Text("$totalCals", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = barColor)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("REMAINING", fontSize = 10.sp, color = theme.textSecondary, letterSpacing = 1.5.sp)
                        Text(
                            if (remaining >= 0) "$remaining kcal" else "${-remaining} over",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (remaining < 0) Color(0xFFEF5350) else theme.textPrimary
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = barColor,
                    trackColor = theme.textSecondary.copy(alpha = 0.15f)
                )
                Text("/ ${dailyGoal.toInt()} kcal goal", color = theme.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MacroStat("Protein", String.format("%.1fg", todaysFoodLog.sumOf { it.protein.extractFloat().toDouble() }), Color(0xFF7C5CBF), theme)
                    VerticalDivider(modifier = Modifier.height(36.dp), color = theme.textSecondary.copy(alpha = 0.2f))
                    MacroStat("Carbs", String.format("%.1fg", todaysFoodLog.sumOf { it.carbs.extractFloat().toDouble() }), Color(0xFF26A69A), theme)
                    VerticalDivider(modifier = Modifier.height(36.dp), color = theme.textSecondary.copy(alpha = 0.2f))
                    MacroStat("Fat", String.format("%.1fg", todaysFoodLog.sumOf { it.fat.extractFloat().toDouble() }), Color(0xFFEF6C00), theme)
                }
            }
        }

        // IMAGE PREVIEW
        if (selectedBitmap != null) {
            Box(modifier = Modifier.padding(bottom = 12.dp)) {
                Image(
                    bitmap = selectedBitmap!!.asImageBitmap(),
                    contentDescription = "Food",
                    modifier = Modifier.size(90.dp).clip(RoundedCornerShape(12.dp))
                )
                IconButton(
                    onClick = { selectedBitmap = null },
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Clear, "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }

        // INPUT ROW
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = userInput,
                onValueChange = { userInput = it },
                label = { Text("Describe food or pick image", color = theme.textSecondary, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = theme.textPrimary,
                    unfocusedTextColor = theme.textPrimary,
                    focusedBorderColor = theme.accentPrimary,
                    unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.4f),
                    cursorColor = theme.accentPrimary
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier
                    .size(56.dp)
                    .background(theme.card, RoundedCornerShape(12.dp))
            ) {
                Text("📷", fontSize = 22.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // MEAL BUTTONS (AI log)
        Text("Log with AI", color = theme.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("🌅" to "Breakfast", "☀️" to "Lunch", "🌙" to "Dinner", "🍎" to "Snack").forEach { (icon, meal) ->
                Button(
                    onClick = { addFood(meal) },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.accentSecondary.copy(alpha = 0.85f),
                        contentColor = Color.White,
                        disabledContainerColor = theme.textSecondary.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 2.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(icon, fontSize = 16.sp)
                        Text(meal.take(5), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // MANUAL ENTRY — collapsible
        var showManualButtons by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showManualButtons = !showManualButtons }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Manual Entry", color = theme.accentPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Icon(
                if (showManualButtons) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = theme.accentPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        if (showManualButtons) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("🌅" to "Breakfast", "☀️" to "Lunch", "🌙" to "Dinner", "🍎" to "Snack").forEach { (icon, meal) ->
                    OutlinedButton(
                        onClick = {
                            selectedMealForCustom = meal
                            showCustomEntryDialog = true
                        },
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.accentPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 2.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(icon, fontSize = 16.sp)
                            Text(meal.take(5), fontSize = 9.sp, color = theme.accentPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(4.dp)),
                color = theme.accentPrimary,
                trackColor = theme.accentPrimary.copy(alpha = 0.15f)
            )
        }

        // TODAY'S LOG HEADER with Clear Today button
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Today's Entries", fontWeight = FontWeight.Bold, color = theme.textPrimary, fontSize = 15.sp)
            if (todaysFoodLog.isNotEmpty()) {
                TextButton(onClick = { showClearTodayDialog = true }) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear Today", color = Color(0xFFEF5350), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (todaysFoodLog.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🍽️", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Nothing logged yet", color = theme.textSecondary)
                    Text("Add your first meal above", color = theme.textSecondary, fontSize = 12.sp)
                }
            }
        } else {
            todaysFoodLog.forEach { item ->
                FoodRow(item, theme, onDelete = { itemPendingDelete = item })
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// --- CUSTOM ENTRY DIALOG ---
@Composable
fun CustomEntryDialog(
    theme: ThemeColors,
    initialMeal: String,
    onDismiss: () -> Unit,
    onSave: (name: String, calories: String, protein: String, carbs: String, fat: String, meal: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var selectedMeal by remember { mutableStateOf(initialMeal) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = theme.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text("Manual Entry", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = theme.textPrimary)
                Text("Enter nutrition info manually", color = theme.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))

                // Meal selector inside dialog
                Text("Category", color = theme.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Breakfast", "Lunch", "Dinner", "Snack").forEach { meal ->
                        ToggleChip(meal.take(5), selectedMeal == meal, theme, Modifier.weight(1f)) { selectedMeal = meal }
                    }
                }

                Spacer(Modifier.height(16.dp))
                StyledTextField("Food Name *", name, theme) { name = it }
                Spacer(Modifier.height(10.dp))
                StyledTextField("Calories (kcal) *", calories, theme) { calories = it }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { StyledTextField("Protein (g)", protein, theme) { protein = it } }
                    Box(Modifier.weight(1f)) { StyledTextField("Carbs (g)", carbs, theme) { carbs = it } }
                    Box(Modifier.weight(1f)) { StyledTextField("Fat (g)", fat, theme) { fat = it } }
                }

                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.textSecondary.copy(alpha = 0.4f))
                    ) { Text("Cancel", color = theme.textSecondary) }

                    Button(
                        onClick = {
                            if (name.isNotBlank() && calories.isNotBlank()) {
                                onSave(
                                    name,
                                    calories,
                                    protein.ifBlank { "0" },
                                    carbs.ifBlank { "0" },
                                    fat.ifBlank { "0" },
                                    selectedMeal
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accentPrimary)
                    ) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// --- SHARED COMPOSABLES ---
@Composable
fun MacroStat(label: String, value: String, color: Color, theme: ThemeColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = theme.textSecondary)
    }
}

@Composable
fun FoodRow(item: FoodItem, theme: ThemeColors, onDelete: () -> Unit) {
    val mealColor = when (item.mealType) {
        "Breakfast" -> Color(0xFFFFA726)
        "Lunch" -> Color(0xFF26A69A)
        "Dinner" -> Color(0xFF7C5CBF)
        else -> Color(0xFFEF5350)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = theme.card),
        elevation = CardDefaults.cardElevation(defaultElevation = if (theme.isDark) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .background(mealColor, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(item.mealType.uppercase(), fontSize = 9.sp, color = mealColor, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Text(item.name, style = MaterialTheme.typography.bodyLarge, color = theme.textPrimary, fontWeight = FontWeight.Medium)
                    Text(
                        "P: ${item.protein}  C: ${item.carbs}  F: ${item.fat}",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${item.calories} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = theme.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}