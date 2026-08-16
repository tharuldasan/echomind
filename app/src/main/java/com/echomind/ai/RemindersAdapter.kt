package com.echomind.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RemindersAdapter(
    private var items: List<Reminder>,
    private val onDeleteClicked: (Reminder) -> Unit
) : RecyclerView.Adapter<RemindersAdapter.ViewHolder>() {

    fun updateData(newItems: List<Reminder>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reminder = items[position]
        holder.bind(reminder, onDeleteClicked)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvReminderTitle)
        private val tvTime: TextView = itemView.findViewById(R.id.tvReminderTime)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvReminderStatus)
        private val tvOriginalVoice: TextView = itemView.findViewById(R.id.tvOriginalVoice)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteReminder)

        fun bind(reminder: Reminder, onDeleteClicked: (Reminder) -> Unit) {
            tvTitle.text = reminder.title

            if (reminder.targetTimeMs > 0L) {
                val sdf = SimpleDateFormat("EEE, MMM d, yyyy 'at' hh:mm a", Locale.getDefault())
                tvTime.text = sdf.format(Date(reminder.targetTimeMs))
                tvTime.visibility = View.VISIBLE

                val isPast = reminder.targetTimeMs < System.currentTimeMillis()
                if (reminder.isTriggered || isPast) {
                    tvStatus.text = "Completed"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_muted))
                } else {
                    tvStatus.text = "Scheduled"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.accent_green))
                }
                tvStatus.visibility = View.VISIBLE
            } else {
                tvTime.text = "Note / No Time Set"
                tvStatus.visibility = View.GONE
            }

            if (reminder.originalText.isNotBlank() && reminder.originalText != reminder.title) {
                tvOriginalVoice.text = "Spoken: \"${reminder.originalText}\""
                tvOriginalVoice.visibility = View.VISIBLE
            } else {
                tvOriginalVoice.visibility = View.GONE
            }

            btnDelete.setOnClickListener {
                onDeleteClicked(reminder)
            }
        }
    }
}
