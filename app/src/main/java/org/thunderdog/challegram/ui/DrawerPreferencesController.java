package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.data.GhostModeManager;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;

public class DrawerPreferencesController extends ViewController<Void> implements View.OnClickListener {

    private RecyclerView recyclerView;
    private SettingsAdapter adapter;

    // IDs for toggles
    private static final int ID_CONTACTS = 3001;
    private static final int ID_CALLS = 3002;
    private static final int ID_SAVED_MESSAGES = 3003;
    private static final int ID_SETTINGS = 3004;
    private static final int ID_KAIMOD = 3005;
    private static final int ID_INVITE = 3006;
    private static final int ID_PROXY = 3007;
    private static final int ID_HELP = 3008;
    private static final int ID_NIGHT_MODE = 3009;
    private static final int ID_FEATURE_TOGGLES = 3010;
    private static final int ID_DEBUG_LOGS = 3011;

    public DrawerPreferencesController (Context context, Tdlib tdlib) {
        super(context, tdlib);
    }

    @Override
    public int getId() {
        return R.id.controller_drawer_settings;
    }
    
    @Override
    public CharSequence getName() {
        return "Настройки меню";
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
                GhostModeManager manager = GhostModeManager.getInstance();
                
                if (itemId == ID_CONTACTS) {
                    view.getToggler().setRadioEnabled(manager.isDrawerItemVisible(GhostModeManager.KEY_DRAWER_CONTACTS), isUpdate);
                } else if (itemId == ID_CALLS) {
                    view.getToggler().setRadioEnabled(manager.isDrawerItemVisible(GhostModeManager.KEY_DRAWER_CALLS), isUpdate);
                } else if (itemId == ID_SAVED_MESSAGES) {
                    view.getToggler().setRadioEnabled(manager.isDrawerItemVisible(GhostModeManager.KEY_DRAWER_SAVED_MESSAGES), isUpdate);
                } else if (itemId == ID_SETTINGS) {
                    view.getToggler().setRadioEnabled(manager.isDrawerItemVisible(GhostModeManager.KEY_DRAWER_SETTINGS), isUpdate);
                } else if (itemId == ID_KAIMOD) {
                    view.getToggler().setRadioEnabled(manager.isDrawerItemVisible(GhostModeManager.KEY_DRAWER_KAIMOD), isUpdate);
                    // Prevent disabling kaimod from here to avoid lockout, or warn
                } else if (itemId == ID_INVITE) {
                    view.getToggler().setRadioEnabled(manager.isDrawerItemVisible(GhostModeManager.KEY_DRAWER_INVITE), isUpdate);
                } else if (itemId == ID_PROXY) {
                    view.getToggler().setRadioEnabled(manager.isDrawerItemVisible(GhostModeManager.KEY_DRAWER_PROXY), isUpdate);
                } else if (itemId == ID_HELP) {
                    view.getToggler().setRadioEnabled(manager.isDrawerItemVisible(GhostModeManager.KEY_DRAWER_HELP), isUpdate);
                } else if (itemId == ID_NIGHT_MODE) {
                    view.getToggler().setRadioEnabled(manager.isDrawerItemVisible(GhostModeManager.KEY_DRAWER_NIGHT_MODE), isUpdate);
                } else if (itemId == ID_FEATURE_TOGGLES) {
                    view.getToggler().setRadioEnabled(manager.isDrawerItemVisible(GhostModeManager.KEY_DRAWER_FEATURE_TOGGLES), isUpdate);
                } else if (itemId == ID_DEBUG_LOGS) {
                    view.getToggler().setRadioEnabled(manager.isDrawerItemVisible(GhostModeManager.KEY_DRAWER_DEBUG_LOGS), isUpdate);
                }
            }
        };
        
        ArrayList<ListItem> items = new ArrayList<>();
        
        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, "Элементы меню"));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_CONTACTS, R.drawable.baseline_perm_contact_calendar_24, R.string.Contacts));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_CALLS, R.drawable.baseline_call_24, R.string.Calls));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_SAVED_MESSAGES, R.drawable.baseline_bookmark_24, R.string.SavedMessages));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_SETTINGS, R.drawable.baseline_settings_24, R.string.Settings));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_KAIMOD, R.drawable.baseline_bug_report_24, "kaimod"));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_INVITE, R.drawable.baseline_person_add_24, R.string.InviteFriends));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_PROXY, R.drawable.baseline_security_24, R.string.Proxy));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_HELP, R.drawable.baseline_help_24, R.string.Help));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_NIGHT_MODE, R.drawable.baseline_brightness_2_24, R.string.NightMode));

        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, "Выберите элементы, которые будут отображаться в боковом меню."));
        
        // Debug/Experimental
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, "Отладка"));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_FEATURE_TOGGLES, R.drawable.outline_toggle_on_24, "Feature Toggles"));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, ID_DEBUG_LOGS, R.drawable.baseline_bug_report_24, "TDLib Logs"));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

        adapter.setItems(items, false);
        recyclerView.setAdapter(adapter);

        return recyclerView;
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        GhostModeManager manager = GhostModeManager.getInstance();
        String key = null;

        if (id == ID_CONTACTS) key = GhostModeManager.KEY_DRAWER_CONTACTS;
        else if (id == ID_CALLS) key = GhostModeManager.KEY_DRAWER_CALLS;
        else if (id == ID_SAVED_MESSAGES) key = GhostModeManager.KEY_DRAWER_SAVED_MESSAGES;
        else if (id == ID_SETTINGS) key = GhostModeManager.KEY_DRAWER_SETTINGS;
        else if (id == ID_KAIMOD) key = GhostModeManager.KEY_DRAWER_KAIMOD;
        else if (id == ID_INVITE) key = GhostModeManager.KEY_DRAWER_INVITE;
        else if (id == ID_PROXY) key = GhostModeManager.KEY_DRAWER_PROXY;
        else if (id == ID_HELP) key = GhostModeManager.KEY_DRAWER_HELP;
        else if (id == ID_NIGHT_MODE) key = GhostModeManager.KEY_DRAWER_NIGHT_MODE;
        else if (id == ID_FEATURE_TOGGLES) key = GhostModeManager.KEY_DRAWER_FEATURE_TOGGLES;
        else if (id == ID_DEBUG_LOGS) key = GhostModeManager.KEY_DRAWER_DEBUG_LOGS;

        if (key != null) {
            boolean currentState = manager.isDrawerItemVisible(key);
            manager.setDrawerItemVisible(key, !currentState);
            adapter.updateValuedSettingById(id);
            
            // Notify user if they hid kaimod
            if (id == ID_KAIMOD && currentState) { // was true, now false
                UI.showToast("kaimod скрыт из меню", Toast.LENGTH_SHORT);
            }
        }
    }
}
