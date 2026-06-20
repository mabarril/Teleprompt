package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Song::class], version = 1, exportSchema = false)
abstract class SongDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var INSTANCE: SongDatabase? = null

        fun getDatabase(context: Context): SongDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SongDatabase::class.java,
                    "song_database"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Pre-populate database after creation
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                populateDatabase(database.songDao())
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun populateDatabase(songDao: SongDao) {
            val evidencasLyrics = """quando eu digo que nao quero mais voce
e porque eu te quero
eu nego as aparencias
disfarco as evidencias
mas pra que viver fingindo
se eu nao posso enganar meu coracao
eu sei que te amo
chega de mentiras
de negar o meu desejo
eu quero o teu calor
como as folhas para o vento
um amor assim e muito mais
e sinto o teu cheiro
e segredo e pecado
e amor e paixao""".trimMargin()

            val garotaLyrics = """olha que coisa mais linda
mais cheia de graca
e ela menina
que vem e que passa
num doce balanco
a caminho do mar
moca do corpo dourado
do sol de ipanema
o seu balancado e mais que um poema
e a coisa mais linda que eu ja vi passar""".trimMargin()

            val asaBrancaLyrics = """quando olhei a terra ardendo
qual fogueira de sao joao
eu perguntei a deus do ceu
por que tamanha judiacao
como um bando de asa branca
bateu asas do sertao
entao eu disse adeus rosinha
guarda contigo o meu coracao""".trimMargin()

            val imagineLyrics = """imagine there is no heaven
it is easy if you try
no hell below us
above us only sky
imagine all the people
living for today
imagine there is no countries
it is not hard to do
nothing to kill or die for
and no religion too
imagine all the people
living life in peace""".trimMargin()

            songDao.insertSong(
                Song(
                    title = "Evidências",
                    artist = "Chitãozinho & Xororó",
                    lyrics = evidencasLyrics,
                    isCustom = false
                )
            )
            songDao.insertSong(
                Song(
                    title = "Garota de Ipanema",
                    artist = "Tom Jobim & Vinícius de Moraes",
                    lyrics = garotaLyrics,
                    isCustom = false
                )
            )
            songDao.insertSong(
                Song(
                    title = "Asa Branca",
                    artist = "Luiz Gonzaga",
                    lyrics = asaBrancaLyrics,
                    isCustom = false
                )
            )
            songDao.insertSong(
                Song(
                    title = "Imagine",
                    artist = "John Lennon",
                    lyrics = imagineLyrics,
                    isCustom = false
                )
            )
        }
    }
}
