package com.easyhood.easyyingbianclip;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 功能：音视频转换类
 * 详细描述：将音视频通过剪辑生产新的文件
 * 作者：guan_qi
 * 创建日期：2023-03-21
 */
public class VideoAudioTransProcess {
    private static final String TAG = "VideoAudioTransProcess";
    private static final long TIMEOUT = 1000;

    /**
     * 混合音视频轨道
     * @param videoInput 视频输入
     * @param audioInput 音频输入
     * @param output 输出
     * @param startTimeUs 开始时间
     * @param endTimeUs 结束时间
     * @param videoVolume 视频轨
     * @param aacVolume aac音频轨
     * @throws Exception 异常
     */
    public static void mixAudioTrack(final String videoInput,
                                     final String audioInput,
                                     final String output,
                                     final Integer startTimeUs,
                                     final Integer endTimeUs,
                                     int videoVolume,
                                     int aacVolume) throws Exception {
        // MP3 混音 压缩 数据 pcm
        File cacheDir = Environment.getExternalStorageDirectory();
        // 还没生成
        final File videoPcmFile = new File(cacheDir, "video" + ".pcm");
        decodeToPCM(videoInput, videoPcmFile.getAbsolutePath(), startTimeUs, endTimeUs);
        // 下载下来的音乐转换成pcm
        File aacPcmFile = new File(cacheDir, "audio" + ".pcm");
        decodeToPCM(audioInput, aacPcmFile.getAbsolutePath(), startTimeUs, endTimeUs);
        // 混音
        File adjustedPcm = new File(cacheDir, "混合后的" + ".pcm");
        mixPcm(videoPcmFile.getAbsolutePath(), aacPcmFile.getAbsolutePath(),
                adjustedPcm.getAbsolutePath(), videoVolume, aacVolume);
        File wavFile = new File(cacheDir, adjustedPcm.getName() + ".wav");
        new PcmToWavUtils(44100, AudioFormat.CHANNEL_IN_STEREO,
                2, AudioFormat.ENCODING_PCM_16BIT).pcmToWav(adjustedPcm.getAbsolutePath(),
                wavFile.getAbsolutePath());
        // 合并
        mixVideoAndMusic(videoInput, output, startTimeUs, endTimeUs, wavFile);

    }

