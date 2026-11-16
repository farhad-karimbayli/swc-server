package swcnoops.server.commands;

import swcnoops.server.commands.auth.GetAuthToken;
import swcnoops.server.commands.auth.preauth.AuthPreauthGeneratePlayerWithFacebook;
import swcnoops.server.commands.auth.preauth.GeneratePlayer;
import swcnoops.server.commands.config.ConfigEndpointsGet;
import swcnoops.server.commands.guild.*;
import swcnoops.server.commands.player.*;

import java.util.HashMap;
import java.util.Map;

public class CommandFactory {
    final private Map<String, CommandAction> commandMap = new HashMap<>();

    public CommandFactory() {
        this.add(new AuthPreauthGeneratePlayerWithFacebook());
        this.add(new GeneratePlayer());
        this.add(new GetAuthToken());
        this.add(new ConfigEndpointsGet());
        this.add(new PlayerLogin());
        this.add(new PlayerPvpStatus());
        this.add(new PlayerContentGet());
        this.add(new PlayerDeviceRegister());
        this.add(new PlayerPreferencesSet());
        this.add(new GuildInviteGet());
        this.add(new GuildInviteAction("guild.invite"));
        this.add(new GuildInviteAction("guild.invite.accept"));
        this.add(new GuildInviteAction("guild.invite.reject"));
        this.add(new GuildWarClaim());
        this.add(new PlayerAccountExternalGet());
        this.add(new PlayerAccountExternalRegister());
        this.add(new PlayerAccountRecover());
        this.add(new PlayerDeviceDeregister());
        this.add(new GuildGetChatKey());
        this.add(new GuildGet());
        this.add(new GuildWarStatus());
        this.add(new PlayerKeepAlive());
        this.add(new PlayerStoreOffersGet());
        this.add(new PlayerStoreShardOffersGet());
        this.add(new PlayerCrateCheckDaily());
        this.add(new PlayerMissionsMissionMap());
        this.add(new PlayerNameSet());
        this.add(new PlayerDeployableTrain());
        this.add(new PlayerDeployableCancel());
        this.add(new PlayerDeployableBuyout());
        this.add(new PlayerDeployableRemove());
        this.add(new PlayerDeployableSpend());
        this.add(new PlayerBuildingCapture());
        this.add(new PlayerBuildingBuyout());
        this.add(new PlayerDeployableUpgradeStart());

        this.add(new PlayerBuildingCancel());
        this.add(new PlayerBuildingMultimove());
        this.add(new PlayerBuildingCollect());
        this.add(new PlayerBuildingCollectAll());
        this.add(new PlayerBuildingConstruct());
        this.add(new PlayerBuildingRearm());
        this.add(new PlayerBuildingUpgrade());
        this.add(new PlayerBuildingClear());
        this.add(new PlayerBuildingMove());
        this.add(new PlayerBuildingInstantUpgrade());
        this.add(new PlayerBuildingUpgradeAll());
        this.add(new PlayerBuildingSwap());

        this.add(new PlayerPveStart());
        this.add(new PlayerPvpBattleStart());
        this.add(new PlayerPvpGetRevengeTarget());
        this.add(new PlayerPveComplete());
        this.add(new PlayerPveCollect());
        this.add(new PlayerMissionsClaimMission());

        this.add(new PlayerFactionSet());

        this.add(new GuildWarMatchmakingStart());
        this.add(new GuildWarMatchmakingCancel());
        this.add(new GuildListOpen());
        this.add(new GuildGetPublic());
        this.add(new GuildJoin());
        this.add(new GuildLeave());
        this.add(new GuildEject());
        this.add(new GuildCreate());
        this.add(new GuildEdit());
        this.add(new GuildTroopsRequest());
        this.add(new GuildTroopsDonate());
        this.add(new GuildNotificationsGet());
        this.add(new GuildPromote());
        this.add(new GuildDemote());
        this.add(new GuildJoinRequest());
        this.add(new GuildJoinAccept());
        this.add(new GuildJoinReject());
        this.add(new GuildSearchByName());
        this.add(new GuildWarBaseSave());
        this.add(new GuildWarTroopsDonate());
        this.add(new GuildWarGetBaseStatus());
        this.add(new GuildWarAttackPlayerStart());
        this.add(new GuildWarDeployableSpend());
        this.add(new GuildWarAttackPlayerComplete());
        this.add(new GuildWarAttackBaseStart());
        this.add(new GuildWarAttackBaseComplete());
        this.add(new GuildBattleShare());

        this.add(new PlayerBattleReplayGet());

        this.add(new GuildWarGetParticipant());
        this.add(new GuildWarGetSyncedParticipant());
        this.add(new GuildWarScoutPlayer());
        this.add(new GuildWarTroopsRequest());

        this.add(new PlayerMissionsActivateMission());
        this.add(new PlayerMissionsClaimCampaign());
        this.add(new PlayerMissionsAction("player.missions.showIntro"));
        this.add(new PlayerMissionsAction("player.missions.startSpecop"));

        this.add(new PlayerFueComplete());
        this.add(new PlayerFueSetQuest());
        this.add(new PlayerPvpGetNextTarget());
        this.add(new PlayerPvpReleaseTarget());
        this.add(new PlayerPvpBattleComplete());
        this.add(new PlayerEpisodesProgressGet());
        this.add(new PlayerIdentityGet());
        this.add(new PlayerIdentitySwitch());
        this.add(new PlayerStoreBuy());
        this.add(new PlayerStoreExtrasAction("player.campaignStore.buy"));
        this.add(new PlayerStoreExtrasAction("player.store.offers.buy"));
        this.add(new PlayerStoreExtrasAction("player.store.offers.reserve"));
        this.add(new PlayerStoreExtrasAction("player.store.offers.trigger"));
        this.add(new PlayerStoreExtrasAction("player.store.tournament.buy"));
        this.add(new PlayerStoreExtrasAction("player.store.multibuy"));
        this.add(new PlayerStoreExtrasAction("player.award.rateMyApp"));

        this.add(new GuildLeaderboardGetLeaders());
        this.add(new PlayerLeaderboardGetLeaders());
        this.add(new PlayerLeaderboardGetForFriends());

        this.add(new PlayerPlanetObjective());
        this.add(new PlayerObjectiveForceUpdate());
        this.add(new PlayerObjectiveClaim());
        this.add(new PlayerPlanetRelocate());

        this.add(new PlayerStoreCrateBuy());
        this.add(new PlayerCrateOpen());
        this.add(new PlayerCrateAward());

        this.add(new PlayerNeighborVisit());
        this.add(new PlayerLeaderboardTournamentGetLeaders());
        this.add(new PlayerLeaderboardTournamentGetRanks());
        this.add(new PlayerLeaderboardTournamentGetRank());
        this.add(new PlayerStoreTournamentRedeem());

        this.add(new PlayerHolonetGetCommandCenterEntry());
        this.add(new PlayerHolonetGetEventMessage());
        this.add(new PlayerHolonetClaimReward());

        this.add(new PlayerPlanetStats());

        this.add(new PlayerEquipmentActivate());
        this.add(new PlayerEquipmentDeactivate());
        this.add(new PlayerEquipmentUpgradeStart());

        this.add(new PlayerEpisodesTaskAction("player.episodes.tasks.intro.viewed"));
        this.add(new PlayerEpisodesTaskAction("player.episodes.tasks.timegate.start"));
        this.add(new PlayerEpisodesTaskAction("player.episodes.tasks.timegate.skip"));
        this.add(new PlayerEpisodesTaskAction("player.episodes.tasks.progress.complete"));
        this.add(new PlayerEpisodesTaskAction("player.episodes.tasks.progress.skip"));
        this.add(new PlayerEpisodesTaskAction("player.episodes.tasks.claim"));

        this.add(new PlayerRaidsUpdate());
        this.add(new PlayerRaidsStart());
        this.add(new PlayerRaidsComplete());

        this.add(new PlayerPerksAction("player.perks.activate"));
        this.add(new PlayerPerksAction("player.perks.cancel"));
        this.add(new PlayerPerksAction("player.perks.invest"));
        this.add(new PlayerPerksAction("player.perks.skipCooldown"));
        this.add(new PlayerStoreShardOffersBuy());
    }

    private void add(CommandAction commandAction) {
        commandMap.put(commandAction.getAction(), commandAction);
    }

    public CommandAction get(String action) {
        return this.commandMap.get(action);
    }
}
