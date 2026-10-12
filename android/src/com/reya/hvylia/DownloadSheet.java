package com.reya.hvylia;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bottom sheet: cover, title, artist / channel, length, MP3 quality with file sizes, folder, «Скачати».
 * Opens at once with what the page already shows; details that need the network fill in behind it.
 */
final class DownloadSheet extends Dialog {
    /** What the sheet shows and what «Скачати» does. */
    static final class Model {
        String cover = "";
        String title = "";
        String subtitle = "";
        String note = "";
        long seconds;
        int count = 1;
        /** Optional: refines the model in the background (network). Throwing shows the error and offers a retry. */
        Loader loader;
        Starter starter;
    }

    interface Loader { void load(Model m) throws Exception; }
    interface Starter { void start(int kbps, Uri tree); }

    private static final int[] RATES = { 320, 192, 128 };
    private static final ExecutorService IO = Executors.newCachedThreadPool();

    private final BrowserActivity act;
    private final Model model;
    private final Handler main = new Handler(Looper.getMainLooper());

    private ImageView cover;
    private TextView title, subtitle, length, status, folder, button;
    private final LinearLayout[] rows = new LinearLayout[3];
    private final TextView[] sizes = new TextView[3];
    private final View[] radios = new View[3];
    private int selected;
    private boolean failed;
    private boolean startAfterFolder;
    private String shownCover = "";

    DownloadSheet(BrowserActivity act, Model model) {
        super(act);
        this.act = act;
        this.model = model;
        int saved = Store.kbps(act);
        selected = saved == 320 ? 0 : saved == 128 ? 2 : 1;
        build();
        refresh();
        if (model.loader != null) load();
    }

    private int dp(float v) { return Ui.dp(act, v); }

    private void build() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(10), dp(20), dp(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SHEET);
        float r = dp(22);
        bg.setCornerRadii(new float[] { r, r, r, r, 0, 0, 0, 0 });
        root.setBackground(bg);

