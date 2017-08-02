package fm.jiecao.jcvideoplayer_lib;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import tv.danmaku.ijk.media.player.IMediaPlayer;

/**
 * Created by Nathen on 16/7/30.
 */
public abstract class JCVideoPlayer extends FrameLayout implements JCMediaPlayerListener, View.OnClickListener, SeekBar.OnSeekBarChangeListener, View.OnTouchListener, TextureView.SurfaceTextureListener {

    public static final String TAG = "JieCaoVideoPlayer";

    public static final int FULLSCREEN_ID = R.id.FULLSCREEN_ID;
    public static final int TINY_ID = R.id.TINY_ID;
    public static final int THRESHOLD = 80;
    public static final int FULL_SCREEN_NORMAL_DELAY = 500;

    public static boolean ACTION_BAR_EXIST = true;
    public static boolean TOOL_BAR_EXIST = true;
    public static boolean WIFI_TIP_DIALOG_SHOWED = false;

    public static long CLICK_QUIT_FULLSCREEN_TIME = 0;

    public static final int SCREEN_LAYOUT_NORMAL = 0;
    public static final int SCREEN_LAYOUT_LIST = 1;
    public static final int SCREEN_WINDOW_FULLSCREEN = 2;
    public static final int SCREEN_WINDOW_TINY = 3;

    public static final int CURRENT_STATE_NORMAL = 0;
    public static final int CURRENT_STATE_PREPARING = 1;
    public static final int CURRENT_STATE_PLAYING = 2;
    public static final int CURRENT_STATE_PLAYING_BUFFERING_START = 3;
    public static final int CURRENT_STATE_PAUSE = 5;
    public static final int CURRENT_STATE_AUTO_COMPLETE = 6;
    public static final int CURRENT_STATE_ERROR = 7;

    public static final int SCREEN_ORIENTATION_INVALID = -2;

    public int currentState = -1;
    public int currentScreen = -1;
    public int pauseBackupState = CURRENT_STATE_PLAYING;

    public String url = "";
    public Object[] objects = null;
    public boolean looping = false;
    public Map<String, String> mapHeadData = new HashMap<>();
    public int seekToInAdvance = -1;
    protected static Bitmap textureSwitchCacheBitmap = null;
    private boolean textureSizeChanged;

    public ImageView startButton;
    public JCResizeImageView textureCacheImg;

    public SeekBar progressBar;
    public ImageView fullscreenButton;
    public TextView currentTimeTextView, totalTimeTextView;
    public ViewGroup textureViewContainer;
    public ViewGroup topContainer, bottomContainer;
    public JCResizeTextureView textureView;
    public Surface surface;

    protected static WeakReference<JCUserAction> JC_USER_EVENT;
    protected static Timer UPDATE_PROGRESS_TIMER;

    protected int mScreenWidth;
    protected int mScreenHeight;
    protected AudioManager mAudioManager;
    protected Handler mHandler;
    protected ProgressTimerTask mProgressTimerTask;

    protected boolean mTouchingProgressBar;
    protected float mDownX;
    protected float mDownY;
    protected boolean mChangeVolume;
    protected boolean mChangePosition;
    protected int mDownPosition;
    protected int mGestureDownVolume;
    protected int mSeekTimePosition;

    protected boolean enableTiny;
    private int mOriginalOrientation = SCREEN_ORIENTATION_INVALID;

    private boolean needKeepCacheImg;
    private boolean needPauseVideo;

    public JCVideoPlayer(Context context) {
        super(context);
        init(context);
    }

    public JCVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void init(Context context) {
        View.inflate(context, getLayoutId(), this);
        startButton = (ImageView) findViewById(R.id.start);
        fullscreenButton = (ImageView) findViewById(R.id.fullscreen);
        progressBar = (SeekBar) findViewById(R.id.progress);
        currentTimeTextView = (TextView) findViewById(R.id.current);
        totalTimeTextView = (TextView) findViewById(R.id.total);
        bottomContainer = (ViewGroup) findViewById(R.id.layout_bottom);
        textureViewContainer = (ViewGroup) findViewById(R.id.surface_container);
        topContainer = (ViewGroup) findViewById(R.id.layout_top);
        textureCacheImg = (JCResizeImageView) findViewById(R.id.cache);

        startButton.setOnClickListener(this);
        fullscreenButton.setOnClickListener(this);
        progressBar.setOnSeekBarChangeListener(this);
        bottomContainer.setOnClickListener(this);
        textureViewContainer.setOnClickListener(this);

        textureViewContainer.setOnTouchListener(this);
        mScreenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        mScreenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        mAudioManager = (AudioManager) getContext().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        mHandler = new Handler();
    }

    public void setEnableTiny(boolean enableTiny) {
        this.enableTiny = enableTiny;
    }

