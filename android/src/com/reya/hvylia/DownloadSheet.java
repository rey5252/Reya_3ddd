package com.reya.hvylia;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bottom sheet: cover, title, channel, duration, MP3 quality with file sizes, folder, "Скачати". */
final class DownloadSheet extends Dialog {
    private static final int[] RATES = { 320, 192, 128 };
    private static final ExecutorService IO = Executors.newCachedThreadPool();

    private final YouTubeActivity act;
    private final String videoId;
    private final String videoUrl;
    private final Handler main = new Handler(Looper.getMainLooper());

    private ImageView cover;
    private TextView title, channel, length, status, folder, button;
    private final LinearLayout[] rows = new LinearLayout[3];
    private final TextView[] sizes = new TextView[3];
    private final View[] radios = new View[3];
    private int selected;
    private long seconds;
    private boolean loading = true;
    private boolean failed;
    private boolean startAfterFolder;
    private String videoTitle = "";

    DownloadSheet(YouTubeActivity act, String videoId) {
        super(act);
        this.act = act;
        this.videoId = videoId;
        this.videoUrl = "https://www.youtube.com/watch?v=" + videoId;
        int saved = Store.kbps(act);
        selected = saved == 320 ? 0 : saved == 128 ? 2 : 1;
        build();
        loadInfo();
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

        // header: cover + title, channel, duration
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
        title = Ui.text(act, "Завантажую дані про відео…", 16, Ui.FG, "manrope-800");
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(0, 1.12f);
        channel = Ui.text(act, "", 13.5f, Ui.FG2, "manrope-600");
        channel.setSingleLine(true);
        channel.setEllipsize(TextUtils.TruncateAt.END);
        length = Ui.text(act, "", 12.5f, Ui.FG3, "manrope-600");
        meta.addView(title);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = dp(6);
        meta.addView(channel, cl);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.topMargin = dp(4);
        meta.addView(length, ll);
        head.addView(meta, ml);
        root.addView(head);

        status = Ui.text(act, "", 13, Ui.DANGER, "manrope-600");
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

        for (int i = 0; i < 3; i++) root.addView(qualityRow(i), rowParams());

        // folder
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
        updateRows();
        updateButton();
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(8);
        return p;
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
        View spacer = new View(act);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
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
            radios[i].setForeground(on ? new android.graphics.drawable.InsetDrawable(Ui.oval(Ui.ACCENT_INK), dp(7)) : null);
            sizes[i].setText(seconds > 0 ? Ui.size(seconds, RATES[i]) : "");
        }
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

    private void updateButton() {
        if (loading) {
            button.setText("Завантажую дані…");
            button.setAlpha(0.55f);
        } else if (failed) {
            button.setText("Спробувати ще раз");
            button.setAlpha(1f);
        } else {
            button.setText("Скачати");
            button.setAlpha(1f);
        }
    }

    private void onDownload() {
        if (loading) return;
        if (failed) { loadInfo(); return; }
        Uri tree = Store.tree(act);
        if (tree == null || Store.treeName(act, tree) == null) {
            startAfterFolder = true;
            act.pickFolder();
            return;
        }
        act.ensureNotificationPermission();
        DownloadService.start(act, videoUrl, videoTitle, RATES[selected], tree);
        Toast.makeText(act, "Скачування почалося. Прогрес — у сповіщеннях.", Toast.LENGTH_SHORT).show();
        dismiss();
    }

    private void loadInfo() {
        loading = true;
        failed = false;
        status.setVisibility(View.GONE);
        updateButton();
        IO.execute(() -> {
            final Bitmap b = DownloadService.fetchBitmap("https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg");
            if (b != null) {
                final Bitmap sq = DownloadService.squareCrop(b, 300);
                main.post(() -> { cover.setImageBitmap(sq); cover.setAlpha(0f); cover.animate().alpha(1f).setDuration(250).start(); });
            }
        });
        IO.execute(() -> {
            try {
                YtDownloader.ensureInit();
                final StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl);
                main.post(() -> {
                    loading = false;
                    videoTitle = info.getName() == null ? "" : info.getName();
                    title.setText(videoTitle.isEmpty() ? "Відео без назви" : videoTitle);
                    channel.setText(DownloadService.channel(info.getUploaderName()));
                    seconds = info.getDuration();
                    length.setText(seconds > 0 ? "Тривалість " + Ui.duration(seconds) : "");
                    updateRows();
                    updateButton();
                });
            } catch (final Throwable e) {
                main.post(() -> {
                    loading = false;
                    failed = true;
                    if (videoTitle.isEmpty()) title.setText("Не вдалося отримати дані");
                    status.setText(DownloadService.explain(e));
                    status.setVisibility(View.VISIBLE);
                    updateButton();
                });
            }
        });
    }
}
