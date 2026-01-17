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
                // Ghost Messages settings
                if (itemId == ID_ENABLE_GHOST_MESSAGES) {
                    view.getToggler().setRadioEnabled(DeletedMessagesManager.getInstance().isGhostEnabled(), isUpdate);
                } else if (itemId == ID_ENABLE_EDIT_HISTORY) {
                    view.getToggler().setRadioEnabled(DeletedMessagesManager.getInstance().isEditHistoryEnabled(), isUpdate);
                }
                // Ghost Mode settings
                else if (itemId == ID_GHOST_MODE) {
                    view.getToggler().setRadioEnabled(GhostModeManager.getInstance().isGhostModeEnabled(), isUpdate);
                } else if (itemId == ID_DONT_READ) {
                    view.getToggler().setRadioEnabled(GhostModeManager.getInstance().isDontReadEnabled(), isUpdate);
                } else if (itemId == ID_DONT_TYPE) {
                    view.getToggler().setRadioEnabled(GhostModeManager.getInstance().isDontTypeEnabled(), isUpdate);
                } else if (itemId == ID_READ_ON_INTERACT) {
                    view.getToggler().setRadioEnabled(GhostModeManager.getInstance().isReadOnInteractEnabled(), isUpdate);
                }
            }
        };
        
        ArrayList<ListItem> items = new ArrayList<>();
        
        // ========== GHOST MODE SECTION ==========
        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, "Режим призрака"));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_GHOST_MODE, R.drawable.baseline_visibility_24, "Включить режим призрака"));
        
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, "Главный переключатель режима призрака. Включите, чтобы активировать настройки ниже."));
        
        // Ghost Mode sub-settings
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_DONT_READ, R.drawable.baseline_done_all_24, "Не читать сообщения"));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_DONT_TYPE, R.drawable.baseline_keyboard_24, "Не отправлять «печатает»"));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_READ_ON_INTERACT, R.drawable.baseline_gesture_24, "Читать при действиях"));
        
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, "«Читать при действиях» - отправлять прочтение при ответе на сообщение или реакции."));
        
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
        
        adapter.setItems(items, false);
        recyclerView.setAdapter(adapter);

        return recyclerView;
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        
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
        
        // Ghost Mode settings
        else if (id == ID_GHOST_MODE) {
            boolean newState = !GhostModeManager.getInstance().isGhostModeEnabled();
            GhostModeManager.getInstance().setGhostModeEnabled(newState);
            adapter.updateValuedSettingById(ID_GHOST_MODE);
            if (newState) {
                UI.showToast("Режим призрака включен 👻", Toast.LENGTH_SHORT);
            } else {
                UI.showToast("Режим призрака выключен", Toast.LENGTH_SHORT);
            }
        } else if (id == ID_DONT_READ) {
            boolean newState = !GhostModeManager.getInstance().isDontReadEnabled();
            GhostModeManager.getInstance().setDontReadEnabled(newState);
            adapter.updateValuedSettingById(ID_DONT_READ);
        } else if (id == ID_DONT_TYPE) {
            boolean newState = !GhostModeManager.getInstance().isDontTypeEnabled();
            GhostModeManager.getInstance().setDontTypeEnabled(newState);
            adapter.updateValuedSettingById(ID_DONT_TYPE);
        } else if (id == ID_READ_ON_INTERACT) {
            boolean newState = !GhostModeManager.getInstance().isReadOnInteractEnabled();
            GhostModeManager.getInstance().setReadOnInteractEnabled(newState);
            adapter.updateValuedSettingById(ID_READ_ON_INTERACT);
        }
    }
}
