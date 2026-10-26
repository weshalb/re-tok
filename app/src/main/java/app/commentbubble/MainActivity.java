package app.commentbubble;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private LinearLayout col;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        col = Ui.column(this);
        int pad = Ui.dp(this, 16);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private boolean serviceEnabled() {
        String on = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String me = new ComponentName(this, BubbleService.class).flattenToString();
        return on != null && on.contains(me);
    }

    private void render() {
        col.removeAllViews();
        boolean on = serviceEnabled();

        col.addView(Ui.heading(this, on ? "Bubble is on" : "Bubble is off"));
        if (!on) {
            col.addView(Ui.text(this, "What this app reads: while TikTok is open, it reads the creator name, caption "
                    + "and other text on screen, so it can tell which video you're watching and show a bubble "
                    + "when a comment on that video was saved. This happens on your phone only. Nothing you "
                    + "watch is uploaded anywhere. It does nothing in other apps.", 14));
            col.addView(Ui.button(this, "I agree, open settings to turn it on", () ->
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
            col.addView(Ui.text(this, "If the switch is greyed out (\"Restricted setting\"): open App info, tap ⋮ "
                    + "in the top corner, choose \"Allow restricted settings\", then try again.", 13));
            col.addView(Ui.button(this, "Open App info", () -> startActivity(
                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))));
        } else {
            col.addView(Ui.text(this, "To save a comment: in TikTok, share a comment and pick \"Save to Comment "
                    + "Bubble\". When that video comes up again, the bubble appears. Tap it to jump to the "
                    + "comment, long-press it to hide it.", 14));
        }

        List<Store.Item> items = Store.all(this);
        col.addView(Ui.heading(this, "Saved comments (" + items.size() + ")"));
        if (items.isEmpty()) col.addView(Ui.text(this, "Nothing saved yet.", 14));
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (Store.Item it : items) {
            col.addView(Ui.text(this, it.label() + "\n" + it.link + "\n" + df.format(new Date(it.createdAt))
                    + " · " + it.texts.size() + " texts remembered", 13));
            LinearLayout row = new LinearLayout(this);
            row.addView(Ui.button(this, "Open", () -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(it.link)))));
            row.addView(Ui.button(this, "Send to friend", () -> {
                try {
                    startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, Store.export(it)), "Send to a friend"));
                } catch (JSONException ignored) {
                }
            }));
            row.addView(Ui.button(this, "Delete", () -> {
                Store.delete(this, it.id);
                render();
            }));
            col.addView(row);
        }

        col.addView(Ui.heading(this, "Test info"));
        col.addView(Ui.text(this, "Last check: " + (BubbleService.lastMatchInfo.isEmpty()
                ? "nothing yet, open TikTok first" : BubbleService.lastMatchInfo), 13));
        String dump = BubbleService.lastDump;
        TextView dumpView = Ui.text(this, dump.isEmpty() ? "No TikTok screen read yet."
                : (dump.length() > 4000 ? dump.substring(0, 4000) + "\n…" : dump), 11);
        dumpView.setTypeface(android.graphics.Typeface.MONOSPACE);
        col.addView(dumpView);
        col.addView(Ui.button(this, "Copy test info", () -> {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText("Comment Bubble test info",
                    BubbleService.lastMatchInfo + "\n\n" + BubbleService.lastDump));
            Toast.makeText(this, "Copied. Paste it in the chat with Claude.", Toast.LENGTH_SHORT).show();
        }));
        col.addView(Ui.button(this, "Refresh", this::render));
    }
}
