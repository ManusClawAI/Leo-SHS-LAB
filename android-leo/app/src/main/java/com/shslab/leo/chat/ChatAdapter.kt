package com.shslab.leo.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.shslab.leo.R

/**
 * Modern Chat Adapter with message actions
 */
class ChatAdapter(
    private val context: Context,
    private val onEdit: (String, String) -> Unit,        // messageId, content
    private val onRegenerate: (String) -> Unit,           // messageId
    private val onListen: (String) -> Unit,               // content
    private val onLike: (String, Boolean) -> Unit,        // messageId, liked
    private val onDislike: (String, Boolean) -> Unit      // messageId, disliked
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
    }

    fun setMessages(msgs: List<ChatMessage>) {
        messages.clear()
        messages.addAll(msgs)
        notifyDataSetChanged()
    }

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(content: String) {
        if (messages.isNotEmpty()) {
            val last = messages.last()
            messages[messages.size - 1] = last.copy(content = content)
            notifyItemChanged(messages.size - 1)
        }
    }

    fun removeMessage(position: Int) {
        if (position in messages.indices) {
            messages.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun getLastUserMessage(): ChatMessage? = messages.findLast { it.role == "user" }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].role == "user") TYPE_USER else TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
        } else {
            AssistantViewHolder(inflater.inflate(R.layout.item_chat_leo, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        when (holder) {
            is UserViewHolder -> holder.bind(msg)
            is AssistantViewHolder -> holder.bind(msg)
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val actionRow: LinearLayout = itemView.findViewById(R.id.actionRow)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        private val btnCopy: ImageButton = itemView.findViewById(R.id.btnCopy)

        fun bind(msg: ChatMessage) {
            tvMessage.text = msg.content

            // Click to show actions
            itemView.setOnClickListener {
                actionRow.visibility = if (actionRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }

            btnEdit.setOnClickListener {
                onEdit(msg.id, msg.content)
                actionRow.visibility = View.GONE
            }

            btnCopy.setOnClickListener {
                copyToClipboard(msg.content)
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                actionRow.visibility = View.GONE
            }
        }
    }

    inner class AssistantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val actionRow: LinearLayout = itemView.findViewById(R.id.actionRow)
        private val btnCopy: ImageButton = itemView.findViewById(R.id.btnCopy)
        private val btnListen: ImageButton = itemView.findViewById(R.id.btnListen)
        private val btnRegenerate: ImageButton = itemView.findViewById(R.id.btnRegenerate)
        private val btnLike: ImageButton = itemView.findViewById(R.id.btnLike)
        private val btnDislike: ImageButton = itemView.findViewById(R.id.btnDislike)

        fun bind(msg: ChatMessage) {
            tvMessage.text = msg.content

            itemView.setOnClickListener {
                actionRow.visibility = if (actionRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }

            btnCopy.setOnClickListener {
                copyToClipboard(msg.content)
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                actionRow.visibility = View.GONE
            }

            btnListen.setOnClickListener {
                onListen(msg.content)
                actionRow.visibility = View.GONE
            }

            btnRegenerate.setOnClickListener {
                onRegenerate(msg.id)
                actionRow.visibility = View.GONE
            }

            btnLike.setOnClickListener {
                onLike(msg.id, !msg.liked)
                notifyItemChanged(adapterPosition)
            }

            btnDislike.setOnClickListener {
                onDislike(msg.id, !msg.disliked)
                notifyItemChanged(adapterPosition)
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Leo", text))
    }
}
