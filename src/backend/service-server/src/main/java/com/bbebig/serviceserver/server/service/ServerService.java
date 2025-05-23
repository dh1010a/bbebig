package com.bbebig.serviceserver.server.service;

import com.bbebig.commonmodule.clientDto.SearchFeignResponseDto.ServerChannelSequenceResponseDto;
import com.bbebig.commonmodule.clientDto.ServiceFeignResponseDto.*;
import com.bbebig.commonmodule.clientDto.StateFeignResponseDto.ServerMemberPresenceResponseDto;
import com.bbebig.commonmodule.clientDto.UserFeignResponseDto.*;
import com.bbebig.commonmodule.global.response.code.error.ErrorStatus;
import com.bbebig.commonmodule.global.response.exception.ErrorHandler;
import com.bbebig.commonmodule.kafka.dto.serverEvent.ServerActionEventDto;
import com.bbebig.commonmodule.kafka.dto.serverEvent.ServerEventType;
import com.bbebig.commonmodule.kafka.dto.serverEvent.ServerMemberActionEventDto;
import com.bbebig.commonmodule.kafka.dto.serverEvent.status.ServerActionStatus;
import com.bbebig.commonmodule.kafka.dto.serverEvent.status.ServerMemberActionStatus;
import com.bbebig.commonmodule.redis.domain.ChannelLastInfo;
import com.bbebig.commonmodule.redis.domain.ServerLastInfo;
import com.bbebig.commonmodule.redis.domain.ServerMemberStatus;
import com.bbebig.serviceserver.category.entity.Category;
import com.bbebig.serviceserver.category.repository.CategoryRepository;
import com.bbebig.serviceserver.channel.entity.Channel;
import com.bbebig.serviceserver.channel.entity.ChannelMember;
import com.bbebig.serviceserver.channel.entity.ChannelType;
import com.bbebig.serviceserver.channel.repository.ChannelMemberRepository;
import com.bbebig.serviceserver.channel.repository.ChannelRepository;
import com.bbebig.serviceserver.global.feign.client.MemberClient;
import com.bbebig.serviceserver.global.feign.client.SearchClient;
import com.bbebig.serviceserver.global.feign.client.StateClient;
import com.bbebig.serviceserver.global.kafka.KafkaProducerService;
import com.bbebig.serviceserver.server.dto.request.ServerCreateRequestDto;
import com.bbebig.serviceserver.server.dto.request.ServerUpdateRequestDto;
import com.bbebig.serviceserver.server.dto.response.*;
import com.bbebig.serviceserver.server.dto.response.ServerReadResponseDto.ServerMemberInfo;
import com.bbebig.serviceserver.server.dto.response.ServerReadResponseDto.ServerMemberInfoResponseDto;
import com.bbebig.serviceserver.server.entity.RoleType;
import com.bbebig.serviceserver.server.entity.Server;
import com.bbebig.serviceserver.server.entity.ServerMember;
import com.bbebig.serviceserver.server.repository.MemberRedisRepositoryImpl;
import com.bbebig.serviceserver.server.repository.ServerMemberRepository;
import com.bbebig.serviceserver.server.repository.ServerRedisRepositoryImpl;
import com.bbebig.serviceserver.server.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.bbebig.serviceserver.server.dto.response.ServerReadResponseDto.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServerService {

    private final ServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final CategoryRepository categoryRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;

    private final ServerRedisRepositoryImpl serverRedisRepository;
    private final MemberRedisRepositoryImpl memberRedisRepository;
    private final KafkaProducerService kafkaProducerService;

    private final MemberClient memberClient;
    private final StateClient stateClient;
    private final SearchClient searchClient;
    private final ServerRedisRepositoryImpl serverRedisRepositoryImpl;


    /**
     * 서버 생성
     */
    @Transactional
    public ServerCreateResponseDto createServer(Long memberId, ServerCreateRequestDto serverCreateRequestDto) {
        Server server = Server.builder()
                .name(serverCreateRequestDto.getServerName())
                .ownerId(memberId)
                .serverImageUrl(serverCreateRequestDto.getServerImageUrl())
                .build();


        MemberInfoResponseDto memberInfo = memberClient.getMemberInfo(memberId);
        // 개발용 로그
        log.info("[Service] ServerService : 채널 생성시 memberInfo: {}", memberInfo);
        ServerMember serverMember = ServerMember.builder()
                .server(server)
                .memberId(memberId)
                .memberNickname(memberInfo.getNickname())
                .memberAvatarImageUrl(memberInfo.getAvatarUrl())
                .memberBannerImageUrl(memberInfo.getBannerUrl())
                .roleType(RoleType.OWNER)
                .build();

        Category chatCategory = Category.builder()
                .server(server)
                .name("채팅 채널")
                .position(1)
                .build();

        Channel chatChannel = Channel.builder()
                .server(server)
                .category(chatCategory)
                .name("일반")
                .position(1)
                .channelType(ChannelType.CHAT)
                .privateStatus(false)
                .build();

        ChannelMember chatChannelMember = ChannelMember.builder()
                .channel(chatChannel)
                .serverMember(serverMember)
                .build();

        Category streamCategory = Category.builder()
                .server(server)
                .name("음성 채널")
                .position(2)
                .build();

        Channel streamChannel = Channel.builder()
                .server(server)
                .category(streamCategory)
                .name("일반")
                .position(1)
                .channelType(ChannelType.VOICE)
                .privateStatus(false)
                .build();

        ChannelMember streamChannelMember = ChannelMember.builder()
                .channel(streamChannel)
                .serverMember(serverMember)
                .build();

        serverRepository.save(server);
        serverMemberRepository.save(serverMember);
        categoryRepository.save(chatCategory);
        categoryRepository.save(streamCategory);
        channelRepository.save(chatChannel);
        channelMemberRepository.save(chatChannelMember);
        channelRepository.save(streamChannel);
        channelMemberRepository.save(streamChannelMember);

        makeServerChannelListCache(server.getId());
        makeServerMemberListCache(server.getId());
        // 방장이 참여하고 있는 서버 목록 캐시 데이터에 추가
        memberRedisRepository.addMemberServerToSet(memberId, server.getId());

        // Kafka로 데이터 발행
        ServerActionEventDto serverActionEventDto = ServerActionEventDto.builder()
                .serverId(server.getId())
                .type(ServerEventType.SERVER_ACTION)
                .serverName(server.getName())
                .profileImageUrl(server.getServerImageUrl())
                .status(ServerActionStatus.CREATE)
                .build();
        kafkaProducerService.sendServerEvent(serverActionEventDto);

        return ServerCreateResponseDto.convertToServerCreateResponseDto(server);
    }

    /**
     * 서버 정보 조회
     */
    @Transactional(readOnly = true)
    public ServerReadResponseDto readServer(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ErrorHandler(ErrorStatus.SERVER_NOT_FOUND));

        List<Channel> channels = channelRepository.findAllByServerId(serverId);
        List<Category> categories = categoryRepository.findAllByServerId(serverId);
        List<ChannelInfo> channelInfoList = new ArrayList<>();
        for (Channel channel : channels) {
            List<ChannelMember> channelMemberList = channelMemberRepository.findAllByChannelId(channel.getId());
            Long sequence = serverRedisRepositoryImpl.getServerChannelSequence(channel.getId());
            if (sequence == null) {
                ServerChannelSequenceResponseDto channelLastSequence = searchClient.getChannelLastSequence(channel.getId());
                sequence = channelLastSequence.getLastSequence();
            }
            // TODO : 추후에 채널별 마지막으로 발행된 시퀀스 번호를 주기적으로 DB에 저장하는 로직 구현
            channel.updateLastSequence(sequence);
            channelRepository.save(channel);

            channelInfoList.add(convertToChannelInfo(channel,
                    channelMemberList.stream().map(channelMember -> channelMember.getServerMember().getMemberId()).toList(), sequence));
        }
        return convertToServerReadResponseDto(server, categories, channelInfoList);
    }

    /**
     * 멤버가 속한 서버 목록 조회
     */
    @Transactional(readOnly = true)
    public ServerListReadResponseDto readServerList(Long memberId) {
        List<ServerMember> serverMembers = serverMemberRepository.findAllByMemberId(memberId);

        List<Server> servers = serverMembers.stream()
                .map(ServerMember::getServer)
                .toList();

        return ServerListReadResponseDto.convertToServerListReadResponseDto(servers);
    }

    /**
     * 서버 업데이트
     */
    @Transactional
    public ServerUpdateResponseDto updateServer(Long memberId, Long serverId, ServerUpdateRequestDto serverUpdateRequestDto) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ErrorHandler(ErrorStatus.SERVER_NOT_FOUND));

        // 서버장 권한 체크
        checkServerOwner(memberId, server);

        server.update(serverUpdateRequestDto);

        // Kafka로 데이터 발행
        ServerActionEventDto serverActionEventDto = ServerActionEventDto.builder()
                .serverId(server.getId())
                .type(ServerEventType.SERVER_ACTION)
                .serverName(server.getName())
                .profileImageUrl(server.getServerImageUrl())
                .status(ServerActionStatus.UPDATE)
                .build();
        kafkaProducerService.sendServerEvent(serverActionEventDto);

        return ServerUpdateResponseDto.convertToServerUpdateResponseDto(server);
    }

    /**
     * 서버 삭제
     */
    @Transactional
    public ServerDeleteResponseDto deleteServer(Long memberId, Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ErrorHandler(ErrorStatus.SERVER_NOT_FOUND));

        // 서버장 권한 체크
        checkServerOwner(memberId, server);

        // 서버 관련 삭제
        serverMemberRepository.deleteAllByServerId(serverId);
        channelRepository.findAllByServerId(serverId)
                .stream()
                .map(Channel::getId)
                .toList()
                .forEach(channelId -> {
                    channelMemberRepository.deleteAllByChannelId(channelId);
                    channelRepository.deleteById(channelId);
                });
        categoryRepository.deleteAllByServerId(serverId);
        serverRepository.delete(server);

        // Redis 캐시 제거
        deleteAllServerRelatedCache(serverId);

        // Kafka로 데이터 발행
        ServerActionEventDto serverActionEventDto = ServerActionEventDto.builder()
                .serverId(server.getId())
                .type(ServerEventType.SERVER_ACTION)
                .serverName(server.getName())
                .profileImageUrl(server.getServerImageUrl())
                .status(ServerActionStatus.DELETE)
                .build();
        kafkaProducerService.sendServerEvent(serverActionEventDto);

        return ServerDeleteResponseDto.convertToServerDeleteResponseDto(server);
    }

    /**
     * 서버에 속해있는 채널 목록 조회
     * FeignClient 를 통해 호출
     * 만약 Redis 에 캐싱된 데이터가 없다면 캐싱하는 로직을 포함
     */
    @Transactional
    public ServerChannelListResponseDto getServerChannelList(Long serverId) {
        serverRepository.findById(serverId)
                .orElseThrow(() -> new ErrorHandler(ErrorStatus.SERVER_NOT_FOUND));

        Set<Long> serverChannelList = serverRedisRepository.getServerChannelList(serverId);
        if (serverChannelList.isEmpty()) {
            List<Long> channelList = makeServerChannelListCache(serverId);
            serverChannelList.addAll(channelList);
        }
        return ServerChannelListResponseDto.builder()
                .serverId(serverId)
                .channelIdList(serverChannelList.stream().toList())
                .build();
    }

    /**
     * 서버에 속해있는 멤버 목록을 조회
     * FeignClient 를 통해 호출
     * 만약 Redis 에 캐싱된 데이터가 없다면 캐싱하는 로직을 포함
     */
    @Transactional
    public ServerMemberListResponseDto getServerMemberIdList(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ErrorHandler(ErrorStatus.SERVER_NOT_FOUND));

        Set<Long> serverMemberList = serverRedisRepository.getServerMemberList(serverId);
        if (serverMemberList.isEmpty()) {
            List<Long> channelList = makeServerMemberListCache(serverId);
            serverMemberList.addAll(channelList);
        }
        return ServerMemberListResponseDto.builder()
                .serverId(serverId)
                .ownerId(server.getOwnerId())
                .memberIdList(serverMemberList.stream().toList())
                .build();
    }

    // 서버 멤버 정보 조회
    // GET /servers/{serverId}/members
    public ServerMemberInfoResponseDto getServerMemberInfo(Long serverId) {
        List<ServerMember> serverMembers = serverMemberRepository.findAllByServerId(serverId);
        if (serverMembers.isEmpty()) {
            throw new ErrorHandler(ErrorStatus.SERVER_MEMBERS_NOT_FOUND);
        }

        List<ServerMemberStatus> serverMemberStatus = serverRedisRepository.getAllServerMemberStatus(serverId);

        if (serverMemberStatus.isEmpty()) {
            ServerMemberPresenceResponseDto responseDto = stateClient.checkServerMemberState(serverId);
            serverMemberStatus = responseDto.getMemberPresenceStatusList().stream()
                    .map(memberPresence -> ServerMemberStatus.builder()
                            .memberId(memberPresence.getMemberId())
                            .globalStatus(memberPresence.getGlobalStatus())
                            .actualStatus(memberPresence.getActualStatus())
                            .globalStatus(memberPresence.getGlobalStatus())
                            .build())
                    .toList();
        }

        Map<Long, ServerMemberStatus> statusMap = serverMemberStatus.stream()
                .collect(Collectors.toMap(ServerMemberStatus::getMemberId, status -> status));

        List<ServerMemberInfo> serverMemberInfoList = serverMembers.stream()
                .map(member -> {
                    ServerMemberStatus status = statusMap.get(member.getMemberId());

                    return convertToServerMemberInfo(member, status);
                })
                .collect(Collectors.toList());

        return ServerMemberInfoResponseDto.builder()
                .serverId(serverId)
                .serverMemberInfoList(serverMemberInfoList)
                .build();
    }

    /**
     * 멤버별로 참여하고 있는 서버 목록 조회
     * FeignClient 를 통해 호출
     * 만약 Redis 에 캐싱된 데이터가 없다면 캐싱하는 로직을 포함
     */
    @Transactional
    public MemberServerListResponseDto getMemberServerList(Long memberId) {
        Set<Long> memberServerList = memberRedisRepository.getMemberServerList(memberId);
        if (memberServerList.isEmpty()) {
            List<Long> channelList = makeMemberServerListCache(memberId);
            memberServerList.addAll(channelList);
        }
        return MemberServerListResponseDto.builder()
                .memberId(memberId)
                .serverIdList(memberServerList.stream().toList())
                .build();
    }

    // 서버 별 채널 마지막 방문 정보 조회
    // GET /servers/{serverId}/channels/info/member/{memberId}
    @Transactional
    public ServerLastInfoResponseDto getServerChannelLastInfoForApi(Long memberId, Long serverId) {
        ServerLastInfo lastInfo = getServerLastInfo(memberId, serverId);
        Map<Long, ChannelLastInfo> channelLastInfoMap = lastInfo.getChannelLastInfoMap();
        return ServerLastInfoResponseDto.builder()
                .serverId(serverId)
                .channelInfoList(
                        channelLastInfoMap.entrySet().stream()
                                .map(entry -> ChannelLastInfoResponseDto.builder()
                                        .channelId(entry.getKey())
                                        .lastReadMessageId(entry.getValue().getLastReadMessageId())
                                        .lastReadSequence(entry.getValue().getLastReadSequence())
                                        .lastAccessAt(entry.getValue().getLastAccessAt())
                                        .build()
                                )
                                .toList()
                )
                .build();
    }

    public ServerLastInfo getServerLastInfo(Long memberId, Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ErrorHandler(ErrorStatus.SERVER_NOT_FOUND));

        ServerMember serverMember = serverMemberRepository.findByMemberIdAndServer(memberId, server)
                .orElseThrow(() -> new ErrorHandler(ErrorStatus.SERVER_MEMBERS_NOT_FOUND));

        log.info("[Service] ServerService : 캐싱된 서버 마지막 정보: {}", memberRedisRepository.existsServerLastInfo(memberId, serverId));
        if (memberRedisRepository.existsServerLastInfo(memberId, serverId)) {
            return memberRedisRepository.getServerLastInfo(memberId, serverId);
        }

        ServerChannelListResponseDto serverChannelList = getServerChannelList(serverId);
        List<Long> channelIdList = serverChannelList.getChannelIdList();

       Map<Long, ChannelLastInfo> result = new HashMap<>();

        for (Long channelId : channelIdList) {
            result.put(channelId, getChannelLastInfo(channelId, serverMember.getId()));
        }
        ServerLastInfo lastInfo = ServerLastInfo.builder()
                .serverId(serverId)
                .channelLastInfoMap(result)
                .build();

        // TODO: 주기적으로 Redis에 저장된 데이터를 PostGreSQL에 저장하는 로직 추가
        memberRedisRepository.saveServerLastInfo(memberId, serverId, lastInfo);

        return lastInfo;
    }

    private ChannelLastInfo getChannelLastInfo(Long channelId, Long serverMemberId) {
        ChannelMember channelMember = channelMemberRepository.findByServerMemberIdAndChannelId(serverMemberId, channelId)
                .orElseThrow(() -> new ErrorHandler(ErrorStatus.CHANNEL_MEMBER_NOT_FOUND));

        return ChannelLastInfo.builder()
                .channelId(channelId)
                .lastReadMessageId(channelMember.getLastReadMessageId() == null ? 0 : channelMember.getLastReadMessageId())
                .lastReadSequence(channelMember.getLastReadSequence() == null ? 0 : channelMember.getLastReadSequence())
                .lastAccessAt(channelMember.getLastAccessAt() == null ? channelMember.getCreatedAt() : channelMember.getLastAccessAt())
                .build();
    }

    /**
     * 서버 참여하기
     */
    public ServerParticipateResponseDto participateServer(Long memberId, Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ErrorHandler(ErrorStatus.SERVER_NOT_FOUND));

        // 이미 서버에 참여 중인 경우
        if (serverMemberRepository.existsByServerIdAndMemberId(serverId, memberId)) {
            throw new ErrorHandler(ErrorStatus.SERVER_MEMBER_ALREADY_EXIST);
        }

        MemberInfoResponseDto memberInfo = memberClient.getMemberInfo(memberId);

        // 서버의 멤버 저장
        ServerMember serverMember = ServerMember.builder()
                .server(server)
                .memberId(memberId)
                .memberNickname(memberInfo.getNickname())
                .memberAvatarImageUrl(memberInfo.getAvatarUrl())
                .memberBannerImageUrl(memberInfo.getBannerUrl())
                .roleType(RoleType.MEMBER)
                .build();
        serverMemberRepository.save(serverMember);

        // 채널의 멤버 저장
        List<ChannelMember> channelMembers = channelRepository.findAllByServerIdAndPrivateStatusFalse(serverId)
                .stream()
                .map(channel -> ChannelMember.builder()
                        .channel(channel)
                        .serverMember(serverMember)
                        .build()
                )
                .toList();
        channelMemberRepository.saveAll(channelMembers);

        // Redis 캐싱
        memberRedisRepository.addMemberServerToSet(memberId, serverId);
        serverRedisRepository.addServerMemberToSet(serverId, memberId);

        // 카프카 이벤트 발행
        ServerMemberActionEventDto serverMemberActionEventDto = ServerMemberActionEventDto.builder()
                .serverId(serverId)
                .type(ServerEventType.SERVER_MEMBER_ACTION)
                .memberId(memberId)
                .nickname(memberInfo.getNickname())
                .avatarUrl(memberInfo.getAvatarUrl())
                .bannerUrl(memberInfo.getBannerUrl())
                .status(ServerMemberActionStatus.JOIN)
                .build();
        kafkaProducerService.sendServerEvent(serverMemberActionEventDto);

        return ServerParticipateResponseDto.convertToServerParticipateResponseDto(server);
    }

    /**
     * 서버 탈퇴하기
     */
    public ServerWithdrawResponseDto withdrawServer(Long memberId, Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ErrorHandler(ErrorStatus.SERVER_NOT_FOUND));

        ServerMember serverMember = serverMemberRepository.findByMemberIdAndServer(memberId, server)
                .orElseThrow(() -> new ErrorHandler(ErrorStatus.SERVER_MEMBERS_NOT_FOUND));

        // 서버 관련 삭제
        channelMemberRepository.deleteAllByServerMember(serverMember);
        serverMemberRepository.delete(serverMember);

        // Redis 캐싱
        memberRedisRepository.removeMemberServerFromSet(memberId, serverId);
        serverRedisRepository.removeServerMemberFromSet(serverId, memberId);

        // 카프카 이벤트 발행
        ServerMemberActionEventDto serverMemberActionEventDto = ServerMemberActionEventDto.builder()
                .serverId(serverId)
                .type(ServerEventType.SERVER_MEMBER_ACTION)
                .memberId(memberId)
                .nickname(serverMember.getMemberNickname())
                .status(ServerMemberActionStatus.LEAVE)
                .build();
        kafkaProducerService.sendServerEvent(serverMemberActionEventDto);

        return ServerWithdrawResponseDto.convertToServerWithdrawResponseDto(server);
    }

    /**
     * 서버에 속해있는 채널 목록을 조회하여 Redis 에 캐싱
     */
    @Transactional
    public List<Long> makeServerChannelListCache(Long serverId) {
        List<Channel> channels = channelRepository.findAllByServerId(serverId);
        if (channels.isEmpty()) {
            throw new ErrorHandler(ErrorStatus.SERVER_CHANNELS_NOT_FOUND);
        }
        List<Long> channelIdList = channels.stream().map(Channel::getId).toList();
        if (!channelIdList.isEmpty()) {
            serverRedisRepository.saveServerChannelSet(serverId, channelIdList);
        }
        return channelIdList;
    }

    /**
     * 서버에 속해있는 멤버 목록을 조회하여 Redis 에 캐싱
     */
    @Transactional
    public List<Long> makeServerMemberListCache(Long serverId) {
        List<ServerMember> serverMembers = serverMemberRepository.findAllByServerId(serverId);
        if (serverMembers.isEmpty()) {
            throw new ErrorHandler(ErrorStatus.SERVER_MEMBERS_NOT_FOUND);
        }
        List<Long> memberIdList = serverMembers.stream().map(ServerMember::getMemberId).toList();
        if (!memberIdList.isEmpty()) {
            serverRedisRepository.saveServerMemberSet(serverId, memberIdList);
        }
        return memberIdList;
    }

    /**
     * 멤버가 참여중인 서버 목록을 조회하여 Redis 에 캐싱
     */
    @Transactional
    public List<Long> makeMemberServerListCache(Long memberId) {
        List<ServerMember> serverMembers = serverMemberRepository.findAllByMemberId(memberId);
        if (serverMembers.isEmpty()) {
            throw new ErrorHandler(ErrorStatus.MEMBER_PARTICIPATE_SERVER_NOT_FOUND);
        }
        List<Long> serverIdList = serverMembers.stream().map(serverMember -> serverMember.getServer().getId()).toList();
        if (!serverIdList.isEmpty()) {
            memberRedisRepository.saveMemberServerSet(memberId, serverIdList);
        }
        return serverIdList;
    }

    // 서버 삭제 시 관련된 캐시 삭제
    private void deleteAllServerRelatedCache(Long serverId) {
        serverRedisRepository.getServerMemberList(serverId).forEach(memberIdInServer ->
                memberRedisRepository.removeMemberServerFromSet(memberIdInServer, serverId));
        serverRedisRepository.deleteServerChannelList(serverId);
        serverRedisRepository.deleteServerMemberList(serverId);
    }

    // 서버장 권한 체크
    private void checkServerOwner(Long memberId, Server server) {
        if (!server.getOwnerId().equals(memberId)) {
            throw new ErrorHandler(ErrorStatus.SERVER_OWNER_FORBIDDEN);
        }
    }
}
