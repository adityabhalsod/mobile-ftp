package com.mobileftp.domain.usecase

import com.mobileftp.data.repository.FtpServerRepository
import com.mobileftp.domain.model.ServerConfig
import javax.inject.Inject

class StartServerUseCase @Inject constructor(
    private val repo: FtpServerRepository
) {
    suspend operator fun invoke(config: ServerConfig): Result<Unit> = repo.start(config)
}
