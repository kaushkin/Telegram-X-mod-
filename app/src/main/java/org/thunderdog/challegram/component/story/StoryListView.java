package org.thunderdog.challegram.component.story;

import android.content.Context;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

import java.util.List;

public class StoryListView extends FrameLayout {

    private final RecyclerView recyclerView;
    private final StoriesAdapter adapter;

    public StoryListView(Context context, Tdlib tdlib) {
        super(context);

        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, Lang.rtl()));
        
        adapter = new StoriesAdapter(context, tdlib);
        recyclerView.setAdapter(adapter);
        
        // Add padding/margins as needed
        recyclerView.setPadding(Screen.dp(8f), Screen.dp(8f), Screen.dp(8f), Screen.dp(8f));
        recyclerView.setClipToPadding(false);
        
        addView(recyclerView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
    
    public void setStories(List<Long> chatIds) {
        adapter.setChats(chatIds);
    }
}
