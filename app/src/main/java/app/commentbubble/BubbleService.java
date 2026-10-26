package app.commentbubble;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Watches TikTok only, recognises the video on screen and floats the reposted comments for it
 * just above the creator name and caption.
 */
public class BubbleService extends AccessibilityService {
    static final Set<String> TIKTOK = new HashSet<>(Arrays.asList("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"));
    private static final int TYPE_ACCESSIBILITY_OVERLAY = 2032;
    private static final long DEBOUNCE_MS = 300;
    private static final int MAX_DEPTH = 40;
    private static final int MAX_CHIPS = 3;

    // Read by the app's screens for the test info and when saving a share.
    static volatile Set<String> lastTexts = Collections.emptySet();
    static volatile Matcher.VideoKey lastVideo;
    static volatile String lastDump = "";
    static volatile String lastTikTokPackage = "com.zhiliaoapp.musically";
    static volatile String lastMatchInfo = "";
    static volatile boolean running;

    private final Matcher matcher = new Matcher();
    private Handler handler;
    private WindowManager wm;
    private LinearLayout overlay;
    private WindowManager.LayoutParams overlayParams;
    private String shownKey;
    private String dismissedScreen = "";
    private String creatorText, captionText, soundText;
    private final Rect creatorBounds = new Rect();
    private final Runnable scan = this::scan;