    /**
     * 合并音视频
     * @param videoInput 视频输入
     * @param output 输出
     * @param startTimeUs 开始时间
     * @param endTimeUs 结束时间
     * @param wavFile 音频wav文件
     * @throws IOException IO异常
     */
    @SuppressLint("WrongConstant")
    private static void mixVideoAndMusic(String videoInput, String output, Integer startTimeUs,
                                         Integer endTimeUs, File wavFile) throws IOException {
        // 视频容器输出
        MediaMuxer mediaMuxer = new MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        // 读取视频的工具类
        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(videoInput);
        // 拿到视频轨道的索引
        int videoIndex = selectTrack(mediaExtractor, false);
        int audioIndex = selectTrack(mediaExtractor, true);
        // 添加轨道索引 视频索引 音频索引
        MediaFormat videoFormat = mediaExtractor.getTrackFormat(videoIndex);
        // 新的视频轨
        mediaMuxer.addTrack(videoFormat);
        // mediaMuxer拥有添加视频流的能力
        MediaFormat audioFormat = mediaExtractor.getTrackFormat(audioIndex);
        int audioBitrate = audioFormat.getInteger(MediaFormat.KEY_BIT_RATE);
        audioFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        // 添加轨道索引
        int muxerAudioIndex = mediaMuxer.addTrack(audioFormat);
        // 开始输出视频任务
        mediaMuxer.start();
        // 音频的wav 音频文件 一个轨道 原始音频数据的 MediaFormat
        MediaExtractor pcmExtrator = new MediaExtractor();
        pcmExtrator.setDataSource(wavFile.getAbsolutePath());
        int audioTrack = selectTrack(pcmExtrator, true);
        pcmExtrator.selectTrack(audioTrack);
        MediaFormat pcmTrackFormat = pcmExtrator.getTrackFormat(audioTrack);
        // 最大一帧的大小
        int maxBufferSize = 0;
        if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxBufferSize = pcmTrackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        } else {
            maxBufferSize = 100 * 1000;
        }
        // 参数对应音频类型、采样率、声道数
        MediaFormat encodeFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                44100, 2);
        // 比特率
        encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate);
        // 音质等级
        encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        // 最大解码体积
        encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxBufferSize);
        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.
                MIMETYPE_AUDIO_AAC);
        encoder.configure(encodeFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        // 开始编码
        encoder.start();
        // 自己读取 从MediaExtractor
        ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        // 是否编码完成
        boolean encodeDone = false;
        while (!encodeDone) {
            int inputBufferIndex = encoder.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                // 返回值是时间戳 <0文件读到了末尾
                long sampleTime = pcmExtrator.getSampleTime();
                if (sampleTime < 0) {
                    encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    int flags = pcmExtrator.getSampleFlags();
                    int size = pcmExtrator.readSampleData(buffer, 0);
                    ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                    inputBuffer.clear();
                    inputBuffer.put(buffer);
                    inputBuffer.position(0);
                    // 通知编码
                    encoder.queueInputBuffer(inputBufferIndex, 0, size, sampleTime, flags);
                    // 放弃内存，一定要写 不写不能导出新的数据
                    pcmExtrator.advance();
                }
            }
            // 输出的容器的索引
            int outIndex = encoder.dequeueOutputBuffer(info, TIMEOUT);
            while (outIndex >= 0) {
                if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    encodeDone = true;
                    break;
                }
                // 通过索引 得到编码好的数据再哪个容器
                ByteBuffer encodeOutputBuffer = encoder.getOutputBuffer(outIndex);
                // 数据写进去了
                mediaMuxer.writeSampleData(muxerAudioIndex, encodeOutputBuffer, info);
                // 清空容器数据 方便下次阅读
                encodeOutputBuffer.clear();
                // 把编码器的数据释放，方便dsp 下一帧存储
                encoder.releaseOutputBuffer(outIndex, false);
                outIndex = encoder.dequeueOutputBuffer(info, TIMEOUT);
            }
        }
        // 视频
        if (audioTrack >= 0) {
            mediaExtractor.unselectTrack(audioTrack);
        }
        // 选择视频轨
        mediaExtractor.selectTrack(videoIndex);
        // seek 到 startTimeUs 的时间戳的前一个I帧
        mediaExtractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_NEXT_SYNC);
        // 视频最大帧的大小
        maxBufferSize = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        buffer = ByteBuffer.allocateDirect(maxBufferSize);
        while (true) {
            long sampleTimeUs = mediaExtractor.getSampleTime();
            if (sampleTimeUs == -1) {
                break;
            }
            if (sampleTimeUs < startTimeUs) {
                mediaExtractor.advance();
                continue;
            }
            if (endTimeUs != null && sampleTimeUs > endTimeUs) {
                break;
            }
            // 写入视频数据
            mediaMuxer.writeSampleData(videoIndex, buffer, info);
            // advance
            mediaExtractor.advance();
        }
        pcmExtrator.release();
        mediaExtractor.release();
        encoder.stop();
        encoder.release();
        mediaMuxer.release();
    }

    /**
     * 混音
     * @param pcm1Path 第一个pcm音频路径
     * @param pcm2Path 第二个pcm音频路径
     * @param toPath 生成路径
     * @param vol1 第一个vol流
     * @param vol2 第二个vol流
     * @throws IOException IO异常
     */
    public static void mixPcm(String pcm1Path, String pcm2Path, String toPath,
                              int vol1, int vol2) throws IOException {
        float volume1 = vol1;
        float volume2 = vol2;
        // 待混音的两条数据流 傅里叶
        FileInputStream is1 = new FileInputStream(pcm1Path);
        FileInputStream is2 = new FileInputStream(pcm2Path);
        boolean end1 = false;
        boolean end2 = false;
        // 输出的数据流
        FileOutputStream fileOutputStream = new FileOutputStream(toPath);
        byte[] buffer1 = new byte[2048];
        byte[] buffer2 = new byte[2048];
        byte[] buffer3 = new byte[2048];
        short temp1;
        short temp2;
        while (!end1 || !end2) {
            if (!end2) {
                end2 = (is2.read(buffer2) == -1);
            }
            if (!end1) {
                end1 = (is1.read(buffer1) == -1);
            }
            int voice = 0;
            // 2个字节
            // 声量值 32767 -32768
            for (int i = 0; i < buffer2.length; i += 2) {
                temp1 = (short) ((buffer1[i] & 0xff) | (buffer1[i + 1] & 0xff) << 8);
                temp2 = (short) ((buffer2[i] & 0xff) | (buffer2[i + 1] & 0xff) << 8);
                voice = (int) (temp1 * volume1 + temp2 * volume2);
                if (voice > 32767) {
                    voice = 32767;
                } else if (voice < -32768) {
                    voice = -32768;
                }
                buffer3[i] = (byte) (voice & 0xFF);
                buffer3[i + 1] = (byte) ((voice >>> 8) & 0xFF);
            }
            fileOutputStream.write(buffer3);
        }
        is1.close();
        is2.close();
        fileOutputStream.close();
    }

    /**
     * 转换成PCM
     * @param musicPath 音频路径
     * @param outPath 输出路径
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @throws Exception 异常
     */
    @SuppressLint("WrongConstant")
    public static void decodeToPCM(String musicPath,
                                   String outPath,
                                   int startTime,
                                   int endTime) throws Exception {
        if (endTime < startTime) {
            return;
        }
        // MediaExtractor:负责将指定类型的媒体文件从文件中找到轨道，并填充到MediaCodec的缓冲区中
        MediaExtractor mediaExtractor = new MediaExtractor();
        // 设置路径
        mediaExtractor.setDataSource(musicPath);
        // 音频索引
        int audioTrack = selectTrack(mediaExtractor, true);
        // 剪辑 选择轨道
        mediaExtractor.selectTrack(audioTrack);
        mediaExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_NEXT_SYNC);
        MediaFormat audioFormat = mediaExtractor.getTrackFormat(audioTrack);

        MediaCodec mediaCodec = MediaCodec.createDecoderByType(audioFormat.
                getString(MediaFormat.KEY_MIME));
        mediaCodec.configure(audioFormat, null, null, 0);
        mediaCodec.start();
        int maxBufferSize = 100 * 1000;
        if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxBufferSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        } else {
            maxBufferSize = 100 * 1000;
        }
        File pcmFile = new File(outPath);
        FileChannel writeChannel = new FileOutputStream(pcmFile).getChannel();
        // 10M 造成内存浪费 10K 异常
        ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int inIndex = mediaCodec.dequeueInputBuffer(1000);
            if (inIndex >= 0) {
                // 获取到 视频容器 里面读取的当前时间戳
                long sampleTimeUs = mediaExtractor.getSampleTime();
                if (sampleTimeUs == -1) {
                    break;
                } else if (sampleTimeUs < startTime) {
                    // 丢弃的意思
                    mediaExtractor.advance();
                } else if (sampleTimeUs > endTime) {
                    break;
                }
                // mediaExtractor
                info.size = mediaExtractor.readSampleData(buffer, 0);
                info.presentationTimeUs = sampleTimeUs;
                info.flags = mediaExtractor.getSampleFlags();
                byte[] content = new byte[buffer.remaining()];
                buffer.get(content);
                FileUtils.writeContent(content);
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inIndex);
                inputBuffer.put(content);
                mediaCodec.queueInputBuffer(inIndex, 0,
                        info.size, info.presentationTimeUs, info.flags);
                // 释放上一帧的压缩数据
                mediaExtractor.advance();
            }
            int outIndex = -1;
            outIndex = mediaCodec.dequeueOutputBuffer(info, 1_000);
            if (outIndex >= 0) {
                ByteBuffer decodeOutputBuffer = mediaCodec.getOutputBuffer(outIndex);
                // 音频数据
                writeChannel.write(decodeOutputBuffer);
                mediaCodec.releaseOutputBuffer(outIndex, false);
            }
            writeChannel.close();
            mediaExtractor.release();
            mediaCodec.stop();
            mediaCodec.release();
        }
    }

    /**
     * 寻找音频轨
     * @param extractor 轨道编辑器
     * @param isAudio 是否为音频
     * @return 轨道索引
     */
    public static int selectTrack(MediaExtractor extractor, boolean isAudio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            // 轨道配置信息 码流读取 sps pps 解析
            MediaFormat format = extractor.getTrackFormat(i);
            // 轨道类型
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (isAudio) {
                if (mime.startsWith("audio")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video")) {
                    return i;
                }
            }
        }
        return -1;
    }
}
