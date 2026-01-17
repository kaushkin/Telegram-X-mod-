package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.data.DeletedMessagesManager;
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

    private static final int ID_ENABLE_GHOST = 1001;
    private static final int ID_ENABLE_EDIT_HISTORY = 1002;
    private static final int ID_CLEAR_GHOSTS = 1003;

    public GhostSettingsController (Context context, Tdlib tdlib) {
        super(context, tdlib);
    }

    @Override
    public int getId() {
        return R.id.controller_privacySettings; // Reuse existing ID
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
                if (itemId == ID_ENABLE_GHOST) {
                    view.getToggler().setRadioEnabled(DeletedMessagesManager.getInstance().isGhostEnabled(), isUpdate);
                } else if (itemId == ID_ENABLE_EDIT_HISTORY) {
                    view.getToggler().setRadioEnabled(DeletedMessagesManager.getInstance().isEditHistoryEnabled(), isUpdate);
                }
            }
        };
        
        ArrayList<ListItem> items = new ArrayList<>();
        
        // Header - Deleted Messages
        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, "Удаленные сообщения"));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_ENABLE_GHOST, R.drawable.baseline_visibility_24, "Сохранять удаленные"));
        
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, "Удаленные сообщения будут сохраняться локально и отмечаться как призраки"));
        
        // Header - Edit History
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, "История редактирования"));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_ENABLE_EDIT_HISTORY, R.drawable.baseline_history_24, "Сохранять историю изменений"));
        
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, "При редактировании сообщения старые версии будут сохраняться"));
        
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
        if (id == ID_ENABLE_GHOST) {
            boolean newState = !DeletedMessagesManager.getInstance().isGhostEnabled();
            DeletedMessagesManager.getInstance().setGhostEnabled(newState);
            adapter.updateValuedSettingById(ID_ENABLE_GHOST);
        } else if (id == ID_ENABLE_EDIT_HISTORY) {
            boolean newState = !DeletedMessagesManager.getInstance().isEditHistoryEnabled();
            DeletedMessagesManager.getInstance().setEditHistoryEnabled(newState);
            adapter.updateValuedSettingById(ID_ENABLE_EDIT_HISTORY);
        } else if (id == ID_CLEAR_GHOSTS) {
            DeletedMessagesManager.getInstance().clearAllGhosts();
            UI.showToast("История очищена", Toast.LENGTH_SHORT);
        }
    }
}
