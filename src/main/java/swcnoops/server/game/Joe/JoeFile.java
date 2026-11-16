package swcnoops.server.game.Joe;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.Id;
import swcnoops.server.game.*;

import java.util.Date;
import java.util.List;

public class JoeFile {
    @Id
    private String fileName;
    private boolean fileGenerated;
    private Date generatedDate;

    private Content content = new Content();

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Date getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(Date generatedDate) {
        this.generatedDate = generatedDate;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }

    public boolean isFileGenerated() {
        return fileGenerated;
    }

    public void setFileGenerated(boolean fileGenerated) {
        this.fileGenerated = fileGenerated;
    }

    static public class Content {
        private JoeObjects objects = new JoeObjects();

        public JoeObjects getObjects() {
            return objects;
        }

        public void setObjects(JoeObjects objects) {
            this.objects = objects;
        }
    }

    static public class JoeObjects {
        @JsonProperty("CampaignData")
        private List<CampaignData> campaignData;
        @JsonProperty("CampaignMissionData")
        private List<CampaignMissionData> campaignMissionData;
        @JsonProperty("TournamentData")
        private List<TournamentData> tournamentData;
        @JsonProperty("TournamentTierData")
        private List<TournamentTierData> tournamentTierData;
        @JsonProperty("Raid")
        private List<RaidData> raid;
        @JsonProperty("RaidMissionPool")
        private List<RaidMissionPoolData> raidMissionPool;

        @JsonProperty("ObjSeries")
        private List<ObjSeriesData> objSeries;

        @JsonProperty("ObjTable")
        private List<ObjTableData> objTable;

        @JsonProperty("Crate")
        private List<CrateItemData> crate;
        @JsonProperty("CrateSupply")
        private List<CrateSupplyData> crateSupply;
        @JsonProperty("CrateSupplyPool")
        private List<CrateSupplyPoolData> crateSupplyPool;
        @JsonProperty("CrateSupplyScale")
        private List<CrateSupplyScaleData> crateSupplyScale;
        @JsonProperty("ShardShopSeries")
        private List<ShardShopSeriesData> shardShopSeries;
        @JsonProperty("ShardShopPool")
        private List<ShardShopPoolData> shardShopPool;
        @JsonProperty("Cost")
        private List<CostData> cost;
        @JsonProperty("EquipmentData")
        private List<EquipmentData> equipmentData;
        @JsonProperty("CommandCenterEntries")
        private List<CommandCenterEntryData> commandCenterEntries;
        @JsonProperty("Transmissions")
        private List<TransmissionData> transmissions;
        @JsonProperty("LimitedEditionItemData")
        private List<LimitedEditionItemData> limitedEditionItemData;
        @JsonProperty("Perks")
        private List<PerkData> perks;
        @JsonProperty("PerkEffects")
        private List<PerkEffectData> perkEffects;
        @JsonProperty("SquadLevel")
        private List<SquadLevelData> squadLevel;
        @JsonProperty("EpisodeData")
        private List<EpisodeData> episodeData;
        @JsonProperty("EpisodeTask")
        private List<EpisodeTaskData> episodeTask;
        @JsonProperty("EpisodeTaskAction")
        private List<EpisodeTaskActionData> episodeTaskAction;
        @JsonProperty("WarBuffData")
        private List<WarBuffData> warBuffData;

        public List<CampaignData> getCampaignData() {
            return campaignData;
        }

        public void setCampaignData(List<CampaignData> campaignData) {
            this.campaignData = campaignData;
        }

        public List<CampaignMissionData> getCampaignMissionData() {
            return campaignMissionData;
        }

        public void setCampaignMissionData(List<CampaignMissionData> campaignMissionData) {
            this.campaignMissionData = campaignMissionData;
        }

        public List<TournamentData> getTournamentData() {
            return tournamentData;
        }

        public void setTournamentData(List<TournamentData> tournamentData) {
            this.tournamentData = tournamentData;
        }

        public List<TournamentTierData> getTournamentTierData() {
            return tournamentTierData;
        }

        public void setTournamentTierData(List<TournamentTierData> tournamentTierData) {
            this.tournamentTierData = tournamentTierData;
        }

        public List<RaidData> getRaid() {
            return raid;
        }

