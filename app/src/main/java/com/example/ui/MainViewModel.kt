package com.example.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DoctorEntity
import com.example.data.EntryEntity
import com.example.data.LabRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainViewModel(private val repository: LabRepository) : ViewModel() {

    // --- Authentication State ---
    val loginUsername = MutableStateFlow("")
    val loginPassword = MutableStateFlow("")
    val loginError = MutableStateFlow<String?>(null)
    val isLoggedIn = MutableStateFlow(false)

    // Current Credentials (cached from DB)
    private var actualUsername = "admin"
    private var actualPassword = "admin"

    // --- Navigation State ---
    // Screens: "login", "home", "entries", "reports", "doctors", "settings"
    val currentScreen = MutableStateFlow("login")

    // --- Toast / Message Stream ---
    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    // --- Theme State ---
    val darkThemeMode = repository.getThemeFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "light"
    )

    // --- Core Data Flows ---
    val doctors: StateFlow<List<DoctorEntity>> = repository.allDoctorsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val entries: StateFlow<List<EntryEntity>> = repository.allEntriesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Filtering, Searching & Sorting State ---
    val entrySearchQuery = MutableStateFlow("")
    val entrySelectedMonth = MutableStateFlow("") // format "YYYY-MM" (e.g. "2026-06"). Empty means All
    // Sort column: "date", "name", "age", "test", "amount", "doctor", "other"
    val entrySortColumn = MutableStateFlow("date")
    val entrySortAscending = MutableStateFlow(false)

    // Initialize with current month
    init {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        entrySelectedMonth.value = sdf.format(Date())

        viewModelScope.launch {
            actualUsername = repository.getUsername()
            actualPassword = repository.getPassword()
        }
    }

    // --- Filtered and Sorted Entries Flow ---
    val filteredEntries: StateFlow<List<EntryEntity>> = combine(
        entries,
        entrySearchQuery,
        entrySelectedMonth,
        entrySortColumn,
        entrySortAscending
    ) { rawEntries, query, monthFilter, sortCol, sortAsc ->
        var list = rawEntries

        // 1. Month Filter (e.g. YYYY-MM)
        if (monthFilter.isNotEmpty()) {
            list = list.filter { it.dateStr.startsWith(monthFilter) }
        }

        // 2. Search Query (Patient Name, Test, Doctor Name)
        if (query.isNotEmpty()) {
            list = list.filter {
                it.patientName.contains(query, ignoreCase = true) ||
                        it.test.contains(query, ignoreCase = true) ||
                        it.doctorName.contains(query, ignoreCase = true)
            }
        }

        // 3. Sorting
        list = when (sortCol) {
            "name" -> if (sortAsc) list.sortedBy { it.patientName.lowercase() } else list.sortedByDescending { it.patientName.lowercase() }
            "age" -> if (sortAsc) list.sortedBy { it.age } else list.sortedByDescending { it.age }
            "test" -> if (sortAsc) list.sortedBy { it.test.lowercase() } else list.sortedByDescending { it.test.lowercase() }
            "amount" -> if (sortAsc) list.sortedBy { it.amount } else list.sortedByDescending { it.amount }
            "doctor" -> if (sortAsc) list.sortedBy { it.doctorAmount } else list.sortedByDescending { it.doctorAmount }
            "other" -> if (sortAsc) list.sortedBy { it.otherAmount } else list.sortedByDescending { it.otherAmount }
            else -> if (sortAsc) list.sortedBy { it.dateMillis } else list.sortedByDescending { it.dateMillis } // "date" default
        }

        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Calculated Totals (Reactive over Filtered List) ---
    val totalEntriesCount = filteredEntries.mapState { it.size }
    val totalAmount = filteredEntries.mapState { it.sumOf { entry -> entry.amount } }
    val totalDoctorAmount = filteredEntries.mapState { it.sumOf { entry -> entry.doctorAmount } }
    val totalOtherAmount = filteredEntries.mapState { it.sumOf { entry -> entry.otherAmount } }

    // Extension to convert StateFlow of list to StateFlow of calculated property
    private fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> {
        val initial = transform(this.value)
        val flow = this.map { transform(it) }
        return flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)
    }

    // --- Doctors Page Logic ---
    val doctorSearchQuery = MutableStateFlow("")
    val filteredDoctors: StateFlow<List<DoctorEntity>> = combine(
        doctors,
        doctorSearchQuery
    ) { rawDoctors, query ->
        if (query.isEmpty()) {
            rawDoctors
        } else {
            rawDoctors.filter { it.name.contains(query, ignoreCase = true) || it.specialization.contains(query, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Precalculate commission maps for Doctors Page
    val doctorCommissions: StateFlow<Map<Int, Double>> = entries.mapState { list ->
        val map = mutableMapOf<Int, Double>()
        for (entry in list) {
            val dId = entry.doctorId
            if (dId != null) {
                map[dId] = (map[dId] ?: 0.0) + entry.doctorAmount
            }
        }
        map
    }

    // --- Reports Page Logic ---
    val reportSelectedMonth = MutableStateFlow("") // format "YYYY-MM" (e.g. "2026-06")
    val reportSelectedDoctorId = MutableStateFlow<Int?>(null) // null = All Doctors

    init {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        reportSelectedMonth.value = sdf.format(Date())
    }

    // --- Authentication Actions ---
    fun performLogin() {
        viewModelScope.launch {
            val user = loginUsername.value.trim()
            val pass = loginPassword.value

            actualUsername = repository.getUsername()
            actualPassword = repository.getPassword()

            if (user == actualUsername && pass == actualPassword) {
                isLoggedIn.value = true
                loginError.value = null
                currentScreen.value = "home"
                _uiMessage.emit("Welcome to Micro Pathology Lab!")
            } else {
                loginError.value = "Invalid Username or Password"
            }
        }
    }

    fun performLogout() {
        isLoggedIn.value = false
        loginUsername.value = ""
        loginPassword.value = ""
        currentScreen.value = "login"
    }

    // --- Entries Actions ---
    fun addEntry(
        dateMillis: Long,
        patientName: String,
        age: Int,
        test: String,
        amount: Double,
        doctorAmount: Double,
        otherAmount: Double,
        doctorId: Int?,
        doctorName: String
    ) {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateStr = sdf.format(Date(dateMillis))

            val newEntry = EntryEntity(
                dateMillis = dateMillis,
                dateStr = dateStr,
                patientName = patientName.trim(),
                age = age,
                test = test.trim(),
                amount = amount,
                doctorAmount = doctorAmount,
                otherAmount = otherAmount,
                doctorId = doctorId,
                doctorName = doctorName
            )
            repository.insertEntry(newEntry)
            _uiMessage.emit("Entry added successfully")
        }
    }

    fun updateEntry(
        id: Int,
        dateMillis: Long,
        patientName: String,
        age: Int,
        test: String,
        amount: Double,
        doctorAmount: Double,
        otherAmount: Double,
        doctorId: Int?,
        doctorName: String
    ) {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateStr = sdf.format(Date(dateMillis))

            val updated = EntryEntity(
                id = id,
                dateMillis = dateMillis,
                dateStr = dateStr,
                patientName = patientName.trim(),
                age = age,
                test = test.trim(),
                amount = amount,
                doctorAmount = doctorAmount,
                otherAmount = otherAmount,
                doctorId = doctorId,
                doctorName = doctorName
            )
            repository.updateEntry(updated)
            _uiMessage.emit("Entry updated successfully")
        }
    }

    fun deleteEntry(entry: EntryEntity) {
        viewModelScope.launch {
            repository.deleteEntry(entry)
            _uiMessage.emit("Entry deleted successfully")
        }
    }

    // --- Doctors Actions ---
    fun addDoctor(name: String, specialization: String, contact: String) {
        viewModelScope.launch {
            if (name.trim().isEmpty()) {
                _uiMessage.emit("Doctor name cannot be empty")
                return@launch
            }
            val newDoc = DoctorEntity(
                name = name.trim(),
                specialization = specialization.trim(),
                contact = contact.trim()
            )
            repository.insertDoctor(newDoc)
            _uiMessage.emit("Doctor registered successfully")
        }
    }

    fun updateDoctor(id: Int, name: String, specialization: String, contact: String) {
        viewModelScope.launch {
            if (name.trim().isEmpty()) {
                _uiMessage.emit("Doctor name cannot be empty")
                return@launch
            }
            val updated = DoctorEntity(
                id = id,
                name = name.trim(),
                specialization = specialization.trim(),
                contact = contact.trim()
            )
            repository.updateDoctor(updated)
            _uiMessage.emit("Doctor updated successfully")
        }
    }

    fun deleteDoctor(doctor: DoctorEntity) {
        viewModelScope.launch {
            // First, update any entry associated with this doctor so doctorId is null
            val entriesToUpdate = repository.getAllEntries().filter { it.doctorId == doctor.id }
            for (entry in entriesToUpdate) {
                repository.updateEntry(entry.copy(doctorId = null, doctorName = "${entry.doctorName} (Inactive)"))
            }
            repository.deleteDoctor(doctor)
            _uiMessage.emit("Doctor deleted successfully")
        }
    }

    // --- Settings Actions ---
    fun changeCredentials(user: String, pass: String) {
        viewModelScope.launch {
            if (user.trim().isEmpty() || pass.trim().isEmpty()) {
                _uiMessage.emit("Credentials cannot be empty")
                return@launch
            }
            repository.updateCredentials(user.trim(), pass)
            actualUsername = user.trim()
            actualPassword = pass
            _uiMessage.emit("Credentials updated successfully")
        }
    }

    fun setThemeMode(theme: String) {
        viewModelScope.launch {
            repository.updateTheme(theme)
            _uiMessage.emit("Theme updated to ${theme.replaceFirstChar { it.uppercase() }}")
        }
    }

    // --- Backup & Restore (JSON Based) ---
    fun exportBackupJson(): String {
        return try {
            val entriesList = entries.value
            val doctorsList = doctors.value

            val backupObj = JSONObject()

            // Map entries
            val entriesArr = JSONArray()
            for (e in entriesList) {
                val obj = JSONObject().apply {
                    put("id", e.id)
                    put("dateMillis", e.dateMillis)
                    put("dateStr", e.dateStr)
                    put("patientName", e.patientName)
                    put("age", e.age)
                    put("test", e.test)
                    put("amount", e.amount)
                    put("doctorAmount", e.doctorAmount)
                    put("otherAmount", e.otherAmount)
                    put("doctorId", e.doctorId ?: JSONObject.NULL)
                    put("doctorName", e.doctorName)
                }
                entriesArr.put(obj)
            }
            backupObj.put("entries", entriesArr)

            // Map doctors
            val doctorsArr = JSONArray()
            for (d in doctorsList) {
                val obj = JSONObject().apply {
                    put("id", d.id)
                    put("name", d.name)
                    put("specialization", d.specialization)
                    put("contact", d.contact)
                }
                doctorsArr.put(obj)
            }
            backupObj.put("doctors", doctorsArr)
            backupObj.put("timestamp", System.currentTimeMillis())

            backupObj.toString(4) // pretty print
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun importBackupJson(jsonStr: String): Boolean {
        return try {
            val backupObj = JSONObject(jsonStr)

            val entriesArr = backupObj.getJSONArray("entries")
            val doctorsArr = backupObj.getJSONArray("doctors")

            val parsedDoctors = mutableListOf<DoctorEntity>()
            for (i in 0 until doctorsArr.length()) {
                val obj = doctorsArr.getJSONObject(i)
                parsedDoctors.add(
                    DoctorEntity(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        specialization = obj.optString("specialization", ""),
                        contact = obj.optString("contact", "")
                    )
                )
            }

            val parsedEntries = mutableListOf<EntryEntity>()
            for (i in 0 until entriesArr.length()) {
                val obj = entriesArr.getJSONObject(i)
                val docId = if (obj.isNull("doctorId")) null else obj.getInt("doctorId")
                parsedEntries.add(
                    EntryEntity(
                        id = obj.getInt("id"),
                        dateMillis = obj.getLong("dateMillis"),
                        dateStr = obj.getString("dateStr"),
                        patientName = obj.getString("patientName"),
                        age = obj.getInt("age"),
                        test = obj.getString("test"),
                        amount = obj.getDouble("amount"),
                        doctorAmount = obj.getDouble("doctorAmount"),
                        otherAmount = obj.getDouble("otherAmount"),
                        doctorId = docId,
                        doctorName = obj.getString("doctorName")
                    )
                )
            }

            viewModelScope.launch {
                repository.clearAllAndRestore(parsedEntries, parsedDoctors)
                _uiMessage.emit("Backup restored successfully!")
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            viewModelScope.launch {
                _uiMessage.emit("Failed to restore backup: Invalid format")
            }
            false
        }
    }

    // --- PDF Generation System ---
    // Generates the doctor-wise commission report PDF and returns the saved File object
    fun generateDoctorCommissionPdf(context: Context): File? {
        try {
            val monthFilter = reportSelectedMonth.value
            val selectedDocId = reportSelectedDoctorId.value

            val allEntries = entries.value
            val docList = doctors.value

            // Filter entries for the report
            var filtered = allEntries
            if (monthFilter.isNotEmpty()) {
                filtered = filtered.filter { it.dateStr.startsWith(monthFilter) }
            }

            val doctorName = if (selectedDocId != null) {
                val found = docList.find { it.id == selectedDocId }
                filtered = filtered.filter { it.doctorId == selectedDocId }
                found?.name ?: "Unknown"
            } else {
                "All Doctors"
            }

            // Create PDF
            val pdfDocument = PdfDocument()
            // Standard A4 Size: 595 width x 842 height points
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            // Paints
            val textPaint = Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 10f
                isAntiAlias = true
            }

            val headerPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#006064") // Dark Teal
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subheaderPaint = Paint().apply {
                color = android.graphics.Color.DKGRAY
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
            }

            val boldPaint = Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val tableHeaderPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#E0F2F1") // Light Teal accent
                isAntiAlias = true
            }

            // Draw Header
            canvas.drawText("MICRO PATHOLOGY LAB", 40f, 50f, headerPaint)
            canvas.drawText("Doctor-wise Commission & Business Report", 40f, 70f, subheaderPaint)

            // Draw Metadata
            canvas.drawText("Report Month: $monthFilter", 40f, 100f, textPaint)
            canvas.drawText("Doctor: $doctorName", 40f, 115f, textPaint)
            canvas.drawText("Generated On: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}", 350f, 100f, textPaint)

            // Draw Decorative line
            val dividerPaint = Paint().apply {
                color = android.graphics.Color.GRAY
                strokeWidth = 1f
            }
            canvas.drawLine(40f, 130f, 555f, 130f, dividerPaint)

            // Draw Table Headers
            val startY = 150f
            val colDate = 40f
            val colPatient = 110f
            val colTest = 230f
            val colAmount = 350f
            val colComm = 430f
            val colOther = 500f

            // Table header background
            canvas.drawRect(40f, startY - 15, 555f, startY + 5, tableHeaderPaint)
            canvas.drawText("Date", colDate, startY, boldPaint)
            canvas.drawText("Patient Name", colPatient, startY, boldPaint)
            canvas.drawText("Test", colTest, startY, boldPaint)
            canvas.drawText("Amount (₹)", colAmount, startY, boldPaint)
            canvas.drawText("Comm (₹)", colComm, startY, boldPaint)
            canvas.drawText("Other (₹)", colOther, startY, boldPaint)

            var currentY = startY + 22f
            var pageIndex = 1

            // Totals
            var sumAmount = 0.0
            var sumComm = 0.0
            var sumOther = 0.0

            for (e in filtered) {
                // Check if page overflow (keep simple single or limited page representation)
                if (currentY > 750f) {
                    // Start next page in production apps, for simplicity draw indicator
                    canvas.drawText("... continued on next page ...", 220f, currentY, subheaderPaint)
                    break
                }

                // Simple date format conversion for display (e.g. 2026-06-30 -> 30-Jun)
                val displayDate = try {
                    val pSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val d = pSdf.parse(e.dateStr)
                    SimpleDateFormat("dd-MMM", Locale.getDefault()).format(d!!)
                } catch (ex: Exception) {
                    e.dateStr
                }

                canvas.drawText(displayDate, colDate, currentY, textPaint)
                // Truncate name if too long
                val truncatedName = if (e.patientName.length > 18) e.patientName.take(16) + ".." else e.patientName
                canvas.drawText(truncatedName, colPatient, currentY, textPaint)
                val truncatedTest = if (e.test.length > 18) e.test.take(16) + ".." else e.test
                canvas.drawText(truncatedTest, colTest, currentY, textPaint)
                canvas.drawText(String.format(Locale.getDefault(), "%.2f", e.amount), colAmount, currentY, textPaint)
                canvas.drawText(String.format(Locale.getDefault(), "%.2f", e.doctorAmount), colComm, currentY, textPaint)
                canvas.drawText(String.format(Locale.getDefault(), "%.2f", e.otherAmount), colOther, currentY, textPaint)

                sumAmount += e.amount
                sumComm += e.doctorAmount
                sumOther += e.otherAmount

                // Draw thin row line
                canvas.drawLine(40f, currentY + 4, 555f, currentY + 4, Paint().apply {
                    color = android.graphics.Color.parseColor("#E0E0E0")
                    strokeWidth = 0.5f
                })

                currentY += 18f
            }

            // Draw Totals section
            currentY += 10f
            canvas.drawLine(40f, currentY, 555f, currentY, dividerPaint)
            currentY += 15f
            canvas.drawText("TOTALS", colDate, currentY, boldPaint)
            canvas.drawText(String.format(Locale.getDefault(), "₹%.2f", sumAmount), colAmount, currentY, boldPaint)
            canvas.drawText(String.format(Locale.getDefault(), "₹%.2f", sumComm), colComm, currentY, boldPaint)
            canvas.drawText(String.format(Locale.getDefault(), "₹%.2f", sumOther), colOther, currentY, boldPaint)

            // Summary Info box
            currentY += 35f
            canvas.drawRect(40f, currentY, 555f, currentY + 80f, Paint().apply {
                color = android.graphics.Color.parseColor("#F5F5F5")
                isAntiAlias = true
            })

            val summaryTextPaint = Paint().apply {
                color = android.graphics.Color.DKGRAY
                textSize = 9f
                isAntiAlias = true
            }

            canvas.drawText("Summary Statistics:", 50f, currentY + 18f, boldPaint)
            canvas.drawText("Total Patients Logged: ${filtered.size}", 50f, currentY + 35f, summaryTextPaint)
            canvas.drawText("Total Bill Value: ₹${String.format(Locale.getDefault(), "%.2f", sumAmount)}", 50f, currentY + 50f, summaryTextPaint)
            canvas.drawText("Total Doctor Commission (Payout): ₹${String.format(Locale.getDefault(), "%.2f", sumComm)}", 280f, currentY + 35f, summaryTextPaint)
            canvas.drawText("Total Other Expenses: ₹${String.format(Locale.getDefault(), "%.2f", sumOther)}", 280f, currentY + 50f, summaryTextPaint)

            val netProfit = sumAmount - sumComm - sumOther
            val netProfitPaint = Paint().apply {
                color = if (netProfit >= 0) android.graphics.Color.parseColor("#1B5E20") else android.graphics.Color.RED
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("Net Lab Revenue: ₹${String.format(Locale.getDefault(), "%.2f", netProfit)}", 50f, currentY + 70f, netProfitPaint)

            // Signature Footer
            canvas.drawText("Authorized Signatory", 400f, currentY + 120f, boldPaint)
            canvas.drawLine(380f, currentY + 105f, 520f, currentY + 105f, dividerPaint)

            pdfDocument.finishPage(page)

            // Save PDF to cache dir (which doesn't require extra permission)
            val pdfDir = File(context.cacheDir, "reports")
            if (!pdfDir.exists()) {
                pdfDir.mkdirs()
            }
            val fileName = "Commission_Report_${monthFilter}_${doctorName.replace(" ", "_")}.pdf"
            val pdfFile = File(pdfDir, fileName)

            val fos = FileOutputStream(pdfFile)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            fos.close()

            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
