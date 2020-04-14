package com.luomin.dubbing;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AACTrackImpl;
import com.googlecode.mp4parser.util.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 1.背景AAC转PCM
 *
 * @see #fdkdecodec(String, String)
 * 2.拼接所有人物配音PCM(采样时可以加入降噪算法)
 * @see #addAllPcm21(String, ArrayList<DubbingTimeObj> )
 * 3.将背景PCM和人物PCM 44100
 * @see #mixedpcm(String, String, String)
 * 4.将最终PCM分解为多个大小不小于4秒的PCM
 * @see #cutPCM(String, String, String)
 * 5.将分段PCM转换为ADTS的AAC
 * @see #fdkencodec(String, String)
 * 6.将所有的AAC拼接起来
 * @see #addAllAAC21(String, ArrayList)
 * 7.将最终的AAC替换掉原视频的音轨
 * @see #muxAacMp4(String, String, String)
 */
public class Dubbing {

    static {
        System.loadLibrary("dubbing");
    }

    /**
     * 将aac解码为pcm
     *
     * @param pcmFile 输出pcm
     * @param aacFile 输入aac
     * @return 1为成功
     */
    public native static int fdkdecodec(String pcmFile, String aacFile);

    /**
     * 将所有的PCM录音都添加到一个PCM中
     *
     * @param finalPcmFile 合成后的PCM
     * @param itemTimes    每一个PCM的地址和所占时间
     */
    public static void addAllPcm21(String finalPcmFile, ArrayList<DubbingTimeObj> itemTimes) {
        //1.创建最终PCM文件
        File file = new File(finalPcmFile);
        long length = -1;
        if (file.exists()) {
            file.delete();
        }
        try {
            boolean newFile = file.createNewFile();
            if (newFile) {
                length = 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (length == -1) {
                return;
            }
        }

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file, true);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            //计算当前PCM开始的长度。如果不够就写入空数据到该长度
            int bufferSize4Dubbing = 1024;
            long startTime = 0;
            long endTime = 0;
            for (int position = 0; position < itemTimes.size(); position++) {
                DubbingTimeObj dubbingTimeObj = itemTimes.get(position);
                if (new File(dubbingTimeObj.getSourcePcmFile()).exists()) {
                    startTime = dubbingTimeObj.getStart();
                    endTime = dubbingTimeObj.getEnd();

                    long currentFileShouldBeHere = startTime * 44100 * 16 * 2 / 8000;
                    long currentFileShouldBeEndHere = endTime * 44100 * 16 * 2 / 8000;
                    while (currentFileShouldBeHere > length) {
                        //如果没有到这个位置,则补充到这个位置
                        if (currentFileShouldBeHere - length > bufferSize4Dubbing) {
                            bufferedOutputStream.write(new byte[bufferSize4Dubbing]);
                            length += bufferSize4Dubbing;
                        } else {
                            int tempLength = Long.valueOf(currentFileShouldBeHere - length).intValue();
                            bufferedOutputStream.write(new byte[tempLength]);
                            length += tempLength;
                        }
                    }
                    if (length % 2 != 0) {
                        bufferedOutputStream.write(new byte[1]);
                        length += 1;
                    }

                    //写入所有的文件数据
                    FileInputStream fileInputStream = new FileInputStream(dubbingTimeObj.getSourcePcmFile());
                    BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
                    byte[] buffer = new byte[bufferSize4Dubbing];
                    int read;
                    while ((read = bufferedInputStream.read(buffer, 0, bufferSize4Dubbing)) != -1) {
                        if (length + read > currentFileShouldBeEndHere) {
                            int len = Long.valueOf(currentFileShouldBeEndHere - length).intValue();
                            bufferedOutputStream.write(buffer, 0, len);
                            length += len;
                        } else {
                            bufferedOutputStream.write(buffer, 0, read);
                            length += read;
                        }
                    }

                    if (length % 2 != 0) {
                        bufferedOutputStream.write(new byte[1]);
                        length += 1;
                    }

                    bufferedInputStream.close();
                }
            }

            bufferedOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将背景音乐和人的录音合成为一个。44100 16 2 格式
     *
     * @param backgroundPcmFile 背景音乐
     * @param peoplePcmFile     人录音
     * @param outPcmFile        输出的PCM
     * @return 1为成功
     */
    public native static int mixedpcm(String backgroundPcmFile, String peoplePcmFile, String outPcmFile);


    /**
     * 1.是否存在可以裁剪的音频
     * 2.按4秒分割计算可以将音频分割为多少段。最后一段小于3秒将直接添加到上一段后面
     * 3.分割 分割后的文件名 outDir + prefix + index + ".pcm"
     *
     * @param finalPCM 人录音和背景音的混合。格式44100 16 2
     * @param outDir   将分割后的PCM输出到该文件夹
     * @param prefix   分割后名字的前缀
     * @return 最终分割后的个数
     */
    public static int cutPCM(String finalPCM, String outDir, String prefix) {
        //1.是否存在可以裁剪的音频
        if (new File(finalPCM).exists()) {
            //2.按4秒分割计算可以将音频分割为多少段。最后一段小于3秒将直接添加到上一段后面
            File file = new File(finalPCM);
            long length = file.length();
            long bps = 44100 * 16 * 2 / 8;
            long seconds = length / bps;
            long modulesOperator4 = seconds / 4;
            long remainderOperator4 = seconds % 4;
            if (remainderOperator4 >= 3) {
                ++modulesOperator4;
            }

            //3.分割
            BufferedInputStream bufferedInputStream = null;
            BufferedOutputStream bufferedOutputStream = null;
            try {
                bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
                int read = 0;
                int index = 0;
                int currentBytes = 0;
                byte[] buffer = new byte[1024];

                bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(outDir + prefix + index + ".pcm"));
                while ((read = bufferedInputStream.read(buffer, 0, 1024)) > 0) {
                    bufferedOutputStream.write(buffer, 0, read);
                    currentBytes += read;
                    if (currentBytes / bps / 4 > index && index != modulesOperator4) {
                        index++;
                        bufferedOutputStream.close();
                        bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(outDir + prefix + index + ".pcm"));
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (bufferedInputStream != null)
                        bufferedInputStream.close();
                    if (bufferedOutputStream != null)
                        bufferedOutputStream.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return Long.valueOf(modulesOperator4).intValue();
        }
        return -1;
    }

    /**
     * 将pcm编码为aac
     *
     * @param pcmFile 输入pcm
     * @param aacFile 输出aac 格式必须是ADTS
     * @return 1为成功
     */
    public native static int fdkencodec(String pcmFile, String aacFile);

    /**
     * 将所有的ADTS格式的AAC拼接在一起
     *
     * @param finalAACFile  拼接后的AAC
     * @param sourceAACFile 需要拼接的AAC按顺序添加
     */
    public static void addAllAAC21(String finalAACFile, ArrayList<String> sourceAACFile) {
        //1.创建最终PCM文件
        File file = new File(finalAACFile);
        long length = -1;
        if (file.exists()) {
            file.delete();
        }
        try {
            boolean newFile = file.createNewFile();
            if (newFile) {
                length = 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (length == -1) {
                return;
            }
        }

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file, true);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            int bufferSize4Dubbing = 1024;
            for (int position = 0; position < sourceAACFile.size(); position++) {
                if (new File(sourceAACFile.get(position)).exists()) {
                    FileInputStream fileInputStream = new FileInputStream(sourceAACFile.get(position));
                    BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
                    byte[] buffer = new byte[bufferSize4Dubbing];
                    int read = 0;
                    while ((read = bufferedInputStream.read(buffer, 0, bufferSize4Dubbing)) != -1) {
                        bufferedOutputStream.write(buffer, 0, read);
                    }
                    bufferedInputStream.close();
                }
            }

            bufferedOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将 AAC(44100 2 16) 和 MP4(h264) 进行混合[替换了视频的音轨]
     *
     * @param aacPath 音频文件
     * @param mp4Path 视频文件
     * @param outPath 最终合成文件
     * @return true为成功合成
     */
    public static boolean muxAacMp4(String aacPath, String mp4Path, String outPath) {
        try {
            AACTrackImpl aacTrack = new AACTrackImpl(new FileDataSourceImpl(aacPath));
            Movie videoMovie = MovieCreator.build(mp4Path);
            Track videoTracks = null;// 获取视频的单纯视频部分
            for (Track videoMovieTrack : videoMovie.getTracks()) {
                if ("vide".equals(videoMovieTrack.getHandler())) {
                    videoTracks = videoMovieTrack;
                }
            }

            Movie resultMovie = new Movie();
            resultMovie.addTrack(videoTracks);// 视频部分
            resultMovie.addTrack(aacTrack);// 音频部分

            Container out = new DefaultMp4Builder().build(resultMovie);
            FileOutputStream fos = new FileOutputStream(new File(outPath));
            out.writeContainer(fos.getChannel());
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * 所有的步骤都聚集在一起
     * warning: 文件夹需要占用名background.pcm append21.pcm final.pcm outDir + prefix + position + ".pcm" outDir + prefix + position + ".aac"
     *
     * @param outDir 输出的文件夹,最后需要带分隔符
     *               1.背景AAC转PCM
     * @see #fdkdecodec(String, String)
     * 2.拼接所有人物配音PCM(采样时可以加入降噪算法)
     * @see #addAllPcm21(String, ArrayList<DubbingTimeObj> )
     * 3.将背景PCM和人物PCM 44100
     * @see #mixedpcm(String, String, String)
     * 4.将最终PCM分解为多个大小不小于4秒的PCM
     * @see #cutPCM(String, String, String)
     * 5.将分段PCM转换为ADTS的AAC
     * @see #fdkencodec(String, String)
     * 6.将所有的AAC拼接起来
     * @see #addAllAAC21(String, ArrayList)
     * 7.将最终的AAC替换掉原视频的音轨
     * @see #muxAacMp4(String, String, String)
     */
    public static void sumAllProgress(String outDir, String prefix, String sourceBackgroundAAC, ArrayList<DubbingTimeObj> dubbingTimeObjs, String mp4path, String outPath) {
        //1.背景AAC转PCM
        fdkdecodec(outDir + "background.pcm", sourceBackgroundAAC);
        //2.拼接所有人物配音PCM(采样时可以加入降噪算法)
        addAllPcm21(outDir + "append21.pcm", dubbingTimeObjs);
        //3.将背景PCM和人物PCM 44100
        mixedpcm(outDir + "background.pcm", outDir + "append21.pcm", outDir + "final.pcm");
        //4.将最终PCM分解为多个大小不小于4秒的PCM
        int segmentnum = cutPCM(outDir + "final.pcm", outDir, prefix);
        //5.将分段PCM转换为ADTS的AAC
        concurrencyEncodecPCM(segmentnum, outDir, prefix, mp4path, outPath);
    }

    public static void sumAllProgressWithSrt(String outDir, String prefix, String sourceBackgroundAAC, String srtPath, ArrayList<String> recordPcms, String mp4path, String outPath) {
        //1.背景AAC转PCM
        fdkdecodec(outDir + "background.pcm", sourceBackgroundAAC);
        //2.拼接所有人物配音PCM(采样时可以加入降噪算法)
        //2.1解析srt
        addAllPcm21(outDir + "append21.pcm", srtParse(srtPath, recordPcms));
        //3.将背景PCM和人物PCM 44100
        mixedpcm(outDir + "background.pcm", outDir + "append21.pcm", outDir + "final.pcm");
        //4.将最终PCM分解为多个大小不小于4秒的PCM
        int segmentnum = cutPCM(outDir + "final.pcm", outDir, prefix);
        //5.将分段PCM转换为ADTS的AAC
        concurrencyEncodecPCM(segmentnum, outDir, prefix, mp4path, outPath);
    }

    public static void sumAllProgressWithSrtAlreadyBackgroundPcm(String outDir, String prefix, String sourceBackgroundPCM, String srtPath, ArrayList<String> recordPcms, String mp4path, String outPath) {
        //1.背景AAC转PCM
        //fdkdecodec(outDir + "background.pcm", sourceBackgroundAAC);
        //2.拼接所有人物配音PCM(采样时可以加入降噪算法)
        //2.1解析srt
        addAllPcm21(outDir + "append21.pcm", srtParse(srtPath, recordPcms));
        //3.将背景PCM和人物PCM 44100
        mixedpcm(sourceBackgroundPCM, outDir + "append21.pcm", outDir + "final.pcm");
        //4.将最终PCM分解为多个大小不小于4秒的PCM
        int segmentnum = cutPCM(outDir + "final.pcm", outDir, prefix);
        //5.将分段PCM转换为ADTS的AAC
        concurrencyEncodecPCM(segmentnum, outDir, prefix, mp4path, outPath);
    }

    private static void mixedLastTwoSteps(String outDir, String prefix, int segmentnum, String mp4path, String outPath) {
        //6.将所有的AAC拼接起来
        ArrayList<String> sourceAACFile = new ArrayList<>();
        for (int i = 0; i < segmentnum; i++) {
            sourceAACFile.add(outDir + prefix + i + ".aac");
        }
        addAllAAC21(outDir + "final.aac", sourceAACFile);
        //7.将最终的AAC替换掉原视频的音轨
        muxAacMp4(outDir + "final.aac", mp4path, outPath);
    }

    private static void concurrencyEncodecPCM(int segmentnum, String outDir, String prefix, String mp4path, String outPath) {
        ExecutorService executorService = Executors.newCachedThreadPool();
        CountDownLatch countDownLatch = new CountDownLatch(segmentnum);
        for (int j = 0; j < segmentnum; j++) {
            executorService.execute(new TaskEncodecPCM(j, countDownLatch, outDir, prefix));
        }
        executorService.execute(new TaskEncodecPCMSuccess(countDownLatch, outDir, prefix, segmentnum, mp4path, outPath));
    }


    static class TaskEncodecPCM implements Runnable {
        int position;
        CountDownLatch countDownLatch;
        String outDir, prefix;

        TaskEncodecPCM(int position, CountDownLatch countDownLatch, String outDir, String prefix) {
            this.outDir = outDir;
            this.prefix = prefix;
            this.position = position;
            this.countDownLatch = countDownLatch;
        }

        @Override
        public void run() {
            encodec();
            countDownLatch.countDown();
        }

        private void encodec() {
            fdkencodec(outDir + prefix + position + ".pcm", outDir + prefix + position + ".aac");
        }
    }

    static class TaskEncodecPCMSuccess implements Runnable {
        String outDir, prefix, mp4path, outPath;
        int segmentnum;

        TaskEncodecPCMSuccess(CountDownLatch countDownLatch, String outDir, String prefix, int segmentnum, String mp4path, String outPath) {
            this.outDir = outDir;
            this.prefix = prefix;
            this.segmentnum = segmentnum;
            this.mp4path = mp4path;
            this.outPath = outPath;
            this.countDownLatch = countDownLatch;
        }

        CountDownLatch countDownLatch;

        @Override
        public void run() {
            try {
                this.countDownLatch.await();
                mixedLastTwoSteps(outDir, prefix, segmentnum, mp4path, outPath);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 解析srt格式的字幕文件
     * srt格式：字幕序号
     * 字幕显示的起始和结束时间
     * 字幕内容(可多行)
     * 空白行(表示本字幕段的结束)
     *
     * @param srcPath
     * @param recordPcms 所有的录制的PCM
     * @return
     */
    private static ArrayList<DubbingTimeObj> srtParse(String srcPath, ArrayList<String> recordPcms) {
        ArrayList<DubbingTimeObj> dubbingTimeObjs = new ArrayList<>();
        File file = new File(srcPath);
        try {
            BufferedReader bufferedInputStream = new BufferedReader(new FileReader(file));

            String line;
            while ((line = bufferedInputStream.readLine()) != null) {
                if (line.matches("[0-9]{2}:[0-9]{2}:[0-9]{2},[0-9]{3} --> [0-9]{2}:[0-9]{2}:[0-9]{2},[0-9]{3}")) {
                    DubbingTimeObj dubbingTimeObj = new DubbingTimeObj();
                    String[] startAndEndTime = line.split("-->");
                    if (startAndEndTime.length == 2) {
                        dubbingTimeObj.setStart(timeToLong(startAndEndTime[0].trim()));
                        dubbingTimeObj.setEnd(timeToLong(startAndEndTime[1].trim()));
                        if (recordPcms.size() >= dubbingTimeObjs.size()) {
                            dubbingTimeObj.setSourcePcmFile(recordPcms.get(dubbingTimeObjs.size()));
                        }
                        dubbingTimeObjs.add(dubbingTimeObj);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return dubbingTimeObjs;
    }


    /**
     * time String to Long
     *
     * @param time 00:00:00.000
     * @return long unit million seconds
     */
    private static long timeToLong(String time) {
        long result = 0l;
        time = time.replace(" ", "");
        String[] times = time.split(":");
        for (int i = 0; i < times.length; i++) {
            switch (i) {
                case 0:
                    result += Integer.valueOf(times[i]) * 60 * 60l;
                    break;
                case 1:
                    result += Integer.valueOf(times[i]) * 60l;
                    break;
                case 2:
                    result = result * 1000;
                    String replace = times[i].replace(",", ".");
                    Float aFloat = Float.valueOf(replace) * 1000;
                    result += aFloat;
                    break;
            }
        }

        return result;
    }

}