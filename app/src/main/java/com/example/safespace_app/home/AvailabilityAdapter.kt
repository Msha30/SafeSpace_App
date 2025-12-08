package com.example.safespace_app.home

import android.app.TimePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.DayAvailability
import com.example.safespace_app.R
import com.example.safespace_app.TimeSlot

class AvailabilityAdapter(
    private val context: Context,
    private val days: MutableList<DayAvailability>,
    private val studentSelectionMode: Boolean = false,
    private val dateLabels: List<String> = emptyList(),
    private val onStudentSelect: ((dayIndex: Int, slotIndex: Int) -> Unit)? = null
) : RecyclerView.Adapter<AvailabilityAdapter.AvailabilityViewHolder>() {

    private var selectedDayIndex: Int? = null
    private var selectedSlotIndex: Int? = null

    inner class AvailabilityViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtDay = view.findViewById<TextView>(R.id.txtDayName)
        val slotLayouts = listOf(
            view.findViewById<LinearLayout>(R.id.timeSlot1),
            view.findViewById<LinearLayout>(R.id.timeSlot2),
            view.findViewById<LinearLayout>(R.id.timeSlot3),
            view.findViewById<LinearLayout>(R.id.timeSlot4)
        )
        val slotLabels = listOf(
            view.findViewById<TextView>(R.id.txtTimeSlot1),
            view.findViewById<TextView>(R.id.txtTimeSlot2),
            view.findViewById<TextView>(R.id.txtTimeSlot3),
            view.findViewById<TextView>(R.id.txtTimeSlot4)
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvailabilityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_availability, parent, false)
        return AvailabilityViewHolder(view)
    }

    override fun getItemCount(): Int = days.size

    override fun onBindViewHolder(holder: AvailabilityViewHolder, position: Int) {
        val day = days[position]
        holder.txtDay.text = if (studentSelectionMode && dateLabels.isNotEmpty())
            dateLabels[position] else day.dayName

        for (i in 0 until 4) {
            val layout = holder.slotLayouts[i]
            val label = holder.slotLabels[i]
            val slot = day.slots[i]

            label.text = slot.label

            val isSelected = if (studentSelectionMode)
                selectedDayIndex == position && selectedSlotIndex == i
            else slot.selected

            val isEnabled = !studentSelectionMode || slot.selected
            updateSlotColor(layout, label, isEnabled, studentSelectionMode, isSelected)

            layout.setOnClickListener {
                val actualPos = holder.adapterPosition
                if (actualPos == RecyclerView.NO_POSITION) return@setOnClickListener
                val slot = days[actualPos].slots[i]
                val enabled = !studentSelectionMode || slot.selected
                if (!enabled) return@setOnClickListener

                if (studentSelectionMode) {
                    val prevDay = selectedDayIndex
                    val prevSlot = selectedSlotIndex
                    selectedDayIndex = actualPos
                    selectedSlotIndex = i
                    notifyItemChanged(prevDay ?: -1)
                    notifyItemChanged(actualPos)
                    onStudentSelect?.invoke(actualPos, i)
                } else {
                    slot.selected = !slot.selected
                    updateSlotColor(layout, label, slot.selected, false, slot.selected)
                }
            }


            layout.setOnLongClickListener {
                val actualPos = holder.adapterPosition
                if (actualPos == RecyclerView.NO_POSITION) return@setOnLongClickListener true
                if (!studentSelectionMode) {
                    showTimePicker(days[actualPos].slots[i], holder.slotLabels[i])
                }
                true
            }

        }
    }

    private fun updateSlotColor(
        layout: LinearLayout,
        label: TextView,
        enabled: Boolean,
        studentMode: Boolean,
        isSelected: Boolean
    ) {
        val colorRes = when {
            studentMode && !enabled -> R.drawable.f_cancel_btn  // greyed out
            studentMode && isSelected -> R.drawable.f_rounded_clicked // student picked
            !studentMode && isSelected -> R.drawable.f_rounded_clicked // peer picked
            else -> R.drawable.f_rounded_white
        }
        layout.background = ContextCompat.getDrawable(context, colorRes)

        when {
            studentMode && !enabled -> label.setTextColor(ContextCompat.getColor(context, R.color.white))
            else -> label.setTextColor(ContextCompat.getColor(context, R.color.black))
        }
    }

    private fun showTimePicker(slot: TimeSlot, labelView: TextView) {
        val context = labelView.context

        val parts = slot.label.split(" - ")
        val startParts = parts[0].split(":")
        val endParts = parts[1].split(":")

        val startHour = startParts[0].toInt()
        val startMinute = startParts[1].toInt()
        val endHour = endParts[0].toInt()
        val endMinute = endParts[1].toInt()

        TimePickerDialog(context, { _, sh, sm ->
            TimePickerDialog(context, { _, eh, em ->
                val startTime = String.format("%02d:%02d", sh, sm)
                val endTime = String.format("%02d:%02d", eh, em)
                slot.label = "$startTime - $endTime"
                labelView.text = slot.label
            }, endHour, endMinute, false).show()
        }, startHour, startMinute, false).show()
    }

    /** Returns currently selected student slot as Pair(dayIndex, slotIndex) or null */
    fun getStudentSelectedSlot(): Pair<Int, Int>? {
        return if (selectedDayIndex != null && selectedSlotIndex != null)
            Pair(selectedDayIndex!!, selectedSlotIndex!!)
        else null
    }
}
