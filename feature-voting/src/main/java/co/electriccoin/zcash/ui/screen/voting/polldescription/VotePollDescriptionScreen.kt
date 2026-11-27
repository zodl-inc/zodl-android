package co.electriccoin.zcash.ui.screen.voting.polldescription

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.ExternalUrl
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VotePollDescriptionScreen(args: VotePollDescriptionArgs) {
    val navigationRouter = koinInject<NavigationRouter>()
    VotePollDescriptionView(
        state =
            remember(args) {
                VotePollDescriptionState(
                    title = stringRes(args.title),
                    description = stringRes(args.description),
                    discussionUrl = args.discussionUrl,
                    onDiscussionClick = {
                        args.discussionUrl?.let { navigationRouter.forward(ExternalUrl(it)) }
                    },
                    onBack = navigationRouter::back,
                )
            }
    )
}

@Serializable
data class VotePollDescriptionArgs(
    val title: String,
    val description: String,
    val discussionUrl: String?,
)
