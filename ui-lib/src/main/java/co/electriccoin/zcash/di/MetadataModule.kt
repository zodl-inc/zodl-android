package co.electriccoin.zcash.di

import co.electriccoin.zcash.ui.common.datasource.MetadataDataSource
import co.electriccoin.zcash.ui.common.datasource.MetadataDataSourceImpl
import co.electriccoin.zcash.ui.common.provider.DirectorySync
import co.electriccoin.zcash.ui.common.provider.MetadataKeyStorageProvider
import co.electriccoin.zcash.ui.common.provider.MetadataKeyStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.MetadataProvider
import co.electriccoin.zcash.ui.common.provider.MetadataProviderImpl
import co.electriccoin.zcash.ui.common.provider.MetadataStorageProvider
import co.electriccoin.zcash.ui.common.provider.MetadataStorageProviderImpl
import co.electriccoin.zcash.ui.common.provider.OsDirectorySync
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.repository.MetadataRepositoryImpl
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataEncryptor
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataEncryptorImpl
import co.electriccoin.zcash.ui.common.serialization.metadata.MetadataSerializer
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val metadataModule =
    module {
        singleOf(::MetadataSerializer)
        singleOf(::MetadataEncryptorImpl) bind MetadataEncryptor::class
        factoryOf(::MetadataKeyStorageProviderImpl) bind MetadataKeyStorageProvider::class
        factoryOf(::MetadataStorageProviderImpl) bind MetadataStorageProvider::class
        factoryOf(::OsDirectorySync) bind DirectorySync::class
        factoryOf(::MetadataProviderImpl) bind MetadataProvider::class
        singleOf(::MetadataDataSourceImpl) bind MetadataDataSource::class
        singleOf(::MetadataRepositoryImpl) bind MetadataRepository::class
    }
