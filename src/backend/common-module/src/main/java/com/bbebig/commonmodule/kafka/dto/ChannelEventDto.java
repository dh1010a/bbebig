package com.bbebig.commonmodule.kafka.dto;

import com.bbebig.commonmodule.kafka.dto.model.ChannelEventType;
import com.bbebig.commonmodule.kafka.dto.model.ChannelType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChannelEventDto {

	@NotNull(message = "멤버 ID는 null일 수 없습니다.")
	private Long memberId;

	// CHANNEL_ENTER, CHANNEL_LEAVE
	@NotNull(message = "타입(type)은 null일 수 없습니다.")
	private ChannelEventType type;

	@NotNull(message = "채널 타입은 null일 수 없습니다.")
	private ChannelType channelType;

	private String sessionId;

	private Long lastReadMessageId;

	private Long lastReadSequence;

	// 서버에 있는 채널일 경우
	private Long serverId;

	@NotNull(message = "채널 ID는 null일 수 없습니다.")
	private Long channelId;

	@PastOrPresent(message = "이벤트 시간은 현재 시간 이전이어야 합니다.")
	private LocalDateTime eventTime;

	@JsonCreator
	public ChannelEventDto(
			@JsonProperty("memberId") Long memberId,
			@JsonProperty("type") ChannelEventType type,
			@JsonProperty("channelType") ChannelType channelType,
			@JsonProperty("sessionId") String sessionId,
			@JsonProperty("lastReadMessageId") Long lastReadMessageId,
			@JsonProperty("lastReadSequence") Long lastReadSequence,
			@JsonProperty("serverId") Long serverId,
			@JsonProperty("channelId") Long channelId,
			@JsonProperty("eventTime") LocalDateTime eventTime
	) {
		this.memberId = memberId;
		this.type = type;
		this.channelType = channelType;
		this.sessionId = sessionId;
		this.lastReadMessageId = lastReadMessageId;
		this.lastReadSequence = lastReadSequence;
		this.serverId = serverId;
		this.channelId = channelId;
		this.eventTime = eventTime;
	}

	public void updateSessionId(String sessionId) {
		this.sessionId = sessionId;
	}
}
