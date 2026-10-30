package com.qingyu.personalaios.feature.me

import androidx.lifecycle.ViewModelStore
import com.qingyu.personalaios.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModelTest {
    private class Repository : PersonalSpaceRepository {
        override val space = MutableStateFlow(PersonalSpace())
        var calls = 0
        var fail = false
        var cancelled = false
        override suspend fun avatar(source: String) {
            calls++; delay(100)
            if (cancelled) throw CancellationException()
            if (fail) error("invalid image")
            space.value = space.value.copy(avatar = source)
        }
        override suspend fun publish(body: String, sources: List<String>) {
            calls++; delay(100)
            if (cancelled) throw CancellationException()
            if (fail) error("disk full")
            space.value = space.value.copy(posts = space.value.posts + SpacePost("p", 1, body, sources))
        }
        override suspend fun deletePost(id: String) {
            calls++;delay(100);if(fail) error("disk full")
            space.value=space.value.copy(posts=space.value.posts.filterNot { it.id==id })
        }
        override suspend fun rename(name:String) { delay(100);if(fail) error("disk full");space.value=space.value.copy(name=name) }
    }
    @Test fun renamingTrimsAndPreservesAvatarAndPosts()=scenario { model,repo ->
        val original=PersonalSpace(avatar="photo",posts=listOf(SpacePost("p",1,"keep",emptyList())))
        repo.space.value=original;advanceUntilIdle();var saved=false
        model.rename("  晴郁  ") { saved=true };advanceUntilIdle()
        assertTrue(saved);assertEquals(original.copy(name="晴郁"),model.space.value)
    }
    @Test fun emptyAndFailedNamesNeverReplaceSavedProfile()=scenario { model,repo ->
        model.rename("  ");advanceUntilIdle();assertEquals("Doki",model.space.value.name);assertNotNull(model.error.value)
        repo.fail=true;model.rename("new");advanceUntilIdle();assertEquals("Doki",model.space.value.name);assertFalse(model.working.value)
    }
    private fun scenario(block: suspend TestScope.(MeViewModel, Repository) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = Repository(); val model = MeViewModel(repo)
        val store = ViewModelStore().apply { put("me", model) }
        try { block(model, repo) } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun fastDoubleTapPublishesOnlyOnce() = scenario { model, repo ->
        var saved = 0
        model.publish("hello", emptyList()) { saved++ }
        model.publish("hello", emptyList()) { saved++ }
        assertTrue(model.working.value); advanceUntilIdle()
        assertEquals(1, repo.calls); assertEquals(1, saved); assertFalse(model.working.value)
    }
    @Test fun emptyPostIsIgnoredButPhotoOnlyPostSaves() = scenario { model, repo ->
        var saved = false
        model.publish("   ", emptyList()) { saved = true }; runCurrent()
        assertEquals(0, repo.calls); assertFalse(saved)
        model.publish("", listOf("photo")) { saved = true }; advanceUntilIdle()
        assertTrue(saved); assertEquals(listOf("photo"), repo.space.value.posts.single().images)
    }
    @Test fun diskFailureLeavesComposerOpenAndAllowsRetry() = scenario { model, repo ->
        var saved = false; repo.fail = true
        model.publish("keep words", emptyList()) { saved = true }; advanceUntilIdle()
        assertFalse(saved); assertNotNull(model.error.value); assertFalse(model.working.value)
        repo.fail = false
        model.publish("keep words", emptyList()) { saved = true }; advanceUntilIdle()
        assertTrue(saved); assertNull(model.error.value)
        assertEquals("keep words", repo.space.value.posts.single().body)
    }
    @Test fun failedAvatarImportCanBeRetried() = scenario { model, repo ->
        repo.fail = true; model.avatar("photo"); advanceUntilIdle()
        assertNull(repo.space.value.avatar); assertNotNull(model.error.value)
        repo.fail = false; model.avatar("photo"); advanceUntilIdle()
        assertEquals("photo", repo.space.value.avatar); assertNull(model.error.value)
    }
    @Test fun cancellationDoesNotReportFailureOrCloseComposer() = scenario { model, repo ->
        repo.cancelled = true; var saved = false
        model.publish("words", emptyList()) { saved = true }; advanceUntilIdle()
        assertFalse(saved); assertNull(model.error.value); assertFalse(model.working.value)
        model.avatar("photo"); advanceUntilIdle()
        assertNull(model.error.value); assertFalse(model.working.value)
    }
    @Test fun deletionTargetsOnlyTheChosenPostAndPreventsDoubleSubmit()=scenario { model,repo ->
        repo.space.value=PersonalSpace(posts=listOf(SpacePost("one",1,"keep",emptyList()),SpacePost("two",2,"remove",emptyList())))
        model.deletePost("two");model.deletePost("two");advanceUntilIdle()
        assertEquals(1,repo.calls);assertEquals(listOf("one"),repo.space.value.posts.map { it.id });assertFalse(model.working.value)
    }
    @Test fun failedDeletionKeepsThePostAndAllowsRetry()=scenario { model,repo ->
        repo.space.value=PersonalSpace(posts=listOf(SpacePost("one",1,"keep",emptyList())));repo.fail=true
        model.deletePost("one");advanceUntilIdle();assertEquals(1,repo.space.value.posts.size);assertNotNull(model.error.value)
        repo.fail=false;model.deletePost("one");advanceUntilIdle();assertTrue(repo.space.value.posts.isEmpty());assertNull(model.error.value)
    }
}
