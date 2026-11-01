package com.tanzirdev.shobdo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SplashActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.addView(new GlassBackgroundView(this), new FrameLayout.LayoutParams(-1, -1));
        Ui.edgeToEdge(this, root, (t, bt) -> {});

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER);
        WordmarkView wm = new WordmarkView(this);
        col.addView(wm, new LinearLayout.LayoutParams(-2, Ui.dp(this, 92)));

        TextView tag = new TextView(this);
        tag.setText("Offline captions in every language"); tag.setTextSize(14); tag.setTextColor(Ui.SUB);
        tag.setLetterSpacing(0.04f); tag.setAlpha(0f); tag.setTranslationY(Ui.dp(this, 12));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2); tl.topMargin = Ui.dp(this, 10);
        col.addView(tag, tl);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        GradientBarView bar = new GradientBarView(this);
        bar.setIndeterminate(true);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 4), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = Ui.dp(this, 72);
        bar.setAlpha(0f);
        root.addView(bar, bl);
        setContentView(root);

        wm.playIntro();
        tag.animate().alpha(1f).translationY(0f).setStartDelay(900).setDuration(600).start();
        bar.animate().alpha(1f).setStartDelay(1000).setDuration(500).start();
        root.postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 2300);
    }
}
