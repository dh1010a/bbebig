package com.smilegate.bbebig.presentation.ui.home.mvi

import com.smilegate.bbebig.presentation.base.UiState
import com.smilegate.bbebig.presentation.ui.home.model.CategoryInfo
import com.smilegate.bbebig.presentation.ui.home.model.ChannelInfo
import com.smilegate.bbebig.presentation.ui.home.model.MemberInfo
import com.smilegate.bbebig.presentation.ui.home.model.MessageContent
import com.smilegate.bbebig.presentation.ui.home.model.Server
import com.smilegate.bbebig.presentation.ui.home.model.ServerInfo
import com.smilegate.bbebig.presentation.utils.ImmutableList

data class HomeUiState(
    val isLoading: Boolean,
    val isError: Boolean,
    val isChatServerConnected: Boolean,
    val isSheetVisible: Boolean,
    val isChatRoomVisible: Boolean,
    val myInfo: MemberInfo,
    val categoryMap: Map<Long?, List<CategoryInfo>>,
    val channelInfoList: ImmutableList<Pair<Long?, List<ChannelInfo>>>,
    val serverList: ImmutableList<Server>,
    val receiveChatMessageList: ImmutableList<MessageContent>,
    val senderUserInfo: MemberInfo,
    val selectedChannelInfo: ChannelInfo,
    val serverInfo: ServerInfo,
    val ownerId: Long,
    val serverId: Long,
    val serverImageUrl: String,
    val serverName: String,
) : UiState {
    companion object {
        fun initialize(): HomeUiState = HomeUiState(
            isLoading = false,
            isChatRoomVisible = false,
            isError = false,
            isSheetVisible = false,
            categoryMap = emptyMap(),
            channelInfoList = ImmutableList(),
            ownerId = 0,
            serverId = 0,
            serverImageUrl = "",
            serverList = ImmutableList(),
            serverName = "",
            selectedChannelInfo = ChannelInfo(
                categoryId = 0,
                channelId = 0,
                channelMemberIdList = emptyList(),
                channelName = "",
                channelType = "",
                position = 0,
                privateStatus = false,
            ),
            myInfo = MemberInfo(
                avatarUrl = "",
                bannerUrl = "",
                birthdate = "",
                customPresenceStatus = "",
                email = "",
                userId = 0,
                introduce = "",
                lastAccessAt = "",
                name = "",
                nickname = "",
                colorNumber = 0,
            ),
            isChatServerConnected = false,
            receiveChatMessageList = ImmutableList(),
            senderUserInfo = MemberInfo(
                avatarUrl = "",
                bannerUrl = "",
                birthdate = "",
                customPresenceStatus = "",
                email = "",
                userId = 0,
                introduce = "",
                lastAccessAt = "",
                name = "",
                nickname = "",
                colorNumber = 0,
            ),
            serverInfo = ServerInfo(
                categoryMap = emptyMap(),
                channelList = ImmutableList(),
                ownerId = 0,
                serverId = 0,
                serverImageUrl = "",
                serverName = "",
            ),
        )
    }
}
