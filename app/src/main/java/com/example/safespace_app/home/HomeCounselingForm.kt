package com.example.safespace_app.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.widget.CompoundButtonCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class HomeCounselingForm : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var formContainer: LinearLayout

    // Store selected preferred schedules (up to 3 date/time slots)
    private val selectedSchedules = mutableListOf<ScheduleSlot>()

    /** Stores rendered views keyed by questionId */
    private val answerViews = mutableMapOf<String, View>()

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    data class ScheduleSlot(
        val date: Date,
        val startTime: String, // Format: "HH:mm"
        val endTime: String    // Format: "HH:mm"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_home_counseling_form, container, false)

        val prefs = requireContext().getSharedPreferences("user_cache", Context.MODE_PRIVATE)

        rootView.findViewById<TextView>(R.id.fname).text = prefs.getString("fname", "")
        rootView.findViewById<TextView>(R.id.lname).text = prefs.getString("lname", "")
        rootView.findViewById<TextView>(R.id.program).text = prefs.getString("program", "")
        rootView.findViewById<TextView>(R.id.student_id).text = prefs.getString("studentId", "")

        formContainer = rootView.findViewById(R.id.formContainer)

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadFormDefinition()

        // Setup preferred schedule picker
        setupPreferredSchedulePicker(view)

        view.findViewById<MaterialButton>(R.id.btnsubmit).setOnClickListener {
            submitForm()
        }
    }

    /* ------------------------------------------------------------------ */
    /* ------------------- PREFERRED SCHEDULE PICKER ---------------------- */
    /* ------------------------------------------------------------------ */

    private fun setupPreferredSchedulePicker(view: View) {
        val prefSchedView = view.findViewById<TextInputEditText>(R.id.PrefSched)

        // Make it read-only and clickable
        prefSchedView?.apply {
            isFocusable = false
            isClickable = true
            hint = "Tap to select preferred schedule"

            setOnClickListener {
                showSchedulePickerDialog()
            }
        }

        // Update display
        updateScheduleDisplay()
    }

    private fun showSchedulePickerDialog() {
        if (selectedSchedules.size >= 3) {
            Toast.makeText(requireContext(), "You can only select up to 3 schedule slots", Toast.LENGTH_SHORT).show()
            return
        }

        // Show date picker first
        showDatePicker { selectedDate ->
            // Then show time picker for start time
            showTimePicker("Select Start Time", 8, 0) { startHour, startMinute ->
                // Then show time picker for end time
                showTimePicker("Select End Time", startHour, startMinute) { endHour, endMinute ->
                    val startTime = String.format("%02d:%02d", startHour, startMinute)
                    val endTime = String.format("%02d:%02d", endHour, endMinute)

                    // Validate time range
                    if (endHour < startHour || (endHour == startHour && endMinute <= startMinute)) {
                        Toast.makeText(requireContext(), "End time must be after start time", Toast.LENGTH_SHORT).show()
                        return@showTimePicker
                    }

                    // Add the schedule slot
                    selectedSchedules.add(ScheduleSlot(selectedDate, startTime, endTime))
                    updateScheduleDisplay()

                    // Ask if user wants to add more
                    if (selectedSchedules.size < 3) {
                        android.app.AlertDialog.Builder(requireContext())
                            .setTitle("Add Another Schedule?")
                            .setMessage("You have selected ${selectedSchedules.size} of 3 possible schedule slots. Would you like to add another?")
                            .setPositiveButton("Yes") { _, _ -> showSchedulePickerDialog() }
                            .setNegativeButton("No", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun showDatePicker(onDateSelected: (Date) -> Unit) {
        val calendar = Calendar.getInstance()
        val today = calendar.clone() as Calendar

        // Calculate one week from today
        val weekFromToday = calendar.clone() as Calendar
        weekFromToday.add(Calendar.DAY_OF_YEAR, 7)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance()
                selectedCal.set(year, month, dayOfMonth)

                // Validate: no weekends
                val dayOfWeek = selectedCal.get(Calendar.DAY_OF_WEEK)
                if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                    Toast.makeText(requireContext(), "Please select a weekday (Monday-Friday)", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }

                onDateSelected(selectedCal.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Disable past dates
        datePickerDialog.datePicker.minDate = today.timeInMillis

        // Disable dates beyond one week
        datePickerDialog.datePicker.maxDate = weekFromToday.timeInMillis

        datePickerDialog.show()
    }

    private fun showTimePicker(title: String, defaultHour: Int, defaultMinute: Int, onTimeSelected: (Int, Int) -> Unit) {
        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                // Validate time is between 8 AM and 5 PM
                if (hourOfDay < 8 || hourOfDay > 17 || (hourOfDay == 17 && minute > 0)) {
                    Toast.makeText(requireContext(), "Please select a time between 8:00 AM and 5:00 PM", Toast.LENGTH_SHORT).show()
                    return@TimePickerDialog
                }
                onTimeSelected(hourOfDay, minute)
            },
            defaultHour,
            defaultMinute,
            false // Use 12-hour format
        )

        timePickerDialog.setTitle(title)
        timePickerDialog.show()
    }

    private fun updateScheduleDisplay() {
        val prefSchedView = view?.findViewById<TextInputEditText>(R.id.PrefSched)

        if (selectedSchedules.isEmpty()) {
            prefSchedView?.setText("")
            return
        }

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val displayText = selectedSchedules.mapIndexed { index, slot ->
            val dateStr = dateFormat.format(slot.date)
            val startTime = formatTimeTo12Hour(slot.startTime)
            val endTime = formatTimeTo12Hour(slot.endTime)
            "${index + 1}. $dateStr, $startTime - $endTime"
        }.joinToString("\n")

        prefSchedView?.setText(displayText)
    }

    private fun formatTimeTo12Hour(time24: String): String {
        val parts = time24.split(":")
        var hour = parts[0].toInt()
        val minute = parts[1]
        val amPm = if (hour >= 12) "PM" else "AM"

        if (hour == 0) hour = 12
        else if (hour > 12) hour -= 12

        return "$hour:$minute $amPm"
    }

    /* ------------------------------------------------------------------ */
    /* -------------------- LOAD & RENDER FORM ---------------------------- */
    /* ------------------------------------------------------------------ */

    private fun loadFormDefinition() {
        firestore.collection("CounselingForm")
            .document("RequestForm_Format")
            .get()
            .addOnSuccessListener { doc ->
                val questions = doc.get("questions") as? List<Map<String, Any>> ?: return@addOnSuccessListener
                renderQuestions(questions)
            }
    }

    private fun renderQuestions(questions: List<Map<String, Any>>) {
        formContainer.removeAllViews()

        questions.forEach { q ->
            val id = q["id"] as String
            val questionText = q["text"] as String
            val type = q["type"] as String
            val options = q["options"] as? List<String>

            // ---------- LABEL (matches old UI) ----------
            val label = TextView(requireContext()).apply {
                this.text = questionText
                setTextColor(resources.getColor(R.color.black, null))
                textSize = resources.getDimension(R.dimen.small) / resources.displayMetrics.scaledDensity
                typeface = resources.getFont(R.font.psbold)
                setPadding(0, dp(15), 0, dp(8))
            }
            formContainer.addView(label)

            when (type) {

                // ---------- SHORT / PARAGRAPH ----------
                "short", "paragraph" -> {
                    val input = TextInputEditText(requireContext()).apply {
                        hint = "Enter your Answer"
                        background = resources.getDrawable(R.drawable.f_transparent, null)
                        typeface = resources.getFont(R.font.ps)
                        textSize = resources.getDimension(R.dimen.reg) / resources.displayMetrics.scaledDensity
                        minLines = if (type == "paragraph") 4 else 1
                    }

                    val wrapper = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                        background = resources.getDrawable(R.drawable.f_rounded_white, null)
                        elevation = dp(1).toFloat()
                        setPadding(dp(10), dp(4), dp(10), dp(4))
                        addView(input)
                    }

                    formContainer.addView(wrapper)
                    answerViews[id] = input
                }

                // ---------- RADIO GROUP (multiple) ----------
                "multiple" -> {
                    val group = RadioGroup(requireContext()).apply {
                        orientation = RadioGroup.VERTICAL
                    }

                    options?.forEach { opt ->
                        val rb = RadioButton(requireContext()).apply {
                            text = opt
                            typeface = resources.getFont(R.font.ps)
                            textSize = resources.getDimension(R.dimen.reg) / resources.displayMetrics.scaledDensity
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                dp(40)
                            )
                            setPadding(dp(8), 0, 0, 0)
                        }
                        // tint the radio button circle so it is visible regardless of theme
                        val tint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.black))
                        CompoundButtonCompat.setButtonTintList(rb, tint)

                        group.addView(rb)
                    }

                    formContainer.addView(group)
                    answerViews[id] = group
                }

                // ---------- CHECKBOX ----------
                "checkbox" -> {
                    val layout = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                    }

                    options?.forEach { opt ->
                        val cb = CheckBox(requireContext()).apply {
                            text = opt
                            typeface = resources.getFont(R.font.ps)
                            textSize = resources.getDimension(R.dimen.reg) / resources.displayMetrics.scaledDensity
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                dp(40)
                            )
                            setPadding(dp(8), 0, 0, 0)
                        }
                        // tint the checkbox box so it is visible
                        val tint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.black))
                        CompoundButtonCompat.setButtonTintList(cb, tint)

                        layout.addView(cb)
                    }

                    formContainer.addView(layout)
                    answerViews[id] = layout
                }

                // ---------- DATETIME ----------
                "datetime" -> {
                    val input = TextInputEditText(requireContext()).apply {
                        hint = "Enter your desired date"
                        background = resources.getDrawable(R.drawable.f_transparent, null)
                        typeface = resources.getFont(R.font.ps)
                        textSize = resources.getDimension(R.dimen.reg) / resources.displayMetrics.scaledDensity
                        setPadding(dp(8), dp(12), dp(8), dp(12))
                    }
                    val wrapper = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                        background = resources.getDrawable(R.drawable.f_rounded_white, null)
                        elevation = dp(1).toFloat()
                        setPadding(dp(10), dp(4), dp(10), dp(4))
                        addView(input)
                    }

                    formContainer.addView(wrapper)
                    answerViews[id] = input
                }



                // ---------- DROPDOWN ----------
                "dropdown" -> {
                    val spinner = Spinner(requireContext())
                    spinner.adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        options ?: emptyList()
                    )
                    formContainer.addView(spinner)
                    answerViews[id] = spinner
                }

            }
        }
    }



    /* ------------------------------------------------------------------ */
    /* -------------------------- SUBMISSION ------------------------------ */
    /* ------------------------------------------------------------------ */

    private fun submitForm() {
        // Fetch the form definition to get question metadata
        firestore.collection("CounselingForm")
            .document("RequestForm_Format")
            .get()
            .addOnSuccessListener { doc ->

                val questionsDef = doc.get("questions") as? List<Map<String, Any>> ?: emptyList()
                val title = doc.getString("title") ?: "Counseling Form"

                val answeredQuestions = mutableListOf<Map<String, Any>>()

                // 1️⃣ Get the static PrefPlat RadioGroup from XML
                val prefPlatView = view?.findViewById<RadioGroup>(R.id.PrefPlat)
                val preferredPlatform = prefPlatView?.let {
                    val checkedId = it.checkedRadioButtonId
                    val rb = it.findViewById<RadioButton>(checkedId)
                    rb?.text?.toString()?.trim() ?: ""
                } ?: ""

                // Validate preferred schedule
                if (selectedSchedules.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Please select at least one preferred schedule slot.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                if (preferredPlatform.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Please select your preferred platform.",
                        Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Ensure a stable non-null identifier for savedBy/createdBy
                val currentUid: String = auth.currentUser?.uid ?: ""

                // 2️⃣ Loop through dynamic questions and check required
                for (q in questionsDef) {
                    val id = q["id"] as String
                    val type = q["type"] as String
                    val text = q["text"] as? String ?: ""
                    val qDescription = q["description"] as? String ?: ""
                    val options = q["options"] as? List<String> ?: emptyList()

                    val view = answerViews[id]
                    val answer: Any = when (view) {
                        is TextInputEditText -> view.text?.toString().orEmpty()
                        is RadioGroup -> {
                            val checked = view.checkedRadioButtonId
                            val rb = view.findViewById<RadioButton?>(checked)
                            rb?.text?.toString().orEmpty()
                        }
                        is LinearLayout -> { // checkbox
                            val selected = mutableListOf<String>()
                            for (i in 0 until view.childCount) {
                                val cb = view.getChildAt(i)
                                if (cb is CheckBox && cb.isChecked) selected.add(cb.text.toString())
                            }
                            selected
                        }
                        is Spinner -> view.selectedItem?.toString().orEmpty()
                        else -> ""
                    }

                    // ✅ Make each dynamic field required
                    val isEmpty = when (answer) {
                        is String -> answer.isEmpty()
                        is List<*> -> answer.isEmpty()
                        else -> false
                    }
                    if (isEmpty) {
                        Toast.makeText(requireContext(), "Please fill all required fields.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    // Build question map
                    val questionMap = hashMapOf<String, Any>(
                        "id" to id,
                        "text" to text,
                        "type" to type,
                        "description" to qDescription,
                        "options" to options,
                        "savedBy" to currentUid,
                        "answer" to answer
                    )
                    answeredQuestions.add(questionMap)
                }

                // Convert schedules to Firebase-friendly format
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val preferredScheduleData = selectedSchedules.map { slot ->
                    hashMapOf(
                        "date" to dateFormat.format(slot.date),
                        "startTime" to slot.startTime,
                        "endTime" to slot.endTime
                    )
                }

                // Build submission object
                val submission = hashMapOf<String, Any>(
                    "title" to title,
                    "questions" to answeredQuestions,
                    "preferredPlatform" to preferredPlatform,
                    "preferredSchedule" to preferredScheduleData,
                    "createdAt" to Timestamp.now(),
                    "createdBy" to currentUid
                )

                // Submit to Firestore
                firestore.collection("CounselingForm_Submissions")
                    .add(submission)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Form submitted", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack(R.id.nav_home, false)
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Submission failed", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load form data", Toast.LENGTH_SHORT).show()
            }
    }

}