    @Override
    protected void onServiceConnected() {
        handler = new Handler(Looper.getMainLooper());
        wm = getSystemService(WindowManager.class);
        running = true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        CharSequence p = e.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        if (pkg.equals(getPackageName())) return;
        if (!TIKTOK.contains(pkg)) {
            if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) hideOverlay();
            return;
        }
        lastTikTokPackage = pkg;
        handler.removeCallbacks(scan);
        handler.postDelayed(scan, DEBOUNCE_MS);
    }

    private void scan() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        CharSequence pkg = root.getPackageName();
        if (pkg == null || !TIKTOK.contains(pkg.toString())) {
            hideOverlay();
            return;
        }
        Set<String> texts = new LinkedHashSet<>();
        StringBuilder dump = new StringBuilder();
        creatorText = captionText = soundText = null;
        creatorBounds.setEmpty();
        walk(root, 0, texts, dump);
        matcher.observe(texts);
        lastTexts = texts;
        lastDump = dump.toString();

        Matcher.VideoKey video = new Matcher.VideoKey(creatorText, captionText, soundText);
        // Comment and share sheets hide the caption; keep the last video seen in the feed.
        if (video.valid()) lastVideo = video;
        // A hidden bubble stays hidden only until the next video.
        if (video.valid() && !video.toString().equals(dismissedScreen)) dismissedScreen = "";

        List<Store.Item> found = new ArrayList<>();
        if (video.valid()) {
            for (Store.Item it : Store.all(this)) if (video.sameVideo(it.key())) found.add(it);
        }
        int saved = Store.all(this).size();
        if (found.isEmpty()) {
            lastMatchInfo = "Kein Treffer (" + saved + " gespeichert). Video: " + (video.valid() ? video : "nicht erkannt");
            hideOverlay();
            return;
        }
        String screenId = video.toString();
        lastMatchInfo = found.size() + " Treffer: " + found.get(0).label() + "\nVideo: " + video;
        if (screenId.equals(dismissedScreen)) {
            hideOverlay();
            return;
        }
        showOverlay(found, screenId);
    }

    private void walk(AccessibilityNodeInfo n, int depth, Set<String> texts, StringBuilder dump) {
        if (n == null || depth > MAX_DEPTH) return;
        if (n.isVisibleToUser()) {
            String t = Matcher.normalize(n.getText());
            String d = Matcher.normalize(n.getContentDescription());
            if (t != null) texts.add(t);
            if (d != null) texts.add(d);
            String id = n.getViewIdResourceName();
            if (id != null && n.getText() != null) {
                if (id.endsWith(":id/title") && creatorText == null) {
                    creatorText = n.getText().toString();
                    n.getBoundsInScreen(creatorBounds);
                } else if (id.endsWith(":id/desc") && captionText == null) {
                    captionText = n.getText().toString();
                }
            }
            CharSequence cd = n.getContentDescription();
            if (cd != null && soundText == null && cd.toString().toLowerCase(Locale.ROOT).startsWith("sound:")) {
                soundText = cd.toString().substring(6);
            }
            if (t != null || d != null || id != null) {
                for (int i = 0; i < Math.min(depth, 12); i++) dump.append("  ");
                dump.append(n.getClassName() == null ? "?" : shortName(n.getClassName().toString()));
                if (id != null) dump.append(" #").append(id);
                if (n.getText() != null) dump.append(" text=\"").append(n.getText()).append('"');
                if (n.getContentDescription() != null) dump.append(" desc=\"").append(n.getContentDescription()).append('"');
                dump.append('\n');
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) walk(n.getChild(i), depth + 1, texts, dump);
    }

    private static String shortName(String cls) {
        int i = cls.lastIndexOf('.');
        return i < 0 ? cls : cls.substring(i + 1);
    }

    private int screenHeight() {
        if (Build.VERSION.SDK_INT >= 30) return wm.getCurrentWindowMetrics().getBounds().height();
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        return m.heightPixels;
    }

    /** Distance from the screen bottom so the chips sit just above the creator name. */
    private int bottomOffset() {
        int gap = Ui.dp(this, 10);
        if (creatorBounds.isEmpty()) return (int) (screenHeight() * 0.25f);
        return screenHeight() - creatorBounds.top + gap;
    }

    private void showOverlay(List<Store.Item> items, String screenId) {
        String key = screenId + "#" + items.size();
        if (overlay != null && key.equals(shownKey)) {
            // Same video: only follow the creator name if it moved.
            int y = bottomOffset();
            int x = creatorBounds.isEmpty() ? Ui.dp(this, 12) : creatorBounds.left;
            if (overlayParams.y != y || overlayParams.x != x) {
                overlayParams.y = y;
                overlayParams.x = x;
                try {
                    wm.updateViewLayout(overlay, overlayParams);
                } catch (RuntimeException ignored) {
                }
            }
            return;
        }
        hideOverlay();

        LinearLayout box = Ui.column(this);
        int shown = Math.min(MAX_CHIPS, items.size());
        for (int i = 0; i < shown; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = Ui.dp(this, 6);
            box.addView(chip(items.get(i), screenId), lp);
        }
        if (items.size() > shown) {
            TextView more = Ui.bold(this, "+" + (items.size() - shown) + " weitere", 12, 0xDDFFFFFF);
            more.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), 0, 0);
            box.addView(more);
        }

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = creatorBounds.isEmpty() ? Ui.dp(this, 12) : creatorBounds.left;
        lp.y = bottomOffset();
        try {
            wm.addView(box, lp);
            overlay = box;
            overlayParams = lp;
            shownKey = key;
        } catch (RuntimeException ignored) {
        }
    }

    /** One semi-transparent pill: avatar letter, name, comment preview. */
    private LinearLayout chip(Store.Item item, String screenId) {
        LinearLayout chip = Ui.row(this);
        int p = Ui.dp(this, 6);
        chip.setPadding(p, p, Ui.dp(this, 14), p);
        chip.setBackground(Ui.round(this, 0x8C000000, 22));
        chip.addView(Ui.avatar(this, item.mine ? "Du" : item.who, 28, item.mine ? Ui.ACCENT : 0xFF6C5CE7));
        chip.addView(Ui.spacer(this, 8));

        LinearLayout lines = Ui.column(this);
        lines.addView(Ui.bold(this, item.mine ? "Dein Repost" : item.name() + " hat repostet", 12, 0xFFFFFFFF));
        TextView preview = Ui.text(this, item.note.isEmpty() ? "Zum Kommentar springen ›" : "„" + item.note + "“",
                13, 0xE6FFFFFF);
        preview.setMaxLines(2);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        preview.setMaxWidth(Ui.dp(this, 230));
        lines.addView(preview);
        chip.addView(lines);

        chip.setOnClickListener(v -> {
            dismissedScreen = screenId;
            hideOverlay();
            openLink(item.link);
        });
        chip.setOnLongClickListener(v -> {
            dismissedScreen = screenId;
            hideOverlay();
            return true;
        });
        return chip;
    }

    private void hideOverlay() {
        if (overlay != null) {
            try {
                wm.removeView(overlay);
            } catch (RuntimeException ignored) {
            }
        }
        overlay = null;
        overlayParams = null;
        shownKey = null;
    }

    private void openLink(String link) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(new Intent(i).setPackage(lastTikTokPackage));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
        }
    }

    @Override
    public void onInterrupt() {
        hideOverlay();
    }

    @Override
    public void onDestroy() {
        running = false;
        hideOverlay();
        super.onDestroy();
    }
}