        View grab = new View(act);
        grab.setBackground(Ui.round(0x40FFFFFF, dp(3)));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(dp(42), dp(5));
        gl.gravity = Gravity.CENTER_HORIZONTAL;
        gl.bottomMargin = dp(14);
        root.addView(grab, gl);

        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        cover = new ImageView(act);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(Ui.round(Ui.RAISED, dp(10)));
        cover.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(10)); }
        });
        cover.setClipToOutline(true);
        head.addView(cover, new LinearLayout.LayoutParams(dp(76), dp(76)));
        LinearLayout meta = new LinearLayout(act);
        meta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        ml.leftMargin = dp(14);
        title = Ui.text(act, "", 16, Ui.FG, "manrope-800");
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(0, 1.12f);
        subtitle = Ui.text(act, "", 13.5f, Ui.FG2, "manrope-600");
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        length = Ui.text(act, "", 12.5f, Ui.FG3, "manrope-600");
        length.setSingleLine(true);
        length.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(title);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = dp(6);
        meta.addView(subtitle, cl);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.topMargin = dp(4);
        meta.addView(length, ll);
        head.addView(meta, ml);
        root.addView(head);

        status = Ui.text(act, "", 13, Ui.FG3, "manrope-600");
        status.setVisibility(View.GONE);
        status.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(12);
        root.addView(status, sl);

        TextView section = Ui.text(act, "ЯКІСТЬ MP3", 11.5f, Ui.FG3, "manrope-800");
        section.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams secl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secl.topMargin = dp(20);
        secl.bottomMargin = dp(8);
        root.addView(section, secl);

        for (int i = 0; i < 3; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.bottomMargin = dp(8);
            root.addView(qualityRow(i), p);
        }

        LinearLayout fr = new LinearLayout(act);
        fr.setOrientation(LinearLayout.HORIZONTAL);
        fr.setGravity(Gravity.CENTER_VERTICAL);
        fr.setPadding(dp(14), dp(12), dp(8), dp(12));
        fr.setBackground(Ui.round(0x0FFFFFFF, dp(14)));
        ImageView fi = new ImageView(act);
        fi.setImageResource(R.drawable.ic_folder);
        fi.setColorFilter(Ui.FG2);
        fr.addView(fi, new LinearLayout.LayoutParams(dp(22), dp(22)));
        folder = Ui.text(act, "", 14, Ui.FG, "manrope-600");
        folder.setSingleLine(true);
        folder.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        fl.leftMargin = dp(12);
        fr.addView(folder, fl);
        TextView change = Ui.text(act, "Змінити", 13.5f, Ui.FG, "manrope-800");
        change.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable cb = Ui.round(Color.TRANSPARENT, dp(99));
        cb.setStroke(dp(1.5f), 0x4DFFFFFF);
        change.setBackground(cb);
        fr.addView(change);
        View.OnClickListener pick = v -> act.pickFolder();
        fr.setOnClickListener(pick);
        change.setOnClickListener(pick);
        LinearLayout.LayoutParams frl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        frl.topMargin = dp(14);
        root.addView(fr, frl);
        refreshFolder();

        button = Ui.text(act, "Скачати", 16, Ui.ACCENT_INK, "manrope-800");
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.round(Ui.ACCENT, dp(99)));
        button.setOnClickListener(v -> onDownload());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        bl.topMargin = dp(18);
        root.addView(button, bl);

        setContentView(root);
        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.6f);
            w.setWindowAnimations(R.style.SheetAnim);
            w.setNavigationBarColor(Ui.SHEET);
        }
    }

    private View qualityRow(final int i) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        FrameLayout radioBox = new FrameLayout(act);
        View radio = new View(act);
        radioBox.addView(radio, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        row.addView(radioBox, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView label = Ui.text(act, RATES[i] + " кбіт/с", 15.5f, Ui.FG, "manrope-800");
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.leftMargin = dp(14);
        row.addView(label, ll);
        if (RATES[i] == 192) {
            TextView badge = Ui.text(act, "рекомендовано", 11, Ui.ACCENT_INK, "manrope-800");
            badge.setPadding(dp(8), dp(4), dp(8), dp(4));
            badge.setBackground(Ui.round(Ui.ACCENT, dp(99)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.leftMargin = dp(8);
            row.addView(badge, bl);
        }
        row.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1));
        TextView size = Ui.text(act, "", 13.5f, Ui.FG2, "manrope-600");
        row.addView(size);
        row.setOnClickListener(v -> {
            selected = i;
            Store.setKbps(act, RATES[i]);
            updateRows();
            radios[i].animate().scaleX(1.15f).scaleY(1.15f).setDuration(110)
                    .withEndAction(() -> radios[i].animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
        });
        rows[i] = row;
        sizes[i] = size;
        radios[i] = radio;
        return row;
    }

    private void updateRows() {
        for (int i = 0; i < 3; i++) {
            boolean on = i == selected;
            rows[i].setBackground(Ui.round(on ? 0x241ED760 : 0x0FFFFFFF, dp(14)));
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            if (on) d.setColor(Ui.ACCENT);
            else { d.setColor(Color.TRANSPARENT); d.setStroke(dp(2), Ui.FG3); }
            radios[i].setBackground(d);
            radios[i].setForeground(on ? new InsetDrawable(Ui.oval(Ui.ACCENT_INK), dp(7)) : null);
            sizes[i].setText(model.seconds > 0 ? Ui.size(model.seconds, RATES[i]) : "");
        }
    }

    /** Puts the model on screen; safe to call again after the loader changed it. */
    private void refresh() {
        title.setText(model.title.isEmpty() ? "Без назви" : model.title);
        subtitle.setText(model.subtitle);
        subtitle.setVisibility(model.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        String len = model.seconds > 0 ? (model.count > 1 ? Ui.duration(model.seconds) + " загалом" : "Тривалість " + Ui.duration(model.seconds)) : "";
        String line = model.count > 1 ? model.count + " " + tracks(model.count) + (len.isEmpty() ? "" : " · " + len) : len;
        if (!model.note.isEmpty()) line = line.isEmpty() ? model.note : line + " · " + model.note;
        length.setText(line);
        button.setText(failed ? "Спробувати ще раз" : model.count > 1 ? "Скачати всі (" + model.count + ")" : "Скачати");
        updateRows();
        if (!model.cover.isEmpty() && !model.cover.equals(shownCover)) {
            final String url = shownCover = model.cover;
            IO.execute(() -> {
                Bitmap b = DownloadService.fetchBitmap(url);
                if (b == null) return;
                final Bitmap sq = DownloadService.squareCrop(b, 300);
                main.post(() -> { cover.setImageBitmap(sq); cover.setAlpha(0f); cover.animate().alpha(1f).setDuration(250).start(); });
            });
        }
    }

    private static String tracks(int n) {
        int a = n % 10, b = n % 100;
        return a == 1 && b != 11 ? "трек" : a >= 2 && a <= 4 && (b < 12 || b > 14) ? "треки" : "треків";
    }

    void refreshFolder() {
        Uri tree = Store.tree(act);
        String name = Store.treeName(act, tree);
        folder.setText("Папка: " + (name != null ? name : "не вибрано"));
        folder.setTextColor(name != null ? Ui.FG : Ui.FG2);
    }

    void onFolderChosen() {
        refreshFolder();
        if (startAfterFolder) { startAfterFolder = false; onDownload(); }
    }

    private void onDownload() {
        if (failed) { load(); return; }
        Uri tree = Store.tree(act);
        if (tree == null || Store.treeName(act, tree) == null) {
            startAfterFolder = true;
            act.pickFolder();
            return;
        }
        act.ensureNotificationPermission();
        model.starter.start(RATES[selected], tree);
        Toast.makeText(act, model.count > 1 ? "У черзі " + model.count + " " + tracks(model.count) + ". Прогрес — у сповіщеннях."
                : "Скачування почалося. Прогрес — у сповіщеннях.", Toast.LENGTH_SHORT).show();
        dismiss();
    }

    private void load() {
        failed = false;
        status.setTextColor(Ui.FG3);
        status.setText("Уточнюю дані…");
        status.setVisibility(View.VISIBLE);
        refresh();
        IO.execute(() -> {
            try {
                model.loader.load(model);
                main.post(() -> { status.setVisibility(View.GONE); refresh(); });
            } catch (final Throwable e) {
                main.post(() -> {
                    failed = true;
                    status.setTextColor(Ui.DANGER);
                    status.setText(DownloadService.explain(e));
                    refresh();
                });
            }
        });
    }
}