    public boolean setUp(String url, int screen, Object... objects) {
        // 传入的视频url为空 或者 传入同样的url，那么这个时候就直接返回（ListView在全屏返回时又重新getView会导致播放器被release）
        if (TextUtils.isEmpty(url) || TextUtils.equals(url, this.url)) {
            return false;
        }
        //如果列表中,滑动过快,在还没来得及onScroll的时候自己已经被复用了
        if (enableTiny && this == JCVideoPlayerManager.getScrollListener()) {
            // 确定当前JCVideoPlayer的初始播放地址是当前播放的地址，并且当前没有tinyWindow。
            if (isCurrentPlayingUrl(this.url)) {
                // 一旦触发播放的JCVideoPlayer被复用，则消除ScrollListener引用
                JCVideoPlayerManager.clearScrollListener();
                if (canStartTinyWindow(this.currentState)) {
                    startWindowTiny();
                }
            }
        }
        this.url = url;
        this.objects = objects;
        this.currentScreen = screen;
        //因为JCVideoPlayer被复用的原因，需要check之前保存的Listener已经无效，若无效则更新
        if (isCurrentPlayingUrl(url)) {
            JCVideoPlayerManager.checkAndPutListener(this);
        }
        //如果初始化了一个正在tinyWindow的前身,就应该监听它的滑动,如果显示就在这个listener中播放
        if (enableTiny && isCurrentPlayingUrl(url)) {
            JCVideoPlayerManager.putScrollListener(this);
        }
        setUiWitStateAndScreen(CURRENT_STATE_NORMAL);
        return true;
    }

