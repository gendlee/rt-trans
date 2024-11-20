public class SentenceRecognitionRequest {
    int projectId;
    int subServiceType; // 粤语识别
    String engSerViceType;
    String voiceFormat;
    int sourceType;
    String voiceData;

    public String getVoiceData() {
        return voiceData;
    }

    public void setVoiceData(String voiceData) {
        this.voiceData = voiceData;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public int getSubServiceType() {
        return subServiceType;
    }

    public void setSubServiceType(int subServiceType) {
        this.subServiceType = subServiceType;
    }

    public String getEngSerViceType() {
        return engSerViceType;
    }

    public void setEngSerViceType(String engSerViceType) {
        this.engSerViceType = engSerViceType;
    }

    public String getVoiceFormat() {
        return voiceFormat;
    }

    public void setVoiceFormat(String voiceFormat) {
        this.voiceFormat = voiceFormat;
    }

    public int getSourceType() {
        return sourceType;
    }

    public void setSourceType(int sourceType) {
        this.sourceType = sourceType;
    }
}
