package app.commentbubble;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small design system for screens built in code: dark, rounded, one pink accent. */
final class Ui {
    static final int BG = 0xFF0E0E13;
    static final int CARD = 0xFF1B1B23;
    static final int CARD_HI = 0xFF26262F;
    static final int TEXT = 0xFFF4F4F7;
    static final int MUTED = 0xFF9A9AA8;
    static final int ACCENT = 0xFFFF3D6E;
    static final int OK = 0xFF35D49A;

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    static TextView bold(Context c, CharSequence s, float sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    static TextView label(Context c, CharSequence s) {
        TextView t = bold(c, s.toString().toUpperCase(), 12, MUTED);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(c, 4), dp(c, 22), 0, dp(c, 8));
        return t;
    }

    /** Filled pill button; {@code primary} uses the accent colour. */
    static TextView button(Context c, CharSequence s, boolean primary, Runnable onClick) {
        TextView b = bold(c, s, 15, primary ? 0xFFFFFFFF : TEXT);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 18), dp(c, 13), dp(c, 18), dp(c, 13));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                round(c, primary ? ACCENT : CARD_HI, 24), null));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    /** Small text-only action, used inside cards. */
    static TextView action(Context c, CharSequence s, int color, Runnable onClick) {
        TextView b = bold(c, s, 14, color);
        b.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), null, round(c, 0xFFFFFFFF, 12)));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        l.setBackground(round(c, CARD, 20));
        return l;
    }

    /** Round badge with the first letter of a name. */
    static TextView avatar(Context c, String name, int sizeDp, int color) {
        String letter = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        TextView a = bold(c, letter, sizeDp * 0.45f, 0xFFFFFFFF);
        a.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        a.setBackground(d);
        a.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return a;
    }

    static EditText input(Context c, int id, String value, String hint) {
        EditText e = new EditText(c);
        e.setId(id);
        e.setText(value);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        int p = dp(c, 14);
        e.setPadding(p, p, p, p);
        e.setBackground(round(c, CARD_HI, 14));
        return e;
    }

    static LinearLayout.LayoutParams gapTop(Context c, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    static View spacer(Context c, float wDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, wDp), 1));
        return v;
    }

    static String ago(long t) {
        long m = (System.currentTimeMillis() - t) / 60000;
        if (m < 1) return "gerade eben";
        if (m < 60) return "vor " + m + " Min.";
        if (m < 60 * 24) return "vor " + (m / 60) + " Std.";
        return "vor " + (m / 1440) + " T.";
    }
}
