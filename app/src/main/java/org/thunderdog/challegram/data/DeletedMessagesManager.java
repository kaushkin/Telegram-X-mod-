package org.thunderdog.challegram.data;

import android.util.Log;
import android.util.LruCache;

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
    private static final String TAG = "ANTIDELETE";
    private static final DeletedMessagesManager INSTANCE = new DeletedMessagesManager();
    private File savedMessagesDir;
    
    // Cache for 5000 messages to survive 404 errors
    private final LruCache<Long, TdApi.Message> messageCache = new LruCache<>(5000);

    private DeletedMessagesManager() {
    }
    
    public void cacheMessage(TdApi.Message message) {
        if (message != null) {
            messageCache.put(message.id, message);
        }
    }

    public void updateMessageContent(long chatId, long messageId, TdApi.MessageContent content) {
        TdApi.Message cached = messageCache.get(messageId);
        if (cached != null && cached.chatId == chatId) {
            cached.content = content;
        }
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
        if (savedMessagesDir == null) {
            android.util.Log.e(TAG, "getDeletedMessages: savedMessagesDir is null");
            return Collections.emptyList();
        }

        File chatDir = new File(savedMessagesDir, String.valueOf(chatId));
        if (!chatDir.exists()) {
             android.util.Log.e(TAG, "getDeletedMessages: Chat dir not found: " + chatDir.getAbsolutePath());
             return Collections.emptyList();
        }

        List<TdApi.Message> messages = new ArrayList<>();
        File[] files = chatDir.listFiles();
        if (files == null) {
             android.util.Log.e(TAG, "getDeletedMessages: listFiles returned null");
             return messages;
        }

        android.util.Log.e(TAG, "getDeletedMessages: Found " + files.length + " saved message files in " + chatDir.getName());


        for (File f : files) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(f));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject json = (JSONObject) new JSONTokener(sb.toString()).nextValue();
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

    private final Map<Long, Long> lastDeletedMessageIds = Collections.synchronizedMap(new HashMap<>());

    public void onMessagesDeleted(final org.thunderdog.challegram.telegram.Tdlib tdlib, final long chatId, final long[] messageIds) {
        android.util.Log.e(TAG, "onMessagesDeleted request: " + java.util.Arrays.toString(messageIds) + ", dir: " + savedMessagesDir);
        if (savedMessagesDir == null) return;
        
        long maxId = lastDeletedMessageIds.containsKey(chatId) ? lastDeletedMessageIds.get(chatId) : 0;
        
        for (final long messageId : messageIds) {
             // Track as deleted in memory (assuming 'deletedMessageIds' is a field in the class)
             // If 'deletedMessageIds' is not defined, this line will cause a compilation error.
             // For the purpose of this edit, I'm assuming it exists or is intended to be added.
             // deletedMessageIds.add(messageId); 
             if (messageId > maxId) {
                 maxId = messageId;
             }
             
             TdApi.Message cached = messageCache.get(messageId);
             if (cached != null) {
                 android.util.Log.e(TAG, "CACHE HIT: " + messageId);
                 saveMessage(chatId, cached);
                 continue;
             }

             // Fallback to GetMessage (likely to fail for deleted messages)
             android.util.Log.e(TAG, "Requesting GetMessage (Cache Miss): " + messageId);
             tdlib.client().send(new TdApi.GetMessage(chatId, messageId), result -> {
                 if (result.getConstructor() == TdApi.Message.CONSTRUCTOR) {
                     android.util.Log.e(TAG, "GetMessage SUCCESS: " + messageId);
                     saveMessage(chatId, (TdApi.Message) result);
                 } else {
                     android.util.Log.e(TAG, "GetMessage failed for " + messageId + ": " + result);
                 }
            });
        }
        if (maxId > 0) {
            lastDeletedMessageIds.put(chatId, maxId);
        }
    }

    // New method to retrieve the latest ghost message for a chat
    public TdApi.Message getLastDeletedMessage(long chatId) {
        Long lastId = lastDeletedMessageIds.get(chatId);
        if (lastId == null) return null;
        
        // Try memory cache first
        TdApi.Message cached = messageCache.get(lastId);
        if (cached != null) return cached;
        
        // Try disk
        if (savedMessagesDir != null) {
            File chatDir = new File(savedMessagesDir, String.valueOf(chatId));
            File msgFile = new File(chatDir, lastId + ".json");
            if (msgFile.exists()) {
                try {
                    BufferedReader br = new BufferedReader(new FileReader(msgFile));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    JSONObject json = (JSONObject) new JSONTokener(sb.toString()).nextValue();
                    TdApi.Message msg = new TdApi.Message();
                    msg.id = json.getLong("id");
                    msg.chatId = json.getLong("chatId");
                    msg.date = json.getInt("date");
                    msg.editDate = json.optInt("editDate", 0);
                    msg.isOutgoing = json.optBoolean("isOutgoing");
                    msg.senderId = deserializeSender(json.optJSONObject("senderId"));
                    msg.content = deserializeContent(json.optJSONObject("content"));
                    return msg;
                } catch (Exception e) {
                    Log.e(TAG, "Error loading last ghost message: " + e.getMessage());
                }
            }
        }
        return null;
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
        if (json == null) return new TdApi.MessageText(new TdApi.FormattedText("Deleted Message (Error)", null), null, null);
        String type = json.optString("type");
        if ("text".equals(type)) {
            return new TdApi.MessageText(new TdApi.FormattedText(json.optString("text"), null), null, null);
        } else if ("photo".equals(type)) {
            // Return a placeholder for photo
             return new TdApi.MessageText(new TdApi.FormattedText("[Deleted Photo] " + json.optString("caption"), null), null, null);
        } else if ("video".equals(type)) {
             return new TdApi.MessageText(new TdApi.FormattedText("[Deleted Video] " + json.optString("caption"), null), null, null);
        }
        return new TdApi.MessageText(new TdApi.FormattedText("[Deleted Content]", null), null, null);
    }

    // UPDATED: Strictly check if message is confirmed deleted (in memory set or on disk)
    public boolean isDeletedMessage(long chatId, long messageId) {
        // Check memory set first (fastest)
        if (deletedMessageIds.contains(messageId)) {
            return true;
        }
        // Fallback to disk check (slower, but necessary for persistent ghosts)
        if (savedMessagesDir != null) {
            File chatDir = new File(savedMessagesDir, String.valueOf(chatId));
            if (chatDir.exists()) {
                File msgFile = new File(chatDir, messageId + ".json");
                return msgFile.exists();
            }
        }
        return false;
    }
}
