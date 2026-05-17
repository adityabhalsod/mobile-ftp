package com.mobileftp.domain.usecase

import com.mobileftp.data.repository.FtpServerRepository
import javax.inject.Inject

class StopServerUseCase @Inject constructor(
    private val repo: FtpServerRepository
) {
    suspend operator fun invoke(): Result<Unit> = repo.stop()
}
