package app.commentbubble;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Watches TikTok only, recognises the video on screen and shows a bubble for saved comments. */
public class BubbleService extends AccessibilityService {
    static final Set<String> TIKTOK = new HashSet<>(Arrays.asList("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"));
    private static final int TYPE_ACCESSIBILITY_OVERLAY = 2032;
    private static final long DEBOUNCE_MS = 350;
    private static final int MAX_DEPTH = 40;

    // Read by the app's screens for the debug view and when saving a share.
    static volatile Set<String> lastTexts = Collections.emptySet();
    static volatile String lastDump = "";
    static volatile String lastTikTokPackage = "com.zhiliaoapp.musically";
    static volatile String lastMatchInfo = "";
    static volatile boolean running;

    private final Matcher matcher = new Matcher();
    private Handler handler;
    private WindowManager wm;
    private TextView bubble;
    private String shownItemId;
    private Set<String> dismissedScreen = Collections.emptySet();
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
            if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) hideBubble();
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
            hideBubble();
            return;
        }
        Set<String> texts = new LinkedHashSet<>();
        StringBuilder dump = new StringBuilder();
        walk(root, 0, texts, dump);
        matcher.observe(texts);
        lastTexts = texts;
        lastDump = dump.toString();

        Matcher.Result r = matcher.best(texts, Store.all(this));
        if (r == null) {
            lastMatchInfo = "No match. On screen (distinctive): " + matcher.distinctive(texts);
            hideBubble();
            return;
        }
        lastMatchInfo = String.format("Match %.0f%% (%d texts): %s", r.score * 100, r.hits, r.item.label());
        if (matcher.distinctive(texts).equals(dismissedScreen)) {
            hideBubble();
            return;
        }
        showBubble(r.item, texts);
    }

    private void walk(AccessibilityNodeInfo n, int depth, Set<String> texts, StringBuilder dump) {
        if (n == null || depth > MAX_DEPTH) return;
        if (n.isVisibleToUser()) {
            String t = Matcher.normalize(n.getText());
            String d = Matcher.normalize(n.getContentDescription());
            if (t != null) texts.add(t);
            if (d != null) texts.add(d);
            if (t != null || d != null || n.getViewIdResourceName() != null) {
                for (int i = 0; i < Math.min(depth, 12); i++) dump.append("  ");
                dump.append(n.getClassName() == null ? "?" : shortName(n.getClassName().toString()));
                if (n.getViewIdResourceName() != null) dump.append(" #").append(n.getViewIdResourceName());
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

    private void showBubble(Store.Item item, Set<String> screen) {
        if (bubble != null && item.id.equals(shownItemId)) return;
        hideBubble();
        float dp = getResources().getDisplayMetrics().density;
        TextView v = new TextView(this);
        v.setText("💬 " + item.label());
        v.setTextColor(0xFFFFFFFF);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        v.setMaxLines(2);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setMaxWidth((int) (240 * dp));
        int pad = (int) (12 * dp);
        v.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEED0245E);
        bg.setCornerRadius(20 * dp);
        v.setBackground(bg);
        v.setElevation(6 * dp);

        Set<String> screenKey = matcher.distinctive(screen);
        v.setOnClickListener(x -> {
            dismissedScreen = screenKey;
            hideBubble();
            openLink(item.link);
        });
        v.setOnLongClickListener(x -> {
            dismissedScreen = screenKey;
            hideBubble();
            return true;
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = (int) (12 * dp);
        lp.y = (int) (getResources().getDisplayMetrics().heightPixels * 0.18f);
        try {
            wm.addView(v, lp);
            bubble = v;
            shownItemId = item.id;
        } catch (RuntimeException ignored) {
        }
    }

    private void hideBubble() {
        if (bubble != null) {
            try {
                wm.removeView(bubble);
            } catch (RuntimeException ignored) {
            }
        }
        bubble = null;
        shownItemId = null;
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
        hideBubble();
    }

    @Override
    public void onDestroy() {
        running = false;
        hideBubble();
        super.onDestroy();
    }
}
