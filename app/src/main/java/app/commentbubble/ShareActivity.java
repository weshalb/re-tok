package app.commentbubble;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.util.regex.Pattern;

/** Receives a shared TikTok comment link (or a friend's repost) and saves it. */
public class ShareActivity extends Activity {
    private static final Pattern URL = Pattern.compile("https?://\\S*tiktok\\.com\\S*", Pattern.CASE_INSENSITIVE);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        String text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) text = "";

        Store.Item imported = Store.parseExport(text);
        if (imported != null) {
            Store.add(this, imported);
            Toast.makeText(this, "Repost von " + imported.name() + " hinzugefügt", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        java.util.regex.Matcher m = URL.matcher(text);
        if (!m.find()) {
            Toast.makeText(this, "Kein TikTok-Link gefunden.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        String link = m.group();
        String rest = (text.substring(0, m.start()) + " " + text.substring(m.end())).replaceAll("\\s+", " ").trim();
        if (rest.length() > 120) rest = rest.substring(0, 120);
        Matcher.VideoKey video = BubbleService.lastVideo;

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        LinearLayout col = Ui.column(this);
        int pad = Ui.dp(this, 22);
        col.setPadding(pad, pad, pad, Ui.dp(this, 16));
        col.setBackground(Ui.round(this, Ui.CARD, 24));

        col.addView(Ui.bold(this, "Kommentar reposten", 20, Ui.TEXT));
        TextView vid;
        if (!BubbleService.running || video == null) {
            vid = Ui.text(this, "Die Bubble ist aus, darum weiß ReTok nicht, zu welchem Video das gehört. "
                    + "Schalte sie in ReTok ein und teile den Kommentar nochmal.", 14, Ui.ACCENT);
        } else {
            vid = Ui.text(this, "▶ " + video.creator + (video.caption != null ? " · " + video.caption : ""), 14, Ui.MUTED);
            vid.setMaxLines(2);
            vid.setEllipsize(TextUtils.TruncateAt.END);
            vid.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            vid.setBackground(Ui.round(this, Ui.CARD_HI, 12));
        }
        col.addView(vid, Ui.gapTop(this, 12));

        col.addView(Ui.label(this, "Kommentar oder Notiz"));
        EditText note = Ui.input(this, 2, rest, "Was steht im Kommentar?");
        col.addView(note);

        col.addView(Ui.label(this, "Dein Name für Freund*innen"));
        EditText who = Ui.input(this, 1, prefs.getString("myName", ""), "z. B. Yannik");
        col.addView(who);

        col.addView(Ui.button(this, "Reposten", true,
                () -> save(prefs, link, who, note, video, false)), Ui.gapTop(this, 18));
        col.addView(Ui.button(this, "Reposten und an Freund*innen schicken", false,
                () -> save(prefs, link, who, note, video, true)), Ui.gapTop(this, 10));
        setContentView(col);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
    }

    private void save(SharedPreferences prefs, String link, EditText who, EditText note,
                      Matcher.VideoKey video, boolean send) {
        String name = who.getText().toString().trim();
        prefs.edit().putString("myName", name).apply();
        Store.Item item = Store.newItem(link, name, note.getText().toString().trim(),
                BubbleService.lastTexts, video);
        Store.add(this, item);
        Toast.makeText(this, "Repostet", Toast.LENGTH_SHORT).show();
        if (send) {
            try {
                Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, Store.export(item));
                startActivity(Intent.createChooser(i, "An Freund*innen schicken"));
            } catch (JSONException ignored) {
            }
        }
        finish();
    }
}
