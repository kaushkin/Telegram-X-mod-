package org.thunderdog.challegram.component.story;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;


import java.util.ArrayList;
import java.util.List;

public class StoriesAdapter extends RecyclerView.Adapter<StoriesAdapter.Holder> {

    private final Context context;
    private final Tdlib tdlib;
    private final List<Long> chatIds = new ArrayList<>();

    public StoriesAdapter(Context context, Tdlib tdlib) {
        this.context = context;
        this.tdlib = tdlib;
    }

    public void setChats(List<Long> newChatIds) {
        this.chatIds.clear();
        if (newChatIds != null) {
            this.chatIds.addAll(newChatIds);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        StoryView view = new StoryView(context, tdlib);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        long chatId = chatIds.get(position);
        StoryView view = (StoryView) holder.itemView;
        
        // Fetch chat info (synchronously or relying on cache for now)
        TdApi.Chat chat = tdlib.chat(chatId);
        String title = chat != null ? chat.title : "User";
        
        // TODO: Check actual story read state
        boolean isRead = false; 
        
        view.setChat(chatId, title, isRead);
    }

    @Override
    public void onViewAttachedToWindow(@NonNull Holder holder) {
        ((StoryView) holder.itemView).attach();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull Holder holder) {
        ((StoryView) holder.itemView).detach();
    }
    
    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        ((StoryView) holder.itemView).performDestroy();
    }

    @Override
    public int getItemCount() {
        return chatIds.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        public Holder(@NonNull StoryView itemView) {
            super(itemView);
        }
    }
}
