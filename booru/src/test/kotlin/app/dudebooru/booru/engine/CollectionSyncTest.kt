package app.dudebooru.booru.engine

import app.dudebooru.booru.TestSupport
import app.dudebooru.booru.engine.danbooru.DanbooruEngine
import app.dudebooru.booru.engine.moebooru.MoebooruEngine
import app.dudebooru.booru.model.Credentials
import app.dudebooru.booru.site.EngineType
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CollectionSyncTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun danbooru() = DanbooruEngine(TestSupport.site(server, EngineType.DANBOORU), TestSupport.http())
    private fun moebooru() = MoebooruEngine(TestSupport.site(server, EngineType.MOEBOORU, salt = "choujin-steiner--PASSWORD--"), TestSupport.http())

    @Test
    fun danbooruLikeAndSave() = runBlocking {
        server.enqueue(TestSupport.json("{}"))
        server.enqueue(TestSupport.json("{}"))
        danbooru().pushCollectionState(42, like = true, save = true, session = Session(Credentials.ApiKey("dude", "key")))
        val vote = server.takeRequest()
        assertEquals("POST", vote.method)
        assertEquals("/posts/42/votes.json", vote.url.encodedPath)
        assertEquals("1", vote.url.queryParameter("score"))
        assertTrue(vote.headers["Authorization"]!!.startsWith("Basic "))
        val fav = server.takeRequest()
        assertEquals("/favorites.json", fav.url.encodedPath)
        assertEquals("42", fav.url.queryParameter("post_id"))
    }

    @Test
    fun danbooruUnsaveDeletesByPostIdAndToleratesAlreadyGone() = runBlocking {
        server.enqueue(TestSupport.json("""{"success":false,"message":"not found"}""", code = 404))
        danbooru().pushCollectionState(42, like = null, save = false, session = Session(Credentials.ApiKey("dude", "key")))
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/favorites/42.json", req.url.encodedPath)
        assertEquals(1, server.requestCount)
    }

    /** Приёмка ТЗ: лайк на Yande.re не затирает сохранение, снятие сохранения возвращает голос 1. */
    @Test
    fun moebooruLikeDoesNotOverwriteSave() = runBlocking {
        val session = Session(moebooru().credentials("dude", "hunter2"))
        // Лайк при неизвестном «сохранено»: читаем текущий голос (3) — менять нечего.
        server.enqueue(TestSupport.json("""{"success":true,"vote":3}"""))
        moebooru().pushCollectionState(7, like = true, save = null, session = session)
        assertEquals(1, server.requestCount)
        assertTrue("reading must not send score", !server.takeRequest().body!!.utf8().contains("score="))
    }

    @Test
    fun moebooruUnsaveWithLikeReturnsVoteOne() = runBlocking {
        val session = Session(moebooru().credentials("dude", "hunter2"))
        server.enqueue(TestSupport.json("""{"success":true}"""))
        moebooru().pushCollectionState(7, like = true, save = false, session = session)
        val form = server.takeRequest().body!!.utf8()
        assertTrue(form, form.contains("score=1"))
    }

    @Test
    fun moebooruUnsaveWithoutLikeRemovesVote() = runBlocking {
        val session = Session(moebooru().credentials("dude", "hunter2"))
        server.enqueue(TestSupport.json("""{"success":true}"""))
        moebooru().pushCollectionState(7, like = false, save = false, session = session)
        assertTrue(server.takeRequest().body!!.utf8().contains("score=0"))
    }

    @Test
    fun moebooruSaveIsVoteThreeAndAlreadyVotedIsFine() = runBlocking {
        val session = Session(moebooru().credentials("dude", "hunter2"))
        server.enqueue(TestSupport.json("""{"success":false,"reason":"Already voted"}""", code = 423))
        moebooru().pushCollectionState(7, like = false, save = true, session = session)
        assertTrue(server.takeRequest().body!!.utf8().contains("score=3"))
    }

    @Test
    fun favoritesQueries() = runBlocking {
        server.enqueue(TestSupport.json("[]"))
        danbooru().favorites(null, 100, Session(Credentials.ApiKey("dude", "k")))
        assertEquals("ordfav:dude", server.takeRequest().url.queryParameter("tags"))
        server.enqueue(TestSupport.json("""{"posts":[]}"""))
        moebooru().favorites(null, 100, Session(Credentials.PasswordHash("dude", "h")))
        assertEquals("vote:3:dude order:vote", server.takeRequest().url.queryParameter("tags"))
    }
}
