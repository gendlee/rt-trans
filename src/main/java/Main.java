import javax.sound.sampled.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.tencentcloudapi.asr.v20190614.*;
import com.tencentcloudapi.asr.v20190614.models.*;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;

public class Main {
    private static final float SAMPLE_RATE = 48000.0f;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int CHANNELS = 2;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;
    // 文本文件，作为OBS的输入
    private static final String SUBTITLE_FILE = "subtitles.txt";
    private static final String TENCENT_SECRET_ID = "AKIDFKhspibP0NYUCgv263ngLn37yax5yd3D";
    private static final String TENCENT_SECRET_KEY = "zjEUVHtJgqqeiZCZhZCZhaFfrqb8RWMy";
    private static AsrClient asrClient;
    
    public static void main(String[] args) {
        // 初始化腾讯云语音识别客户端
        initAsrClient();
        
        // 设置音频格式
        AudioFormat format = new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_IN_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
        );

        DataLine.Info targetInfo = new DataLine.Info(
            TargetDataLine.class,
            format
        );

        try {
            // 查找 BlackHole 设备
            TargetDataLine targetLine = findVirtualCableDevice(targetInfo);
            if (targetLine == null) {
                throw new RuntimeException("未找到 BlackHole 设备");
            }

            // 打开并启动数据线
            targetLine.open(format);
            targetLine.start();

            // 创建音频捕获线程
            Thread captureThread = new Thread(() -> captureAudio(targetLine));
            captureThread.start();

            // 等待用户输入以停止程序
            System.out.println("程序已启动，按回车键停止...");
            System.in.read();

            // 清理资源
            targetLine.stop();
            targetLine.close();

        } catch (Exception e) {
            System.err.println("音频捕获错误: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private static void initAsrClient() {
        try {
            Credential cred = new Credential(TENCENT_SECRET_ID, TENCENT_SECRET_KEY);
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("asr.tencentcloudapi.com");
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            asrClient = new AsrClient(cred, "ap-guangzhou", clientProfile);
        } catch (Exception e) {
            System.err.println("初始化语音识别客户端失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static TargetDataLine findVirtualCableDevice(DataLine.Info targetInfo) {
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        System.out.println("\n=== 可用音频设备列表及其支持的格式 ===");
        
        for (Mixer.Info mixerInfo : mixerInfos) {
            System.out.println("\n设备名称: " + mixerInfo.getName());
            System.out.println("设备描述: " + mixerInfo.getDescription());
            
            try {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                Line.Info[] lineInfos = mixer.getTargetLineInfo();
                
                // 打印该设备支持的格式
                for (Line.Info lineInfo : lineInfos) {
                    if (lineInfo instanceof DataLine.Info) {
                        AudioFormat[] formats = ((DataLine.Info) lineInfo).getFormats();
                        System.out.println("支持的格式:");
                        for (AudioFormat format : formats) {
                            System.out.println("  " + format.toString());
                        }
                    }
                }
                
                // BlackHole 设备匹配
                String deviceName = mixerInfo.getName().toLowerCase();
                if (deviceName.contains("blackhole")) {
                    System.out.println("\n找到匹配设备: " + mixerInfo.getName());
                    
                    // 尝试不同的音频格式
                    AudioFormat[] formatsToTry = {
                        // 尝试不同的格式组合
                        new AudioFormat(48000, 32, 2, true, true),  // 大端序
                        new AudioFormat(48000, 32, 2, true, false), // 小端序
                        new AudioFormat(48000, 16, 2, true, false), // 16位
                        new AudioFormat(44100, 16, 2, true, false), // 44.1kHz
                        new AudioFormat(44100, 32, 2, true, false)  // 44.1kHz, 32位
                    };
                    
                    for (AudioFormat format : formatsToTry) {
                        try {
                            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                            if (AudioSystem.isLineSupported(info)) {
                                System.out.println("支持的格式: " + format);
                                TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                                return line;
                            }
                        } catch (Exception e) {
                            System.out.println("格式不支持: " + format);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("尝试获取设备 " + mixerInfo.getName() + " 失败: " + e.getMessage());
            }
        }
        
        return null;
    }

    private static void captureAudio(TargetDataLine targetLine) {
        byte[] buffer = new byte[4096];
        ByteArrayOutputStream audioData = new ByteArrayOutputStream();
        int sampleCount = 0;
        
        while (true) {
            int count = targetLine.read(buffer, 0, buffer.length);
            if (count > 0) {
                audioData.write(buffer, 0, count);
                sampleCount += count / (2 * CHANNELS); // 16位 = 2字节

                // 累积约1秒的音频数据后进行识别
                if (sampleCount >= SAMPLE_RATE) {
                    byte[] audioChunk = audioData.toByteArray();
                    byte[] convertedAudio = convertAudioFormat(audioChunk);
                    
                    // 调试：保存音频文件
                    if (convertedAudio != null) {
                        saveAudioToFile(convertedAudio, "debug_audio.wav");
                    }
                    
                    audioData.reset();
                    sampleCount = 0;

                    if (convertedAudio != null) {
                        try {
                            String recognizedText = recognizeSpeech(convertedAudio);
                            if (recognizedText != null && !recognizedText.isEmpty()) {
                                System.out.println("识别结果: " + recognizedText);
                                updateSubtitleFile(recognizedText);
                            }
                        } catch (Exception e) {
                            System.err.println("处理音频时出错: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private static String recognizeSpeech(byte[] audioData) {
        try {
            // 配置语音识别请求
            SentenceRecognitionRequest req = new SentenceRecognitionRequest();
            req.setProjectId(0L);
            req.setSubServiceType(2L); // 粤语识别
            req.setEngSerViceType("16k_zh");
            req.setVoiceFormat("wav");  // 指定格式为 wav
            req.setSourceType(1L);
            req.setData(Base64.getEncoder().encodeToString(audioData));

            // 发送请求并获取结果
            SentenceRecognitionResponse resp = asrClient.SentenceRecognition(req);
            return resp.getResult();
        } catch (Exception e) {
            System.err.println("语音识别错误: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static void updateSubtitleFile(String text) {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                    new FileOutputStream(SUBTITLE_FILE), 
                    StandardCharsets.UTF_8))) {
            writer.write(text);
        } catch (IOException e) {
            System.err.println("更新字幕文件失败: " + e.getMessage());
        }
    }

    private static byte[] convertAudioFormat(byte[] sourceAudio) {
        try {
            // 创建源音频格式（48kHz，16位，立体声）
            AudioFormat sourceFormat = new AudioFormat(
                48000.0f,
                16,
                2,
                true,
                false
            );
            
            // 创建目标音频格式（16kHz，16位，单声道）
            AudioFormat targetFormat = new AudioFormat(
                16000.0f,
                16,
                1,
                true,
                false
            );
            
            // 计算音频帧数
            long frameLength = sourceAudio.length / sourceFormat.getFrameSize();
            
            // 创建音频输入流
            AudioInputStream sourceStream = new AudioInputStream(
                new ByteArrayInputStream(sourceAudio),
                sourceFormat,
                frameLength
            );
            
            // 转换音频格式
            AudioInputStream convertedStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
            
            // 计算转换后的帧数
            long convertedFrameLength = (long) (frameLength * targetFormat.getSampleRate() / sourceFormat.getSampleRate());
            
            // 创建一个新的音频输入流，指定帧长度
            AudioInputStream lengthSpecifiedStream = new AudioInputStream(
                convertedStream,
                targetFormat,
                convertedFrameLength
            );
            
            // 写入 WAV 格式
            ByteArrayOutputStream wavStream = new ByteArrayOutputStream();
            AudioSystem.write(lengthSpecifiedStream, AudioFileFormat.Type.WAVE, wavStream);
            
            // 调试信息
            byte[] result = wavStream.toByteArray();
            System.out.println("转换前音频大小: " + sourceAudio.length + " 字节");
            System.out.println("转换后音频大小: " + result.length + " 字节");
            
            return result;
        } catch (Exception e) {
            System.err.println("音频格式转换失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // 为了便于调试，添加音频保存功能
    private static void saveAudioToFile(byte[] audioData, String filename) {
        try {
            try (FileOutputStream fos = new FileOutputStream(filename)) {
                fos.write(audioData);
            }
            System.out.println("音频已保存到文件: " + filename);
        } catch (IOException e) {
            System.err.println("保存音频文件失败: " + e.getMessage());
        }
    }
}