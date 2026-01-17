package org.thunderdog.challegram.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Manager for Ghost Mode feature - allows hiding read receipts, typing indicators, etc.
 */
public class GhostModeManager {
    
    private static final GhostModeManager INSTANCE = new GhostModeManager();
    
    private SharedPreferences prefs;
    
    // Preference keys
    private static final String PREF_FILE = "ghost_mode_settings";
    private static final String KEY_GHOST_ENABLED = "ghost_mode_enabled";
    private static final String KEY_DONT_READ = "dont_read";
    private static final String KEY_DONT_TYPE = "dont_type";
    private static final String KEY_DONT_ONLINE = "dont_online";
    private static final String KEY_READ_ON_INTERACT = "read_on_interact";
    
    // Local read tracking - map of chatId -> unread count offset (subtrahend)
    private final Map<Long, Integer> chatUnreadOffsets = new ConcurrentHashMap<>();

    // ... (Keep existing methods until we replace them)

    // ========== Local Read Tracking ==========
    
    /**
     * Mark a chat as locally read by saving the current unread count as an offset.
     * @param chatId The chat ID
     * @param currentUnreadCount The current unread count from TdApi.Chat
     */
    public void markChatLocallyRead(long chatId, int currentUnreadCount) {
        if (currentUnreadCount > 0) {
            chatUnreadOffsets.put(chatId, currentUnreadCount);
        }
    }
    
    /**
     * Get the subtrahend (offset) for the unread count calculation
     */
    public int getChatUnreadOffset(long chatId) {
        Integer offset = chatUnreadOffsets.get(chatId);
        return offset != null ? offset : 0;
    }

    /**
     * Check if chat is marked as locally read (has an offset)
     */
    public boolean isChatLocallyRead(long chatId) {
        return chatUnreadOffsets.containsKey(chatId);
    }
    
    /**
     * Clear locally read status for a chat
     */
    public void clearLocallyRead(long chatId) {
        chatUnreadOffsets.remove(chatId);
    }
}
