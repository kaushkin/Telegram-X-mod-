package org.thunderdog.challegram.data;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeletedMessagesManager { // Sync fix
    private static final String TAG = "ANTIDELETE";
    private static final DeletedMessagesManager INSTANCE = new DeletedMessagesManager();
    private File savedMessagesDir;
    
    // Cache helper: Store recent messages to grab content when deleted
    // Limit to 500 messages to prevent memory leak
    private final LruCache<Long, TdApi.Message> messageCache = new LruCache<>(500);
    // Map FileID -> MessageIDs to update cache when file downloads
    private final Map<Integer, Set<Long>> fileIdToMessageIds = Collections.synchronizedMap(new HashMap<>());
    private final Set<Long> deletedMessageIds = Collections.synchronizedSet(new HashSet<>());

    private DeletedMessagesManager() {
    }
    
    public void cacheMessage(TdApi.Message message) {
        if (!isGhostEnabled()) return;
        int constructor = message.content.getConstructor();
        if (constructor == TdApi.MessageText.CONSTRUCTOR || 
            constructor == TdApi.MessagePhoto.CONSTRUCTOR ||
            constructor == TdApi.MessageVideo.CONSTRUCTOR ||
            constructor == TdApi.MessageDocument.CONSTRUCTOR) {
             messageCache.put(message.id, message);
             indexFiles(message);
        }
    }
    
    private void indexFiles(TdApi.Message message) {
        List<TdApi.File> files = new ArrayList<>();
        if (message.content instanceof TdApi.MessagePhoto) {
            for (TdApi.PhotoSize sz : ((TdApi.MessagePhoto) message.content).photo.sizes) {
                files.add(sz.photo);
            }
        } else if (message.content instanceof TdApi.MessageVideo) {
            files.add(((TdApi.MessageVideo) message.content).video.video);
        } else if (message.content instanceof TdApi.MessageDocument) {
            files.add(((TdApi.MessageDocument) message.content).document.document);
        }
        
        for (TdApi.File f : files) {
            if (f != null) {
                Set<Long> msgs = fileIdToMessageIds.get(f.id);
                if (msgs == null) {
                    msgs = Collections.synchronizedSet(new HashSet<>());
                    fileIdToMessageIds.put(f.id, msgs);
                }
                msgs.add(message.id);
            }
        }
    }
    
    public void updateFile(TdApi.File file) {
        Set<Long> msgs = fileIdToMessageIds.get(file.id);
        if (msgs != null) {
            synchronized(msgs) {
                for (Long msgId : msgs) {
                    TdApi.Message cached = messageCache.get(msgId);
                    if (cached != null) {
                        updateMessageFile(cached, file);
                    }
                }
            }
        }
    }
    
    private void updateMessageFile(TdApi.Message message, TdApi.File file) {
        if (message.content instanceof TdApi.MessagePhoto) {
            for (TdApi.PhotoSize sz : ((TdApi.MessagePhoto) message.content).photo.sizes) {
                if (sz.photo.id == file.id) {
                    sz.photo = file; // Update reference
                }
            }
        } else if (message.content instanceof TdApi.MessageVideo) {
            TdApi.Video v = ((TdApi.MessageVideo) message.content).video;
            if (v.video.id == file.id) v.video = file;
        } else if (message.content instanceof TdApi.MessageDocument) {
             TdApi.Document d = ((TdApi.MessageDocument) message.content).document;
             if (d.document.id == file.id) d.document = file;
        }
    }

    private Context context;
    private SharedPreferences prefs;

    public void updateMessageContent(long chatId, long messageId, TdApi.MessageContent content) {
        TdApi.Message cached = messageCache.get(messageId);
        if (cached != null && cached.chatId == chatId) {
            cached.content = content;
        }
    }
    
    public TdApi.Message getCachedMessage(long messageId) {
        return messageCache.get(messageId);
    }

    public static DeletedMessagesManager getInstance() {
        return INSTANCE;
    }

    public void init(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("ghost_settings", Context.MODE_PRIVATE);
        this.savedMessagesDir = new File(context.getExternalFilesDir(null), "deleted_msgs_v1");
        if (!savedMessagesDir.exists()) {
            savedMessagesDir.mkdirs();
        }
        // Initialize edit history dir
        this.editHistoryDir = new File(context.getExternalFilesDir(null), "edit_history_v1");
        if (!editHistoryDir.exists()) {
            editHistoryDir.mkdirs();
        }
    }
 
    public boolean isGhostEnabled() {
        return prefs != null && prefs.getBoolean("ghost_enabled", true);
    }
    
    public void setGhostEnabled(boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean("ghost_enabled", enabled).apply();
        }
    }
    
    public void clearAllGhosts() {
        messageCache.evictAll();
        lastDeletedMessageIds.clear();
        deletedMessageIds.clear();
        if (savedMessagesDir != null && savedMessagesDir.exists()) {
            deleteRecursive(savedMessagesDir);
            savedMessagesDir.mkdirs();
        }
        // Also clear edit history
        if (editHistoryDir != null && editHistoryDir.exists()) {
            deleteRecursive(editHistoryDir);
            editHistoryDir.mkdirs();
        }
    }
    
    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);
        fileOrDirectory.delete();
    }

    // ============ EDIT HISTORY FEATURE ============
    
    private File editHistoryDir;
    
    public void initEditHistory(Context ctx) {
        this.editHistoryDir = new File(ctx.getExternalFilesDir(null), "edit_history_v1");
        if (!editHistoryDir.exists()) {
            editHistoryDir.mkdirs();
        }
    }
    
    public boolean isEditHistoryEnabled() {
        return prefs != null && prefs.getBoolean("edit_history_enabled", true);
    }
    
    public void setEditHistoryEnabled(boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean("edit_history_enabled", enabled).apply();
        }
    }
    
    /**
     * Save old content before message is updated.
     * Called from Tdlib.updateMessageContent() BEFORE the content changes.
     */
    public void saveEditVersion(long chatId, long messageId, TdApi.MessageContent oldContent) {
        if (!isEditHistoryEnabled() || editHistoryDir == null) return;
        if (oldContent == null) return;
        
        try {
            // Create directory for this message's history: edit_history_v1/{chatId}/{messageId}/
            File chatDir = new File(editHistoryDir, String.valueOf(chatId));
            File msgDir = new File(chatDir, String.valueOf(messageId));
            if (!msgDir.exists()) msgDir.mkdirs();
            
            // Filename is timestamp to preserve order
            long timestamp = System.currentTimeMillis();
            File versionFile = new File(msgDir, timestamp + ".json");
            
            JSONObject json = new JSONObject();
            json.put("timestamp", timestamp);
            json.put("content", serializeContent(oldContent, chatId, messageId));
            
            FileWriter writer = new FileWriter(versionFile);
            writer.write(json.toString());
            writer.close();
            
            Log.i(TAG, "Saved edit version for message " + messageId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save edit version: " + e.getMessage());
        }
    }
    
    /**
     * Get all previous versions of a message (for history viewer)
     */
    public List<EditHistoryEntry> getEditHistory(long chatId, long messageId) {
        List<EditHistoryEntry> history = new ArrayList<>();
        if (editHistoryDir == null) return history;
        
        File chatDir = new File(editHistoryDir, String.valueOf(chatId));
        File msgDir = new File(chatDir, String.valueOf(messageId));
        
        if (!msgDir.exists()) return history;
        
        File[] files = msgDir.listFiles();
        if (files == null) return history;
        
        // Sort by timestamp (filename)
        java.util.Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
        
        for (File f : files) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(f));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                
                JSONObject json = (JSONObject) new JSONTokener(sb.toString()).nextValue();
                long timestamp = json.getLong("timestamp");
                TdApi.MessageContent content = deserializeContent(json.optJSONObject("content"));
                
                history.add(new EditHistoryEntry(timestamp, content));
            } catch (Exception e) {
                Log.e(TAG, "Error reading edit history: " + e.getMessage());
            }
        }
        
        return history;
    }
    
    /**
     * Entry class for edit history
     */
    public static class EditHistoryEntry {
        public final long timestamp;
        public final TdApi.MessageContent content;
        
        public EditHistoryEntry(long timestamp, TdApi.MessageContent content) {
            this.timestamp = timestamp;
            this.content = content;
        }
        
        public String getContentText() {
            if (content instanceof TdApi.MessageText) {
                return ((TdApi.MessageText) content).text.text;
            }
            return "[unsupported content]";
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
            json.put("content", serializeContent(message.content, message.chatId, message.id));

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
             // Track as deleted in memory
             deletedMessageIds.add(messageId); 
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
            Long currentMax = lastDeletedMessageIds.get(chatId);
            if (currentMax == null || maxId > currentMax) {
                lastDeletedMessageIds.put(chatId, maxId);
            }
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

    private JSONObject serializeContent(TdApi.MessageContent content, long chatId, long messageId) throws Exception {
        JSONObject obj = new JSONObject();
        if (content instanceof TdApi.MessageText) {
            obj.put("type", "text");
            obj.put("text", ((TdApi.MessageText) content).text.text);
        } else if (content instanceof TdApi.MessagePhoto) {
            obj.put("type", "photo");
            TdApi.MessagePhoto photo = (TdApi.MessagePhoto) content;
            obj.put("caption", photo.caption.text);
            
            // Find best photo size
            String savedPath = saveMediaFile(photo.photo.sizes, chatId, messageId);
            if (savedPath != null) {
                obj.put("localPath", savedPath);
            }
        } else if (content instanceof TdApi.MessageVideo) {
            obj.put("type", "video");
            TdApi.MessageVideo video = (TdApi.MessageVideo) content;
            obj.put("caption", video.caption.text);
            
            String savedPath = saveMediaFile(video.video.video, chatId, messageId);
            if (savedPath != null) {
                obj.put("localPath", savedPath);
            }
        } else if (content instanceof TdApi.MessageDocument) {
            obj.put("type", "document");
            TdApi.MessageDocument doc = (TdApi.MessageDocument) content;
            obj.put("caption", doc.caption.text);
            obj.put("fileName", doc.document.fileName);
            
            String savedPath = saveMediaFile(doc.document.document, chatId, messageId);
            if (savedPath != null) {
                obj.put("localPath", savedPath);
            }
        }
        return obj;
    }

    private TdApi.MessageContent deserializeContent(JSONObject json) {
        if (json == null) return new TdApi.MessageText(new TdApi.FormattedText("Deleted Message (Error)", null), null, null);
        String type = json.optString("type");
        String localPath = json.optString("localPath");
        
        if ("text".equals(type)) {
            return new TdApi.MessageText(new TdApi.FormattedText(json.optString("text"), null), null, null);
        } else if ("photo".equals(type)) {
            if (localPath != null && !localPath.isEmpty() && new File(localPath).exists()) {
                 // Reconstruct Photo using no-arg constructor
                 TdApi.PhotoSize[] sizes = new TdApi.PhotoSize[1];
                 TdApi.File f = new TdApi.File();
                 f.id = 0; // Invalid ID but path matters
                 f.local = new TdApi.LocalFile();
                 f.local.path = localPath;
                 f.local.isDownloadingCompleted = true;
                 f.local.canBeDownloaded = false;
                 f.local.downloadedPrefixSize = 0;
                 f.local.downloadedSize = new File(localPath).length();
                 f.size = f.local.downloadedSize;
                 
                 sizes[0] = new TdApi.PhotoSize("x", f, (int)f.size / 2, (int)f.size / 2, new int[0]);
                 TdApi.Photo photo = new TdApi.Photo();
                 photo.sizes = sizes;
                 photo.hasStickers = false;
                 
                 TdApi.MessagePhoto content = new TdApi.MessagePhoto();
                 content.photo = photo;
                 content.caption = new TdApi.FormattedText(json.optString("caption"), null);
                 return content;
            }
            return new TdApi.MessageText(new TdApi.FormattedText("[Deleted Photo] " + json.optString("caption"), null), null, null);
        } else if ("video".equals(type)) {
             if (localPath != null && !localPath.isEmpty() && new File(localPath).exists()) {
                 TdApi.File f = new TdApi.File();
                 f.local = new TdApi.LocalFile();
                 f.local.path = localPath;
                 f.local.isDownloadingCompleted = true;
                 f.size = new File(localPath).length();
                 
                 TdApi.Video video = new TdApi.Video();
                 video.video = f;
                 video.fileName = "deleted_video.mp4";
                 
                 TdApi.MessageVideo content = new TdApi.MessageVideo();
                 content.video = video;
                 content.caption = new TdApi.FormattedText(json.optString("caption"), null);
                 return content;
             }
             return new TdApi.MessageText(new TdApi.FormattedText("[Deleted Video] " + json.optString("caption"), null), null, null);
        } else if ("document".equals(type)) {
             if (localPath != null && !localPath.isEmpty() && new File(localPath).exists()) {
                 TdApi.File f = new TdApi.File();
                 f.local = new TdApi.LocalFile();
                 f.local.path = localPath;
                 f.local.isDownloadingCompleted = true;
                 f.size = new File(localPath).length();

                 TdApi.Document doc = new TdApi.Document();
                 doc.document = f;
                 doc.fileName = json.optString("fileName", "deleted_file");
                 
                 TdApi.MessageDocument content = new TdApi.MessageDocument();
                 content.document = doc;
                 content.caption = new TdApi.FormattedText(json.optString("caption"), null);
                 return content;
             }
             return new TdApi.MessageText(new TdApi.FormattedText("[Deleted File] " + json.optString("caption"), null), null, null);
        }
        return new TdApi.MessageText(new TdApi.FormattedText("[Deleted Content]", null), null, null);
    }

    private String saveMediaFile(Object mediaSource, long chatId, long messageId) {
        if (savedMessagesDir == null) return null;
        try {
            String sourcePath = null;
            String extension = "";
            
            if (mediaSource instanceof TdApi.PhotoSize[]) {
                // Find biggest downloaded photo
                TdApi.PhotoSize[] sizes = (TdApi.PhotoSize[]) mediaSource;
                TdApi.PhotoSize best = null;
                for (TdApi.PhotoSize sz : sizes) {
                    if (sz.photo.local.isDownloadingCompleted) {
                        if (best == null || sz.width > best.width) {
                            best = sz;
                        }
                    }
                }
                
                if (best != null) {
                    sourcePath = best.photo.local.path;
                    extension = ".jpg";
                    Log.i(TAG, "Selected best photo size: " + best.type + " width=" + best.width + " path=" + sourcePath);
                } else {
                    Log.w(TAG, "No downloaded photo size found for msg " + messageId);
                    // Debug availability
                    for (TdApi.PhotoSize sz : sizes) {
                         Log.d(TAG, "Size " + sz.type + ": downloaded=" + sz.photo.local.isDownloadingCompleted + " path=" + sz.photo.local.path);
                    }
                }
            } else if (mediaSource instanceof TdApi.File) {
                TdApi.File f = (TdApi.File) mediaSource;
                if (f.local.isDownloadingCompleted) {
                    sourcePath = f.local.path;
                    // Determine extension from path or default
                    extension = ".dat"; 
                    if (sourcePath.contains(".")) {
                       extension = sourcePath.substring(sourcePath.lastIndexOf("."));
                    }
                    Log.i(TAG, "File source path: " + sourcePath);
                } else {
                    Log.w(TAG, "File not downloaded for msg " + messageId + " path=" + f.local.path);
                }
            }
            
            if (sourcePath != null && new File(sourcePath).exists()) {
                File chatDir = new File(savedMessagesDir, String.valueOf(chatId));
                if (!chatDir.exists()) chatDir.mkdirs();
                
                File dest = new File(chatDir, "media_" + messageId + extension);
                copyFile(new File(sourcePath), dest);
                Log.i(TAG, "Saved media to: " + dest.getAbsolutePath());
                return dest.getAbsolutePath();
            } else {
                Log.e(TAG, "Source file does not exist or path null: " + sourcePath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save media: " + e);
        }
        return null;
    }

    private void copyFile(File source, File dest) throws Exception {
        try (java.io.FileInputStream is = new java.io.FileInputStream(source);
             java.io.FileOutputStream os = new java.io.FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        }
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
