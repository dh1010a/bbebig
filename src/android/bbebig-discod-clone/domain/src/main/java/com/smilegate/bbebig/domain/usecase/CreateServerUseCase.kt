package com.smilegate.bbebig.domain.usecase

import com.smilegate.bbebig.data.repository.ServerRepository
import com.smilegate.bbebig.domain.model.ServerCreateDomainModel
import com.smilegate.bbebig.domain.model.param.ServerCreateParam
import com.smilegate.bbebig.domain.model.param.toRequestModel
import com.smilegate.bbebig.domain.model.toDomainModel
import dagger.Reusable
import javax.inject.Inject

@Reusable
class CreateServerUseCase @Inject constructor(
    private val serverRepository: ServerRepository,
) {
    suspend operator fun invoke(serverCreateParam: ServerCreateParam): ServerCreateDomainModel {
        return serverRepository.createServer(serverCreateParam.toRequestModel()).toDomainModel()
    }
}
