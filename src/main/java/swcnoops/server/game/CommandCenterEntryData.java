package swcnoops.server.game;

import java.util.List;

public class CommandCenterEntryData implements JoeData {
    private String uid;
    private List<String> audienceConditions;
    private String startDate;
    private String endDate;
    private int layout;
    private int priority;
    private String image;
    private String titleText;
    private String bodyText;

    @Override
    public String getUid() {
        return uid;
    }

    public List<String> getAudienceConditions() {
        return audienceConditions;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public int getLayout() {
        return layout;
    }

    public int getPriority() {
        return priority;
    }

    public String getImage() {
        return image;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getBodyText() {
        return bodyText;
    }
}
