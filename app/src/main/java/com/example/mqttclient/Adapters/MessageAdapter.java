package com.example.mqttclient.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mqttclient.R;
import com.example.mqttclient.Database.MessageEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private List<MessageEntity> messages = new ArrayList<>();
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault());

    public void setMessages(List<MessageEntity> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        MessageEntity msg = messages.get(position);
        holder.bind(msg);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageTime, messageQos, messageRetained, messagePayload, messageClientId;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTime = itemView.findViewById(R.id.message_time);
            messageQos = itemView.findViewById(R.id.message_qos);
            messageRetained = itemView.findViewById(R.id.message_retained);
            messagePayload = itemView.findViewById(R.id.message_payload);
//            messageClientId = itemView.findViewById(R.id.message_client_id);
        }

        void bind(MessageEntity msg) {
            // timestamp теперь long, всегда имеет значение (0 по умолчанию, но мы не сохраняем 0)
            if (msg.timestamp > 0) {
                messageTime.setText(timeFormat.format(new Date(msg.timestamp)));
            } else {
                messageTime.setText("");
            }

            messageQos.setText("QoS:" + msg.qos);

            if (msg.retained > 0) {
                messageRetained.setVisibility(View.VISIBLE);
                messageRetained.setText("RET");
            } else {
                messageRetained.setVisibility(View.GONE);
            }

            String payload = msg.payload;
            if (payload != null && payload.length() > 500) {
                payload = payload.substring(0, 500) + "...";
            }
            messagePayload.setText(payload != null ? payload : "(пустое сообщение)");

//            String client = (msg.clientId != null && !msg.clientId.isEmpty()) ? msg.clientId : "unknown";
//            messageClientId.setText("ClientID: " + client);
        }
    }
}
