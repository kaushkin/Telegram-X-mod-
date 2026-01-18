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
    private File ghostCacheDir;
    
    // Cache helper: Store recent messages to grab content when deleted
    // Limit to 500 messages to prevent memory leak
    private final LruCache<Long, TdApi.Message> messageCache = new LruCache<>(500);
    // Map FileID -> MessageIDs to update cache when file downloads
    private final Map<Integer, Set<Long>> fileIdToMessageIds = Collections.synchronizedMap(new HashMap<>());
    private final Set<Long> deletedMessageIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<Long> deletedByMeMessageIds = Collections.synchronizedSet(new HashSet<>());

    private DeletedMessagesManager() {
    }
    
    
    public void cacheMessage(TdApi.Message message) {
        if (!isGhostEnabled()) return;
        
        if (message.isOutgoing) {
            android.util.Log.e(TAG, "Caching OUTGOING message: " + message.id + " from chat: " + message.chatId);
            messageCache.put(message.id, message);
            indexFiles(message);
            return;
        }
        
        int constructor = message.content.getConstructor();
        if (constructor == TdApi.MessageText.CONSTRUCTOR || 
            constructor == TdApi.MessagePhoto.CONSTRUCTOR ||
            constructor == TdApi.MessageVideo.CONSTRUCTOR ||
            constructor == TdApi.MessageDocument.CONSTRUCTOR) {
             android.util.Log.e(TAG, "Caching INCOMING message: " + message.id);
             messageCache.put(message.id, message);
             indexFiles(message);
        }
    }
    
    private void indexFiles(TdApi.Message message) {
        // Disabled by user request to fix crash
        if (true) return;
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
                Log.i(TAG, "Indexed file " + f.id + " for msg " + message.id);
            }
        }
    }
    
    public void updateFile(TdApi.File file) {
        // Log.v(TAG, "updateFile received for " + file.id);
        Set<Long> msgs = fileIdToMessageIds.get(file.id);
        if (msgs != null) {
            Log.i(TAG, "updateFile: Found messages for file " + file.id + ": " + msgs + " completed=" + file.local.isDownloadingCompleted);
            synchronized(msgs) {
                for (Long msgId : msgs) {
                    TdApi.Message cached = messageCache.get(msgId);
                    if (cached != null) {
                        updateMessageFile(cached, file);
                        Log.i(TAG, "Updated cached file in msg " + msgId);
                    }
                }
            }
        }
    }
    

    
    private void updateMessageFile(TdApi.Message message, TdApi.File file) {
        // Pre-cache logic disabled
        /*
        if (file.local.isDownloadingCompleted) {
            String safePath = preCacheFile(file);
            if (safePath != null) {
                file.local.path = safePath;
            }
        }
        */

        if (message.content instanceof TdApi.MessagePhoto) {
            for (TdApi.PhotoSize sz : ((TdApi.MessagePhoto) message.content).photo.sizes) {
                if (sz.photo.id == file.id) {
                    // Regression check: Don't overwrite completed file with incomplete one
                    if (sz.photo.local.isDownloadingCompleted && !file.local.isDownloadingCompleted) {
                         Log.w(TAG, "Ignored regression for file " + file.id);
                         continue;
                    }
                    sz.photo = file; // Update reference
                }
            }
        } else if (message.content instanceof TdApi.MessageVideo) {
            TdApi.Video v = ((TdApi.MessageVideo) message.content).video;
            if (v.video.id == file.id) {
                 if (v.video.local.isDownloadingCompleted && !file.local.isDownloadingCompleted) {
                     Log.w(TAG, "Ignored regression for video " + file.id);
                     return;
                 }
                 v.video = file;
            }
        } else if (message.content instanceof TdApi.MessageDocument) {
             TdApi.Document d = ((TdApi.MessageDocument) message.content).document;
             if (d.document.id == file.id) {
                 if (d.document.local.isDownloadingCompleted && !file.local.isDownloadingCompleted) {
                     Log.w(TAG, "Ignored regression for doc " + file.id);
                     return;
                 }
                 d.document = file;
             }
        }
    }

    private String preCacheFile(TdApi.File file) {
        if (ghostCacheDir == null) return null;
        try {
            String originalPath = file.local.path;
            if (originalPath == null || !new File(originalPath).exists()) return null;
            
            // Generate unique name: file_{id}.ext
            String ext = ".dat";
            if (originalPath.contains(".")) {
                ext = originalPath.substring(originalPath.lastIndexOf("."));
            }
            File dest = new File(ghostCacheDir, "file_" + file.id + ext);
            
            // If already cached, return cached path
            if (dest.exists() && dest.length() > 0) {
                 // Log.d(TAG, "File " + file.id + " already pre-cached: " + dest.getAbsolutePath());
                 return dest.getAbsolutePath();
            }
            
            copyFile(new File(originalPath), dest);
            Log.i(TAG, "ANTIDELETE: Pre-cached file " + file.id + " to " + dest.getAbsolutePath());
            return dest.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to pre-cache file " + file.id + ": " + e.getMessage());
        }
        return null;
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
        this.ghostCacheDir = new File(context.getExternalCacheDir(), "ghost_cache");
        if (!ghostCacheDir.exists()) {
            ghostCacheDir.mkdirs();
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

    // Cache for lists of deleted messages per chat to avoid disk reads
    private final Map<Long, List<TdApi.Message>> chatDeletedMessagesCache = new java.util.concurrent.ConcurrentHashMap<>();

    // ... (existing code) ...

    public void saveMessage(long chatId, TdApi.Message message) {
        // Update memory cache immediately
        List<TdApi.Message> cachedList = chatDeletedMessagesCache.get(chatId);
        if (cachedList != null) {
            synchronized (cachedList) {
                // Remove if exists (update)
                for (int i = 0; i < cachedList.size(); i++) {
                    if (cachedList.get(i).id == message.id) {
                        cachedList.remove(i);
                        break;
                    }
                }
                cachedList.add(message);
                // Sort desc
                Collections.sort(cachedList, (m1, m2) -> Long.compare(m2.id, m1.id));
            }
        }

        new Thread(() -> {
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

            } catch (Exception e) {
                // Ignore
            }
        }).start();
    }

    public List<TdApi.Message> getDeletedMessages(long chatId) {
        // Check cache first
        List<TdApi.Message> cachedList = chatDeletedMessagesCache.get(chatId);
        if (cachedList != null) {
            synchronized (cachedList) {
                return new ArrayList<>(cachedList);
            }
        }

        if (savedMessagesDir == null) {
            return Collections.emptyList();
        }

        File chatDir = new File(savedMessagesDir, String.valueOf(chatId));
        if (!chatDir.exists()) {
             return Collections.emptyList();
        }

        List<TdApi.Message> messages = new ArrayList<>();
        File[] files = chatDir.listFiles();
        if (files == null) {
             return messages;
        }

        for (File f : files) {
            // Only process JSON files, skip media files
            if (!f.getName().endsWith(".json")) continue;
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
                // Ignore
            }
        }
        
        // Sort
        Collections.sort(messages, (m1, m2) -> Long.compare(m2.id, m1.id));
        
        // Populate cache (synchronized list)
        chatDeletedMessagesCache.put(chatId, Collections.synchronizedList(new ArrayList<>(messages)));
        
        return messages;
    }
    
    public void markAsDeletedByMe(long[] messageIds) {
        for (long id : messageIds) {
            deletedByMeMessageIds.add(id);
        }
    }

    public void updateMessageId(long oldId, TdApi.Message newMessage) {
        messageCache.remove(oldId);
        if (newMessage.isOutgoing) {
            messageCache.put(newMessage.id, newMessage);
            indexFiles(newMessage);
        }
    }

    public boolean isDeletedByMe(long messageId) {
        boolean result = deletedByMeMessageIds.contains(messageId);
        if (result) {
            deletedByMeMessageIds.remove(messageId);
        }
        return result;
    }

    public boolean isMessageDeleted(long messageId) {
        return deletedMessageIds.contains(messageId);
    }

    public String getDeletedMessageText(long messageId) {
        TdApi.Message cached = messageCache.get(messageId);
        if (cached == null) return null;
        
        if (cached.content instanceof TdApi.MessageText) {
            TdApi.MessageText textContent = (TdApi.MessageText) cached.content;
            String text = textContent.text != null ? textContent.text.text : null;
            return text;
        }
        
        return null;
    }

    public void deleteGhostMessage(long messageId) {
        messageCache.remove(messageId);
        deletedMessageIds.remove(messageId);
        
        // Update cache
        for (List<TdApi.Message> list : chatDeletedMessagesCache.values()) {
             boolean removed = false;
             synchronized (list) {
                 for (int i = 0; i < list.size(); i++) {
                     if (list.get(i).id == messageId) {
                         list.remove(i);
                         removed = true;
                         break;
                     }
                 }
             }
             if (removed) break;
        }
        
        new Thread(() -> {
            if (savedMessagesDir == null) return;
            
            File[] chatDirs = savedMessagesDir.listFiles();
            if (chatDirs == null) return;
            
            for (File chatDir : chatDirs) {
                if (chatDir != null && chatDir.isDirectory()) {
                    File msgFile = new File(chatDir, messageId + ".json");
                    if (msgFile.exists()) {
                        msgFile.delete();
                        return;
                    }
                }
            }
        }).start();
    }

    private final Map<Long, Long> lastDeletedMessageIds = Collections.synchronizedMap(new HashMap<>());

    public void onMessagesDeleted(final org.thunderdog.challegram.telegram.Tdlib tdlib, final long chatId, final long[] messageIds) {
        if (savedMessagesDir == null) return;
        
        long maxId = lastDeletedMessageIds.containsKey(chatId) ? lastDeletedMessageIds.get(chatId) : 0;
        
        for (final long messageId : messageIds) {
             if (deletedByMeMessageIds.contains(messageId)) {
                 deletedByMeMessageIds.remove(messageId);
                 continue;
             }
             
             deletedMessageIds.add(messageId); 
             if (messageId > maxId) {
                 maxId = messageId;
             }
             
             TdApi.Message cached = messageCache.get(messageId);
             if (cached != null) {
                 saveMessage(chatId, cached);
                 continue;
             }

             // Fallback to GetMessage (likely to fail for deleted messages)
             tdlib.client().send(new TdApi.GetMessage(chatId, messageId), result -> {
                 if (result.getConstructor() == TdApi.Message.CONSTRUCTOR) {
                     saveMessage(chatId, (TdApi.Message) result);
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
            
            // Find best photo size and save dimensions
            TdApi.PhotoSize best = null;
            for (TdApi.PhotoSize sz : photo.photo.sizes) {
                if (sz.photo.local.isDownloadingCompleted) {
                    if (best == null || sz.width > best.width) best = sz;
                }
            }
            if (best != null) {
                obj.put("width", best.width);
                obj.put("height", best.height);
            }

            String savedPath = saveMediaFile(photo.photo.sizes, chatId, messageId);
            if (savedPath != null) {
                obj.put("localPath", savedPath);
            }
        } else if (content instanceof TdApi.MessageVideo) {
            obj.put("type", "video");
            TdApi.MessageVideo video = (TdApi.MessageVideo) content;
            obj.put("caption", video.caption.text);
            obj.put("width", video.video.width);
            obj.put("height", video.video.height);
            
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
                 
                 // Use saved dimensions or safe default (fixes crash)
                 int w = json.optInt("width");
                 int h = json.optInt("height");
                 if (w <= 0) w = 512;
                 if (h <= 0) h = 512;
                 
                 sizes[0] = new TdApi.PhotoSize("x", f, w, h, new int[0]);
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
                 int w = json.optInt("width");
                 int h = json.optInt("height");
                 video.width = w > 0 ? w : 512;
                 video.height = h > 0 ? h : 512;
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
        // Disabled by user request to fix crash
        if (true) return null;
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
                         Log.d(TAG, "Size " + sz.type + " #" + sz.photo.id + ": downloaded=" + sz.photo.local.isDownloadingCompleted + " path=" + sz.photo.local.path);
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
                    Log.w(TAG, "File not downloaded for msg " + messageId + " #" + f.id + " path=" + f.local.path);
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
