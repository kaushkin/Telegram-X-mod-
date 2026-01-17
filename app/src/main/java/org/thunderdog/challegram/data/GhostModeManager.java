package org.thunderdog.challegram.data;

import android.content.Context;
import android.content.SharedPreferences;

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
    
    public static GhostModeManager getInstance() {
        return INSTANCE;
    }
    
    public void init(Context context) {
        this.prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }
    
    // ========== Global Ghost Mode Toggle ==========
    
    public boolean isGhostModeEnabled() {
        return prefs != null && prefs.getBoolean(KEY_GHOST_ENABLED, false);
    }
    
    public void setGhostModeEnabled(boolean enabled) {
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
}
