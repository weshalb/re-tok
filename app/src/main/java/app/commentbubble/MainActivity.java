package app.commentbubble;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private LinearLayout col;
    private boolean showFriends;
    private boolean showTestInfo;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        col = Ui.column(this);
        int pad = Ui.dp(this, 18);
        col.setPadding(pad, Ui.dp(this, 28), pad, Ui.dp(this, 40));
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

        LinearLayout head = Ui.row(this);
        TextView logo = Ui.bold(this, "ReTok", 30, Ui.TEXT);
        head.addView(logo, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        head.addView(statusPill(on));
        col.addView(head);
        col.addView(Ui.text(this, "Kommentare reposten und über TikTok schweben sehen.", 15, Ui.MUTED), Ui.gapTop(this, 4));

        if (!on) col.addView(setupCard(), Ui.gapTop(this, 20));

        List<Store.Item> all = Store.all(this);
        List<Store.Item> mine = new ArrayList<>();
        List<Store.Item> friends = new ArrayList<>();
        for (Store.Item it : all) (it.mine ? mine : friends).add(it);

        col.addView(tabs(mine.size(), friends.size()), Ui.gapTop(this, 24));
        List<Store.Item> list = showFriends ? friends : mine;
        if (list.isEmpty()) {
            col.addView(emptyCard(), Ui.gapTop(this, 12));
        } else {
            for (Store.Item it : list) col.addView(itemCard(it), Ui.gapTop(this, 12));
        }

        col.addView(Ui.label(this, "Test-Info"));
        col.addView(Ui.action(this, showTestInfo ? "Ausblenden" : "Anzeigen", Ui.MUTED, () -> {
            showTestInfo = !showTestInfo;
            render();
        }));
        if (showTestInfo) col.addView(testCard(), Ui.gapTop(this, 8));
    }

    private View statusPill(boolean on) {
        LinearLayout pill = Ui.row(this);
        pill.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        pill.setBackground(Ui.round(this, Ui.CARD, 20));
        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(on ? Ui.OK : Ui.ACCENT);
        dot.setBackground(d);
        pill.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8)));
        pill.addView(Ui.spacer(this, 8));
        pill.addView(Ui.bold(this, on ? "Aktiv" : "Aus", 13, Ui.TEXT));
        return pill;
    }

    private View setupCard() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.bold(this, "Bubble einschalten", 18, Ui.TEXT));
        c.addView(Ui.text(this, "Während TikTok offen ist, liest ReTok den Creator-Namen und die Beschreibung "
                + "des Videos auf deinem Bildschirm. So erkennt die App, zu welchem Video du oder deine "
                + "Freund*innen einen Kommentar repostet habt. Das passiert nur auf deinem Handy, nichts davon "
                + "wird hochgeladen. In anderen Apps macht ReTok nichts.", 14, Ui.MUTED), Ui.gapTop(this, 8));
        c.addView(Ui.button(this, "Einverstanden, einschalten", true, () ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))), Ui.gapTop(this, 14));
        c.addView(Ui.text(this, "Schalter grau oder „Zugriff verweigert“? App-Info öffnen, oben rechts ⋮, "
                + "„Eingeschränkte Einstellungen zulassen“.", 13, Ui.MUTED), Ui.gapTop(this, 12));
        c.addView(Ui.button(this, "App-Info öffnen", false, () -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))),
                Ui.gapTop(this, 10));
        return c;
    }

    private View tabs(int mineCount, int friendCount) {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        bar.setBackground(Ui.round(this, Ui.CARD, 22));
        bar.addView(tab("Meine · " + mineCount, !showFriends, false), new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(tab("Freund*innen · " + friendCount, showFriends, true), new LinearLayout.LayoutParams(0, -2, 1));
        return bar;
    }

    private View tab(String text, boolean active, boolean friends) {
        TextView t = Ui.bold(this, text, 14, active ? Ui.TEXT : Ui.MUTED);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        if (active) t.setBackground(Ui.round(this, Ui.CARD_HI, 18));
        t.setOnClickListener(v -> {
            showFriends = friends;
            render();
        });
        return t;
    }

    private View emptyCard() {
        LinearLayout c = Ui.card(this);
        if (showFriends) {
            c.addView(Ui.bold(this, "Noch nichts von Freund*innen", 16, Ui.TEXT));
            c.addView(Ui.text(this, "Wenn dir jemand einen ReTok-Repost schickt (WhatsApp, Insta …): Nachricht "
                    + "lange drücken, Teilen, ReTok wählen.", 14, Ui.MUTED), Ui.gapTop(this, 6));
        } else {
            c.addView(Ui.bold(this, "So repostest du einen Kommentar", 16, Ui.TEXT));
            c.addView(Ui.text(this, "1. In TikTok einen Kommentar lange drücken und teilen.\n"
                    + "2. ReTok auswählen und speichern.\n"
                    + "3. Kommt das Video wieder, schwebt der Kommentar über dem Creator-Namen. "
                    + "Antippen bringt dich hin, lange drücken blendet ihn aus.", 14, Ui.MUTED), Ui.gapTop(this, 6));
        }
        return c;
    }

    private View itemCard(Store.Item it) {
        LinearLayout c = Ui.card(this);
        LinearLayout top = Ui.row(this);
        top.addView(Ui.avatar(this, it.mine ? "Du" : it.who, 36, it.mine ? Ui.ACCENT : 0xFF6C5CE7));
        top.addView(Ui.spacer(this, 12));
        LinearLayout who = Ui.column(this);
        who.addView(Ui.bold(this, it.mine ? "Dein Repost" : it.name(), 15, Ui.TEXT));
        who.addView(Ui.text(this, Ui.ago(it.createdAt), 12, Ui.MUTED));
        top.addView(who);
        c.addView(top);

        if (!it.note.isEmpty()) {
            TextView note = Ui.text(this, "„" + it.note + "“", 16, Ui.TEXT);
            c.addView(note, Ui.gapTop(this, 12));
        }
        TextView video = Ui.text(this, "▶ " + it.videoLabel(), 13, Ui.MUTED);
        video.setMaxLines(1);
        video.setEllipsize(TextUtils.TruncateAt.END);
        video.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        video.setBackground(Ui.round(this, Ui.CARD_HI, 12));
        c.addView(video, Ui.gapTop(this, 12));

        LinearLayout actions = Ui.row(this);
        actions.addView(Ui.action(this, "Zum Kommentar", Ui.ACCENT, () -> open(it.link)));
        actions.addView(Ui.action(this, "Teilen", Ui.TEXT, () -> share(it)));
        View fill = new View(this);
        actions.addView(fill, new LinearLayout.LayoutParams(0, 1, 1));
        actions.addView(Ui.action(this, "Löschen", Ui.MUTED, () -> {
            Store.delete(this, it.id);
            render();
        }));
        c.addView(actions, Ui.gapTop(this, 8));
        return c;
    }

    private View testCard() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, BubbleService.lastMatchInfo.isEmpty()
                ? "Noch nichts gelesen, öffne zuerst TikTok." : BubbleService.lastMatchInfo, 13, Ui.TEXT));
        String dump = BubbleService.lastDump;
        TextView d = Ui.text(this, dump.isEmpty() ? "" : (dump.length() > 3000 ? dump.substring(0, 3000) + "\n…" : dump),
                10, Ui.MUTED);
        d.setTypeface(android.graphics.Typeface.MONOSPACE);
        d.setTextIsSelectable(true);
        c.addView(d, Ui.gapTop(this, 8));
        c.addView(Ui.button(this, "Test-Info kopieren", false, () -> {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText("ReTok Test-Info",
                    BubbleService.lastMatchInfo + "\n\n" + BubbleService.lastDump));
            Toast.makeText(this, "Kopiert", Toast.LENGTH_SHORT).show();
        }), Ui.gapTop(this, 10));
        return c;
    }

    private void open(String link) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        try {
            startActivity(new Intent(i).setPackage(BubbleService.lastTikTokPackage));
        } catch (android.content.ActivityNotFoundException e) {
            startActivity(i);
        }
    }

    private void share(Store.Item it) {
        try {
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, Store.export(it)), "Repost an Freund*innen schicken"));
        } catch (JSONException ignored) {
        }
    }
}
