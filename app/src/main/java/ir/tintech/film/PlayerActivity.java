package ir.tintech.film;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TrackSelectionDialogBuilder;

import java.util.Collections;

/**
 * FilmBuff internal player.
 * Direct CDN playback + Media3 subtitle timeline, with controls that fully disappear during viewing.
 */
@UnstableApi
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO = "video";
    public static final String EXTRA_SUB = "sub";
    public static final String EXTRA_TITLE = "title";

    private ExoPlayer player;
    private PlayerView playerView;
    private View quickControls;
    private TextView gestureHint;
    private String videoUrl;
    private String subUrl;
    private String title;
    private int resizeModeIndex = 0;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideGestureHint = () -> {
        if (gestureHint != null) gestureHint.setVisibility(View.GONE);
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);
        enterImmersiveMode();

        playerView = findViewById(R.id.playerView);
        quickControls = findViewById(R.id.quickControls);
        gestureHint = findViewById(R.id.gestureHint);

        ImageButton btnClose = findViewById(R.id.btnClose);
        ImageButton btnQuality = findViewById(R.id.btnQuality);
        ImageButton btnAudio = findViewById(R.id.btnAudio);
        ImageButton btnAspect = findViewById(R.id.btnAspect);
        ImageButton btnPip = findViewById(R.id.btnPip);

        parseIntent(getIntent());
        btnClose.setOnClickListener(v -> finish());
        btnQuality.setOnClickListener(v -> showTrackDialog(C.TRACK_TYPE_VIDEO, "کیفیت تصویر"));
        btnAudio.setOnClickListener(v -> showTrackDialog(C.TRACK_TYPE_AUDIO, "صدای پخش"));
        btnAspect.setOnClickListener(v -> cycleResizeMode());
        btnPip.setOnClickListener(v -> enterPip());

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            btnPip.setVisibility(View.GONE);
        }

        if (TextUtils.isEmpty(videoUrl)) {
            Toast.makeText(this, "لینک پخش معتبر نیست", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initPlayer();
        setupGestureControls();
    }

    private void parseIntent(Intent intent) {
        if (intent == null) return;
        videoUrl = intent.getStringExtra(EXTRA_VIDEO);
        subUrl = intent.getStringExtra(EXTRA_SUB);
        title = intent.getStringExtra(EXTRA_TITLE);

        Uri data = intent.getData();
        if (data != null) {
            if (TextUtils.isEmpty(videoUrl)) {
                String u = data.getQueryParameter("u");
                if (u == null) u = data.getQueryParameter("url");
                if (u != null) videoUrl = u;
            }
            if (TextUtils.isEmpty(subUrl)) {
                String s = data.getQueryParameter("sub");
                if (s == null) s = data.getQueryParameter("vtt");
                if (s == null) s = data.getQueryParameter("srt");
                if (s != null) subUrl = s;
            }
            if (TextUtils.isEmpty(title)) {
                String t = data.getQueryParameter("title");
                if (t != null) title = t;
            }
            if (TextUtils.isEmpty(videoUrl) && isDirectMedia(data.toString())) {
                videoUrl = data.toString();
            }
        }
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .build();

        player.setHandleAudioBecomingNoisy(true);
        player.setTrackSelectionParameters(
                player.getTrackSelectionParameters()
                        .buildUpon()
                        .setPreferredTextLanguage("fa")
                        .setSelectUndeterminedTextLanguage(true)
                        .build());

        playerView.setPlayer(player);
        playerView.setShowSubtitleButton(true);
        playerView.setControllerShowTimeoutMs(2600);
        playerView.setControllerHideOnTouch(true);
        playerView.setControllerAutoShow(true);
        playerView.setKeepContentOnPlayerReset(true);

        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            quickControls.setVisibility(visibility == View.VISIBLE ? View.VISIBLE : View.GONE);
        });

        DefaultTimeBar timeBar = playerView.findViewById(androidx.media3.ui.R.id.exo_progress);
        if (timeBar != null) {
            timeBar.setPlayedColor(0xFFFF7A18);
            timeBar.setBufferedColor(0x8871C8FF);
            timeBar.setUnplayedColor(0x55FFFFFF);
            timeBar.setScrubberColor(0xFFFFFFFF);
        }

        MediaItem.Builder mb = new MediaItem.Builder().setUri(Uri.parse(videoUrl));
        if (!TextUtils.isEmpty(subUrl) && subUrl.startsWith("http")) {
            MediaItem.SubtitleConfiguration subCfg =
                    new MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
                            .setMimeType(guessSubMime(subUrl))
                            .setLanguage("fa")
                            .setLabel("فارسی")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build();
            mb.setSubtitleConfigurations(Collections.singletonList(subCfg));
        }

        player.setMediaItem(mb.build());
        player.setPlayWhenReady(true);
        player.prepare();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(PlayerActivity.this,
                        "پخش مستقیم ممکن نشد — اتصال یا CDN را بررسی کنید",
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
                // Media3 keeps the selected side-loaded subtitle on the same media timeline.
            }
        });
    }

    private void showTrackDialog(@C.TrackType int trackType, String dialogTitle) {
        if (player == null) return;
        try {
            new TrackSelectionDialogBuilder(this, dialogTitle, player, trackType)
                    .setAllowAdaptiveSelections(trackType == C.TRACK_TYPE_VIDEO)
                    .setShowDisableOption(trackType != C.TRACK_TYPE_VIDEO)
                    .build()
                    .show();
            playerView.hideController();
        } catch (Exception e) {
            Toast.makeText(this, "گزینه‌ای برای انتخاب وجود ندارد", Toast.LENGTH_SHORT).show();
        }
    }

    private void cycleResizeMode() {
        resizeModeIndex = (resizeModeIndex + 1) % 3;
        if (resizeModeIndex == 0) {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            showTransientHint("اندازه: Fit");
        } else if (resizeModeIndex == 1) {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
            showTransientHint("اندازه: Fill");
        } else {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
            showTransientHint("اندازه: Zoom");
        }
        playerView.hideController();
    }

    private void setupGestureControls() {
        GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (player == null || player.getDuration() <= 0) return false;
                boolean rewind = e.getX() < (playerView.getWidth() / 2f);
                long target = player.getCurrentPosition() + (rewind ? -10_000 : 10_000);
                target = Math.max(0, Math.min(target, player.getDuration()));
                player.seekTo(target);
                showTransientHint(rewind ? "−10 ثانیه" : "+10 ثانیه");
                return true;
            }
        });

        playerView.setOnTouchListener((v, event) -> {
            detector.onTouchEvent(event);
            return false;
        });
    }

    private void showTransientHint(String text) {
        uiHandler.removeCallbacks(hideGestureHint);
        gestureHint.setText(text);
        gestureHint.setVisibility(View.VISIBLE);
        uiHandler.postDelayed(hideGestureHint, 850);
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || player == null) return;
        try {
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9))
                    .build();
            enterPictureInPictureMode(params);
        } catch (Exception ignored) {
        }
    }

    private void enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private static String guessSubMime(String url) {
        String u = url.toLowerCase();
        if (u.contains(".vtt")) return MimeTypes.TEXT_VTT;
        if (u.contains(".ass") || u.contains(".ssa")) return MimeTypes.TEXT_SSA;
        return MimeTypes.APPLICATION_SUBRIP;
    }

    static boolean isDirectMedia(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains(".mp4") || u.contains(".mkv") || u.contains(".m3u8")
                || u.contains("m3u8?") || u.contains(".m3u");
    }

    static boolean isAppPlayPath(Uri uri) {
        if (uri == null) return false;
        String path = uri.getPath() != null ? uri.getPath() : "";
        return path.equals("/play") || path.startsWith("/play?");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null) playerView.onResume();
        enterImmersiveMode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && player != null
                && player.isPlaying()
                && !isInPictureInPictureMode()) {
            enterPip();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
                                              Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            playerView.hideController();
            quickControls.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStop() {
        if (player != null && !isInPictureInPictureMode()) playerView.onPause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
