package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.data.DeletedMessagesManager;
import org.thunderdog.challegram.data.GhostModeManager;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;

public class GhostSettingsController extends ViewController<Void> implements View.OnClickListener {

    private RecyclerView recyclerView;
    private SettingsAdapter adapter;

    // Ghost Messages (existing)
    private static final int ID_ENABLE_GHOST_MESSAGES = 1001;
    private static final int ID_ENABLE_EDIT_HISTORY = 1002;
    private static final int ID_CLEAR_GHOSTS = 1003;
    
    // Ghost Mode (new)
    private static final int ID_GHOST_MODE = 2001;
    private static final int ID_DONT_READ = 2002;
    private static final int ID_DONT_TYPE = 2003;
    private static final int ID_READ_ON_INTERACT = 2004;
    private static final int ID_DONT_ONLINE = 2005;
    private static final int ID_DRAWER_SETTINGS = 2006;
    private static final int ID_DEVELOPER = 2007;

    public GhostSettingsController (Context context, Tdlib tdlib) {
        super(context, tdlib);
    }

    @Override
    public int getId() {
        return R.id.controller_privacySettings;
    }
    
    @Override
    public CharSequence getName() {
        return "Настройки kaimod";
    }

    @Override
    protected int getBackButton () {
        return BackHeaderButton.TYPE_BACK;
    }

    @Override
    protected View onCreateView(Context context) {
        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutParams(FrameLayoutFix.newParams(
            FrameLayoutFix.LayoutParams.MATCH_PARENT, 
            FrameLayoutFix.LayoutParams.MATCH_PARENT));
        recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        ViewSupport.setThemedBackground(recyclerView, ColorId.background, this);
        
        adapter = new SettingsAdapter(this) {
            @Override
            protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
                final int itemId = item.getId();
                boolean ghostEnabled = GhostModeManager.getInstance().isGhostModeEnabled();
                
                // Ghost Messages settings
                if (itemId == ID_ENABLE_GHOST_MESSAGES) {
                    view.getToggler().setRadioEnabled(DeletedMessagesManager.getInstance().isGhostEnabled(), isUpdate);
                } else if (itemId == ID_ENABLE_EDIT_HISTORY) {
                    view.getToggler().setRadioEnabled(DeletedMessagesManager.getInstance().isEditHistoryEnabled(), isUpdate);
                }
                // Ghost Mode main toggle
                else if (itemId == ID_GHOST_MODE) {
                    view.getToggler().setRadioEnabled(ghostEnabled, isUpdate);
                }
                // Ghost Mode sub-settings - only enabled when ghost mode is on
                else if (itemId == ID_DONT_READ) {
                    view.getToggler().setRadioEnabled(ghostEnabled && GhostModeManager.getInstance().isDontReadEnabled(), isUpdate);
                    view.setEnabled(ghostEnabled);
                    view.setAlpha(ghostEnabled ? 1.0f : 0.5f);
                } else if (itemId == ID_DONT_TYPE) {
                    view.getToggler().setRadioEnabled(ghostEnabled && GhostModeManager.getInstance().isDontTypeEnabled(), isUpdate);
                    view.setEnabled(ghostEnabled);
                    view.setAlpha(ghostEnabled ? 1.0f : 0.5f);
                } else if (itemId == ID_READ_ON_INTERACT) {
                    view.getToggler().setRadioEnabled(ghostEnabled && GhostModeManager.getInstance().isReadOnInteractEnabled(), isUpdate);
                    view.setEnabled(ghostEnabled);
                    view.setAlpha(ghostEnabled ? 1.0f : 0.5f);
                } else if (itemId == ID_DONT_ONLINE) {
                    view.getToggler().setRadioEnabled(ghostEnabled && GhostModeManager.getInstance().isDontOnlineEnabled(), isUpdate);
                    view.setEnabled(ghostEnabled);
                    view.setAlpha(ghostEnabled ? 1.0f : 0.5f);
                }
            }
        };
        
        ArrayList<ListItem> items = new ArrayList<>();
        