    @Override
    public int getScreenType() {
        return currentScreen;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public int getState() {
        return currentState;
    }

    public boolean setUp(String url, int screen, Map<String, String> mapHeadData, Object... objects) {
        if (setUp(url, screen, objects)) {
            this.mapHeadData.clear();
            this.mapHeadData.putAll(mapHeadData);
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.start) {
            Log.i(TAG, "onClick start [" + this.hashCode() + "] ");
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(getContext(), getResources().getString(R.string.no_url), Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentState == CURRENT_STATE_NORMAL || currentState == CURRENT_STATE_ERROR) {
                if (!url.startsWith("file") && !JCUtils.isWifiConnected(getContext()) && !WIFI_TIP_DIALOG_SHOWED) {
                    showWifiDialog();
                    return;
                }
                prepareVideo();
                onEvent(currentState != CURRENT_STATE_ERROR ? JCUserAction.ON_CLICK_START_ICON : JCUserAction.ON_CLICK_START_ERROR);
            } else if (currentState == CURRENT_STATE_PLAYING) {
                obtainCache();
                onEvent(JCUserAction.ON_CLICK_PAUSE);
                Log.d(TAG, "pauseCurVideo [" + this.hashCode() + "] ");
                JCMediaManager.instance().mediaPlayer.pause();
                setUiWitStateAndScreen(CURRENT_STATE_PAUSE);
                refreshCache();
            } else if (currentState == CURRENT_STATE_PAUSE) {
                onEvent(JCUserAction.ON_CLICK_RESUME);
                if (pauseBackupState == CURRENT_STATE_PLAYING ) {
                    if (JCMediaManager.instance().mediaPlayer.isPlayable()) {
                        JCMediaManager.instance().mediaPlayer.start();
                    }
                }
                setUiWitStateAndScreen(pauseBackupState);
            } else if (currentState == CURRENT_STATE_AUTO_COMPLETE) {
                onEvent(JCUserAction.ON_CLICK_START_AUTO_COMPLETE);
                prepareVideo();
            }
        } else if (i == R.id.fullscreen) {
            Log.i(TAG, "onClick fullscreen [" + this.hashCode() + "] ");
            if (currentState == CURRENT_STATE_AUTO_COMPLETE) return;
            if (currentScreen == SCREEN_WINDOW_FULLSCREEN) {
                //quit fullscreen
                backPress();
            } else {
                Log.d(TAG, "toFullscreenActivity [" + this.hashCode() + "] ");
                onEvent(JCUserAction.ON_ENTER_FULLSCREEN);
                startWindowFullscreen();
            }
        } else if (i == R.id.surface_container && currentState == CURRENT_STATE_ERROR) {
            Log.i(TAG, "onClick surfaceContainer State=Error [" + this.hashCode() + "] ");
            prepareVideo();
        }
    }

    public void prepareVideo() {
        Log.d(TAG, "prepareVideo [" + this.hashCode() + "] ");
        JCVideoPlayerManager.completeAll();
        JCVideoPlayerManager.putListener(this);
        addTextureView();

        mAudioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        JCUtils.scanForActivity(getContext()).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        JCVideoPlayerManager.putScrollListener(this);

        JCMediaManager.instance().prepare(url, mapHeadData, looping);
        setUiWitStateAndScreen(CURRENT_STATE_PREPARING);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int id = v.getId();
        if (id == R.id.surface_container) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Log.i(TAG, "onTouch surfaceContainer actionDown [" + this.hashCode() + "] ");
                    mTouchingProgressBar = true;

                    mDownX = x;
                    mDownY = y;
                    mChangeVolume = false;
                    mChangePosition = false;
                    /////////////////////
                    break;
                case MotionEvent.ACTION_MOVE:
                    Log.i(TAG, "onTouch surfaceContainer actionMove [" + this.hashCode() + "] ");
                    float deltaX = x - mDownX;
                    float deltaY = y - mDownY;
                    float absDeltaX = Math.abs(deltaX);
                    float absDeltaY = Math.abs(deltaY);
                    if (currentScreen == SCREEN_WINDOW_FULLSCREEN) {
                        if (!mChangePosition && !mChangeVolume) {
                            if (absDeltaX > THRESHOLD || absDeltaY > THRESHOLD) {
                                cancelProgressTimer();
                                if (absDeltaX >= THRESHOLD) {
                                    // 全屏模式下的CURRENT_STATE_ERROR状态下,不响应进度拖动事件.
                                    // 否则会因为mediaplayer的状态非法导致App Crash
                                    if (currentState != CURRENT_STATE_ERROR) {
                                        mChangePosition = true;
                                        mDownPosition = getCurrentPositionWhenPlaying();
                                    }
                                } else {
                                    mChangeVolume = true;
                                    mGestureDownVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                                }
                            }
                        }
                    }
                    if (mChangePosition) {
                        int totalTimeDuration = getDuration();
                        mSeekTimePosition = (int) (mDownPosition + deltaX * totalTimeDuration / mScreenWidth);
                        if (mSeekTimePosition > totalTimeDuration)
                            mSeekTimePosition = totalTimeDuration;
                        String seekTime = JCUtils.stringForTime(mSeekTimePosition);
                        String totalTime = JCUtils.stringForTime(totalTimeDuration);

                        showProgressDialog(deltaX, seekTime, mSeekTimePosition, totalTime, totalTimeDuration);
                    }
                    if (mChangeVolume) {
                        deltaY = -deltaY;
                        int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int deltaV = (int) (max * deltaY * 3 / mScreenHeight);
                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mGestureDownVolume + deltaV, 0);
                        int volumePercent = (int) (mGestureDownVolume * 100 / max + deltaY * 3 * 100 / mScreenHeight);

                        showVolumeDialog(-deltaY, volumePercent);
                    }

                    break;
                case MotionEvent.ACTION_UP:
                    Log.i(TAG, "onTouch surfaceContainer actionUp [" + this.hashCode() + "] ");
                    mTouchingProgressBar = false;
                    dismissProgressDialog();
                    dismissVolumeDialog();
                    if (mChangePosition) {
                        onEvent(JCUserAction.ON_TOUCH_SCREEN_SEEK_POSITION);
                        JCMediaManager.instance().mediaPlayer.seekTo(mSeekTimePosition);
                        int duration = getDuration();
                        int progress = mSeekTimePosition * 100 / (duration == 0 ? 1 : duration);
                        progressBar.setProgress(progress);
                    }
                    if (mChangeVolume) {
                        onEvent(JCUserAction.ON_TOUCH_SCREEN_SEEK_VOLUME);
                    }
                    startProgressTimer();
                    break;
            }
        }
        return false;
    }

    public void addTextureView() {
        Log.d(TAG, "addTextureView [" + this.hashCode() + "] ");
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }
        textureView = new JCResizeTextureView(getContext());
        JCMediaManager.instance().setTextureView(textureView);
        textureView.setVideoSize(JCMediaManager.instance().getVideoSize());
        textureView.setRotation(JCMediaManager.instance().videoRotation);
        textureView.setSurfaceTextureListener(this);

        FrameLayout.LayoutParams layoutParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER);
        textureViewContainer.addView(textureView, layoutParams);

        textureCacheImg.setVideoSize(JCMediaManager.instance().getVideoSize());
        textureCacheImg.setRotation(JCMediaManager.instance().videoRotation);

    }

    public void setUiWitStateAndScreen(int state) {
        currentState = state;
        switch (currentState) {
            case CURRENT_STATE_NORMAL:
                if (isCurrentMediaListener()) {
                    if (enableTiny && !isCurrentPlayingUrl(this.url)) {
                        Log.e(TAG, "setUiWitStateAndScreen url is not playing, but err to close");
                    }
                    cancelProgressTimer();
                    JCMediaManager.instance().releaseMediaPlayer();
                }
                break;
            case CURRENT_STATE_PREPARING:
                resetProgressAndTime();
                break;
            case CURRENT_STATE_PLAYING:
            case CURRENT_STATE_PAUSE:
            case CURRENT_STATE_PLAYING_BUFFERING_START:
                startProgressTimer();
                break;
            case CURRENT_STATE_ERROR:
                cancelProgressTimer();
                if (isCurrentMediaListener()) {
                    JCMediaManager.instance().releaseMediaPlayer();
                }
                break;
            case CURRENT_STATE_AUTO_COMPLETE:
                cancelProgressTimer();
                progressBar.setProgress(100);
                currentTimeTextView.setText(totalTimeTextView.getText());
                break;
        }
    }

    public void startProgressTimer() {
        cancelProgressTimer();
        UPDATE_PROGRESS_TIMER = new Timer();
        mProgressTimerTask = new ProgressTimerTask(this);
        UPDATE_PROGRESS_TIMER.schedule(mProgressTimerTask, 0, 300);
    }

    public void cancelProgressTimer() {
        if (UPDATE_PROGRESS_TIMER != null) {
            UPDATE_PROGRESS_TIMER.cancel();
            UPDATE_PROGRESS_TIMER.purge();
        }
        if (mProgressTimerTask != null) {
            mProgressTimerTask.cancel();
        }
    }

    @Override
    public void onPrepared() {
        Log.i(TAG, "onPrepared " + " [" + this.hashCode() + "] ");

        if (currentState != CURRENT_STATE_PREPARING) return;
        JCMediaManager.instance().mediaPlayer.start();
        if (seekToInAdvance != -1) {
            JCMediaManager.instance().mediaPlayer.seekTo(seekToInAdvance);
            seekToInAdvance = -1;
        }
        startProgressTimer();
        setUiWitStateAndScreen(CURRENT_STATE_PLAYING);
    }

    public void clearFullscreenLayout() {
        ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(getContext()))
                .findViewById(Window.ID_ANDROID_CONTENT);
        View oldF = vp.findViewById(FULLSCREEN_ID);
        View oldT = vp.findViewById(TINY_ID);
        if (oldF != null) {
            vp.removeView(oldF);
        }
        if (oldT != null) {
            vp.removeView(oldT);
        }
        showSupportActionBar(getContext());
    }

    @Override
    public void onAutoCompletion() {
        Log.i(TAG, "onAutoCompletion " + " [" + this.hashCode() + "] ");
        onEvent(JCUserAction.ON_AUTO_COMPLETE);
        dismissVolumeDialog();
        dismissProgressDialog();
        setUiWitStateAndScreen(CURRENT_STATE_AUTO_COMPLETE);
        if (!isOneScreenTypeLive()) {
            JCVideoPlayerManager.popListener();//自己进入autoComplete状态，其他的进入complete状态
            JCVideoPlayerManager.completeAll();
        }
    }

    protected boolean isOneScreenTypeLive() {
        return JCVideoPlayerManager.listenerSize() == 1;
    }

    @Override
    public void onCompletion() {
        Log.i(TAG, "onCompletion " + " [" + this.hashCode() + "] ");
        setUiWitStateAndScreen(CURRENT_STATE_NORMAL);
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }

        JCMediaManager.instance().currentVideoWidth = 0;
        JCMediaManager.instance().currentVideoHeight = 0;

        // 清理缓存变量
        JCMediaManager.instance().bufferPercent = 0;
        JCMediaManager.instance().videoRotation = 0;

        mAudioManager.abandonAudioFocus(onAudioFocusChangeListener);
        JCUtils.scanForActivity(getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        clearFullscreenLayout();
        restoreScreenOrientation();

        // 清理cover image,回收bitmap内存
        clearCacheImage();
    }

    @SuppressWarnings("WrongConstant")
    protected void restoreScreenOrientation() {
        if (mOriginalOrientation != SCREEN_ORIENTATION_INVALID) {
            JCUtils.getAppCompActivity(getContext()).setRequestedOrientation(mOriginalOrientation);
        }
    }

    @Override
    public boolean backToOtherListener() {//这里这个名字这么写并不对,这是在回退的时候gotoother,如果直接gotoother就不叫这个名字

        obtainCache();

        Log.i(TAG, "backToOtherListener " + " [" + this.hashCode() + "] ");
        restoreScreenOrientation();
        if (currentScreen == JCVideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN
                || currentScreen == JCVideoPlayerStandard.SCREEN_WINDOW_TINY) {
            onEvent(currentScreen == JCVideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN ?
                    JCUserAction.ON_QUIT_FULLSCREEN :
                    JCUserAction.ON_QUIT_TINYSCREEN);
            if (isOneScreenTypeLive()) {//directly fullscreen
                JCMediaPlayerListener pop = JCVideoPlayerManager.popListener();
                if (pop != null) {
                    pop.onCompletion();
                }
                JCMediaManager.instance().releaseMediaPlayer();
                showSupportActionBar(getContext());
                return true;
            }
            ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(getContext()))
                    .findViewById(Window.ID_ANDROID_CONTENT);
            vp.removeView(this);
            JCMediaManager.instance().lastState = currentState;//save state
            JCVideoPlayerManager.popListener();
            JCMediaPlayerListener first = JCVideoPlayerManager.getFirst();
            if (first != null) {
                first.goBackThisListener();
                CLICK_QUIT_FULLSCREEN_TIME = System.currentTimeMillis();

                refreshCache();
            } else {
                JCVideoPlayerManager.completeAll();
            }
            return true;
        }

        return false;
    }

    public static long lastAutoFullscreenTime = 0;

    @Override
    public void autoFullscreen(float x) {
        if (isCurrentMediaListener()
                && currentState == CURRENT_STATE_PLAYING
                && currentScreen != SCREEN_WINDOW_FULLSCREEN
                && currentScreen != SCREEN_WINDOW_TINY) {
            if (x > 0) {
                JCUtils.getAppCompActivity(getContext()).setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                JCUtils.getAppCompActivity(getContext()).setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
            }
            startWindowFullscreen();
        }
    }

    @Override
    public void autoQuitFullscreen() {
        if ((System.currentTimeMillis() - lastAutoFullscreenTime) > 2000
                && isCurrentMediaListener()
                && currentState == CURRENT_STATE_PLAYING
                && currentScreen == SCREEN_WINDOW_FULLSCREEN) {
            lastAutoFullscreenTime = System.currentTimeMillis();
            backPress();
        }
    }

    // 此方法在Activity或Fragment的onPause生命周期中调用
    @Override
    public void onPause() {
        if (currentScreen != SCREEN_WINDOW_TINY) {
            if (currentState == CURRENT_STATE_PLAYING
                    || currentState == CURRENT_STATE_PAUSE
                    || currentState == CURRENT_STATE_PREPARING
                    || currentState == CURRENT_STATE_PLAYING_BUFFERING_START) {
                pauseBackupState = currentState;
                obtainCache();
                Log.d(TAG, "onPause [" + this.hashCode() + "] ");
                if (currentState == CURRENT_STATE_PLAYING) {
                    JCMediaManager.instance().mediaPlayer.pause();
                }
                if (currentState == CURRENT_STATE_PAUSE) {
                    pauseBackupState = CURRENT_STATE_PLAYING;
                } else {
                    setUiWitStateAndScreen(CURRENT_STATE_PAUSE);
                }
                refreshCache();
                needKeepCacheImg = true;
                needPauseVideo = true;
            }
        } else {
            releaseAllVideos();
        }
    }

    @Override
    public void goBackThisListener() {
        Log.i(TAG, "goBackThisListener " + " [" + this.hashCode() + "] ");

        currentState = JCMediaManager.instance().lastState;
        setUiWitStateAndScreen(currentState);
        addTextureView();

        showSupportActionBar(getContext());
    }

    @Override
    public void onBufferingUpdate(int percent) {
        if (currentState != CURRENT_STATE_NORMAL && currentState != CURRENT_STATE_PREPARING) {
            Log.v(TAG, "onBufferingUpdate " + percent + " [" + this.hashCode() + "] ");
            JCMediaManager.instance().bufferPercent = percent;
            setTextAndProgress(percent);
        }
    }

    @Override
    public void onSeekComplete() {
    }

    @Override
    public void onError(int what, int extra) {
        Log.e(TAG, "onError " + what + " - " + extra + " [" + this.hashCode() + "] ");
        if (what != 38 && what != -38) {
            setUiWitStateAndScreen(CURRENT_STATE_ERROR);
        }
    }

    @Override
    public void onInfo(int what, int extra) {
        Log.d(TAG, "onInfo what - " + what + " extra - " + extra);
        if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
            JCMediaManager.instance().backupBufferState = currentState;
            setUiWitStateAndScreen(CURRENT_STATE_PLAYING_BUFFERING_START);
            Log.d(TAG, "MEDIA_INFO_BUFFERING_START");
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
            if (JCMediaManager.instance().backupBufferState != -1) {
                if (currentState == CURRENT_STATE_PLAYING_BUFFERING_START) {
                    setUiWitStateAndScreen(JCMediaManager.instance().backupBufferState);
                } else if (needPauseVideo && currentState == CURRENT_STATE_PAUSE) {
                    if (JCMediaManager.instance().mediaPlayer.isPlaying()) {
                        JCMediaManager.instance().mediaPlayer.pause();
                    }
                }
                JCMediaManager.instance().backupBufferState = -1;
            }
            needPauseVideo = false;
            Log.d(TAG, "MEDIA_INFO_BUFFERING_END");
        } else if (what == IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED) {
            JCMediaManager.instance().videoRotation = extra;
            textureView.setRotation(extra);
            textureCacheImg.setRotation(JCMediaManager.instance().videoRotation);
            Log.d(TAG, "MEDIA_INFO_VIDEO_ROTATION_CHANGED");
        }
    }

    @Override
    public void onVideoSizeChanged() {
        Log.i(TAG, "onVideoSizeChanged " + " [" + this.hashCode() + "] ");
        textureView.setVideoSize(JCMediaManager.instance().getVideoSize());
        textureCacheImg.setVideoSize(JCMediaManager.instance().getVideoSize());
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "onSurfaceTextureAvailable [" + this.hashCode() + "] ");
        this.surface = new Surface(surface);
        JCMediaManager.instance().setDisplay(this.surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "onSurfaceTextureSizeChanged [" + this.hashCode() + "] ");
        // 如果SurfaceTexture还没有更新Image，则记录SizeChanged事件，否则忽略
        textureSizeChanged = true;
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.i(TAG, "onSurfaceTextureDestroyed [" + this.hashCode() + "]");
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        Log.i(TAG, "onSurfaceTextureUpdated [" + this.hashCode() + "] textureSizeChanged=" + textureSizeChanged);
        // 如果textureSizeChanged=true，则说明此次Updated事件不是Image更新引起的   应该是TextureSizeChanged引起的 所以不需要更新 textureCacheImg
        if (textureCacheImg.getVisibility() == VISIBLE) {
            if (!textureSizeChanged) {
                if (!needKeepCacheImg) {
                    textureCacheImg.setVisibility(INVISIBLE);
                    textureView.setHasUpdated();
                } else {
                    needKeepCacheImg = false;
                }
            } else {
                textureSizeChanged = false;
            }
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        Log.i(TAG, "bottomProgress onStartTrackingTouch [" + this.hashCode() + "] ");
        cancelProgressTimer();
        ViewParent vpdown = getParent();
        while (vpdown != null) {
            vpdown.requestDisallowInterceptTouchEvent(true);
            vpdown = vpdown.getParent();
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        Log.i(TAG, "bottomProgress onStopTrackingTouch [" + this.hashCode() + "] ");
        onEvent(JCUserAction.ON_SEEK_POSITION);
        startProgressTimer();
        ViewParent vpup = getParent();
        while (vpup != null) {
            vpup.requestDisallowInterceptTouchEvent(false);
            vpup = vpup.getParent();
        }
        if (currentState != CURRENT_STATE_PLAYING &&
                currentState != CURRENT_STATE_PAUSE) return;
        int time = seekBar.getProgress() * getDuration() / 100;
        JCMediaManager.instance().mediaPlayer.seekTo(time);
        Log.i(TAG, "seekTo " + time + " [" + this.hashCode() + "] ");
    }

    public static boolean backPress() {
        Log.i(TAG, "backPress");
        JCMediaPlayerListener first = JCVideoPlayerManager.getFirst();
        if (first != null) {
            if (first.getScreenType() == SCREEN_WINDOW_TINY) {
                if (first instanceof JCVideoPlayer) {
                    ((JCVideoPlayer) first).release();
                    return true;
                }
            } else {
                return first.backToOtherListener();
            }
        }
        return false;
    }

    public void startWindowFullscreen() {

        obtainCache();

        Log.i(TAG, "startWindowFullscreen " + " [" + this.hashCode() + "] ");
        CLICK_QUIT_FULLSCREEN_TIME = System.currentTimeMillis();
        hideSupportActionBar(getContext());
        mOriginalOrientation = JCUtils.getAppCompActivity(getContext()).getRequestedOrientation();
        switchToFullOrientation(getContext());

        ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(getContext()))
                .findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(FULLSCREEN_ID);
        if (old != null) {
            vp.removeView(old);
        }
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }
        try {
            @SuppressWarnings("unchecked")
            Constructor<JCVideoPlayer> constructor = (Constructor<JCVideoPlayer>) JCVideoPlayer.this.getClass().getConstructor(Context.class);
            JCVideoPlayer jcVideoPlayer = constructor.newInstance(getContext());
            jcVideoPlayer.setId(FULLSCREEN_ID);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            vp.addView(jcVideoPlayer, lp);
            jcVideoPlayer.mOriginalOrientation = this.mOriginalOrientation;
            jcVideoPlayer.setUp(url, JCVideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN, objects);
            jcVideoPlayer.setUiWitStateAndScreen(currentState);
            jcVideoPlayer.addTextureView();

            JCVideoPlayerManager.putListener(jcVideoPlayer);
        } catch (Exception e) {
            e.printStackTrace();
        }

        refreshCache();

    }

    public void startWindowTiny() {

        obtainCache();

        Log.i(TAG, "startWindowTiny " + " [" + this.hashCode() + "] ");
        onEvent(JCUserAction.ON_ENTER_TINYSCREEN);

        ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(getContext()))
                .findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(TINY_ID);
        if (old != null) {
            vp.removeView(old);
        }
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }
        try {
            @SuppressWarnings("unchecked")
            Constructor<JCVideoPlayer> constructor = (Constructor<JCVideoPlayer>) JCVideoPlayer.this.getClass().getConstructor(Context.class);
            JCVideoPlayer mJcVideoPlayer = constructor.newInstance(getContext());
            mJcVideoPlayer.setId(TINY_ID);
            int width = mScreenWidth * 2 / 3;
            int height = width * 9 / 16;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
            lp.gravity = Gravity.END | Gravity.BOTTOM;
            vp.addView(mJcVideoPlayer, lp);
            mJcVideoPlayer.setUp(url, JCVideoPlayer.SCREEN_WINDOW_TINY, objects);
            mJcVideoPlayer.setUiWitStateAndScreen(currentState);
            mJcVideoPlayer.addTextureView();

            JCVideoPlayerManager.putListener(mJcVideoPlayer);
        } catch (Exception e) {
            e.printStackTrace();
        }

        refreshCache();

    }

    private void quitWindowTiny() {
        Log.d("XXXX", "quitWindowTiny " + " [" + this.hashCode() + "] ");
        JCMediaPlayerListener first = JCVideoPlayerManager.getFirst();
        if (first != null) {
            first.backToOtherListener();
        }
    }

    private static class ProgressTimerTask extends TimerTask {

        WeakReference<JCVideoPlayer> mJCVideoPlayerWeakReference;

        ProgressTimerTask(JCVideoPlayer jcVideoPlayer) {
            mJCVideoPlayerWeakReference = new WeakReference<>(jcVideoPlayer);
        }

        @Override
        public void run() {
            final JCVideoPlayer jcVideoPlayer = mJCVideoPlayerWeakReference.get();
            if (jcVideoPlayer != null) {
                if (jcVideoPlayer.currentState == CURRENT_STATE_PLAYING
                        || jcVideoPlayer.currentState == CURRENT_STATE_PAUSE
                        || jcVideoPlayer.currentState == CURRENT_STATE_PLAYING_BUFFERING_START) {
                    int position = jcVideoPlayer.getCurrentPositionWhenPlaying();
                    int duration = jcVideoPlayer.getDuration();
                    Log.v(TAG, "onProgressUpdate " + position + "/" + duration + " [" + this.hashCode() + "] ");
                    Handler handler = jcVideoPlayer.mHandler;
                    if (handler != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                jcVideoPlayer.setTextAndProgress(JCMediaManager.instance().bufferPercent);
                            }
                        });
                    }
                }
            }
        }
    }

    public int getCurrentPositionWhenPlaying() {
        int position = 0;
        if (currentState == CURRENT_STATE_PLAYING || currentState == CURRENT_STATE_PAUSE || currentState == CURRENT_STATE_PLAYING_BUFFERING_START) {
            try {
                position = (int) JCMediaManager.instance().mediaPlayer.getCurrentPosition();
            } catch (IllegalStateException e) {
                e.printStackTrace();
                return position;
            }
        }
        return position;
    }

    public int getDuration() {
        int duration = 0;
        try {
            duration = (int) JCMediaManager.instance().mediaPlayer.getDuration();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return duration;
        }
        return duration;
    }

    public void setTextAndProgress(int secProgress) {
        int position = getCurrentPositionWhenPlaying();
        int duration = getDuration();
        int progress = position * 100 / (duration == 0 ? 1 : duration);
        setProgressAndTime(progress, secProgress, position, duration);
    }

    public void setProgressAndTime(int progress, int secProgress, int currentTime, int totalTime) {
        if (!mTouchingProgressBar) {
            if (progress != 0) progressBar.setProgress(progress);
        }
        if (secProgress > 95) secProgress = 100;
        if (secProgress != 0) progressBar.setSecondaryProgress(secProgress);
        if (currentTime != 0) currentTimeTextView.setText(JCUtils.stringForTime(currentTime));
        totalTimeTextView.setText(JCUtils.stringForTime(totalTime));
    }

    public void resetProgressAndTime() {
        progressBar.setProgress(0);
        progressBar.setSecondaryProgress(0);
        currentTimeTextView.setText(JCUtils.stringForTime(0));
        totalTimeTextView.setText(JCUtils.stringForTime(0));
    }

    public static AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    releaseAllVideos();
                    Log.d(TAG, "AUDIOFOCUS_LOSS [" + this.hashCode() + "]");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (JCMediaManager.instance().mediaPlayer.isPlaying()) {
                        JCMediaManager.instance().mediaPlayer.pause();
                    }
                    Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT [" + this.hashCode() + "]");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    break;
            }
        }
    };

    public void release() {
        if (isCurrentPlayingUrl(url) &&
                (System.currentTimeMillis() - CLICK_QUIT_FULLSCREEN_TIME) > FULL_SCREEN_NORMAL_DELAY) {
            //如果正在全屏播放就不能手动调用release
            JCMediaPlayerListener first = JCVideoPlayerManager.getFirst();
            if (first != null && first.getScreenType() != SCREEN_WINDOW_FULLSCREEN) {
                Log.d(TAG, "release [" + this.hashCode() + "]");
                releaseAllVideos();
            }
        }
    }

    //isCurrentMediaListener and isCurrentPlayUrl should be two logic methods,isCurrentMediaListener is for different jcvd with same
    //url when fullscreen or tiny screen. isCurrentPlayUrl is to find where is myself when back from tiny screen.
    //Sometimes they are overlap.
    protected final boolean isCurrentMediaListener() {
        JCMediaPlayerListener first = JCVideoPlayerManager.getFirst();
        return first != null && first == this;
    }


    protected final boolean isCurrentPlayingUrl(String url) {
        return TextUtils.equals(url, JCMediaManager.instance().mediaPlayer.getDataSource());
    }

    public final boolean isPlaying() {
        return JCMediaManager.instance().mediaPlayer.isPlaying();
    }

    public static void releaseAllVideos() {
        Log.d(TAG, "releaseAllVideos");
        JCVideoPlayerManager.completeAll();
        JCMediaManager.instance().releaseMediaPlayer();
    }

    public static void pauseCurVideo() {
        Log.d(TAG, "pauseCurVideo");
        JCVideoPlayerManager.pauseVideo();
    }

    public static void setJcUserAction(JCUserAction jcUserEvent) {
        JC_USER_EVENT = new WeakReference<>(jcUserEvent);
    }

    public void onEvent(int type) {
        if (JC_USER_EVENT != null && isCurrentMediaListener()) {
            JCUserAction jcUserAction = JC_USER_EVENT.get();
            if (jcUserAction != null) {
                jcUserAction.onEvent(type, url, currentScreen, objects);
            }
        }
    }

    @Override
    public void onScrollChange() {//这里需要自己判断自己是 进入小窗,退出小窗,暂停还是播放
        if (isCurrentPlayingUrl(url)) {
            JCMediaPlayerListener first = JCVideoPlayerManager.getFirst();
            if (first == null) return;
            if (first.getScreenType() == SCREEN_WINDOW_TINY) {
                //如果正在播放的是小窗,择机退出小窗
                if (isShown()) {//已经显示,就退出小窗
                    quitWindowTiny();
                }
            } else {
                //如果正在播放的不是小窗,择机进入小窗
                if (!isShown()) {//已经隐藏
                    if (!canStartTinyWindow(currentState)) {
                        releaseAllVideos();
                    } else {
                        startWindowTiny();
                    }
                }
            }
        }
    }

    private boolean canStartTinyWindow(int currentState) {
        boolean noTinyScreen = !JCVideoPlayerManager.hasSameScreenTypeListener(SCREEN_WINDOW_TINY);
        boolean stateOk = currentState == CURRENT_STATE_PREPARING
                        || currentState == CURRENT_STATE_PLAYING
                        || currentState == CURRENT_STATE_PLAYING_BUFFERING_START;
        return noTinyScreen && stateOk;
    }

    public static void onScroll() {//这里就应该保证,listener的正确的完整的赋值,调用非播放的控件
        JCMediaPlayerListener jcMediaPlayerListener = JCVideoPlayerManager.getScrollListener();
        if (jcMediaPlayerListener != null &&
                jcMediaPlayerListener.getState() != CURRENT_STATE_ERROR &&
                jcMediaPlayerListener.getState() != CURRENT_STATE_AUTO_COMPLETE) {
            jcMediaPlayerListener.onScrollChange();
        }
    }

    public static void startDirectFullscreen(Context context, int orientation, Class _class, String url, Object... objects) {
        hideSupportActionBar(context);
        int originalOrientation = JCUtils.getAppCompActivity(context).getRequestedOrientation();
        JCUtils.getAppCompActivity(context).setRequestedOrientation(orientation);
        ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(context))//.getWindow().getDecorView();
                .findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(JCVideoPlayer.FULLSCREEN_ID);
        if (old != null) {
            vp.removeView(old);
        }
        try {
            @SuppressWarnings("unchecked")
            Constructor<JCVideoPlayer> constructor = _class.getConstructor(Context.class);
            JCVideoPlayer jcVideoPlayer = constructor.newInstance(context);
            jcVideoPlayer.setId(JCVideoPlayer.FULLSCREEN_ID);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

            vp.addView(jcVideoPlayer, lp);

            jcVideoPlayer.mOriginalOrientation = originalOrientation;
            jcVideoPlayer.setUp(url, JCVideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN, objects);
            jcVideoPlayer.addTextureView();

            jcVideoPlayer.startButton.performClick();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void switchToFullOrientation(Context context) {
        JCUtils.getAppCompActivity(context).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }

    @SuppressWarnings("RestrictedApi")
    public static void hideSupportActionBar(Context context) {
        if (ACTION_BAR_EXIST) {
            ActionBar ab = JCUtils.getAppCompActivity(context).getSupportActionBar();
            if (ab != null) {
                ab.setShowHideAnimationEnabled(false);
                ab.hide();
            }
        }
        if (TOOL_BAR_EXIST) {
            JCUtils.getAppCompActivity(context).getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    @SuppressWarnings("RestrictedApi")
    public static void showSupportActionBar(Context context) {
        if (ACTION_BAR_EXIST) {
            ActionBar ab = JCUtils.getAppCompActivity(context).getSupportActionBar();
            if (ab != null) {
                ab.setShowHideAnimationEnabled(false);
                ab.show();
            }
        }
        if (TOOL_BAR_EXIST) {
            JCUtils.getAppCompActivity(context).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    public static class JCAutoFullscreenListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {//可以得到传感器实时测量出来的变化值
            final float x = event.values[SensorManager.DATA_X];
            float y = event.values[SensorManager.DATA_Y];
            float z = event.values[SensorManager.DATA_Z];
            //过滤掉用力过猛会有一个反向的大数值
            if (((x > -15 && x < -10) || (x < 15 && x > 10)) && Math.abs(y) < 1.5) {
                if ((System.currentTimeMillis() - lastAutoFullscreenTime) > 2000) {
                    JCMediaPlayerListener first = JCVideoPlayerManager.getFirst();
                    if (first != null) {
                        first.autoFullscreen(x);
                    }
                    lastAutoFullscreenTime = System.currentTimeMillis();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }


    private void obtainCache() {
        Point videoSize = JCMediaManager.instance().getVideoSize();
        if (videoSize != null && textureView.hasUpdated) {
            Bitmap bitmap = textureView.getBitmap();
            if (bitmap != null) {
                textureSwitchCacheBitmap = bitmap;
            }
        }
    }

    public void refreshCache() {
        if (textureSwitchCacheBitmap != null) {
            JCVideoPlayer jcVideoPlayer = ((JCVideoPlayer) JCVideoPlayerManager.getFirst());
            if (jcVideoPlayer != null) {
                jcVideoPlayer.textureCacheImg.setImageBitmap(textureSwitchCacheBitmap);
                jcVideoPlayer.textureCacheImg.setVisibility(VISIBLE);
            }
        }
    }

    public void clearCacheImage() {
        textureSwitchCacheBitmap = null;
        textureCacheImg.setImageBitmap(null);
    }

    public void showWifiDialog() {
    }

    public void showProgressDialog(float deltaX,
                                   String seekTime, int seekTimePosition,
                                   String totalTime, int totalTimeDuration) {
    }

    public void dismissProgressDialog() {

    }

    public void showVolumeDialog(float deltaY, int volumePercent) {

    }

    public void dismissVolumeDialog() {

    }


    public abstract int getLayoutId();


}
