package org.thunderdog.challegram.component.story;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.TextUtils;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ComplexReceiverProvider;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.BaseView;

import me.vkryl.core.lambda.Destroyable;

public class StoryView extends BaseView implements Destroyable, ComplexReceiverProvider {

    private final AvatarReceiver avatarReceiver;
    private final ComplexReceiver complexReceiver;
    private final Paint ringPaint;

    private long chatId;
    private String title;
    private Text trimmedTitle;
    private boolean isRead;

    private static final int AVATAR_SIZE = Screen.dp(56f); // Size of the avatar
    private static final int VIEW_WIDTH = Screen.dp(72f);  // Total width of the item
    private static final int VIEW_HEIGHT = Screen.dp(86f); // Total height
    private static final float RING_WIDTH = Screen.dp(2f); // Width of the story ring

    public StoryView(Context context, Tdlib tdlib) {
        super(context, tdlib);
        
        avatarReceiver = new AvatarReceiver(this);
        complexReceiver = new ComplexReceiver(this);
        
        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(RING_WIDTH);
        
        // Gradient for story ring (purple to blue/cyan style)
        Shader shader = new LinearGradient(0, 0, AVATAR_SIZE, AVATAR_SIZE,
                new int[]{0xFF3355FF, 0xFFaa33ff}, null, Shader.TileMode.CLAMP);
        ringPaint.setShader(shader); // Default gradient, can be updated based on Theme
    }

    public void setChat(long chatId, String title, boolean isRead) {
        if (this.chatId != chatId) {
            this.chatId = chatId;
            avatarReceiver.requestChat(tdlib, chatId, AvatarReceiver.Options.SHOW_ONLINE);
        }
        this.title = title;
        this.isRead = isRead;
        
        // Update title text
        if (title != null) {
            trimmedTitle = new Text.Builder(title, VIEW_WIDTH - Screen.dp(4), Paints.robotoStyleProvider(11f), TextColorSets.Regular.NORMAL)
                    .singleLine()
                    .build();
        } else {
            trimmedTitle = null;
        }
        
        // Update ring color based on read state (gray if read, gradient if not)
        if (isRead) {
            ringPaint.setShader(null);
            ringPaint.setColor(Theme.getColor(ColorId.textLight));
        } else {
            // Re-apply gradient
             Shader shader = new LinearGradient(0, 0, AVATAR_SIZE, AVATAR_SIZE,
                new int[]{0xFF0077FF, 0xFF8833FF}, null, Shader.TileMode.CLAMP);
            ringPaint.setShader(shader);
        }
        
        requestLayout();
        invalidate();
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(VIEW_WIDTH, VIEW_HEIGHT);
        int cx = VIEW_WIDTH / 2;
        int cy = Screen.dp(8f) + AVATAR_SIZE / 2;
        int r = AVATAR_SIZE / 2;
        
        // Slightly smaller avatar to fit inside ring with padding
        int avatarR = r - Screen.dp(3f);
        
        avatarReceiver.setBounds(cx - avatarR, cy - avatarR, cx + avatarR, cy + avatarR);
        avatarReceiver.setRadius(avatarR); // Force circle
    }

    @Override
    protected void onDraw(Canvas c) {
        int cx = VIEW_WIDTH / 2;
        int cy = Screen.dp(8f) + AVATAR_SIZE / 2;
        int r = AVATAR_SIZE / 2;

        // Draw Ring
        c.drawCircle(cx, cy, r, ringPaint);

        // Draw Avatar
        avatarReceiver.draw(c);

        // Draw Name
        if (trimmedTitle != null) {
            int textX = (VIEW_WIDTH - trimmedTitle.getWidth()) / 2;
            int textY = cy + r + Screen.dp(16f); // Position below avatar
            trimmedTitle.draw(c, textX, textY);
        }
    }


    public void attach() {
        avatarReceiver.attach();
        complexReceiver.attach();
    }


    public void detach() {
        avatarReceiver.detach();
        complexReceiver.detach();
    }

    @Override
    public void performDestroy() {
        avatarReceiver.destroy();
        complexReceiver.performDestroy();
    }

    @Override
    public ComplexReceiver getComplexReceiver() {
        return complexReceiver;
    }
}
