package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import com.example.data.AppDatabase
import com.example.data.DoctorEntity
import com.example.data.EntryEntity
import com.example.data.LabRepository
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    
    // Shared state triggers for global keyboard shortcuts
    private val triggerAddEntryDialog = mutableStateOf(false)
    private val triggerFocusSearch = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup repository and viewmodel manually (Simple Constructor Injection)
        val database = AppDatabase.getDatabase(this)
        val repository = LabRepository(database)
        viewModel = MainViewModel(repository)

        setContent {
            val themeMode by viewModel.darkThemeMode.collectAsStateWithLifecycle()

            MyApplicationTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigationWrapper(
                        viewModel = viewModel,
                        triggerAddEntryDialog = triggerAddEntryDialog,
                        triggerFocusSearch = triggerFocusSearch
                    )
                }
            }
        }
    }

    // Capture Ctrl + N and Ctrl + F for global shortcuts
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && event.isCtrlPressed) {
            if (keyCode == KeyEvent.KEYCODE_N) {
                if (viewModel.isLoggedIn.value) {
                    triggerAddEntryDialog.value = true
                    Toast.makeText(this, "Shortcut: New Entry Form opened", Toast.LENGTH_SHORT).show()
                }
                return true
            } else if (keyCode == KeyEvent.KEYCODE_F) {
                if (viewModel.isLoggedIn.value) {
                    viewModel.currentScreen.value = "entries"
                    triggerFocusSearch.value = true
                    Toast.makeText(this, "Shortcut: Focus Search Bar", Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

// --- Print Adapter for Android PDF System ---
class MyPrintDocumentAdapter(private val file: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: android.os.CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val pdi = PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback?.onLayoutFinished(pdi, true)
    }

    override fun onWrite(
        pages: Array<out android.print.PageRange>?,
        destination: android.os.ParcelFileDescriptor?,
        cancellationSignal: android.os.CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        var input: InputStream? = null
        var output: OutputStream? = null
        try {
            input = FileInputStream(file)
            output = FileOutputStream(destination?.fileDescriptor)
            val buf = ByteArray(1024)
            var bytesRead: Int
            while (input.read(buf).also { bytesRead = it } > 0) {
                output.write(buf, 0, bytesRead)
            }
            callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        } catch (ee: Exception) {
            callback?.onWriteFailed(ee.toString())
        } finally {
            try {
                input?.close()
                output?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

// --- Core Helper Functions ---
fun sharePdfFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "com.aistudio.micropathlab.kdrslt.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Commission Report"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun openPdfFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "com.aistudio.micropathlab.kdrslt.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No PDF Viewer installed. Please share or print instead.", Toast.LENGTH_LONG).show()
    }
}

fun printPdfFile(context: Context, file: File) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "MicroPathLab_${file.name.substringBeforeLast(".")}"
        printManager.print(
            jobName,
            MyPrintDocumentAdapter(file),
            null
        )
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to initialize print manager: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// --- Main Responsive Navigation Screen Wrapper ---
@Composable
fun AppNavigationWrapper(
    viewModel: MainViewModel,
    triggerAddEntryDialog: MutableState<Boolean>,
    triggerFocusSearch: MutableState<Boolean>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe screens & messages
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    // Handle show add entry dialog triggers
    var showAddEntryDialog by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<EntryEntity?>(null) }

    LaunchedEffect(triggerAddEntryDialog.value) {
        if (triggerAddEntryDialog.value) {
            entryToEdit = null
            showAddEntryDialog = true
            triggerAddEntryDialog.value = false // reset
        }
    }

    // Observe Toast message stream
    LaunchedEffect(Unit) {
        viewModel.uiMessage.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    if (!isLoggedIn || currentScreen == "login") {
        LoginScreen(viewModel = viewModel)
    } else {
        // Detect wide screens (tablets / desktop-like layouts)
        val configuration = LocalConfiguration.current
        val isWideScreen = configuration.screenWidthDp >= 600

        // Custom Adaptive Side Rail Navigation or Bottom Bar Navigation
        Scaffold(
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        val navItems = listOf(
                            Triple("home", Icons.Default.Dashboard, "Home"),
                            Triple("entries", Icons.AutoMirrored.Filled.List, "Entries"),
                            Triple("reports", Icons.Default.Assessment, "Reports"),
                            Triple("doctors", Icons.Default.MedicalServices, "Doctors"),
                            Triple("settings", Icons.Default.Settings, "Settings")
                        )
                        navItems.forEach { (screen, icon, label) ->
                            NavigationBarItem(
                                selected = currentScreen == screen,
                                onClick = { viewModel.currentScreen.value = screen },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.testTag("nav_${screen}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentScreen == "home" || currentScreen == "entries") {
                    FloatingActionButton(
                        onClick = {
                            entryToEdit = null
                            showAddEntryDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_entry")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Entry")
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Left Sidebar for Tablet / DeX Mode (Persistent Navigation Rail)
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        header = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Science,
                                        contentDescription = "Logo",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "MicroLab",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        val navItems = listOf(
                            Triple("home", Icons.Default.Dashboard, "Home"),
                            Triple("entries", Icons.AutoMirrored.Filled.List, "Entries"),
                            Triple("reports", Icons.Default.Assessment, "Reports"),
                            Triple("doctors", Icons.Default.MedicalServices, "Doctors"),
                            Triple("settings", Icons.Default.Settings, "Settings")
                        )
                        navItems.forEach { (screen, icon, label) ->
                            NavigationRailItem(
                                selected = currentScreen == screen,
                                onClick = { viewModel.currentScreen.value = screen },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .testTag("rail_${screen}")
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))

                        // Logout button at bottom of Rail
                        IconButton(
                            onClick = { viewModel.performLogout() },
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Main Content Screen Area
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (currentScreen) {
                        "home" -> HomeScreen(
                            viewModel = viewModel,
                            onAddEntryTrigger = {
                                entryToEdit = null
                                showAddEntryDialog = true
                            }
                        )
                        "entries" -> EntriesScreen(
                            viewModel = viewModel,
                            onEditEntry = { entry ->
                                entryToEdit = entry
                                showAddEntryDialog = true
                            },
                            triggerFocusSearch = triggerFocusSearch
                        )
                        "reports" -> ReportsScreen(viewModel = viewModel)
                        "doctors" -> DoctorsScreen(viewModel = viewModel)
                        "settings" -> SettingsScreen(viewModel = viewModel)
                    }
                }
            }

            // Entry Add/Edit Dialog
            if (showAddEntryDialog) {
                AddEditEntryDialog(
                    viewModel = viewModel,
                    entryToEdit = entryToEdit,
                    onDismiss = { showAddEntryDialog = false }
                )
            }
        }
    }
}

// --- SCREEN 1: LOGIN ---
@Composable
fun LoginScreen(viewModel: MainViewModel) {
    val username by viewModel.loginUsername.collectAsStateWithLifecycle()
    val password by viewModel.loginPassword.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()

    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 420.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Lab Logo icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                    MaterialTheme.colorScheme.primary
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Science,
                        contentDescription = "Lab Logo",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "MICRO PATHOLOGY LAB",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Text(
                    "Laboratory Management Suite",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Username field
                OutlinedTextField(
                    value = username,
                    onValueChange = { viewModel.loginUsername.value = it },
                    label = { Text("Username") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = "User") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.loginPassword.value = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Lock") },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Error message
                if (loginError != null) {
                    Text(
                        text = loginError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag("login_error")
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.performLogin() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("login_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Sign In Securely",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Default: admin / admin",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// --- SCREEN 2: HOME DASHBOARD ---
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onAddEntryTrigger: () -> Unit
) {
    val totalEntries by viewModel.totalEntriesCount.collectAsStateWithLifecycle()
    val totalAmt by viewModel.totalAmount.collectAsStateWithLifecycle()
    val totalDocAmt by viewModel.totalDoctorAmount.collectAsStateWithLifecycle()
    val totalOthAmt by viewModel.totalOtherAmount.collectAsStateWithLifecycle()
    val activeMonth by viewModel.entrySelectedMonth.collectAsStateWithLifecycle()

    val formattedMonthName = remember(activeMonth) {
        if (activeMonth.isEmpty()) "All Time"
        else {
            try {
                val sdfIn = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                val sdfOut = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                sdfOut.format(sdfIn.parse(activeMonth)!!)
            } catch (e: Exception) {
                activeMonth
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Welcoming header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Lab Dashboard",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Performance Overview for: $formattedMonthName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                Button(
                    onClick = onAddEntryTrigger,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Entry")
                }
            }
        }

        // Summary Cards Layout Grid
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val configuration = LocalConfiguration.current
                val isWide = configuration.screenWidthDp >= 600

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DashboardCard(
                            title = "Total Entries",
                            value = "$totalEntries",
                            icon = Icons.AutoMirrored.Filled.List,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardCard(
                            title = "Gross Revenue",
                            value = "₹${String.format(Locale.getDefault(), "%,.2f", totalAmt)}",
                            icon = Icons.Default.CurrencyRupee,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DashboardCard(
                            title = "Doctor Commission",
                            value = "₹${String.format(Locale.getDefault(), "%,.2f", totalDocAmt)}",
                            icon = Icons.Default.MedicalServices,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        val netProfit = totalAmt - totalDocAmt - totalOthAmt
                        val isPositive = netProfit >= 0
                        DashboardCard(
                            title = "Net Lab Revenue",
                            value = "₹${String.format(Locale.getDefault(), "%,.2f", netProfit)}",
                            icon = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            color = if (isPositive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    DashboardCard(
                        title = "Total Entries",
                        value = "$totalEntries",
                        icon = Icons.AutoMirrored.Filled.List,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DashboardCard(
                        title = "Gross Revenue",
                        value = "₹${String.format(Locale.getDefault(), "%,.2f", totalAmt)}",
                        icon = Icons.Default.CurrencyRupee,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DashboardCard(
                        title = "Doctor Payouts",
                        value = "₹${String.format(Locale.getDefault(), "%,.2f", totalDocAmt)}",
                        icon = Icons.Default.MedicalServices,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val netProfit = totalAmt - totalDocAmt - totalOthAmt
                    val isPositive = netProfit >= 0
                    DashboardCard(
                        title = "Net Revenue",
                        value = "₹${String.format(Locale.getDefault(), "%,.2f", netProfit)}",
                        icon = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        color = if (isPositive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Quick Shortcuts and visual instructions helper
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Pro-Tip: Navigation and shortcuts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Press Ctrl + N to open the New Entry Dialog instantly from anywhere. Press Ctrl + F to view and search table records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// --- SCREEN 3: ENTRIES VIEW (TABLE) ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntriesScreen(
    viewModel: MainViewModel,
    onEditEntry: (EntryEntity) -> Unit,
    triggerFocusSearch: MutableState<Boolean>
) {
    val filteredEntries by viewModel.filteredEntries.collectAsStateWithLifecycle()
    val searchQuery by viewModel.entrySearchQuery.collectAsStateWithLifecycle()
    val activeMonth by viewModel.entrySelectedMonth.collectAsStateWithLifecycle()
    val sortCol by viewModel.entrySortColumn.collectAsStateWithLifecycle()
    val sortAsc by viewModel.entrySortAscending.collectAsStateWithLifecycle()

    // Calculated totals for filtered view at the bottom
    val totalAmt by viewModel.totalAmount.collectAsStateWithLifecycle()
    val totalDocAmt by viewModel.totalDoctorAmount.collectAsStateWithLifecycle()
    val totalOthAmt by viewModel.totalOtherAmount.collectAsStateWithLifecycle()

    var showDeleteConfirmDialog by remember { mutableStateOf<EntryEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Title block
        Text(
            "Laboratory Entries",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Filters bar: Search & Month
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.entrySearchQuery.value = it },
                placeholder = { Text("Search patient, test, doctor...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("entry_search_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Month Picker Dropdown
            var dropdownExpanded by remember { mutableStateOf(false) }
            val monthsList = remember {
                val list = mutableListOf<Pair<String, String>>()
                list.add("" to "All Time")

                val cal = Calendar.getInstance()
                val sdfValue = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                val sdfLabel = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                for (i in 0 until 12) {
                    list.add(sdfValue.format(cal.time) to sdfLabel.format(cal.time))
                    cal.add(Calendar.MONTH, -1)
                }
                list
            }

            Box {
                Button(
                    onClick = { dropdownExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    val label = monthsList.find { it.first == activeMonth }?.second ?: "Select Month"
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(label)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    monthsList.forEach { (valStr, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.entrySelectedMonth.value = valStr
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Table Data Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                // Table Headers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableHeaderCell("Date", "date", sortCol, sortAsc, 95.dp) { viewModel.entrySortColumn.value = "date"; viewModel.entrySortAscending.value = !sortAsc }
                    TableHeaderCell("Patient Name", "name", sortCol, sortAsc, 160.dp) { viewModel.entrySortColumn.value = "name"; viewModel.entrySortAscending.value = !sortAsc }
                    TableHeaderCell("Age", "age", sortCol, sortAsc, 60.dp) { viewModel.entrySortColumn.value = "age"; viewModel.entrySortAscending.value = !sortAsc }
                    TableHeaderCell("Test Name", "test", sortCol, sortAsc, 130.dp) { viewModel.entrySortColumn.value = "test"; viewModel.entrySortAscending.value = !sortAsc }
                    TableHeaderCell("Amount", "amount", sortCol, sortAsc, 100.dp) { viewModel.entrySortColumn.value = "amount"; viewModel.entrySortAscending.value = !sortAsc }
                    TableHeaderCell("Doctor Comm.", "doctor", sortCol, sortAsc, 110.dp) { viewModel.entrySortColumn.value = "doctor"; viewModel.entrySortAscending.value = !sortAsc }
                    TableHeaderCell("Other (₹)", "other", sortCol, sortAsc, 90.dp) { viewModel.entrySortColumn.value = "other"; viewModel.entrySortAscending.value = !sortAsc }
                    Text("Doctor Referral", fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp)
                    Text("Actions", fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp, textAlign = TextAlign.Center)
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // List of Entries rows
                if (filteredEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(min = 900.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No entries found matching criteria", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(min = 975.dp)
                    ) {
                        items(filteredEntries, key = { it.id }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Formatting Dates
                                val dispDate = try {
                                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    val date = inputFormat.parse(entry.dateStr)
                                    SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault()).format(date!!)
                                } catch (e: Exception) {
                                    entry.dateStr
                                }

                                Text(dispDate, modifier = Modifier.width(95.dp), fontSize = 13.sp)
                                Text(entry.patientName, modifier = Modifier.width(160.dp), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${entry.age} yrs", modifier = Modifier.width(60.dp), fontSize = 13.sp)
                                Text(entry.test, modifier = Modifier.width(130.dp), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("₹${String.format(Locale.getDefault(), "%.2f", entry.amount)}", modifier = Modifier.width(100.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("₹${String.format(Locale.getDefault(), "%.2f", entry.doctorAmount)}", modifier = Modifier.width(110.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                                Text("₹${String.format(Locale.getDefault(), "%.2f", entry.otherAmount)}", modifier = Modifier.width(90.dp), fontSize = 13.sp)
                                Text(entry.doctorName, modifier = Modifier.width(130.dp), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

                                // Edit & Delete Actions row
                                Row(
                                    modifier = Modifier.width(100.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    IconButton(
                                        onClick = { onEditEntry(entry) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(
                                        onClick = { showDeleteConfirmDialog = entry },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // Monthly Summary Banner at the bottom
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Monthly Total Summary:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SummaryStatBadge(label = "Amount", value = "₹${String.format(Locale.getDefault(), "%,.2f", totalAmt)}", color = MaterialTheme.colorScheme.primary)
                    SummaryStatBadge(label = "Doc Payout", value = "₹${String.format(Locale.getDefault(), "%,.2f", totalDocAmt)}", color = MaterialTheme.colorScheme.tertiary)
                    val net = totalAmt - totalDocAmt - totalOthAmt
                    SummaryStatBadge(label = "Net Revenue", value = "₹${String.format(Locale.getDefault(), "%,.2f", net)}", color = if (net >= 0) Color(0xFF1B5E20) else Color.Red)
                }
            }
        }
    }

    // Confirmation dialog before deleting
    if (showDeleteConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteEntry(showDeleteConfirmDialog!!)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel")
                }
            },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete the entry for patient '${showDeleteConfirmDialog!!.patientName}'? This action cannot be undone.") }
        )
    }
}

@Composable
fun SummaryStatBadge(label: String, value: String, color: Color) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun TableHeaderCell(
    text: String,
    columnKey: String,
    currentSortCol: String,
    isAscending: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onHeaderClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(width)
            .clickable { onHeaderClick() }
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (currentSortCol == columnKey) {
            Icon(
                imageVector = if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// --- SCREEN 4: REPORTS ---
@Composable
fun ReportsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val doctorsList by viewModel.doctors.collectAsStateWithLifecycle()
    val reportMonth by viewModel.reportSelectedMonth.collectAsStateWithLifecycle()
    val reportDocId by viewModel.reportSelectedDoctorId.collectAsStateWithLifecycle()

    var generatedFile by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            "Commission Reports Portal",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Generate doctor commission summaries, download offline PDF receipts, print, or share directly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Report Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                // Select Month field
                var monthDropdownExp by remember { mutableStateOf(false) }
                val monthsList = remember {
                    val list = mutableListOf<Pair<String, String>>()
                    val cal = Calendar.getInstance()
                    val sdfValue = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                    val sdfLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    for (i in 0 until 12) {
                        list.add(sdfValue.format(cal.time) to sdfLabel.format(cal.time))
                        cal.add(Calendar.MONTH, -1)
                    }
                    list
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = monthsList.find { it.first == reportMonth }?.second ?: "Select Month",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Billing Month") },
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { monthDropdownExp = true },
                        enabled = false, // ensures click handler takes over
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { monthDropdownExp = true }
                    )
                    DropdownMenu(
                        expanded = monthDropdownExp,
                        onDismissRequest = { monthDropdownExp = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        monthsList.forEach { (valStr, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.reportSelectedMonth.value = valStr
                                    monthDropdownExp = false
                                }
                            )
                        }
                    }
                }

                // Select Doctor
                var docDropdownExp by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    val activeDocName = if (reportDocId == null) "All Referral Doctors" else doctorsList.find { it.id == reportDocId }?.name ?: "All Referral Doctors"
                    OutlinedTextField(
                        value = activeDocName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Referral Doctor") },
                        leadingIcon = { Icon(Icons.Default.MedicalServices, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { docDropdownExp = true },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { docDropdownExp = true }
                    )
                    DropdownMenu(
                        expanded = docDropdownExp,
                        onDismissRequest = { docDropdownExp = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Referral Doctors") },
                            onClick = {
                                viewModel.reportSelectedDoctorId.value = null
                                docDropdownExp = false
                            }
                        )
                        doctorsList.forEach { doctor ->
                            DropdownMenuItem(
                                text = { Text(doctor.name) },
                                onClick = {
                                    viewModel.reportSelectedDoctorId.value = doctor.id
                                    docDropdownExp = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val file = viewModel.generateDoctorCommissionPdf(context)
                        if (file != null) {
                            generatedFile = file
                            Toast.makeText(context, "PDF Report successfully generated!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Error compiling report. Verify you have entries logged.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Science, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compile & Generate PDF Report", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Report actions banner (View, Print, Share)
        AnimatedVisibility(
            visible = generatedFile != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (generatedFile != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Report successfully compiled!",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            generatedFile!!.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 4.dp),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { openPdfFile(context, generatedFile!!) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open / View", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { printPdfFile(context, generatedFile!!) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Print PDF", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { sharePdfFile(context, generatedFile!!) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SCREEN 5: DOCTORS DIRECTORY ---
@Composable
fun DoctorsScreen(viewModel: MainViewModel) {
    val doctorsList by viewModel.filteredDoctors.collectAsStateWithLifecycle()
    val commissionsMap by viewModel.doctorCommissions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.doctorSearchQuery.collectAsStateWithLifecycle()

    var showAddDoctorDialog by remember { mutableStateOf(false) }
    var doctorToEdit by remember { mutableStateOf<DoctorEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<DoctorEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Title Block
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Referral Doctors Directory",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Manage medical practitioners and view total commissions earned.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Button(
                onClick = { doctorToEdit = null; showAddDoctorDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Register Doctor")
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.doctorSearchQuery.value = it },
            placeholder = { Text("Search doctors by name or specialization...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("doctor_search_input"),
            shape = RoundedCornerShape(12.dp)
        )

        // Doctors list cards
        if (doctorsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MedicalServices, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No doctors registered. Click 'Register Doctor' above to start.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(doctorsList, key = { it.id }) { doctor ->
                    val commissionEarned = commissionsMap[doctor.id] ?: 0.0
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(doctor.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text(doctor.specialization.ifEmpty { "General Pathologist" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    if (doctor.contact.isNotEmpty()) {
                                        Text("Contact: ${doctor.contact}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                }
                            }

                            // Commission Stat and actions
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text("Total Commission", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                        Text("₹${String.format(Locale.getDefault(), "%,.2f", commissionEarned)}", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }

                                Row {
                                    IconButton(
                                        onClick = { doctorToEdit = doctor; showAddDoctorDialog = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { showDeleteConfirmDialog = doctor },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Doctor Dialog
    if (showAddDoctorDialog) {
        AddEditDoctorDialog(
            viewModel = viewModel,
            doctorToEdit = doctorToEdit,
            onDismiss = { showAddDoctorDialog = false }
        )
    }

    // Delete Doctor Confirmation Dialog
    if (showDeleteConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDoctor(showDeleteConfirmDialog!!)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel")
                }
            },
            title = { Text("Unregister Doctor") },
            text = { Text("Are you sure you want to unregister Dr. ${showDeleteConfirmDialog!!.name}? Any pathology entries previously mapped to this doctor will remain but the doctor mapping will be set to inactive.") }
        )
    }
}

// --- SCREEN 6: SETTINGS ---
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeTheme by viewModel.darkThemeMode.collectAsStateWithLifecycle()

    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    var backupCodeToRestore by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                "Laboratory Settings",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Manage security credentials, switch layouts, backup records, or restore database.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // 1. Theme Selection
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Theme Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThemeOptionCard(
                            label = "Light Mode",
                            icon = Icons.Default.LightMode,
                            selected = activeTheme == "light",
                            onClick = { viewModel.setThemeMode("light") },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionCard(
                            label = "Dark Mode",
                            icon = Icons.Default.DarkMode,
                            selected = activeTheme == "dark",
                            onClick = { viewModel.setThemeMode("dark") },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionCard(
                            label = "System Default",
                            icon = Icons.Default.SettingsSuggest,
                            selected = activeTheme == "system",
                            onClick = { viewModel.setThemeMode("system") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Change Security Credentials
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Change Login Credentials", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text("New Username") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("New Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (usernameInput.trim().isEmpty() || passwordInput.trim().isEmpty()) {
                                Toast.makeText(context, "Username and Password cannot be empty!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.changeCredentials(usernameInput, passwordInput)
                                usernameInput = ""
                                passwordInput = ""
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Update Credentials")
                    }
                }
            }
        }

        // 3. Database Backup & Export (JSON Based)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Backup & Export Database", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Generates a complete offline backup code. You can copy this code or share it to move your laboratory entries and doctors securely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val code = viewModel.exportBackupJson()
                                if (code.isNotEmpty()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Lab Backup Code", code)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Backup code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to compile backup", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Backup Code")
                        }

                        Button(
                            onClick = {
                                val code = viewModel.exportBackupJson()
                                if (code.isNotEmpty()) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Micro Pathology Lab Backup")
                                        putExtra(Intent.EXTRA_TEXT, code)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Backup File"))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Backup Code")
                        }
                    }
                }
            }
        }

        // 4. Restore Database from JSON Code
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Restore Database", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Paste a valid JSON backup code below to restore all doctors and billing entries. Note: This will overwrite current entries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    OutlinedTextField(
                        value = backupCodeToRestore,
                        onValueChange = { backupCodeToRestore = it },
                        label = { Text("Paste Backup Code Here") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        maxLines = 10,
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    Button(
                        onClick = {
                            if (backupCodeToRestore.trim().isEmpty()) {
                                Toast.makeText(context, "Please paste a backup code first!", Toast.LENGTH_SHORT).show()
                            } else {
                                val success = viewModel.importBackupJson(backupCodeToRestore)
                                if (success) {
                                    backupCodeToRestore = ""
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.SettingsBackupRestore, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Database")
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeOptionCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .border(
                width = if (selected) 2.dp else 0.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// --- DIALOG COMPONENT: ADD/EDIT DOCTOR ---
@Composable
fun AddEditDoctorDialog(
    viewModel: MainViewModel,
    doctorToEdit: DoctorEntity?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(doctorToEdit?.name ?: "") }
    var specialization by remember { mutableStateOf(doctorToEdit?.specialization ?: "") }
    var contact by remember { mutableStateOf(doctorToEdit?.contact ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (doctorToEdit == null) "Register Doctor" else "Edit Doctor Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Doctor Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = specialization,
                    onValueChange = { specialization = it },
                    label = { Text("Specialization (e.g., MD Pathologist)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = { Text("Contact Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.trim().isEmpty()) {
                                return@Button
                            }
                            if (doctorToEdit == null) {
                                viewModel.addDoctor(name, specialization, contact)
                            } else {
                                viewModel.updateDoctor(doctorToEdit.id, name, specialization, contact)
                            }
                            onDismiss()
                        }
                    ) {
                        Text("Save Details")
                    }
                }
            }
        }
    }
}

// --- DIALOG COMPONENT: ADD/EDIT PATHOLOGY ENTRY ---
@Composable
fun AddEditEntryDialog(
    viewModel: MainViewModel,
    entryToEdit: EntryEntity?,
    onDismiss: () -> Unit
) {
    val doctorsList by viewModel.doctors.collectAsStateWithLifecycle()

    var dateMillis by remember { mutableStateOf(entryToEdit?.dateMillis ?: System.currentTimeMillis()) }
    var patientName by remember { mutableStateOf(entryToEdit?.patientName ?: "") }
    var ageStr by remember { mutableStateOf(entryToEdit?.age?.toString() ?: "") }
    var test by remember { mutableStateOf(entryToEdit?.test ?: "") }
    var amountStr by remember { mutableStateOf(entryToEdit?.amount?.toString() ?: "") }
    var doctorAmountStr by remember { mutableStateOf(entryToEdit?.doctorAmount?.toString() ?: "") }
    var otherAmountStr by remember { mutableStateOf(entryToEdit?.otherAmount?.toString() ?: "") }

    // Doctor referral selection
    var selectedDocId by remember { mutableStateOf<Int?>(entryToEdit?.doctorId) }
    var selectedDocName by remember { mutableStateOf(entryToEdit?.doctorName ?: "Self / None") }

    // Standard Android calendar date picker trigger
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = dateMillis

    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            dateMillis = cal.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (entryToEdit == null) "New Pathology Entry" else "Modify Entry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Date Picker trigger field
                val formattedDate = remember(dateMillis) {
                    SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(dateMillis))
                }
                OutlinedTextField(
                    value = formattedDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date *") },
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { datePickerDialog.show() }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Choose Date")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // Patient Name
                OutlinedTextField(
                    value = patientName,
                    onValueChange = { patientName = it },
                    label = { Text("Patient Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("patient_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Age
                    OutlinedTextField(
                        value = ageStr,
                        onValueChange = { ageStr = it },
                        label = { Text("Age *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("age_input")
                    )

                    // Test Name
                    OutlinedTextField(
                        value = test,
                        onValueChange = { test = it },
                        label = { Text("Test Name *") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(2f)
                            .testTag("test_input")
                    )
                }

                // Billing info row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Total Bill (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("amount_input")
                    )

                    OutlinedTextField(
                        value = doctorAmountStr,
                        onValueChange = { doctorAmountStr = it },
                        label = { Text("Doc Payout (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("doc_payout_input")
                    )

                    OutlinedTextField(
                        value = otherAmountStr,
                        onValueChange = { otherAmountStr = it },
                        label = { Text("Other Exp (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("other_exp_input")
                    )
                }

                // Referral Doctor Dropdown selection
                var docDropdownExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedDocName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Referral Doctor") },
                        leadingIcon = { Icon(Icons.Default.MedicalServices, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { docDropdownExpanded = true },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { docDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = docDropdownExpanded,
                        onDismissRequest = { docDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Self / None") },
                            onClick = {
                                selectedDocId = null
                                selectedDocName = "Self / None"
                                docDropdownExpanded = false
                            }
                        )
                        doctorsList.forEach { doctor ->
                            DropdownMenuItem(
                                text = { Text(doctor.name) },
                                onClick = {
                                    selectedDocId = doctor.id
                                    selectedDocName = doctor.name
                                    docDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val patientNameVal = patientName.trim()
                            val testVal = test.trim()
                            val ageVal = ageStr.toIntOrNull() ?: 0
                            val amountVal = amountStr.toDoubleOrNull() ?: 0.0
                            val doctorAmountVal = doctorAmountStr.toDoubleOrNull() ?: 0.0
                            val otherAmountVal = otherAmountStr.toDoubleOrNull() ?: 0.0

                            if (patientNameVal.isEmpty() || testVal.isEmpty() || ageVal <= 0 || amountVal <= 0.0) {
                                Toast.makeText(context, "Please complete all mandatory (*) fields with valid data", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (entryToEdit == null) {
                                viewModel.addEntry(
                                    dateMillis = dateMillis,
                                    patientName = patientNameVal,
                                    age = ageVal,
                                    test = testVal,
                                    amount = amountVal,
                                    doctorAmount = doctorAmountVal,
                                    otherAmount = otherAmountVal,
                                    doctorId = selectedDocId,
                                    doctorName = selectedDocName
                                )
                            } else {
                                viewModel.updateEntry(
                                    id = entryToEdit.id,
                                    dateMillis = dateMillis,
                                    patientName = patientNameVal,
                                    age = ageVal,
                                    test = testVal,
                                    amount = amountVal,
                                    doctorAmount = doctorAmountVal,
                                    otherAmount = otherAmountVal,
                                    doctorId = selectedDocId,
                                    doctorName = selectedDocName
                                )
                            }
                            onDismiss()
                        },
                        modifier = Modifier.testTag("save_entry_button")
                    ) {
                        Text("Save Entry")
                    }
                }
            }
        }
    }
}
