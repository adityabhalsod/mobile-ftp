package com.mobileftp.domain.usecase

import com.mobileftp.data.repository.FtpClientRepository
import com.mobileftp.domain.model.RemoteFile
import javax.inject.Inject

class ListDirectoryUseCase @Inject constructor(
    private val client: FtpClientRepository
) {
    suspend operator fun invoke(path: String): Result<List<RemoteFile>> = runCatching {
        client.list(path)
    }
}
