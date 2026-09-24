package ir.tintech.film;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Rational;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.media3.ui.TrackSelectionDialogBuilder;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class PlayerActivity extends AppCompatActivity {
    public static final String EXTRA_VIDEO = "video", EXTRA_SUB = "sub", EXTRA_TITLE = "title",
            EXTRA_ORIGIN = "origin", EXTRA_NET = "net";
    private static final int PICK_SUBTITLE = 2101;
    private ExoPlayer player;
    private PlayerView playerView;
    private TextView hint, status;
    private View errorPanel;
    private SharedPreferences prefs;
    private String video, sourceSub, appOrigin, route, localPath = "", localMime = "", localName = "";
    private boolean relay, pendingLocal, pickerOpen, resumeAfterStop, destroyed, importing;
    private int resizeIndex;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private final Runnable hideHint = () -> { if (hint != null) hint.setVisibility(View.GONE); };
    private final Runnable bufferTimeout = () -> {
        if (player != null && player.getPlayWhenReady() && player.getPlaybackState() == Player.STATE_BUFFERING) {
            if (!tryRelay()) showError("دریافت ویدئو طول کشید. اتصال یا مسیر پخش را بررسی کنید.");
        }
    };

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBack(); }
        });
        prefs = getSharedPreferences("player", MODE_PRIVATE);
        playerView = findViewById(R.id.playerView);
        hint = findViewById(R.id.gestureHint);
        status = playerView.findViewById(R.id.playerStatus);
        errorPanel = findViewById(R.id.playerError);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.playerRoot), (v, insets) -> {
            androidx.core.graphics.Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            v.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom); return insets;
        });
        Intent intent = getIntent();
        appOrigin = intent.getStringExtra(EXTRA_ORIGIN);
        if (!new SiteConfig(this).trusted(appOrigin)) appOrigin = UrlPolicy.origin(new SiteConfig(this).addresses().get(0));
        else appOrigin = UrlPolicy.origin(appOrigin);
        video = UrlPolicy.directVideo(intent.getStringExtra(EXTRA_VIDEO), appOrigin);
        sourceSub = intent.getStringExtra(EXTRA_SUB);
        if (!UrlPolicy.isHttp(sourceSub)) sourceSub = null;
        String requestedRoute = intent.getStringExtra(EXTRA_NET);
        route = UrlPolicy.routeMode(requestedRoute == null ? prefs.getString("route", "auto") : requestedRoute);
        String title = intent.getStringExtra(EXTRA_TITLE);
        ((TextView)playerView.findViewById(R.id.playerTitle)).setText(title == null || title.trim().isEmpty() ? "FilmBuff" : title);
        if (!UrlPolicy.isHttp(video)) { toast("لینک پخش معتبر نیست"); finish(); return; }
        if (state != null) {
            localPath = state.getString("localPath", ""); localMime = state.getString("localMime", ""); localName = state.getString("localName", "");
            try {
                File f = new File(localPath);
                if (!f.isFile() || !f.getCanonicalPath().startsWith(getCacheDir().getCanonicalPath() + "/subtitles/")) localPath = "";
            } catch (Exception e) { localPath = ""; }
            route = UrlPolicy.routeMode(state.getString("route")); resizeIndex = state.getInt("resize", 0);
        }
        bindButtons();
        initPlayer();
        applyResize(); applySubtitleStyle();
        relay = state != null ? state.getBoolean("relay") : "relay".equals(route) || (!"direct".equals(route) && video.startsWith("http://"));
        player.setPlaybackSpeed(state == null ? 1f : state.getFloat("speed", 1f));
        prepare(state == null ? 0 : state.getLong("position", 0), state == null || state.getBoolean("playing", true));
        setupGestures(); enterImmersive();
    }

    private void bindButtons() {
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnSubtitles).setOnClickListener(v -> subtitleSettings());
        findViewById(R.id.btnQuality).setOnClickListener(v -> trackDialog(C.TRACK_TYPE_VIDEO, "کیفیت تصویر"));
        findViewById(R.id.btnAudio).setOnClickListener(v -> trackDialog(C.TRACK_TYPE_AUDIO, "زبان صدا"));
        findViewById(R.id.btnSpeed).setOnClickListener(v -> speedDialog());
        findViewById(R.id.btnAspect).setOnClickListener(v -> { resizeIndex = (resizeIndex + 1) % 3; applyResize(); showHint(new String[]{"اندازهٔ اصلی", "پر کردن صفحه", "بزرگ‌نمایی"}[resizeIndex]); });
        findViewById(R.id.btnNetwork).setOnClickListener(v -> routeDialog());
        findViewById(R.id.btnPip).setOnClickListener(v -> enterPip());
        if (!canPip()) findViewById(R.id.btnPip).setVisibility(View.GONE);
        findViewById(R.id.playerRetry).setOnClickListener(v -> { resetRoute(); prepare(player.getCurrentPosition(), true); });
        findViewById(R.id.playerChangeRoute).setOnClickListener(v -> routeDialog());
        findViewById(R.id.playerExit).setOnClickListener(v -> finish());
    }
    private void initPlayer() {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("FilmBuff/" + BuildConfig.VERSION_NAME)
                .setConnectTimeoutMs(10000).setReadTimeoutMs(15000).setAllowCrossProtocolRedirects(false);
        DefaultDataSource.Factory sources = new DefaultDataSource.Factory(this, http);
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(sources))
                .setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setPreferredTextLanguage("fa")
                .setSelectUndeterminedTextLanguage(true).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !prefs.getBoolean("subEnabled", true)).build());
        playerView.setPlayer(player);
        playerView.setControllerShowTimeoutMs(BuildConfig.IS_TV ? 5500 : 3500);
        playerView.setControllerAnimationEnabled(true);
        playerView.setControllerHideOnTouch(true);
        playerView.setKeepContentOnPlayerReset(true);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                handler.removeCallbacks(bufferTimeout);
                if (state == Player.STATE_BUFFERING && player.getPlayWhenReady()) handler.postDelayed(bufferTimeout, 22000);
                if (state == Player.STATE_READY) { errorPanel.setVisibility(View.GONE); updateStatus(); }
            }
            @Override public void onPlayWhenReadyChanged(boolean ready, int reason) {
                handler.removeCallbacks(bufferTimeout);
                if (ready && player.getPlaybackState() == Player.STATE_BUFFERING) handler.postDelayed(bufferTimeout, 22000);
            }
            @Override public void onPlayerError(PlaybackException e) {
                handler.removeCallbacks(bufferTimeout);
                if (e.errorCode >= 2000 && e.errorCode < 3000 && tryRelay()) return;
                showError(e.errorCode >= 4000 && e.errorCode < 6000
                        ? "این قالب ویدئو یا صدا روی دستگاه پشتیبانی نمی‌شود. کیفیت دیگری را انتخاب کنید."
                        : "پخش متوقف شد. اتصال، اعتبار لینک یا مسیر پخش را بررسی کنید.");
            }
            @Override public void onTracksChanged(Tracks tracks) {
                if (!pendingLocal) return;
                for (Tracks.Group group : tracks.getGroups()) if (group.getType() == C.TRACK_TYPE_TEXT) {
                    for (int i = 0; i < group.length; i++) {
                        String id = group.getTrackFormat(i).id;
                        if (id != null && id.contains("filmbuff-local") && group.isTrackSupported(i)) {
                            pendingLocal = false;
                            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                    .setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(), i)).build());
                            return;
                        }
                    }
                }
            }
        });
    }
    private void prepare(long position, boolean play) {
        if (player == null) return;
        handler.removeCallbacks(bufferTimeout); errorPanel.setVisibility(View.GONE);
        String url = relay ? UrlPolicy.relay(video, appOrigin) : video;
        if (url.isEmpty()) { showError("آدرس اتصال معتبر نیست"); return; }
        MediaItem.Builder media = new MediaItem.Builder().setUri(url);
        if (UrlPolicy.mediaPath(video).endsWith(".m3u8")) media.setMimeType(MimeTypes.APPLICATION_M3U8);
        List<MediaItem.SubtitleConfiguration> subtitles = new ArrayList<>();
        if (sourceSub != null) subtitles.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(sourceSub))
                .setId("filmbuff-source").setMimeType(UrlPolicy.subtitleMime(sourceSub)).setLanguage("fa")
                .setLabel("زیرنویس آنلاین").setSelectionFlags(localPath.isEmpty() ? C.SELECTION_FLAG_DEFAULT : 0).build());
        if (!localPath.isEmpty()) subtitles.add(new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(new File(localPath)))
                .setId("filmbuff-local").setMimeType(localMime).setLanguage("fa").setLabel(localName)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build());
        pendingLocal = !localPath.isEmpty();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).build());
        player.setMediaItem(media.setSubtitleConfigurations(subtitles).build(), Math.max(0, position));
        player.prepare(); player.setPlayWhenReady(play); updateStatus();
    }
    private boolean tryRelay() {
        if (!"auto".equals(route) || relay || UrlPolicy.relay(video, appOrigin).isEmpty()) return false;
        relay = true; showHint("در حال امتحان مسیر جایگزین…");
        prepare(player.getCurrentPosition(), player.getPlayWhenReady()); return true;
    }
    private void resetRoute() { relay = "relay".equals(route) || ("auto".equals(route) && video.startsWith("http://")); }
    private void updateStatus() { status.setText("FILMBUFF  /  " + (relay ? "اتصال از طریق سایت" : "اتصال مستقیم") + (BuildConfig.IS_TV ? "  ·  TV" : "")); }
    private void routeDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("مسیر پخش")
                .setSingleChoiceItems(new String[]{"خودکار (پیشنهادی)", "مستقیم", "از طریق سایت"}, "auto".equals(route) ? 0 : "direct".equals(route) ? 1 : 2,
                        (d, which) -> { route = new String[]{"auto", "direct", "relay"}[which]; prefs.edit().putString("route", route).apply(); d.dismiss(); resetRoute(); prepare(player.getCurrentPosition(), true); })
                .setNegativeButton("بستن", null).show();
    }
    private void showError(String text) {
        if (destroyed) return;
        player.pause();
        ((TextView)findViewById(R.id.playerErrorText)).setText(text);
        errorPanel.setVisibility(View.VISIBLE); playerView.hideController();
        findViewById(R.id.playerRetry).requestFocus();
    }
    private void trackDialog(int type, String title) {
        boolean available = false;
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) if (g.getType() == type) available = true;
        if (!available) { toast("این فایل گزینهٔ دیگری ندارد"); return; }
        new TrackSelectionDialogBuilder(this, title, player, type).setAllowAdaptiveSelections(true).build().show();
    }
    private void speedDialog() {
        float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f}; int selected = 2;
        for (int i = 0; i < speeds.length; i++) if (speeds[i] == player.getPlaybackParameters().speed) selected = i;
        new MaterialAlertDialogBuilder(this).setTitle("سرعت پخش").setSingleChoiceItems(new String[]{"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"}, selected,
                (d, which) -> { player.setPlaybackSpeed(speeds[which]); d.dismiss(); }).setNegativeButton("بستن", null).show();
    }
    private void applyResize() { playerView.setResizeMode(new int[]{AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_FILL, AspectRatioFrameLayout.RESIZE_MODE_ZOOM}[Math.max(0, Math.min(2, resizeIndex))]); }

    private void applySubtitleStyle() {
        SubtitleView sub = playerView.getSubtitleView(); if (sub == null) return;
        sub.setApplyEmbeddedStyles(false); sub.setApplyEmbeddedFontSizes(false);
        sub.setStyle(new CaptionStyleCompat(prefs.getInt("subColor", Color.WHITE), prefs.getBoolean("subBackground", false) ? 0xC0000000 : Color.TRANSPARENT,
                Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, Typeface.DEFAULT_BOLD));
        sub.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getInt("subSize", BuildConfig.IS_TV ? 28 : 22));
        sub.setBottomPaddingFraction(prefs.getInt("subBottom", 8) / 100f);
    }
    private void subtitleSettings() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), dp(18)); content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); scroll.addView(content);
        TextView preview = label("فیلم خوب، یک شب به‌یادماندنی", 22);
        preview.setGravity(android.view.Gravity.CENTER); preview.setPadding(dp(12), dp(18), dp(12), dp(18)); preview.setBackgroundColor(0xFF0A1117);
        Runnable update = () -> { applySubtitleStyle(); preview.setTextColor(prefs.getInt("subColor", Color.WHITE)); preview.setTextSize(prefs.getInt("subSize", BuildConfig.IS_TV ? 28 : 22)); };
        content.addView(preview); update.run();
        SwitchMaterial enabled = new SwitchMaterial(this); enabled.setText("نمایش زیرنویس");
        enabled.setChecked(!player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT));
        enabled.setOnCheckedChangeListener((v, checked) -> { prefs.edit().putBoolean("subEnabled", checked).apply(); player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !checked).build()); });
        content.addView(enabled);
        Button track = button("انتخاب زبان یا زیرنویس ویدئو"); content.addView(track);
        track.setOnClickListener(v -> trackDialog(C.TRACK_TYPE_TEXT, "انتخاب زیرنویس"));
        Button importFile = button(importing ? "در حال خواندن فایل…" : "افزودن فایل زیرنویس"); content.addView(importFile);
        importFile.setEnabled(!importing);
        content.addView(label(localPath.isEmpty() ? "SRT · VTT · ASS · SSA  |  حداکثر ۴ مگابایت" : localName, 13));
        if (!localPath.isEmpty()) {
            Button remove = button("حذف زیرنویس اضافه‌شده"); content.addView(remove);
            remove.setOnClickListener(v -> { localPath = ""; localName = ""; prepare(player.getCurrentPosition(), player.getPlayWhenReady()); remove.setEnabled(false); toast("زیرنویس فایل برداشته شد"); });
        }
        content.addView(label("رنگ زیرنویس", 16));
        LinearLayout colors = new LinearLayout(this);
        int[] palette = {Color.WHITE, 0xFFFFDF66, 0xFFB9F6E1, 0xFF7FDDFF, 0xFFFFA4CB};
        String[] colorNames = {"سفید", "زرد", "سبز", "آبی", "صورتی"};
        for (int i = 0; i < palette.length; i++) {
            final int color = palette[i]; Button swatch = button("●"); swatch.setTextColor(color); swatch.setTextSize(28); swatch.setContentDescription(colorNames[i]);
            colors.addView(swatch, new LinearLayout.LayoutParams(0, dp(52), 1));
            swatch.setOnClickListener(v -> { prefs.edit().putInt("subColor", color).apply(); update.run(); });
        }
        content.addView(colors);
        LinearLayout custom = new LinearLayout(this); custom.setGravity(android.view.Gravity.CENTER_VERTICAL);
        EditText hex = new EditText(this); hex.setSingleLine(); hex.setTextDirection(View.TEXT_DIRECTION_LTR); hex.setHint("#FFFFFF"); hex.setContentDescription("کد رنگ دلخواه"); hex.setText(String.format(java.util.Locale.ROOT, "#%06X", prefs.getInt("subColor", Color.WHITE) & 0xFFFFFF));
        custom.addView(hex, new LinearLayout.LayoutParams(0, dp(52), 1)); Button apply = button("اعمال رنگ"); custom.addView(apply);
        apply.setOnClickListener(v -> { String value = hex.getText().toString().trim(); if (!value.matches("#[0-9a-fA-F]{6}")) { hex.setError("نمونه: #FFFFFF"); return; } prefs.edit().putInt("subColor", Color.parseColor(value)).apply(); update.run(); });
        content.addView(custom);
        slider(content, "اندازهٔ زیرنویس", "subSize", 14, 42, BuildConfig.IS_TV ? 28 : 22, update);
        slider(content, "فاصله از پایین تصویر", "subBottom", 3, 25, 8, update);
        SwitchMaterial background = new SwitchMaterial(this); background.setText("پس‌زمینهٔ تیرهٔ زیرنویس"); background.setChecked(prefs.getBoolean("subBackground", false));
        background.setOnCheckedChangeListener((v, checked) -> { prefs.edit().putBoolean("subBackground", checked).apply(); applySubtitleStyle(); }); content.addView(background);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("زیرنویس و ظاهر آن").setView(scroll).setPositiveButton("تمام", null).create();
        importFile.setOnClickListener(v -> { dialog.dismiss(); pickSubtitle(); });
        dialog.show();
        if (BuildConfig.IS_TV && dialog.getWindow() != null) dialog.getWindow().setLayout(dp(560), (int)(getResources().getDisplayMetrics().heightPixels * .85f));
    }
    private void slider(LinearLayout parent, String title, String key, int min, int max, int defaultValue, Runnable update) {
        TextView caption = label(title + " · " + prefs.getInt(key, defaultValue), 15); parent.addView(caption);
        SeekBar seek = new SeekBar(this); seek.setMax(max - min); seek.setProgress(prefs.getInt(key, defaultValue) - min); seek.setContentDescription(title); seek.setMinimumHeight(dp(48)); parent.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) { if (fromUser) { prefs.edit().putInt(key, value + min).apply(); caption.setText(title + " · " + (value + min)); update.run(); } }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }
    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(BuildConfig.IS_TV ? 17 : 14); b.setMinHeight(dp(48)); b.setBackgroundResource(R.drawable.control_focus); b.setPadding(dp(12), dp(4), dp(12), dp(4)); return b; }
    private TextView label(String text, int size) { TextView v = new TextView(this); v.setText(text); v.setTextColor(0xFFDCE6EA); v.setTextSize(size); v.setPadding(0, dp(10), 0, dp(10)); return v; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void pickSubtitle() {
        if (importing) return;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { pickerOpen = true; startActivityForResult(pick, PICK_SUBTITLE); }
        catch (Exception e) { pickerOpen = false; toast("انتخاب‌گر فایل روی دستگاه موجود نیست؛ یک برنامهٔ سازگار با انتخاب فایل نصب کنید."); }
    }
    @Override protected void onActivityResult(int request, int result, @Nullable Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_SUBTITLE) return;
        pickerOpen = false;
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (!"content".equals(uri.getScheme())) { toast("فایل را از انتخاب‌گر دستگاه انتخاب کنید"); return; }
        importing = true; showHint("در حال خواندن زیرنویس…");
        files.execute(() -> {
            File output = null;
            try {
                String name = "زیرنویس فایل";
                try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (c != null && c.moveToFirst() && !c.isNull(0)) name = c.getString(0);
                }
                SubtitleSupport.Document document;
                try (InputStream input = getContentResolver().openInputStream(uri)) { document = SubtitleSupport.read(input); }
                File directory = new File(getCacheDir(), "subtitles");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("فضای کافی برای فایل موجود نیست");
                output = File.createTempFile("caption-", document.extension, directory);
                try (FileOutputStream stream = new FileOutputStream(output)) { stream.write(document.text.getBytes(StandardCharsets.UTF_8)); }
                final File ready = output; final String displayName = name;
                handler.post(() -> {
                    importing = false;
                    if (destroyed || player == null) { ready.delete(); return; }
                    localPath = ready.getAbsolutePath(); localMime = document.mime; localName = displayName;
                    prefs.edit().putBoolean("subEnabled", true).apply();
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build());
                    prepare(player.getCurrentPosition(), player.getPlayWhenReady() || resumeAfterStop);
                    showHint("زیرنویس اضافه شد");
                });
            } catch (Exception e) {
                if (output != null) output.delete();
                handler.post(() -> { importing = false; if (!destroyed) toast(e instanceof java.io.IOException && e.getMessage() != null ? e.getMessage() : "خواندن فایل زیرنویس ممکن نشد"); });
            }
        });
    }

    private void setupGestures() {
        if (BuildConfig.IS_TV) return;
        GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { seekBy(e.getX() < playerView.getWidth() / 2f ? -10000 : 10000); return true; }
        });
        playerView.setOnTouchListener((v, e) -> { detector.onTouchEvent(e); return false; });
    }
    private void seekBy(long amount) {
        if (player == null || !player.isCurrentMediaItemSeekable()) return;
        long position = Math.max(0, player.getCurrentPosition() + amount);
        if (player.getDuration() != C.TIME_UNSET) position = Math.min(position, player.getDuration());
        player.seekTo(position); showHint(amount < 0 ? "۱۰ ثانیه عقب" : "۱۰ ثانیه جلو");
    }
    private void showHint(String text) { hint.setText(text); hint.setVisibility(View.VISIBLE); handler.removeCallbacks(hideHint); handler.postDelayed(hideHint, 1400); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (BuildConfig.IS_TV && player != null && errorPanel.getVisibility() != View.VISIBLE) {
            int key = event.getKeyCode();
            if (key == KeyEvent.KEYCODE_MENU) { if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) subtitleSettings(); return true; }
            if (!playerView.isControllerFullyVisible() && (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) seekBy(key == KeyEvent.KEYCODE_DPAD_LEFT ? -10000 : 10000);
                return true;
            }
            if ((key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) && !playerView.isControllerFullyVisible()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) { playerView.showController(); findViewById(androidx.media3.ui.R.id.exo_play_pause).requestFocus(); }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    private boolean canPip() { return !BuildConfig.IS_TV && Build.VERSION.SDK_INT >= 26 && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE); }
    private boolean inPip() { return Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode(); }
    private void enterPip() {
        if (Build.VERSION.SDK_INT < 26 || !canPip() || player == null || pickerOpen) return;
        try { enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build()); }
        catch (Exception e) { toast("تصویر در تصویر در دسترس نیست"); }
    }
    private void enterImmersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) { c.hide(WindowInsets.Type.systemBars()); c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
        } else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override protected void onStart() {
        super.onStart();
        if (player != null) { playerView.onResume(); if (resumeAfterStop) { player.play(); resumeAfterStop = false; } }
        enterImmersive();
    }
    @Override protected void onResume() { super.onResume(); enterImmersive(); }
    @Override protected void onUserLeaveHint() { super.onUserLeaveHint(); if (player != null && player.isPlaying() && !inPip() && !pickerOpen) enterPip(); }
    @Override public void onPictureInPictureModeChanged(boolean pip, Configuration config) {
        super.onPictureInPictureModeChanged(pip, config);
        if (playerView != null) { playerView.setUseController(!pip); if (pip) playerView.hideController(); }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (player != null) {
            out.putLong("position", player.getCurrentPosition()); out.putBoolean("playing", player.getPlayWhenReady() || resumeAfterStop);
            out.putFloat("speed", player.getPlaybackParameters().speed); out.putInt("resize", resizeIndex);
            out.putString("route", route); out.putBoolean("relay", relay);
            out.putString("localPath", localPath); out.putString("localMime", localMime); out.putString("localName", localName);
        }
        super.onSaveInstanceState(out);
    }
    @Override protected void onStop() {
        if (player != null && !inPip()) { resumeAfterStop = player.getPlayWhenReady(); player.pause(); playerView.onPause(); handler.removeCallbacks(bufferTimeout); }
        super.onStop();
    }
    private void handleBack() {
        if (BuildConfig.IS_TV && playerView.isControllerFullyVisible() && errorPanel.getVisibility() != View.VISIBLE) playerView.hideController();
        else finish();
    }
    @Override protected void onDestroy() {
        destroyed = true; files.shutdownNow(); handler.removeCallbacksAndMessages(null);
        if (player != null) { playerView.setPlayer(null); player.release(); player = null; }
        super.onDestroy();
    }
}
