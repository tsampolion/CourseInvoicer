package gr.edusuite.invoicer.ui.nav

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryStateOwnerTest {
    private class DraftViewModel(val handle: SavedStateHandle) : ViewModel()

    @Test fun registryRoundTripRestoresDraftsAndKeepsEntriesSeparate() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val factory = viewModelFactory { initializer { DraftViewModel(createSavedStateHandle()) } }
            fun model(owner: EntryStateOwner) = ViewModelProvider.create(owner, factory)[DraftViewModel::class]
            val first = EntryStateOwner(ViewModelStore())
            val second = EntryStateOwner(ViewModelStore())
            model(first).handle["draft.notes"] = "first entry"
            model(second).handle["draft.notes"] = "second entry"
            val firstSaved = first.save()
            val secondSaved = second.save()
            first.viewModelStore.clear()
            second.viewModelStore.clear()
            val restoredFirst = EntryStateOwner(ViewModelStore(), firstSaved)
            val restoredSecond = EntryStateOwner(ViewModelStore(), secondSaved)
            assertEquals("first entry", model(restoredFirst).handle.get<String>("draft.notes"))
            assertEquals("second entry", model(restoredSecond).handle.get<String>("draft.notes"))
            model(restoredFirst).handle.remove<String>("draft.notes")
            val cleared = EntryStateOwner(ViewModelStore(), restoredFirst.save())
            assertNull(model(cleared).handle.get<String>("draft.notes"))
            cleared.viewModelStore.clear()
            restoredFirst.viewModelStore.clear()
            restoredSecond.viewModelStore.clear()
        }
    }
}
