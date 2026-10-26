package app.commentbubble;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Receives a shared TikTok comment link (or a friend's exported comment) and saves it. */
public class ShareActivity extends Activity {
    private static final Pattern URL = Pattern.compile("https?://\\S*tiktok\\.com\\S*", Pattern.CASE_INSENSITIVE);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) text = "";

        Store.Item imported = Store.parseExport(text);
        if (imported != null) {
            Store.add(this, imported);
            Toast.makeText(this, "Added: " + imported.label(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        java.util.regex.Matcher m = URL.matcher(text);
        if (!m.find()) {
            Toast.makeText(this, "No TikTok link found in what you shared.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        String link = m.group();
        String rest = (text.substring(0, m.start()) + " " + text.substring(m.end())).replaceAll("\\s+", " ").trim();
        if (rest.length() > 120) rest = rest.substring(0, 120);
        Set<String> screen = BubbleService.lastTexts;
        Matcher.VideoKey video = BubbleService.lastVideo;

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        LinearLayout col = Ui.column(this);
        int pad = Ui.dp(this, 20);
        col.setPadding(pad, pad, pad, pad);
        setTitle("Save this comment");

        if (!BubbleService.running || screen.isEmpty()) {
            col.addView(Ui.text(this, "The bubble isn't on yet, so the app doesn't know which video this is. "
                    + "Turn it on in the Comment Bubble app, then share the comment again.", 14));
        } else if (video != null) {
            col.addView(Ui.text(this, "Video: " + video, 13));
        } else {
            List<String> preview = new ArrayList<>(screen);
            col.addView(Ui.text(this, "Video on screen: " + String.join(" · ",
                    preview.subList(0, Math.min(4, preview.size()))), 13));
        }

        col.addView(Ui.text(this, "Your name (shown to friends)", 13));
        EditText who = new EditText(this);
        who.setId(1);
        who.setText(prefs.getString("myName", ""));
        who.setHint("e.g. Yannik");
        col.addView(who);

        col.addView(Ui.text(this, "Note or the comment text (optional)", 13));
        EditText note = new EditText(this);
        note.setId(2);
        note.setText(rest);
        col.addView(note);

        col.addView(Ui.button(this, "Save", () -> {
            save(prefs, link, who, note, screen, video, false);
        }));
        col.addView(Ui.button(this, "Save and send to a friend", () -> {
            save(prefs, link, who, note, screen, video, true);
        }));
        setContentView(col);
    }

    private void save(SharedPreferences prefs, String link, EditText who, EditText note, Set<String> screen,
                      Matcher.VideoKey video, boolean send) {
        String name = who.getText().toString().trim();
        prefs.edit().putString("myName", name).apply();
        Store.Item item = Store.newItem(link, name, note.getText().toString().trim(), screen, video);
        Store.add(this, item);
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        if (send) {
            try {
                Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, Store.export(item));
                startActivity(Intent.createChooser(i, "Send to a friend"));
            } catch (JSONException ignored) {
            }
        }
        finish();
    }
}
