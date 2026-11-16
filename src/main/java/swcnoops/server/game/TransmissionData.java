package swcnoops.server.game;

public class TransmissionData implements JoeData {
    private String uid;
    private String faction;
    private String startDate;
    private String endDate;
    private String titleText;
    private String bodyText;
    private String image;
    private String npc;
    private String btn1;
    private String btn1action;
    private String btn1data;
    private String transType;

    @Override
    public String getUid() {
        return uid;
    }

    public String getFaction() {
        return faction;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getImage() {
        return image;
    }

    public String getNpc() {
        return npc;
    }

    public String getBtn1() {
        return btn1;
    }

    public String getBtn1action() {
        return btn1action;
    }

    public String getBtn1data() {
        return btn1data;
    }

    public String getTransType() {
        return transType;
    }
}
