package com.example.mqttclient.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault());

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
        TextView messageTime, messagePayload, messageQos;
        ImageView retainedIcon;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTime = itemView.findViewById(R.id.message_time);
            messageQos = itemView.findViewById(R.id.message_qos);
            messagePayload = itemView.findViewById(R.id.message_payload);
            retainedIcon = itemView.findViewById(R.id.message_retained_icon);
        }

        void bind(MessageEntity msg) {
            if (msg.timestamp > 0) {
                messageTime.setText(timeFormat.format(new Date(msg.timestamp)));
            } else {
                messageTime.setText("");
            }

            switch (msg.qos) {
                case 0:
                    messageQos.setText("0");
                    break;
                case 1:
                    messageQos.setText("1");
                    break;
                case 2:
                    messageQos.setText("2");
                    break;
            }

            if (msg.retained > 0) {
                retainedIcon.setImageResource(R.drawable.ic_cloud_filled);
                retainedIcon.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.orange_500));
                retainedIcon.setVisibility(View.VISIBLE);
            } else {
                retainedIcon.setImageResource(R.drawable.ic_cloud_filled);
                retainedIcon.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.gray_400));
                retainedIcon.setVisibility(View.VISIBLE);
            }

            String payload = msg.payload;
            if (payload != null && payload.length() > 500) {
                payload = payload.substring(0, 500) + "...";
            }
            messagePayload.setText(payload != null ? payload : "(пустое сообщение)");
        }
    }
}
