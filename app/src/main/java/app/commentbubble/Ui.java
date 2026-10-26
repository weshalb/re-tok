package app.commentbubble;

import android.content.Context;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tiny helpers for building screens in code. */
final class Ui {
    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, CharSequence s, float sp) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextIsSelectable(true);
        t.setPadding(0, dp(c, 6), 0, dp(c, 6));
        return t;
    }

    static TextView heading(Context c, CharSequence s) {
        TextView t = text(c, s, 18);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(c, 18), 0, dp(c, 4));
        return t;
    }

    static Button button(Context c, CharSequence s, Runnable onClick) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
}
