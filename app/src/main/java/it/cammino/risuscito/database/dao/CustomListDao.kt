package it.cammino.risuscito.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import it.cammino.risuscito.database.entities.CustomList
import it.cammino.risuscito.database.pojo.Posizione

@Suppress("unused")
@Dao
interface CustomListDao {

    @Query("SELECT * FROM customlist")
    fun all(): List<CustomList>

    @Query("DELETE FROM customlist")
    fun truncateTable()

    @Query("SELECT B.titolo, B.pagina, B.source, B.color, B.id, A.timestamp, A.position, A.notaPosizione FROM customlist A, canto B WHERE A.id = :id AND A.idCanto = B.id ORDER BY A.timestamp ASC")
    fun getList(id: Int): LiveData<List<Posizione>>

    @Query("SELECT B.titolo FROM customlist A , canto B WHERE A.id = :id AND A.position = :position AND A.idCanto = B.id")
    fun getTitoloByPosition(id: Int, position: Int): String?

    @Query("SELECT A.idCanto FROM customlist A WHERE A.id = :id AND A.position = :position")
    fun getIdByPosition(id: Int, position: Int): Int?

    @Query("SELECT * from customlist WHERE id = :id AND position = :position")
    fun getPosition(id: Int, position: Int): CustomList

    @Query("SELECT * from customlist WHERE id = :id AND position = :position AND idCanto = :idCanto")
    fun getPositionSpecific(id: Int, position: Int, idCanto: Int): CustomList

    @Query("UPDATE customlist SET idCanto = :idCanto WHERE id = :id AND position = :position")
    fun updatePositionNoTimestamp(idCanto: Int, id: Int, position: Int)

    @Query("UPDATE customlist SET notaPosizione = :notaCanto WHERE id = :id AND position = :position AND idCanto = :idCanto")
    fun updateNotaPosition(notaCanto: String, id: Int, position: Int, idCanto: Int)

    @Query("UPDATE customlist SET idCanto = :idCantoNew, notaPosizione = :notaCantoNew WHERE id = :id AND position = :position AND idCanto = :idCantoOld")
    fun updatePositionNoTimestamp(
        idCantoNew: Int,
        notaCantoNew: String,
        id: Int,
        position: Int,
        idCantoOld: Int
    )

    @Update
    fun updatePosition(position: CustomList): Int

    @Insert
    fun insertPosition(position: CustomList)

    @Query("DELETE FROM customlist WHERE id = :id")
    fun deleteListById(id: Int)

    @Delete
    fun deletePosition(position: CustomList)

    @Query("SELECT COUNT(*) FROM customlist WHERE id = :idLista AND position = :position AND idCanto = :idCanto")
    fun checkExistsPosition(idLista: Int, position: Int, idCanto: Int): Int
}

