package com.flexa.core.data.db

import androidx.room.TypeConverter
import com.flexa.core.data.rest.RestRepository.Companion.json
import com.flexa.core.entity.TransactionFeePrice
import com.flexa.core.shared.LegacyFlexcode
import com.flexa.core.shared.Promotion
import kotlinx.serialization.encodeToString

class StringListConverter {
    @TypeConverter
    fun fromString(value: String): List<String> {
        return json.decodeFromString<List<String>>(value)
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return json.encodeToString(list)
    }
}

class ObjectListsConverter {
    @TypeConverter
    fun toLegacyFlexcode(value: String?): List<LegacyFlexcode> {
        return value?.let { json.decodeFromString<List<LegacyFlexcode>>(it) } ?: emptyList()
    }

    @TypeConverter
    fun fromLegacyFlexcode(list: List<LegacyFlexcode>?): String? {
        return list?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toPromotion(value: String?): List<Promotion> {
        return value?.let { json.decodeFromString<List<Promotion>>(it) }?: emptyList()
    }

    @TypeConverter
    fun fromPromotion(list: List<Promotion>?): String? {
        return list?.let { json.encodeToString(it) }
    }
}

class ObjectConverters {
    @TypeConverter
    fun fromTransactionFeePrice(item: TransactionFeePrice?): String? {
        return item?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toTransactionFeePrice(string: String?): TransactionFeePrice? {
        return string?.let { json.decodeFromString<TransactionFeePrice>(it) }
    }
}