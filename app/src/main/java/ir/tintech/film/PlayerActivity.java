package ir.tintech.film;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
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
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.Collections;

/**
 * پلیر داخلی — CDN مستقیم، زیرنویس روی همان timeline (منطق مشابه VLC)
 */
@UnstableApi
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO = "video";
    public static final String EXTRA_SUB = "sub";
    public static final String EXTRA_TITLE = "title";

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView titleView;
    private String videoUrl;
    private String subUrl;
    private String title;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.playerView);
        titleView = findViewById(R.id.playerTitle);
        ImageButton btnClose = findViewById(R.id.btnClose);

        parseIntent(getIntent());
        titleView.setText(TextUtils.isEmpty(title) ? "پخش آنلاین" : title);
        btnClose.setOnClickListener(v -> finish());

        if (TextUtils.isEmpty(videoUrl)) {
            Toast.makeText(this, "لینک پخش معتبر نیست", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initPlayer();
    }

    private void parseIntent(Intent intent) {
        if (intent == null) return;
        videoUrl = intent.getStringExtra(EXTRA_VIDEO);
        subUrl = intent.getStringExtra(EXTRA_SUB);
        title = intent.getStringExtra(EXTRA_TITLE);

        // پشتیبانی از deep link /play?u=&sub=&title=
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
            // اگر خود URI لینک ویدیو بود
            if (TextUtils.isEmpty(videoUrl) && isDirectMedia(data.toString())) {
                videoUrl = data.toString();
            }
        }
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setShowSubtitleButton(true);
        playerView.setControllerShowTimeoutMs(3500);
        playerView.setControllerHideOnTouch(true);

        MediaItem.Builder mb = new MediaItem.Builder().setUri(Uri.parse(videoUrl));
        if (!TextUtils.isEmpty(subUrl) && subUrl.startsWith("http")) {
            String mime = guessSubMime(subUrl);
            MediaItem.SubtitleConfiguration subCfg =
                    new MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
                            .setMimeType(mime)
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
                        "پخش مستقیم ممکن نشد — VPN را خاموش کنید",
                        Toast.LENGTH_LONG).show();
            }
        });
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
    }

    @Override
    protected void onStop() {
        if (player != null) playerView.onPause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
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
