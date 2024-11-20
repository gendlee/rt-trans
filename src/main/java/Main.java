// 引入相关库和SDK
import com.tencentcloudapi.asr.v20190614.AsrClient;
import com.tencentcloudapi.asr.v20190614.models.*;
import com.xfyun.client.TtsClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest; // 确保导入路径正确
import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class Main {
    private static final float SAMPLE_RATE = 16000;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;
    
    public static void main(String[] args) {
        // 1. 初始化腾讯语音识别客户端
        AsrClient asrClient = new AsrClient("your-secret-id", "your-secret-key"); // todo

        // 设置音频格式
        AudioFormat format = new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_IN_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
        );

        // 设置数据行信息
        DataLine.Info targetInfo = new DataLine.Info(
            TargetDataLine.class,
            format
        );

        try {
            // 获取所有可用的混音器
            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
            TargetDataLine targetLine = null;

            // 查找 VB-Audio Virtual Cable
            for (Mixer.Info mixerInfo : mixerInfos) {
                System.out.println("发现音频设备: " + mixerInfo.getName());
                if (mixerInfo.getName().contains("CABLE Output")) {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    try {
                        targetLine = (TargetDataLine) mixer.getLine(targetInfo);
                        break;
                    } catch (Exception e) {
                        System.err.println("无法获取该设备的数据线: " + e.getMessage());
                    }
                }
            }

            if (targetLine == null) {
                throw new RuntimeException("未找到 VB-Audio Virtual Cable 设备");
            }

            // 打开并启动数据线
            targetLine.open(format);
            targetLine.start();

            // 创建音频捕获线程
            TargetDataLine finalTargetLine = targetLine;
            Thread captureThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                ByteArrayOutputStream audioData = new ByteArrayOutputStream();
                boolean running = true;

                while (running) {
                    int count = finalTargetLine.read(buffer, 0, buffer.length);
                    if (count > 0) {
                        // 将捕获的音频数据添加到缓冲区
                        audioData.write(buffer, 0, count);

                        // 当累积了足够的数据时（例如，1秒的音频）
                        if (audioData.size() >= SAMPLE_RATE * 2) { // 16位=2字节
                            byte[] audioChunk = audioData.toByteArray();
                            // 重置缓冲区
                            audioData.reset();

                            // 发送音频数据到语音识别服务
                            try {
                                String cantoneseText = recognizeSpeech(audioChunk);
                                if (cantoneseText != null && !cantoneseText.isEmpty()) {
                                    // 翻译并播放
                                    String mandarinText = translateCantoneseToMandarin(cantoneseText);
                                    byte[] synthesizedAudio = synthesizeSpeech(mandarinText);
                                    playAudio(synthesizedAudio);
                                }
                            } catch (Exception e) {
                                System.err.println("处理音频时出错: " + e.getMessage());
                            }
                        }
                    }
                }
            });

            // 启动捕获线程
            captureThread.start();

            // 等待用户输入以停止程序
            System.out.println("按回车键停止程序...");
            System.in.read();

            // 清理资源
            targetLine.stop();
            targetLine.close();

        } catch (Exception e) {
            System.err.println("音频捕获错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 语音识别方法
    private static String recognizeSpeech(byte[] audioData) {
        // 使用腾讯云语音识别API
        try {
            // 配置请求
            SentenceRecognitionRequest req = new SentenceRecognitionRequest();
            req.setProjectId(0);
            req.setSubServiceType(2); // 粤语识别
            req.setEngSerViceType("16k_zh");
            req.setVoiceFormat("wav");
            req.setSourceType(1);
            req.setVoiceData(Base64.getEncoder().encodeToString(audioData));

            // 发送请求
            SentenceRecognitionResponse response = asrClient.SentenceRecognition(req);
            return response.getResult();
        } catch (Exception e) {
            System.err.println("语音识别错误: " + e.getMessage());
            return null;
        }
    }

    // 语音合成方法
    private static byte[] synthesizeSpeech(String text) {
        try {
            return ttsClient.synthesize(text, "xiaoyan");
        } catch (Exception e) {
            System.err.println("语音合成错误: " + e.getMessage());
            return null;
        }
    }

    private static String translateCantoneseToMandarin(String cantoneseText) {
        // 腾讯云翻译API的配置
        final String SECRET_ID = "你的SECRET_ID";  //todo
        final String SECRET_KEY = "你的SECRET_KEY"; // todo
        final String TRANS_API_HOST = "https://tmt.tencentcloudapi.com";

        try {
            // 准备请求参数
            String salt = String.valueOf(System.currentTimeMillis());
            // 生成签名
            String src = SECRET_ID + cantoneseText + salt + SECRET_KEY;
            String sign = MD5.md5(src);

            // 构建请求参数
            Map<String, String> params = new HashMap<>();
            params.put("q", cantoneseText);
            params.put("from", "yue");     // 粤语
            params.put("to", "zh");        // 普通话
            params.put("appid", SECRET_ID);
            params.put("salt", salt);
            params.put("sign", sign);

            // 发送HTTP请求
            String result = HttpUtil.post(TRANS_API_HOST, params);

            // 解析JSON响应
            JSONObject jsonObject = new JSONObject(result);
            JSONArray transResult = jsonObject.getJSONArray("trans_result");
            if (transResult != null && !transResult.isEmpty()) {
                return transResult.getJSONObject(0).getString("dst");
            }

            return cantoneseText; // 如果翻译失败，返回原文

        } catch (Exception e) {
            System.err.println("翻译过程出现错误: " + e.getMessage());
            return cantoneseText; // 发生错误时返回原文
        }
    }

    // MD5工具类
    private static class MD5 {
        public static String md5(String string) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] bytes = md.digest(string.getBytes("UTF-8"));
                return toHex(bytes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static String toHex(byte[] bytes) {
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        }
    }

    private static void playAudio(byte[] audio) {
        try {
            // 创建音频输入流
            AudioInputStream audioInputStream = new AudioInputStream(
                new ByteArrayInputStream(audio),
                new AudioFormat(16000, 16, 1, true, false),
                audio.length
            );

            // 获取音频剪辑
            DataLine.Info info = new DataLine.Info(Clip.class, audioInputStream.getFormat());
            Clip clip = (Clip) AudioSystem.getLine(info);

            // 打开音频剪辑并开始播放
            clip.open(audioInputStream);
            clip.start();

            // 等待音频播放完成
            while (!clip.isRunning())
                Thread.sleep(10);
            while (clip.isRunning())
                Thread.sleep(10);

            // 关闭音频剪辑
            clip.close();
        } catch (LineUnavailableException | IOException  | InterruptedException e) {
            System.err.println("播放音频时出现错误: " + e.getMessage());
        }
    }
}