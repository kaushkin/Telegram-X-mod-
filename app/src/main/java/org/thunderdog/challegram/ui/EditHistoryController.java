package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.data.DeletedMessagesManager;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageText;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Controller showing edit history of a message as proper message bubbles
 */
public class EditHistoryController extends ViewController<EditHistoryController.Args> {

    public static class Args {
        public final long chatId;
        public final long messageId;
        public final TdApi.Message originalMessage;
        
        public Args(long chatId, long messageId, TdApi.Message original) {
            this.chatId = chatId;
            this.messageId = messageId;
            this.originalMessage = original;
        }
    }

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private List<DeletedMessagesManager.EditHistoryEntry> historyEntries;

    public EditHistoryController(Context context, Tdlib tdlib) {
        super(context, tdlib);
    }

    @Override
    public int getId() {
        return R.id.controller_messageSeen; // reuse existing ID
    }

    @Override
    public CharSequence getName() {
        return "История изменений";
    }

    @Override
    protected int getBackButton() {
        return BackHeaderButton.TYPE_BACK;
    }

    @Override
    protected View onCreateView(Context context) {
        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutParams(FrameLayoutFix.newParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        ViewSupport.setThemedBackground(recyclerView, ColorId.chatBackground, this);

        // Load history
        Args args = getArgumentsStrict();
        historyEntries = DeletedMessagesManager.getInstance().getEditHistory(args.chatId, args.messageId);
        
        adapter = new HistoryAdapter(context, historyEntries, args.originalMessage);
        recyclerView.setAdapter(adapter);

        return recyclerView;
    }

    /**
     * Simple adapter showing edit history entries as styled text items
     */
    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
        
        private final Context context;
        private final List<DeletedMessagesManager.EditHistoryEntry> entries;
        private final TdApi.Message originalMessage;
        private final SimpleDateFormat dateFormat;

        public HistoryAdapter(Context ctx, List<DeletedMessagesManager.EditHistoryEntry> entries, TdApi.Message original) {
            this.context = ctx;
            this.entries = entries;
            this.originalMessage = original;
            this.dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        @Override
        public HistoryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            HistoryItemView view = new HistoryItemView(context);
            view.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(HistoryViewHolder holder, int position) {
            DeletedMessagesManager.EditHistoryEntry entry = entries.get(position);
            String dateStr = dateFormat.format(new Date(entry.timestamp));
            String text = entry.getContentText();
            holder.itemView.bind(dateStr, text, position + 1, entries.size());
        }

        class HistoryViewHolder extends RecyclerView.ViewHolder {
            public final HistoryItemView itemView;
            
            public HistoryViewHolder(HistoryItemView itemView) {
                super(itemView);
                this.itemView = itemView;
            }
        }
    }

    /**
     * Custom view for displaying a single history entry styled like a message bubble
     */
    private class HistoryItemView extends FrameLayoutFix {
        
        private android.widget.TextView dateView;
        private android.widget.TextView textView;
        private android.widget.TextView indexView;
        
        public HistoryItemView(Context context) {
            super(context);
            
            setPadding(Screen.dp(12), Screen.dp(8), Screen.dp(12), Screen.dp(8));
            
            // Container with bubble background
            FrameLayoutFix bubble = new FrameLayoutFix(context);
            bubble.setPadding(Screen.dp(12), Screen.dp(8), Screen.dp(12), Screen.dp(8));
            
            // Create rounded background programmatically
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFF2A2F38); // Dark bubble color
            bg.setCornerRadius(Screen.dp(12));
            bubble.setBackground(bg);
            
            android.widget.LinearLayout content = new android.widget.LinearLayout(context);
            content.setOrientation(android.widget.LinearLayout.VERTICAL);
            
            // Version index
            indexView = new android.widget.TextView(context);
            indexView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
            indexView.setTextColor(0xFF7B68EE); // Light purple
            content.addView(indexView);
            
            // Date
            dateView = new android.widget.TextView(context);
            dateView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
            dateView.setTextColor(0xFF888888);
            content.addView(dateView);
            
            // Message text
            textView = new android.widget.TextView(context);
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setTextColor(0xFFFFFFFF);
            textView.setPadding(0, Screen.dp(4), 0, 0);
            content.addView(textView);
            
            bubble.addView(content, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
            
            addView(bubble, new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        }
        
        public void bind(String date, String text, int index, int total) {
            indexView.setText("Версия " + index + " из " + total);
            dateView.setText(date);
            textView.setText(text);
        }
    }
}