        // ========== GHOST MODE SECTION ==========
        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, "Режим призрака"));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_GHOST_MODE, R.drawable.baseline_visibility_24, "Включить режим призрака"));
        
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_DONT_READ, R.drawable.baseline_done_all_24, "Не читать сообщения"));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_DONT_TYPE, R.drawable.baseline_keyboard_24, "Не отправлять «печатает»"));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_DONT_ONLINE, R.drawable.baseline_eye_off_24, "Скрывать онлайн"));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_READ_ON_INTERACT, R.drawable.baseline_gesture_24, "Читать при действиях"));
        
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, "Режим призрака скрывает статусы прочтения и набора текста."));

        // ========== DRAWER SETTINGS ==========
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, "Боковое меню"));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_SETTING, ID_DRAWER_SETTINGS, R.drawable.baseline_settings_24, "Настройки бокового меню"));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, "Скрыть или показать элементы бокового меню."));
        
        // ========== SAVED MESSAGES SECTION ==========
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, "Удаленные сообщения"));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_ENABLE_GHOST_MESSAGES, R.drawable.baseline_delete_24, "Сохранять удаленные"));
        
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, "Удаленные сообщения будут сохраняться локально."));
        
        // Edit History
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, "История редактирования"));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_ENABLE_EDIT_HISTORY, R.drawable.baseline_history_24, "Сохранять историю изменений"));
        
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, "При редактировании сообщения старые версии будут сохраняться."));
        
        // Clear section  
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_SETTING, ID_CLEAR_GHOSTS, R.drawable.baseline_delete_forever_24, "Очистить всю историю"));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, "Разработчик мода"));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_SETTING, ID_DEVELOPER, R.drawable.baseline_person_24, "@pvumu"));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, "Связаться с разработчиком мода."));
        
        adapter.setItems(items, false);
        recyclerView.setAdapter(adapter);

        return recyclerView;
    }
    
    private void updateGhostSubSettings() {
        adapter.updateValuedSettingById(ID_DONT_READ);
        adapter.updateValuedSettingById(ID_DONT_TYPE);
        adapter.updateValuedSettingById(ID_DONT_ONLINE);
        adapter.updateValuedSettingById(ID_READ_ON_INTERACT);
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        boolean ghostEnabled = GhostModeManager.getInstance().isGhostModeEnabled();
        
        // Ghost Messages settings
        if (id == ID_ENABLE_GHOST_MESSAGES) {
            boolean newState = !DeletedMessagesManager.getInstance().isGhostEnabled();
            DeletedMessagesManager.getInstance().setGhostEnabled(newState);
            adapter.updateValuedSettingById(ID_ENABLE_GHOST_MESSAGES);
        } else if (id == ID_ENABLE_EDIT_HISTORY) {
            boolean newState = !DeletedMessagesManager.getInstance().isEditHistoryEnabled();
            DeletedMessagesManager.getInstance().setEditHistoryEnabled(newState);
            adapter.updateValuedSettingById(ID_ENABLE_EDIT_HISTORY);
        } else if (id == ID_CLEAR_GHOSTS) {
            DeletedMessagesManager.getInstance().clearAllGhosts();
            UI.showToast("История очищена", Toast.LENGTH_SHORT);
        }
        
        // Ghost Mode main toggle
        else if (id == ID_GHOST_MODE) {
            boolean newState = !ghostEnabled;
            GhostModeManager.getInstance().setGhostModeEnabled(newState);
            
            // When turning ON, enable all sub-settings automatically
            if (newState) {
                GhostModeManager.getInstance().setDontReadEnabled(true);
                GhostModeManager.getInstance().setDontTypeEnabled(true);
                GhostModeManager.getInstance().setDontOnlineEnabled(true);
                GhostModeManager.getInstance().setReadOnInteractEnabled(true);
                UI.showToast("Режим призрака включен 👻", Toast.LENGTH_SHORT);
            } else {
                UI.showToast("Режим призрака выключен", Toast.LENGTH_SHORT);
            }
            
            adapter.updateValuedSettingById(ID_GHOST_MODE);
            adapter.updateValuedSettingById(ID_GHOST_MODE);
            updateGhostSubSettings();
        } else if (id == ID_DRAWER_SETTINGS) {
            UI.navigateTo(new DrawerPreferencesController(context, tdlib));
        } else if (id == ID_DEVELOPER) {
            tdlib.ui().openUrl(this, "https://t.me/pvumu", new org.thunderdog.challegram.telegram.TdlibUi.UrlOpenParameters());
        }
        
        // Ghost Mode sub-settings - only work when ghost mode is enabled
        else if (id == ID_DONT_READ) {
            if (!ghostEnabled) {
                UI.showToast("Сначала включите режим призрака", Toast.LENGTH_SHORT);
                return;
            }
            boolean newState = !GhostModeManager.getInstance().isDontReadEnabled();
            GhostModeManager.getInstance().setDontReadEnabled(newState);
            adapter.updateValuedSettingById(ID_DONT_READ);
        } else if (id == ID_DONT_TYPE) {
            if (!ghostEnabled) {
                UI.showToast("Сначала включите режим призрака", Toast.LENGTH_SHORT);
                return;
            }
            boolean newState = !GhostModeManager.getInstance().isDontTypeEnabled();
            GhostModeManager.getInstance().setDontTypeEnabled(newState);
            adapter.updateValuedSettingById(ID_DONT_TYPE);
        } else if (id == ID_READ_ON_INTERACT) {
            if (!ghostEnabled) {
                UI.showToast("Сначала включите режим призрака", Toast.LENGTH_SHORT);
                return;
            }
            boolean newState = !GhostModeManager.getInstance().isReadOnInteractEnabled();
            GhostModeManager.getInstance().setReadOnInteractEnabled(newState);
            adapter.updateValuedSettingById(ID_READ_ON_INTERACT);
        } else if (id == ID_DONT_ONLINE) {
            if (!ghostEnabled) {
                UI.showToast("Сначала включите режим призрака", Toast.LENGTH_SHORT);
                return;
            }
            boolean newState = !GhostModeManager.getInstance().isDontOnlineEnabled();
            GhostModeManager.getInstance().setDontOnlineEnabled(newState);
            adapter.updateValuedSettingById(ID_DONT_ONLINE);
        }
    }
}
