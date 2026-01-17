package org.thunderdog.challegram.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for Ghost Mode feature - allows hiding read receipts, typing indicators, etc.
 */
public class GhostModeManager {
    
    private static final GhostModeManager INSTANCE = new GhostModeManager();
    
    public interface Listener {
        void onDrawerItemsChanged();
    }
    
    private final java.util.List<Listener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
    
    private void notifyDrawerItemsChanged() {
        for (Listener listener : listeners) {
            listener.onDrawerItemsChanged();
        }
    }
    
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
    
    public static GhostModeManager getInstance() {
        return INSTANCE;
    }
    
    public void init(Context context) {
        if (context == null) {
            android.util.Log.e("GHOST_MODE", "GhostModeManager.init called with NULL context!");
            return;
        }
        this.prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        android.util.Log.i("GHOST_MODE", "GhostModeManager initialized with context: " + context + ", prefs: " + prefs);
    }
    
    // ========== Global Ghost Mode Toggle ==========
    
    public boolean isGhostModeEnabled() {
        if (prefs == null) {
            android.util.Log.w("GHOST_MODE", "isGhostModeEnabled called but prefs is null!");
            return false;
        }
        boolean enabled = prefs.getBoolean(KEY_GHOST_ENABLED, false);
        // Too verbose? android.util.Log.v("GHOST_MODE", "isGhostModeEnabled: " + enabled);
        return enabled;
    }
    
    public void setGhostModeEnabled(boolean enabled) {
        android.util.Log.i("GHOST_MODE", "Setting GhostModeEnabled: " + enabled);
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_GHOST_ENABLED, enabled).apply();
        }
    }
    
    // ========== Individual Settings ==========
    
    /**
     * Don't send read receipts (ViewMessages)
     */
    public boolean isDontReadEnabled() {
        return prefs != null && prefs.getBoolean(KEY_DONT_READ, true);
    }
    
    public void setDontReadEnabled(boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_DONT_READ, enabled).apply();
        }
    }
    
    /**
     * Don't send typing indicators (SendChatAction)
     */
    public boolean isDontTypeEnabled() {
        return prefs != null && prefs.getBoolean(KEY_DONT_TYPE, true);
    }
    
    public void setDontTypeEnabled(boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_DONT_TYPE, enabled).apply();
        }
    }
    
    /**
     * Don't show online status
     */
    public boolean isDontOnlineEnabled() {
        return prefs != null && prefs.getBoolean(KEY_DONT_ONLINE, false);
    }
    
    public void setDontOnlineEnabled(boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_DONT_ONLINE, enabled).apply();
        }
    }
    
    /**
     * Read messages when sending/reacting (bypass ghost mode when interacting)
     */
    public boolean isReadOnInteractEnabled() {
        return prefs != null && prefs.getBoolean(KEY_READ_ON_INTERACT, true);
    }
    
    public void setReadOnInteractEnabled(boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_READ_ON_INTERACT, enabled).apply();
        }
    }
    
    // ========== Logic Helpers ==========
    
    /**
     * Check if read receipt should be blocked
     */
    public boolean shouldBlockReadReceipt() {
        return isGhostModeEnabled() && isDontReadEnabled();
    }
    
    /**
     * Check if typing indicator should be blocked
     */
    public boolean shouldBlockTyping() {
        return isGhostModeEnabled() && isDontTypeEnabled();
    }
    
    /**
     * Check if online status should be hidden
     */
    public boolean shouldHideOnline() {
        return isGhostModeEnabled() && isDontOnlineEnabled();
    }
    
    /**
     * Check if should read on interact (send message, reaction, etc.)
     */
    public boolean shouldReadOnInteract() {
        return isGhostModeEnabled() && isReadOnInteractEnabled();
    }
    
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

    // ========== Drawer Customization ==========

    public static final String KEY_DRAWER_CONTACTS = "drawer_contacts";
    public static final String KEY_DRAWER_CALLS = "drawer_calls";
    public static final String KEY_DRAWER_SAVED_MESSAGES = "drawer_saved_messages";
    public static final String KEY_DRAWER_SETTINGS = "drawer_settings";
    public static final String KEY_DRAWER_KAIMOD = "drawer_kaimod";
    public static final String KEY_DRAWER_INVITE = "drawer_invite";
    public static final String KEY_DRAWER_PROXY = "drawer_proxy";
    public static final String KEY_DRAWER_HELP = "drawer_help";
    public static final String KEY_DRAWER_NIGHT_MODE = "drawer_night_mode";
    public static final String KEY_DRAWER_FEATURE_TOGGLES = "drawer_feature_toggles";
    public static final String KEY_DRAWER_DEBUG_LOGS = "drawer_debug_logs";

    public boolean isDrawerItemVisible(String key) {
        if (prefs == null) return true;
        if (key.equals(KEY_DRAWER_FEATURE_TOGGLES) || key.equals(KEY_DRAWER_DEBUG_LOGS)) {
            return prefs.getBoolean(key, false);
        }
        return prefs.getBoolean(key, true);
    }

    public void setDrawerItemVisible(String key, boolean visible) {
        if (prefs != null) {
            prefs.edit().putBoolean(key, visible).apply();
            notifyDrawerItemsChanged();
        }
    }
}
