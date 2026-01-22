import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.R
import com.example.safespace_app.GroupChatMessage
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class GroupChatAdapter : ListAdapter<GroupChatMessage, RecyclerView.ViewHolder>(DiffCallback()) {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            val view = inflater.inflate(R.layout.item_chat_sent, parent, false)
            SentViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_received, parent, false)
            ReceivedViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is SentViewHolder) {
            holder.bind(message)
        } else if (holder is ReceivedViewHolder) {
            holder.bind(message)
        }
    }

    class SentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)

        fun bind(message: GroupChatMessage) {
            messageText.text = message.message
            timeText.text = formatTime(message.timestamp)
        }
    }

    class ReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val senderName: TextView = itemView.findViewById(R.id.senderName)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)

        fun bind(message: GroupChatMessage) {
            senderName.text = message.senderName
            messageText.text = message.message
            timeText.text = formatTime(message.timestamp)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<GroupChatMessage>() {
        override fun areItemsTheSame(oldItem: GroupChatMessage, newItem: GroupChatMessage) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: GroupChatMessage, newItem: GroupChatMessage) =
            oldItem == newItem
    }

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2

        fun formatTime(ts: Long): String {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return sdf.format(Date(ts))
        }
    }
}
