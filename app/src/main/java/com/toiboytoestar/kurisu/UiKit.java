package com.toiboytoestar.kurisu;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tiny helpers to build the dark gold UI of the Hub and Settings screens in code. */
final class UiKit {
    private UiKit() {}

    static final int GOLD = Color.parseColor("#FFCF40");
    static final int INK = Color.parseColor("#ECECEC");
    static final int DIM = Color.parseColor("#99FFFFFF");
    static final int BG = Color.parseColor("#0B0B10");

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static GradientDrawable box(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1), stroke);
        return g;
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    static TextView title(Context c, String s) {
        TextView t = text(c, s, 13, GOLD);
        t.setAllCaps(true);
        t.setPadding(0, dp(c, 18), 0, dp(c, 6));
        return t;
    }

    static TextView button(Context c, String s) {
        TextView t = text(c, s, 13, GOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9));
        t.setBackground(box(c, 0x33000000, 0x88FFCF40, 16));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(Context c, int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static EditText edit(Context c, String hint, boolean multiline) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0x88FFFFFF);
        e.setTextSize(15);
        e.setBackground(box(c, 0x22FFFFFF, 0x55FFCF40, 10));
        e.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        e.setInputType(multiline
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return e;
    }

    static View card(Context c, View inner) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(box(c, 0x16FFFFFF, 0x33FFCF40, 10));
        l.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        l.addView(inner, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert);
    }
}
