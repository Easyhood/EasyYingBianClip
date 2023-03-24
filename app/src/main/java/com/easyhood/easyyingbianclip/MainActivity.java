package com.easyhood.easyyingbianclip;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.VideoView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private VideoView videoView;
    private SeekBar musicSeekBar;
    private SeekBar voiceSeekBar;
    private int musicVolume = 0;
    private int voiceVolume = 0;
    private Runnable runnable;
    private int duration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermission(this);
        videoView = findViewById(R.id.videoView);
        musicSeekBar = findViewById(R.id.musicSeekBar);
        voiceSeekBar = findViewById(R.id.voiceSeekBar);
        musicSeekBar.setMax(100);
        voiceSeekBar.setMax(100);
        musicSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                musicVolume = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        voiceSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                voiceVolume = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        new Thread() {
            @Override
            public void run() {
                final String aacPath = new File(Environment.getExternalStorageDirectory(), "music.mp3").getAbsolutePath();
                final String videoPath = new File(Environment.getExternalStorageDirectory(), "input.mp4").getAbsolutePath();
                try {
                    copyAssets("music.mp3", aacPath);
                    copyAssets("input.mp4", videoPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        startPlay(new File(Environment.getExternalStorageDirectory(),
                "input.mp4").getAbsolutePath());
    }

    /**
     * 权限检查
     * @param activity Activity
     * @return false
     */
    public static boolean checkPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);

        }
        return false;
    }

    /**
     * 开始播放
     * @param path 路径
     */
    private void startPlay(String path) {
        ViewGroup.LayoutParams layoutParams = videoView.getLayoutParams();
        layoutParams.height = 675;
        layoutParams.width = 1285;
        videoView.setLayoutParams(layoutParams);
        videoView.setVideoPath(path);
        videoView.start();
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                duration = mp.getDuration() / 1000;
                mp.setLooping(true);
                final Handler handler = new Handler();
                runnable = new Runnable() {
                    @Override
                    public void run() {
                        handler.postDelayed(runnable, 1000);
                    }
                };
                handler.postDelayed(runnable, 1000);
            }
        });
    }

    /**
     * 开始播放音视频
     * @param view View
     */
    public void startPlayVideoAudio(View view) {
        // 剪辑的起始时间 终止时间 视频调整后的音乐大小 原生大小
        File cacheDir = Environment.getExternalStorageDirectory();
        final File videoFile = new File(cacheDir, "input.mp4");
        final File audioFile = new File(cacheDir, "music.mp3");
        // 剪辑好的视频输出放哪里
        final File outputFile = new File(cacheDir, "output.mp4");
        new Thread(){
            @Override
            public void run() {
                try {
                    VideoAudioTransProcess.mixAudioTrack(
                            videoFile.getAbsolutePath(),
                            audioFile.getAbsolutePath(),
                            outputFile.getAbsolutePath(),
                            (int) (60 * 1000 * 1000),
                            (int) (100 * 1000 * 1000),
                            voiceVolume,
                            musicVolume
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * 复制资源
     * @param assetsName 资源名
     * @param path 路径
     * @throws IOException IO异常
     */
    private void copyAssets(String assetsName, String path) throws IOException {
        AssetFileDescriptor assetFileDescriptor = getAssets().openFd(assetsName);
        FileChannel from = new FileInputStream(assetFileDescriptor.getFileDescriptor()).getChannel();
        FileChannel to = new FileOutputStream(path).getChannel();
    }
}