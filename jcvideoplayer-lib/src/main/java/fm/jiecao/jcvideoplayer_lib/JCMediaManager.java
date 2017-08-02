package fm.jiecao.jcvideoplayer_lib;

import android.graphics.Point;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.lang.ref.WeakReference;
import java.util.Map;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;


/**
 * <p>统一管理MediaPlayer的地方,只有一个mediaPlayer实例，那么不会有多个视频同时播放，也节省资源。</p>
 * <p>Unified management MediaPlayer place, there is only one MediaPlayer instance, then there will be no more video broadcast at the same time, also save resources.</p>
 * Created by Nathen
 * On 2015/11/30 15:39
 */
class JCMediaManager implements IMediaPlayer.OnPreparedListener, IMediaPlayer.OnCompletionListener,
        IMediaPlayer.OnBufferingUpdateListener, IMediaPlayer.OnSeekCompleteListener, IMediaPlayer.OnErrorListener,
        IMediaPlayer.OnVideoSizeChangedListener, IMediaPlayer.OnInfoListener {
    private static final String TAG = "JieCaoVideoPlayer";

    private static final int HANDLER_PREPARE = 0;
    private static final int HANDLER_DISPLAY = 1;
    private static final int HANDLER_RELEASE = 2;

    private static volatile JCMediaManager sJCMediaManager;
    private WeakReference<TextureView> mTextureViewWeakReference;
    IjkMediaPlayer mediaPlayer;

    int currentVideoWidth = 0;
    int currentVideoHeight = 0;
    int lastState;
    int bufferPercent;
    int backupBufferState = -1;
    int videoRotation;

    private MediaHandler mMediaHandler;
    private Handler mainThreadHandler;

    static JCMediaManager instance() {
        if (sJCMediaManager == null) {
            synchronized (JCMediaManager.class) {
                if (sJCMediaManager == null) {
                    sJCMediaManager = new JCMediaManager();
                }
            }
        }
        return sJCMediaManager;
    }

    private JCMediaManager() {
        mediaPlayer = new IjkMediaPlayer();
        HandlerThread mediaHandlerThread = new HandlerThread(TAG);
        mediaHandlerThread.start();
        mMediaHandler = new MediaHandler((mediaHandlerThread.getLooper()));
        mainThreadHandler = new Handler();
    }

    public void setTextureView(TextureView view) {
        mTextureViewWeakReference = new WeakReference<>(view);
    }

    Point getVideoSize() {
        if (currentVideoWidth != 0 && currentVideoHeight != 0) {
            return new Point(currentVideoWidth, currentVideoHeight);
        } else {
            return null;
        }
    }

    private class MediaHandler extends Handler {

        MediaHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_PREPARE:
                    try {
                        currentVideoWidth = 0;
                        currentVideoHeight = 0;
                        mediaPlayer.release();
                        mediaPlayer = new IjkMediaPlayer();
                        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mediaPlayer.setDataSource(((FuckBean) msg.obj).url, ((FuckBean) msg.obj).mapHeadData);
                        mediaPlayer.setLooping(((FuckBean) msg.obj).looping);
                        mediaPlayer.setOnPreparedListener(JCMediaManager.this);
                        mediaPlayer.setOnCompletionListener(JCMediaManager.this);
                        mediaPlayer.setOnBufferingUpdateListener(JCMediaManager.this);
                        mediaPlayer.setScreenOnWhilePlaying(true);
                        mediaPlayer.setOnSeekCompleteListener(JCMediaManager.this);
                        mediaPlayer.setOnErrorListener(JCMediaManager.this);
                        mediaPlayer.setOnInfoListener(JCMediaManager.this);
                        mediaPlayer.setOnVideoSizeChangedListener(JCMediaManager.this);
                        mediaPlayer.prepareAsync();
                        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case HANDLER_DISPLAY:
                    if (msg.obj == null) {
                        instance().mediaPlayer.setSurface(null);
                    } else {
                        Surface holder = (Surface) msg.obj;
                        if (holder.isValid()) {
                            Log.i(TAG, "set surface");
                            instance().mediaPlayer.setSurface(holder);
                            mainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (mTextureViewWeakReference != null) {
                                        TextureView textureView = mTextureViewWeakReference.get();
                                        if (textureView != null) {
                                            textureView.requestLayout();
                                        }
                                    }
                                }
                            });
                        }
                    }
                    break;
                case HANDLER_RELEASE:
                    mediaPlayer.reset();
                    mediaPlayer.release();
                    break;
            }
        }
    }


    void prepare(final String url, final Map<String, String> mapHeadData, boolean loop) {
        if (TextUtils.isEmpty(url)) return;
        Message msg = new Message();
        msg.what = HANDLER_PREPARE;
        msg.obj = new FuckBean(url, mapHeadData, loop);
        mMediaHandler.sendMessage(msg);
    }

    void releaseMediaPlayer() {
        Message msg = new Message();
        msg.what = HANDLER_RELEASE;
        mMediaHandler.sendMessage(msg);
    }

    void setDisplay(Surface holder) {
        Message msg = new Message();
        msg.what = HANDLER_DISPLAY;
        msg.obj = holder;
        mMediaHandler.sendMessage(msg);
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                JCMediaPlayerListener first = JCVideoPlayerManager.getFirst();
                if (first != null) {
                    first.onPrepared();
                }
            }
        });
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                JCMediaPlayerListener first = JCVideoPlayerManager.getFirst();
                if (first != null) {
                    first.onAutoCompletion();
                }
            }
        });
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer mp, final int percent) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                JCMediaPlayerListener first = JCVideoPlayerManager.getFirst();
                if (first != null) {
                    first.onBufferingUpdate(percent);
                }
            }
        });
    }

    @Override
    public void onSeekComplete(IMediaPlayer mp) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                JCMediaPlayerListener first = JCVideoPlayerManager.getFirst();
                if (first != null) {
                    first.onSeekComplete();
                }
            }
        });
    }

    @Override
    public boolean onError(IMediaPlayer mp, final int what, final int extra) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                JCMediaPlayerListener first = JCVideoPlayerManager.getFirst();
                if (first != null) {
                    first.onError(what, extra);
                }
            }
        });
        return true;
    }

    @Override
    public boolean onInfo(IMediaPlayer mp, final int what, final int extra) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                JCMediaPlayerListener first = JCVideoPlayerManager.getFirst();
                if (first != null) {
                    first.onInfo(what, extra);
                }
            }
        });
        return false;
    }

    @Override
    public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sar_num, int sar_den) {
        currentVideoWidth = mp.getVideoWidth();
        currentVideoHeight = mp.getVideoHeight();
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                JCMediaPlayerListener first = JCVideoPlayerManager.getFirst();
                if (first != null) {
                    first.onVideoSizeChanged();
                }
            }
        });
    }


    private class FuckBean {
        String url;
        Map<String, String> mapHeadData;
        boolean looping;

        FuckBean(String url, Map<String, String> mapHeadData, boolean loop) {
            this.url = url;
            this.mapHeadData = mapHeadData;
            this.looping = loop;
        }
    }
}
