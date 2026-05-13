package com.safepath.indore.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.safepath.indore.R
import com.safepath.indore.data.EmergencyGuideItem

class EmergencyGuideAdapter(private var items: List<EmergencyGuideItem>) :
    RecyclerView.Adapter<EmergencyGuideAdapter.GuideViewHolder>() {

    fun updateData(newItems: List<EmergencyGuideItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuideViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emergency_guide, parent, false)
        return GuideViewHolder(view)
    }

    override fun onBindViewHolder(holder: GuideViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    inner class GuideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textEmoji: TextView = itemView.findViewById(R.id.textEmoji)
        private val textQuestion: TextView = itemView.findViewById(R.id.textQuestion)
        private val textAnswer: TextView = itemView.findViewById(R.id.textAnswer)
        private val iconExpand: ImageView = itemView.findViewById(R.id.iconExpand)
        private val questionHeader: View = itemView.findViewById(R.id.questionHeader)

        fun bind(item: EmergencyGuideItem) {
            textEmoji.text = item.emoji
            textQuestion.text = item.question
            textAnswer.text = item.answer

            // Set initial visibility
            textAnswer.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
            iconExpand.rotation = if (item.isExpanded) 180f else 0f

            questionHeader.setOnClickListener {
                item.isExpanded = !item.isExpanded
                notifyItemChanged(adapterPosition)
            }
        }
    }
}
