package fm.jiecao.jcvideoplayer_lib;

import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.LinkedList;

/**
 * Put JCVideoPlayer into layout
 * From a JCVideoPlayer to another JCVideoPlayer
 * Created by Nathen on 16/7/26.
 */
public class JCVideoPlayerManager {
    // 用来存放可以滚动出现小窗模式
    private static WeakReference<JCMediaPlayerListener> CURRENT_SCROLL_LISTENER;
    // 用来存放有播放操作的JCVideoPlayer， 存在正常模式，列表模式，全屏模式，小窗模式
    private static LinkedList<WeakReference<JCMediaPlayerListener>> LISTENER_LIST = new LinkedList<>();

    public static void putScrollListener(@NonNull JCMediaPlayerListener listener) {
        if (listener.getScreenType() == JCVideoPlayer.SCREEN_WINDOW_TINY ||
                listener.getScreenType() == JCVideoPlayer.SCREEN_WINDOW_FULLSCREEN) return;
        CURRENT_SCROLL_LISTENER = new WeakReference<>(listener);//每次setUp的时候都应该add
    }

    public static JCMediaPlayerListener getScrollListener() {
        if (CURRENT_SCROLL_LISTENER != null) {
            return CURRENT_SCROLL_LISTENER.get();
        }
        return null;
    }

    public static void clearScrollListener() {
        CURRENT_SCROLL_LISTENER = null;
    }

    public static void putListener(@NonNull JCMediaPlayerListener listener) {
        LISTENER_LIST.push(new WeakReference<>(listener));
    }

    // 主要的过滤JCVideoPlayer被复用的case
    public static void checkAndPutListener(@NonNull JCMediaPlayerListener listener) {
        if (listener.getScreenType() == JCVideoPlayer.SCREEN_WINDOW_TINY ||
                listener.getScreenType() == JCVideoPlayer.SCREEN_WINDOW_FULLSCREEN) return;
        int index = -1;
        for (int i = 0; i < LISTENER_LIST.size(); i++) {
            JCMediaPlayerListener jcMediaPlayerListener = LISTENER_LIST.get(i).get();
            if (jcMediaPlayerListener != null
                    && jcMediaPlayerListener.getScreenType() == listener.getScreenType()) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            LISTENER_LIST.set(index, new WeakReference<>(listener));
        }
    }

    public static JCMediaPlayerListener popListener() {
        if (LISTENER_LIST.size() == 0) {
            return null;
        }
        return LISTENER_LIST.pop().get();
    }

    public static int listenerSize() {
        return LISTENER_LIST.size();
    }

    public static boolean hasSameScreenTypeListener(int screenType) {
        for (int i = 0; i < LISTENER_LIST.size(); i++) {
            JCMediaPlayerListener jcMediaPlayerListener = LISTENER_LIST.get(i).get();
            if (jcMediaPlayerListener != null
                    && jcMediaPlayerListener.getScreenType() == screenType) {
                return true;
            }
        }
        return false;
    }

    public static JCMediaPlayerListener getFirst() {
        if (LISTENER_LIST.size() == 0) {
            return null;
        }
        return LISTENER_LIST.getFirst().get();
    }

    static void completeAll() {
        JCMediaPlayerListener ll = popListener();
        while (ll != null) {
            ll.onCompletion();
            ll = popListener();
        }
    }

    static void pauseVideo() {
        JCMediaPlayerListener first = getFirst();
        if (first != null) {
            first.onPause();
        }
    }
}
