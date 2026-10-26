package com.zemmy.sigurvita.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zemmy.sigurvita.core.data.db.entity.CachedSeriesEntity

@Dao
interface SeriesDao {
    @Query("SELECT * FROM cached_series ORDER BY name ASC")
    fun getAllSeriesList(): List<CachedSeriesEntity>

    @Query("SELECT * FROM cached_series WHERE id = :seriesId")
    fun getSeriesById(seriesId: Int): CachedSeriesEntity?

    @Query("SELECT * FROM cached_series WHERE id IN (:ids)")
    fun getSeriesByIds(ids: List<Int>): List<CachedSeriesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSeries(series: List<CachedSeriesEntity>)

    @Query("DELETE FROM cached_series")
    fun clearAll(): Int
}
