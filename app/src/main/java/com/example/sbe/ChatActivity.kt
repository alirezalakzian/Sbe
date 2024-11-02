import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sbe.ChatAdapter
import com.example.sbe.ChatMessage
import com.example.sbe.databinding.ActivityChatBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val db = FirebaseFirestore.getInstance()
    private val chatRoomId = "CHAT_ROOM_ID" // شناسه یکتا برای اتاق چت

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // استفاده از View Binding
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)

        // اضافه کردن پیام‌های جدید به RecyclerView
        db.collection("chats")
            .document(chatRoomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                val messages = snapshots?.documents?.map { document ->
                    ChatMessage(
                        senderId = document.getString("senderId") ?: "",
                        receiverId = document.getString("receiverId") ?: "",
                        messageText = document.getString("messageText") ?: "",
                        timestamp = document.getLong("timestamp") ?: 0L
                    )
                } ?: emptyList()

                binding.chatRecyclerView.adapter = ChatAdapter(messages)
            }

        // دکمه ارسال پیام
        binding.sendButton.setOnClickListener {
            val messageText = binding.messageInput.text.toString()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
            }
        }
    }

    private fun sendMessage(messageText: String) {
        val message = hashMapOf(
            "senderId" to "USER_ID",
            "receiverId" to "OPERATOR_ID",
            "messageText" to messageText,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("chats")
            .document(chatRoomId)
            .collection("messages")
            .add(message)
            .addOnSuccessListener {
                binding.messageInput.setText("")
            }
            .addOnFailureListener {
                // مدیریت خطا در ارسال پیام
            }
    }
}
