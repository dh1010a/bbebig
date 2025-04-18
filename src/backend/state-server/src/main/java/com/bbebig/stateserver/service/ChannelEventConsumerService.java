package com.bbebig.stateserver.service;

import com.bbebig.commonmodule.kafka.dto.ChannelEventDto;
import com.bbebig.commonmodule.kafka.dto.model.ChannelEventType;
import com.bbebig.commonmodule.redis.domain.DeviceInfo;
import com.bbebig.commonmodule.redis.domain.MemberPresenceStatus;
import com.bbebig.stateserver.repository.MemberRedisRepositoryImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelEventConsumerService {

	private final MemberRedisRepositoryImpl memberRedisRepositoryImpl;

	@KafkaListener(topics = "${spring.kafka.topic.channel-event}", groupId = "${spring.kafka.consumer.group-id.channel-event}", containerFactory = "channelEventListener")
	public void consumeForChannelEvent(ChannelEventDto channelEventDto) {
		if (channelEventDto == null) {
			log.error("[State] ChannelEventConsumerService: 채널 이벤트 정보 없음");
			return;
		}

		if (channelEventDto.getType().equals(ChannelEventType.CHANNEL_ENTER)) {
			MemberPresenceStatus memberPresenceStatus = handleJoinEvent(channelEventDto);

		} else if (channelEventDto.getType().equals(ChannelEventType.CHANNEL_LEAVE)) {
			MemberPresenceStatus memberPresenceStatus = handleLeaveEvent(channelEventDto);
		} else {
			log.error("[State] ChannelEventConsumerService: 채널 이벤트 타입이 잘못되었습니다. channelEventDto: {}", channelEventDto);
			return;
		}
	}

	// 채널 참여 이벤트 처리
	private MemberPresenceStatus handleJoinEvent(ChannelEventDto channelEventDto) {
		MemberPresenceStatus memberPresenceStatus = memberRedisRepositoryImpl.getMemberPresenceStatus(channelEventDto.getMemberId());
		if (memberPresenceStatus == null) {
			// TODO : 추후 예외 처리 로직 추가
			log.error("[State] ChannelEventConsumerService: 채널 참여 이벤트 처리 도중 Redis에 상태 정보가 없어서 처리 실패. memberId: {}", channelEventDto.getMemberId());
			return null;
		}
		List<DeviceInfo> devices = memberPresenceStatus.getDevices();
		if (devices == null || devices.isEmpty() ||
				devices.stream().noneMatch(deviceInfo -> deviceInfo.getSocketSessionId().equals(channelEventDto.getSessionId()))) {
			// TODO : 추후 예외 처리 로직 추가
			log.error("[State] ChannelEventConsumerService: 채널 참여 이벤트 처리 도중 디바이스 정보가 없어서 처리 실패. memberId: {}", channelEventDto.getMemberId());
			return null;
		}

		devices.forEach(deviceInfo -> {
			if (deviceInfo.getSocketSessionId().equals(channelEventDto.getSessionId())) {
				deviceInfo.updateCurrent(channelEventDto.getChannelType(), channelEventDto.getChannelId(), channelEventDto.getServerId());
				deviceInfo.updateLastActiveTime(channelEventDto.getEventTime());
			}
		});

		memberRedisRepositoryImpl.saveMemberPresenceStatus(channelEventDto.getMemberId(), memberPresenceStatus);
		return memberPresenceStatus;
	}

	// 채널 퇴장 이벤트 처리
	private MemberPresenceStatus handleLeaveEvent(ChannelEventDto channelEventDto) {
		MemberPresenceStatus memberPresenceStatus = memberRedisRepositoryImpl.getMemberPresenceStatus(channelEventDto.getMemberId());
		if (memberPresenceStatus == null) {
			// TODO : 추후 예외 처리 로직 추가
			log.error("[State] ChannelEventConsumerService: 채널 퇴장 이벤트 처리 도중 Redis에 상태 정보가 없어서 처리 실패. memberId: {}", channelEventDto.getMemberId());
			return null;
		}
		List<DeviceInfo> devices = memberPresenceStatus.getDevices();
		if (devices == null || devices.isEmpty() ||
				devices.stream().noneMatch(deviceInfo -> deviceInfo.getSocketSessionId().equals(channelEventDto.getSessionId()))) {
			// TODO : 추후 예외 처리 로직 추가
			log.error("[State] ChannelEventConsumerService: 채널 퇴장 이벤트 처리 도중 디바이스 정보가 없어서 처리 실패. memberId: {}", channelEventDto.getMemberId());
			return null;
		}

		devices.forEach(deviceInfo -> {
			if (deviceInfo.getSocketSessionId().equals(channelEventDto.getSessionId())) {
				deviceInfo.updateCurrent(null, null, null);
				deviceInfo.updateLastActiveTime(channelEventDto.getEventTime());
			}
		});

		memberRedisRepositoryImpl.saveMemberPresenceStatus(channelEventDto.getMemberId(), memberPresenceStatus);

		// TODO : 채널 퇴장하지 않고 종료되었을때 이벤트 처리 (DISCONNECT) 등 고려하기

		return memberPresenceStatus;
	}
}
