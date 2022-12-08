package com.ss.video.rtc.demo.quickstart;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ss.rtc.demo.quickstart.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天消息的适配器
 */
public class VideoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<FrameLayout> mVideoList = new ArrayList<>();

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_camera_rv,
                parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof VideoViewHolder) {
            ((VideoViewHolder) holder).bind(mVideoList.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return mVideoList.size();
    }

    public void notifyItems(@NonNull List<FrameLayout> items) {
        mVideoList.clear();
        mVideoList.addAll(items);
        notifyDataSetChanged();
    }



    public void addVideo(FrameLayout fl) {
        if (fl == null) {
            return;
        }
        mVideoList.add(fl);
        notifyItemInserted(mVideoList.size() - 1);
    }


    private static class VideoViewHolder extends RecyclerView.ViewHolder {

        private final FrameLayout mVideoFL;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            mVideoFL = (FrameLayout) itemView;
        }

        public void bind(FrameLayout fl) {
            mVideoFL.addView(fl);
        }
    }
}