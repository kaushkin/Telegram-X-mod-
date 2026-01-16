package org.thunderdog.challegram.data;

import android.util.Log;

import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeletedMessagesManager {
    private static final String TAG = "AntiDelete";
    private static final DeletedMessagesManager INSTANCE = new DeletedMessagesManager();
    private File savedMessagesDir;

    private DeletedMessagesManager() {
    }

    public static DeletedMessagesManager getInstance() {
        return INSTANCE;
    }

    public void init(File filesDir) {
        this.savedMessagesDir = new File(filesDir, "deleted_msgs_v1");
        if (!this.savedMessagesDir.exists()) {
            this.savedMessagesDir.mkdirs();
        }
    }

    public void saveMessage(long chatId, TdApi.Message message) {
        if (savedMessagesDir == null) return;
        
        try {
            File chatDir = new File(savedMessagesDir, String.valueOf(chatId));
            if (!chatDir.exists()) chatDir.mkdirs();

            JSONObject json = new JSONObject();
            json.put("id", message.id);
            json.put("chatId", message.chatId);
            json.put("senderId", serializeSender(message.senderId));
            json.put("date", message.date);
            json.put("editDate", message.editDate);
            json.put("isOutgoing", message.isOutgoing);
            json.put("content", serializeContent(message.content));

            // Mark as locally deleted (Ghost)
            json.put("is_ghost", true);

            File msgFile = new File(chatDir, message.id + ".json");
            FileWriter writer = new FileWriter(msgFile);
            writer.write(json.toString());
            writer.close();

            Log.i(TAG, "Saved deleted message: " + message.id);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save message: " + e.getMessage());
        }
    }

    public List<TdApi.Message> getDeletedMessages(long chatId) {
        if (savedMessagesDir == null) return Collections.emptyList();

        File chatDir = new File(savedMessagesDir, String.valueOf(chatId));
        if (!chatDir.exists()) return Collections.emptyList();

        List<TdApi.Message> messages = new ArrayList<>();
        File[] files = chatDir.listFiles();
        if (files == null) return messages;

        for (File f : files) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(f));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject json = new JSONTokener(sb.toString()).nextValue();
                TdApi.Message msg = new TdApi.Message();
                msg.id = json.getLong("id");
                msg.chatId = json.getLong("chatId");
                msg.date = json.getInt("date");
                msg.editDate = json.optInt("editDate", 0);
                msg.isOutgoing = json.optBoolean("isOutgoing");
                msg.senderId = deserializeSender(json.optJSONObject("senderId"));
                msg.content = deserializeContent(json.optJSONObject("content"));

                if (msg.content != null) {
                    messages.add(msg);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading ghost message: " + e.getMessage());
            }
        }
        return messages;
    }

    public void onMessagesDeleted(final org.thunderdog.challegram.telegram.Tdlib tdlib, final long chatId, final long[] messageIds) {
        if (savedMessagesDir == null) return;
        for (final long messageId : messageIds) {
             tdlib.client().send(new TdApi.GetMessage(chatId, messageId), result -> {
                 if (result.getConstructor() == TdApi.Message.CONSTRUCTOR) {
                     saveMessage(chatId, (TdApi.Message) result);
                 }
            });
        }
    }

    // --- Serialization Helpers ---

    private JSONObject serializeSender(TdApi.MessageSender sender) throws Exception {
        JSONObject obj = new JSONObject();
        if (sender instanceof TdApi.MessageSenderUser) {
            obj.put("type", "user");
            obj.put("userId", ((TdApi.MessageSenderUser) sender).userId);
        } else if (sender instanceof TdApi.MessageSenderChat) {
            obj.put("type", "chat");
            obj.put("chatId", ((TdApi.MessageSenderChat) sender).chatId);
        }
        return obj;
    }

    private TdApi.MessageSender deserializeSender(JSONObject json) {
        if (json == null) return new TdApi.MessageSenderUser(0);
        String type = json.optString("type");
        if ("user".equals(type)) {
            return new TdApi.MessageSenderUser(json.optLong("userId"));
        } else if ("chat".equals(type)) {
            return new TdApi.MessageSenderChat(json.optLong("chatId"));
        }
        return new TdApi.MessageSenderUser(0);
    }

    private JSONObject serializeContent(TdApi.MessageContent content) throws Exception {
        JSONObject obj = new JSONObject();
        if (content instanceof TdApi.MessageText) {
            obj.put("type", "text");
            obj.put("text", ((TdApi.MessageText) content).text.text);
        } else if (content instanceof TdApi.MessagePhoto) {
            obj.put("type", "photo");
            obj.put("caption", ((TdApi.MessagePhoto) content).caption.text);
            // Save minimal info, implementing full photo serialization is complex without file paths.
            // We'll rely on cached paths if possible or just display caption.
            // Actually, we'd need to serialize Photo sizes.
        } else if (content instanceof TdApi.MessageVideo) {
            obj.put("type", "video");
            obj.put("caption", ((TdApi.MessageVideo) content).caption.text);
        }
        return obj;
    }

    private TdApi.MessageContent deserializeContent(JSONObject json) {
        if (json == null) return new TdApi.MessageText(new TdApi.FormattedText("Deleted Message (Error)", null), null);
        String type = json.optString("type");
        if ("text".equals(type)) {
            return new TdApi.MessageText(new TdApi.FormattedText(json.optString("text"), null), null);
        } else if ("photo".equals(type)) {
            // Return a placeholder for photo
             return new TdApi.MessageText(new TdApi.FormattedText("[Deleted Photo] " + json.optString("caption"), null), null);
        } else if ("video".equals(type)) {
             return new TdApi.MessageText(new TdApi.FormattedText("[Deleted Video] " + json.optString("caption"), null), null);
        }
        return new TdApi.MessageText(new TdApi.FormattedText("[Deleted Content]", null), null);
    }
}
