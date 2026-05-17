package com.mobileftp.domain.usecase

import com.mobileftp.data.repository.ConnectionProfileRepository
import com.mobileftp.data.repository.FtpClientRepository
import com.mobileftp.domain.model.ConnectionProfile
import javax.inject.Inject

class ConnectClientUseCase @Inject constructor(
    private val client: FtpClientRepository,
    private val profiles: ConnectionProfileRepository
) {
    suspend operator fun invoke(profile: ConnectionProfile): Result<Unit> {
        val result = client.connect(profile)
        if (result.isSuccess && profile.id > 0L) {
            profiles.touch(profile.id)
        }
        return result
    }
}
