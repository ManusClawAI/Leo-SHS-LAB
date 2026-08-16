package com.shslab.leo.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shslab.leo.R

/**
 * Adapter for chat sessions in the drawer
 */
class SessionAdapter(
    private val onClick: (ChatSession) -> Unit,
    private val onLongClick: (ChatSession) -> Unit
) : RecyclerView.Adapter<SessionAdapter.SessionVH>() {

    private val sessions = mutableListOf<ChatSession>()

    fun setSessions(list: List<ChatSession>) {
        sessions.clear()
        sessions.addAll(list)
        notifyDataSetChanged()
    }

    fun addSession(session: ChatSession) {
        sessions.add(0, session)
        notifyItemInserted(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_session, parent, false)
        return SessionVH(view)
    }

    override fun onBindViewHolder(holder: SessionVH, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount(): Int = sessions.size

    inner class SessionVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvSessionTitle)
        private val imgPin: ImageView = itemView.findViewById(R.id.imgPin)

        fun bind(session: ChatSession) {
            tvTitle.text = session.title
            imgPin.visibility = if (session.pinned) View.VISIBLE else View.GONE

            itemView.setOnClickListener { onClick(session) }
            itemView.setOnLongClickListener {
                onLongClick(session)
                true
            }
        }
    }
}
