package fm.jiecao.jiecaovideoplayer;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.picasso.Picasso;

import fm.jiecao.jcvideoplayer_lib.JCVideoPlayer;
import fm.jiecao.jcvideoplayer_lib.JCVideoPlayerStandard;

public class RecyclerViewVideoAdapter extends RecyclerView.Adapter<RecyclerViewVideoAdapter.MyViewHolder> {

    private Context context;
    private boolean enableTiny;
    public static final String TAG = "RecyclerViewVideoAdapter";

    public RecyclerViewVideoAdapter(Context context) {
        this.context = context;
    }

    public RecyclerViewVideoAdapter(Context context, boolean enableTiny) {
        this.context = context;
        this.enableTiny = enableTiny;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        MyViewHolder holder = new MyViewHolder(LayoutInflater.from(
                context).inflate(R.layout.item_videoview, parent,
                false));
        return holder;
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        Log.i(TAG, "onBindViewHolder[" + holder.jcVideoPlayer.hashCode() + "] pos=" + position);

        holder.jcVideoPlayer.setEnableTiny(enableTiny);
        holder.jcVideoPlayer.setUp(
                VideoConstant.videoUrls[position], JCVideoPlayer.SCREEN_LAYOUT_LIST,
                VideoConstant.videoTitles[position]);
        Picasso.with(holder.jcVideoPlayer.getContext())
                .load(VideoConstant.videoThumbs[position])
                .into(holder.jcVideoPlayer.thumbImageView);
    }

    @Override
    public int getItemCount() {
        return VideoConstant.videoUrls.length;
    }

    class MyViewHolder extends RecyclerView.ViewHolder {
        JCVideoPlayerStandard jcVideoPlayer;

        public MyViewHolder(View itemView) {
            super(itemView);
            jcVideoPlayer = (JCVideoPlayerStandard) itemView.findViewById(R.id.videoplayer);
        }
    }

}
