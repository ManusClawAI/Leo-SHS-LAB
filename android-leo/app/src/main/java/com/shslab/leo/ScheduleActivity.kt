package com.shslab.leo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shslab.leo.schedule.ScheduleManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Scheduled Tasks Activity
 */
class ScheduleActivity : AppCompatActivity() {

    private lateinit var scheduleManager: ScheduleManager
    private lateinit var adapter: TaskAdapter
    private lateinit var listView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scheduleManager = ScheduleManager(this)

        // Build UI
        val layout = LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "Scheduled Tasks"
            textSize = 20f
            setPadding(0, 0, 16, 16)
        }
        val btnAdd = Button(this).apply { text = "+ New Task" }
        btnAdd.setOnClickListener { showNewTaskDialog() }
        header.addView(title)
        header.addView(btnAdd)
        layout.addView(header)

        // List
        listView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ScheduleActivity)
        }
        adapter = TaskAdapter(scheduleManager.getAllTasks()) { task ->
            AlertDialog.Builder(this)
                .setTitle(task.title)
                .setMessage(task.prompt)
                .setPositiveButton("Delete") { _, _ ->
                    scheduleManager.cancelTask(task.id)
                    refreshTasks()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        listView.adapter = adapter
        layout.addView(listView)

        setContentView(layout)
        refreshTasks()
    }

    private fun refreshTasks() {
        adapter.updateTasks(scheduleManager.getAllTasks())
    }

    private fun showNewTaskDialog() {
        val input = EditText(this).apply { hint = "What should Leo do?" }
        AlertDialog.Builder(this)
            .setTitle("New Scheduled Task")
            .setView(input)
            .setPositiveButton("Set Time") { _, _ ->
                val prompt = input.text.toString().trim()
                if (prompt.isNotBlank()) {
                    showDateTimePicker(prompt)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDateTimePicker(prompt: String) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            cal.set(year, month, day)
            TimePickerDialog(this, { _, hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                val title = prompt.take(30)
                scheduleManager.scheduleTask(title, prompt, cal.timeInMillis)
                Toast.makeText(this, "Task scheduled for ${SimpleDateFormat("MMM dd, HH:mm").format(cal.time)}", Toast.LENGTH_LONG).show()
                refreshTasks()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
}

class TaskAdapter(
    private var tasks: List<ScheduleManager.ScheduledTask>,
    private val onClick: (ScheduleManager.ScheduledTask) -> Unit
) : RecyclerView.Adapter<TaskAdapter.VH>() {

    fun updateTasks(newTasks: List<ScheduleManager.ScheduledTask>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(android.R.id.text1)
        val tvTime: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val task = tasks[position]
        holder.tvTitle.text = task.title
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm")
        holder.tvTime.text = "${sdf.format(Date(task.scheduledTime))}\n${task.prompt}"
        holder.itemView.setOnClickListener { onClick(task) }
    }

    override fun getItemCount(): Int = tasks.size
}