        public void setRaid(List<RaidData> raid) {
            this.raid = raid;
        }

        public List<RaidMissionPoolData> getRaidMissionPool() {
            return raidMissionPool;
        }

        public void setRaidMissionPool(List<RaidMissionPoolData> raidMissionPool) {
            this.raidMissionPool = raidMissionPool;
        }

        public List<ObjSeriesData> getObjSeries() {
            return objSeries;
        }

        public void setObjSeries(List<ObjSeriesData> objSeries) {
            this.objSeries = objSeries;
        }

        public List<ObjTableData> getObjTable() {
            return objTable;
        }

        public void setObjTable(List<ObjTableData> objTable) {
            this.objTable = objTable;
        }

        public List<CrateItemData> getCrate() { return crate; }
        public void setCrate(List<CrateItemData> crate) { this.crate = crate; }
        public List<CrateSupplyData> getCrateSupply() { return crateSupply; }
        public void setCrateSupply(List<CrateSupplyData> crateSupply) { this.crateSupply = crateSupply; }
        public List<CrateSupplyPoolData> getCrateSupplyPool() { return crateSupplyPool; }
        public void setCrateSupplyPool(List<CrateSupplyPoolData> crateSupplyPool) { this.crateSupplyPool = crateSupplyPool; }
        public List<CrateSupplyScaleData> getCrateSupplyScale() { return crateSupplyScale; }
        public void setCrateSupplyScale(List<CrateSupplyScaleData> crateSupplyScale) { this.crateSupplyScale = crateSupplyScale; }
        public List<ShardShopSeriesData> getShardShopSeries() { return shardShopSeries; }
        public void setShardShopSeries(List<ShardShopSeriesData> shardShopSeries) { this.shardShopSeries = shardShopSeries; }
        public List<ShardShopPoolData> getShardShopPool() { return shardShopPool; }
        public void setShardShopPool(List<ShardShopPoolData> shardShopPool) { this.shardShopPool = shardShopPool; }
        public List<CostData> getCost() { return cost; }
        public void setCost(List<CostData> cost) { this.cost = cost; }
        public List<EquipmentData> getEquipmentData() { return equipmentData; }
        public void setEquipmentData(List<EquipmentData> equipmentData) { this.equipmentData = equipmentData; }
        public List<CommandCenterEntryData> getCommandCenterEntries() { return commandCenterEntries; }
        public void setCommandCenterEntries(List<CommandCenterEntryData> commandCenterEntries) { this.commandCenterEntries = commandCenterEntries; }
        public List<TransmissionData> getTransmissions() { return transmissions; }
        public void setTransmissions(List<TransmissionData> transmissions) { this.transmissions = transmissions; }
        public List<LimitedEditionItemData> getLimitedEditionItemData() { return limitedEditionItemData; }
        public void setLimitedEditionItemData(List<LimitedEditionItemData> limitedEditionItemData) { this.limitedEditionItemData = limitedEditionItemData; }
        public List<PerkData> getPerks() { return perks; }
        public void setPerks(List<PerkData> perks) { this.perks = perks; }
        public List<PerkEffectData> getPerkEffects() { return perkEffects; }
        public void setPerkEffects(List<PerkEffectData> perkEffects) { this.perkEffects = perkEffects; }
        public List<SquadLevelData> getSquadLevel() { return squadLevel; }
        public void setSquadLevel(List<SquadLevelData> squadLevel) { this.squadLevel = squadLevel; }
        public List<EpisodeData> getEpisodeData() { return episodeData; }
        public void setEpisodeData(List<EpisodeData> episodeData) { this.episodeData = episodeData; }
        public List<EpisodeTaskData> getEpisodeTask() { return episodeTask; }
        public void setEpisodeTask(List<EpisodeTaskData> episodeTask) { this.episodeTask = episodeTask; }
        public List<EpisodeTaskActionData> getEpisodeTaskAction() { return episodeTaskAction; }
        public void setEpisodeTaskAction(List<EpisodeTaskActionData> episodeTaskAction) { this.episodeTaskAction = episodeTaskAction; }
        public List<WarBuffData> getWarBuffData() { return warBuffData; }
        public void setWarBuffData(List<WarBuffData> warBuffData) { this.warBuffData = warBuffData; }
    }
}
