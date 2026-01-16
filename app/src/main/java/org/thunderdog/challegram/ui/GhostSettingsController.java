package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.DeletedMessagesManager;
import org.thunderdog.challegram.navigation.DrawerItemView;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.TimerView;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.collection.IntList;

public class GhostSettingsController extends ViewController<Void> implements View.OnClickListener {

    private RecyclerView recyclerView;
    private SettingsAdapter adapter;

    private static final int ID_ENABLE_GHOST = 1001;
    private static final int ID_CLEAR_GHOSTS = 1002;

    public GhostSettingsController (Context context, Tdlib tdlib) {
        super(context, tdlib);
    }

    @Override
    public int getId() {
        return 199001;
    }

    @Override
    protected View onCreateView(Context context) {
        FrameLayoutFix frame = new FrameLayoutFix(context);
        frame.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ViewSupport.setThemedBackground(frame, ColorId.background, this);

        // Header
        HeaderView header = new HeaderView(context);
        header.setTitle("Настройки удаления"); // Saved Messages Settings
        header.setBackArrow(new HeaderButton(context).setIcon(R.drawable.baseline_arrow_back_24).setOnClickListener(v -> close()));
        header.setBackgroundColor(ColorId.header);
        addThemeInvalidateListener(header);
        
        // RecyclerView
        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setPadding(0, header.getLayoutParams().height + 150, 0, 0); // Offset for header approximately
        // Better handling of margins/header
        FrameLayoutFix.LayoutParams recyclerParams = new FrameLayoutFix.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        // recyclerParams.topMargin = ... we should use correct layout management
        
        // Let's use simple layout for now: Header on top, recycler fills
        // But HeaderView in FrameLayout stays on top usually.
        // We'll set padding to RecyclerView equal to header height + status bar.
        
        adapter = new SettingsAdapter(this) {
             @Override
             protected void setDrawerItem (ListItem item, DrawerItemView view, TimerView timerView, boolean isUpdate) {
             }
        };
        
        ArrayList<ListItem> items = new ArrayList<>();
        
        // Enable Ghost (Toggle)
        boolean isEnabled = DeletedMessagesManager.getInstance().isGhostEnabled();
        items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, ID_ENABLE_GHOST, R.drawable.baseline_visibility_24, "Сохранять удаленные").setBoolValue(isEnabled));
        
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));

        // Clear Ghosts (Button)
        items.add(new ListItem(ListItem.TYPE_SETTING, ID_CLEAR_GHOSTS, R.drawable.baseline_delete_forever_24, "Очистить историю"));

        adapter.setItems(items, false);
        recyclerView.setAdapter(adapter);
        
        frame.addView(recyclerView);
        frame.addView(header); // Add header last to be on top

        return frame;
    }
    
    @Override
    public void onActivityResume() {
        super.onActivityResume();
        // Adjust padding for header
        if (getApplication().getMainExecutor() != null) { // Simple check
           // UI.run(() -> {}); 
        }
    }

    @Override
    public boolean supportsBottomInset() {
        return true;
    }
    
    @Override
    protected void onBottomInsetChanged(int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
        super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
        Views.applyBottomInset(recyclerView, extraBottomInset);
    }
    
    @Override
    public void dispatchSystemInsets(View parentView, ViewGroup.MarginLayoutParams originalParams, android.graphics.Rect legacyInsets, android.graphics.Rect insets, android.graphics.Rect insetsWithoutIme, android.graphics.Rect systemInsets, android.graphics.Rect systemInsetsWithoutIme, boolean fitsSystemWindows) {
        super.dispatchSystemInsets(parentView, originalParams, legacyInsets, insets, insetsWithoutIme, systemInsets, systemInsetsWithoutIme, fitsSystemWindows);
        // header height + status bar
        recyclerView.setPadding(0, systemInsets.top + me.vkryl.core.Screen.dp(56), 0, systemInsets.bottom);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == ID_ENABLE_GHOST) {
            boolean newState = !DeletedMessagesManager.getInstance().isGhostEnabled();
            DeletedMessagesManager.getInstance().setGhostEnabled(newState);
            
            ListItem item = adapter.getItemById(id);
            if (item != null) {
                // In SettingsAdapter, toggling is often handled by setToggled
                adapter.setToggled(item, newState);
            }
        } else if (id == ID_CLEAR_GHOSTS) {
            DeletedMessagesManager.getInstance().clearAllGhosts();
            UI.showToast("История очищена", Toast.LENGTH_SHORT);
        }
    }
}
