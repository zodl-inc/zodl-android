package co.electriccoin.zcash.ui.screen.voting

import co.electriccoin.zcash.configuration.model.map.Configuration
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigState
import co.electriccoin.zcash.ui.configuration.ConfigurationEntries

fun isDefaultVotingConfig(
    chainConfig: VotingChainConfigState,
    configuration: Configuration?
): Boolean =
    chainConfig.isOnDefaultConfig &&
        configuration
            ?.let(ConfigurationEntries.VOTING_CONFIG_URL::getValue)
            .orEmpty()
            .isBlank()
